package app.edroid.data.fdroid

import android.util.Log
import app.edroid.data.fdroid.model.App
import app.edroid.data.fdroid.model.Entry
import app.edroid.data.fdroid.model.PackageV2
import app.edroid.data.fdroid.model.RepoV2
import app.edroid.data.fdroid.model.Repository
import app.edroid.data.fdroid.model.localized
import app.edroid.data.fdroid.net.FDroidRepoService
import app.edroid.data.fdroid.parser.IndexV2StreamParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Repo-level info shown in the UI, extracted from the `repo` section of the index. */
data class RepoInfo(
    val name: String,
    val description: String?,
    val iconUrl: String?,
    val timestamp: Long,
    /** Category id → localized display name. */
    val categories: Map<String, String>,
    /** Anti-feature id → localized display name. */
    val antiFeatures: Map<String, String>,
)

sealed interface RepoState {
    data object Idle : RepoState
    data class Loading(val phase: Phase, val progress: Float?) : RepoState {
        enum class Phase { FETCHING_ENTRY, DOWNLOADING_INDEX, PARSING }
    }
    data class Ready(val repo: RepoInfo, val apps: List<App>, val fromCache: Boolean) : RepoState
    data class Error(val message: String, val cause: Throwable?) : RepoState
}

/**
 * Application-scoped source of truth for one F-Droid repository's package list.
 *
 * Refresh flow:
 *  1. Fetch + verify `entry.jar`.
 *  2. If the cached index has the same timestamp, reuse it; otherwise download `index-v2.json`
 *     (size + sha256 verified) into app storage.
 *  3. Stream-parse the index into lightweight [App] rows.
 *
 * The on-disk copy is kept so later features (app details, search, diffs) can re-read it.
 */
class FDroidRepository(
    val repo: Repository,
    private val service: FDroidRepoService,
    private val storageDir: File,
    private val json: Json,
    private val parser: IndexV2StreamParser = IndexV2StreamParser(json),
) {
    private val _state = MutableStateFlow<RepoState>(RepoState.Idle)
    val state: StateFlow<RepoState> = _state.asStateFlow()

    private val refreshMutex = Mutex()
    private val repoDir get() = File(storageDir, "repos/${repo.id}")
    private val indexFile get() = File(repoDir, "index-v2.json")
    private val metaFile get() = File(repoDir, "meta.json")

    @Serializable
    private data class CacheMeta(val indexTimestamp: Long, val fetchedAt: Long)

    /**
     * Loads the package list. Uses the on-disk index when it is still current (or when the network
     * is unavailable), and downloads a fresh one otherwise. Concurrent calls are coalesced.
     */
    suspend fun refresh(force: Boolean = false) = refreshMutex.withLock {
        try {
            _state.value = RepoState.Loading(RepoState.Loading.Phase.FETCHING_ENTRY, null)
            val cached = readCacheMeta()

            val entry: Entry? = try {
                service.fetchEntry(repo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (cached == null || !indexFile.exists()) throw e
                Log.w(TAG, "entry.jar unreachable, falling back to cached index", e)
                null
            }

            val needsDownload = entry != null &&
                (force || cached == null || cached.indexTimestamp != entry.timestamp || !indexFile.exists())

            if (entry != null && needsDownload) {
                _state.value = RepoState.Loading(RepoState.Loading.Phase.DOWNLOADING_INDEX, 0f)
                service.downloadIndex(repo, entry.index, indexFile) { read, total ->
                    _state.value = RepoState.Loading(
                        RepoState.Loading.Phase.DOWNLOADING_INDEX,
                        if (total > 0) read.toFloat() / total else null,
                    )
                }
                writeCacheMeta(CacheMeta(entry.timestamp, System.currentTimeMillis()))
            }

            val expected = entry?.index?.numPackages ?: -1
            val (info, apps) = parseIndex(expected)
            _state.value = RepoState.Ready(info, apps, fromCache = entry == null || !needsDownload)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Refresh failed for ${repo.address}", e)
            _state.value = RepoState.Error(e.message ?: e.javaClass.simpleName, e)
        }
    }

    private suspend fun parseIndex(expectedPackages: Int): Pair<RepoInfo, List<App>> =
        withContext(Dispatchers.Default) {
            _state.value = RepoState.Loading(RepoState.Loading.Phase.PARSING, 0f)
            var info: RepoInfo? = null
            val apps = ArrayList<App>(if (expectedPackages > 0) expectedPackages else 4096)
            indexFile.inputStream().buffered(256 * 1024).use { input ->
                parser.parse(input, object : IndexV2StreamParser.Visitor {
                    override fun onRepo(repo: RepoV2) {
                        info = repo.toInfo()
                    }

                    override fun onPackage(packageName: String, pkg: PackageV2) {
                        apps += App.from(packageName, pkg, repo)
                        if (expectedPackages > 0 && apps.size % 100 == 0) {
                            _state.update {
                                RepoState.Loading(
                                    RepoState.Loading.Phase.PARSING,
                                    apps.size.toFloat() / expectedPackages,
                                )
                            }
                        }
                    }
                })
            }
            val repoInfo = info ?: error("Index has no 'repo' section")
            apps.sortByDescending { it.lastUpdated }
            repoInfo to apps
        }

    private fun RepoV2.toInfo() = RepoInfo(
        name = name.localized() ?: repo.name,
        description = description.localized(),
        iconUrl = repo.resolve(icon.localized()),
        timestamp = timestamp,
        categories = categories.mapValues { (id, c) -> c.name.localized() ?: id },
        antiFeatures = antiFeatures.mapValues { (id, a) -> a.name.localized() ?: id },
    )

    private fun readCacheMeta(): CacheMeta? = runCatching {
        if (!metaFile.exists()) null else json.decodeFromString(CacheMeta.serializer(), metaFile.readText())
    }.getOrNull()

    private fun writeCacheMeta(meta: CacheMeta) {
        repoDir.mkdirs()
        metaFile.writeText(json.encodeToString(CacheMeta.serializer(), meta))
    }

    companion object {
        private const val TAG = "FDroidRepository"
    }
}
