package app.edroid.data.fdroid

import android.util.Log
import android.util.LruCache
import app.edroid.data.fdroid.model.App
import app.edroid.data.fdroid.model.AppDetails
import app.edroid.data.fdroid.model.Entry
import app.edroid.data.fdroid.model.PackageV2
import app.edroid.data.fdroid.model.RepoV2
import app.edroid.data.fdroid.model.Repository
import app.edroid.data.fdroid.model.localized
import app.edroid.data.fdroid.net.FDroidRepoService
import app.edroid.data.fdroid.parser.IndexV2StreamParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File

/** Repo-level info shown in the UI, extracted from the `repo` section of the index. */
@Serializable
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

/** A fully parsed index. Stays visible while a newer one is being fetched. */
data class RepoSnapshot(val repo: RepoInfo, val apps: List<App>, val fromCache: Boolean)

/** What the repository is doing right now, independent of whether a [RepoSnapshot] exists. */
sealed interface SyncState {
    data object Idle : SyncState
    data class Loading(val phase: Phase, val progress: Float?) : SyncState {
        enum class Phase { FETCHING_ENTRY, DOWNLOADING_INDEX, PARSING }
    }
    data class Error(val message: String, val cause: Throwable?) : SyncState
}

/**
 * Application-scoped source of truth for one F-Droid repository's package list.
 *
 * Refresh flow:
 *  1. If nothing is loaded yet and a cached index exists, load it so the UI has something to
 *     show straight away: from the compact `apps.json` (a few MB, decoded in well under a second)
 *     when it matches the cached index, or by re-parsing the full index otherwise.
 *  2. Fetch + verify `entry.jar`.
 *  3. If the cached index has the same timestamp, keep it; otherwise download `index-v2.json`
 *     (size + sha256 verified) into app storage, parse that, and rewrite `apps.json`.
 *
 * [snapshot] carries the data and [sync] the progress, so a refresh never blanks the list.
 * The on-disk index is kept so later features (app details, search, diffs) can re-read it.
 */
class FDroidRepository(
    val repo: Repository,
    private val service: FDroidRepoService,
    private val storageDir: File,
    private val json: Json,
    private val parser: IndexV2StreamParser = IndexV2StreamParser(json),
) {
    private val _snapshot = MutableStateFlow<RepoSnapshot?>(null)
    val snapshot: StateFlow<RepoSnapshot?> = _snapshot.asStateFlow()

    private val _sync = MutableStateFlow<SyncState>(SyncState.Idle)
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    private val refreshMutex = Mutex()
    private val detailsCache = LruCache<String, AppDetails>(DETAILS_CACHE_SIZE)
    private val repoDir get() = File(storageDir, "repos/${repo.id}")
    private val indexFile get() = File(repoDir, "index-v2.json")
    private val metaFile get() = File(repoDir, "meta.json")
    private val appsFile get() = File(repoDir, "apps.json")

    @Serializable
    private data class CacheMeta(val indexTimestamp: Long, val fetchedAt: Long, val numPackages: Int = 0)

    /**
     * The parsed rows of one index, keyed by that index's timestamp so a stale file is never
     * shown against a newer index. [version] is bumped whenever [App] or [RepoInfo] change shape.
     */
    @Serializable
    private data class AppsCache(
        val version: Int,
        val indexTimestamp: Long,
        val repo: RepoInfo,
        val apps: List<App>,
    )

    /**
     * Loads the package list. Publishes the on-disk index first when nothing is loaded yet, then
     * downloads a fresh one if the repository has a newer timestamp (or when [force] is set).
     * Network failures keep the current snapshot. Concurrent calls are coalesced.
     */
    suspend fun refresh(force: Boolean = false) = refreshMutex.withLock {
        try {
            val cached = readCacheMeta()
            if (_snapshot.value == null && cached != null && indexFile.exists()) {
                warmStart(cached)
            }

            _sync.value = SyncState.Loading(SyncState.Loading.Phase.FETCHING_ENTRY, null)
            val entry: Entry? = try {
                service.fetchEntry(repo).entry
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_snapshot.value == null) throw e
                Log.w(TAG, "entry.jar unreachable, keeping cached index", e)
                null
            }

            val needsDownload = entry != null &&
                (force || cached == null || cached.indexTimestamp != entry.timestamp || !indexFile.exists())

            if (entry != null && needsDownload) {
                _sync.value = SyncState.Loading(SyncState.Loading.Phase.DOWNLOADING_INDEX, 0f)
                service.downloadIndex(repo, entry.index, indexFile) { read, total ->
                    _sync.value = SyncState.Loading(
                        SyncState.Loading.Phase.DOWNLOADING_INDEX,
                        if (total > 0) read.toFloat() / total else null,
                    )
                }
                writeCacheMeta(CacheMeta(entry.timestamp, System.currentTimeMillis(), entry.index.numPackages))
                detailsCache.evictAll()
            }

            if (needsDownload || _snapshot.value == null) {
                val (info, apps) = parseIndex(entry?.index?.numPackages ?: -1)
                _snapshot.value = RepoSnapshot(info, apps, fromCache = !needsDownload)
                writeAppsCache(entry?.timestamp ?: cached?.indexTimestamp ?: info.timestamp, info, apps)
            }
            _sync.value = SyncState.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Refresh failed for ${repo.address}", e)
            _sync.value = SyncState.Error(e.message ?: e.javaClass.simpleName, e)
        }
    }

    /** Forgets the loaded index and deletes everything cached on disk for this repo. */
    suspend fun deleteCache() = refreshMutex.withLock {
        _snapshot.value = null
        _sync.value = SyncState.Idle
        detailsCache.evictAll()
        withContext(Dispatchers.IO) { repoDir.deleteRecursively() }
        Unit
    }

    /** The row for [packageName] in the current snapshot, if one is loaded. */
    fun app(packageName: String): App? = _snapshot.value?.apps?.firstOrNull { it.packageName == packageName }

    /**
     * Full details for one package, re-read from the on-disk index since the in-memory [App] rows
     * drop descriptions, media and older versions. Recently opened packages are kept in memory.
     * Returns null when the index doesn't list the package.
     */
    suspend fun details(packageName: String): AppDetails? {
        detailsCache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            // A refresh swaps the file by rename, so a scan already running keeps reading the old one.
            check(indexFile.exists()) { "Package index not downloaded yet" }
            val pkg = indexFile.inputStream().buffered(256 * 1024).use { input ->
                parser.findPackage(input, packageName) { ensureActive() }
            }
            pkg?.let { AppDetails.from(packageName, it, repo) }?.also { detailsCache.put(packageName, it) }
        }
    }

    /**
     * Best-effort load of the cached data: the compact rows file first, the full index as a
     * fallback. A corrupt file is ignored and the index is re-downloaded later if needed.
     */
    private suspend fun warmStart(cached: CacheMeta) {
        try {
            _sync.value = SyncState.Loading(SyncState.Loading.Phase.PARSING, null)
            val started = System.currentTimeMillis()
            val fast = readAppsCache(cached.indexTimestamp)
            if (fast != null) {
                _snapshot.value = RepoSnapshot(fast.repo, fast.apps, fromCache = true)
                Log.i(TAG, "Loaded ${fast.apps.size} apps from apps.json in ${System.currentTimeMillis() - started} ms")
                return
            }
            val (info, apps) = parseIndex(cached.numPackages.takeIf { it > 0 } ?: -1)
            _snapshot.value = RepoSnapshot(info, apps, fromCache = true)
            writeAppsCache(cached.indexTimestamp, info, apps)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cached index unreadable, ignoring", e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun readAppsCache(indexTimestamp: Long): AppsCache? = withContext(Dispatchers.Default) {
        if (!appsFile.exists()) return@withContext null
        runCatching {
            appsFile.inputStream().buffered(256 * 1024).use { json.decodeFromStream(AppsCache.serializer(), it) }
        }
            .onFailure { Log.w(TAG, "apps.json unreadable, will re-parse index", it) }
            .getOrNull()
            ?.takeIf { it.version == APPS_CACHE_VERSION && it.indexTimestamp == indexTimestamp }
    }

    /** Written atomically (temp file + rename) so a crash mid-write can't leave a torn cache. */
    private suspend fun writeAppsCache(indexTimestamp: Long, info: RepoInfo, apps: List<App>) =
        withContext(Dispatchers.IO) {
            runCatching {
                repoDir.mkdirs()
                val tmp = File(repoDir, "apps.json.tmp")
                tmp.writeText(
                    json.encodeToString(AppsCache.serializer(), AppsCache(APPS_CACHE_VERSION, indexTimestamp, info, apps)),
                )
                if (!tmp.renameTo(appsFile)) {
                    appsFile.delete()
                    check(tmp.renameTo(appsFile)) { "rename failed" }
                }
            }.onFailure { Log.w(TAG, "Could not write apps.json", it) }
            Unit
        }

    private suspend fun parseIndex(expectedPackages: Int): Pair<RepoInfo, List<App>> =
        withContext(Dispatchers.Default) {
            _sync.value = SyncState.Loading(SyncState.Loading.Phase.PARSING, 0f)
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
                            _sync.update {
                                SyncState.Loading(
                                    SyncState.Loading.Phase.PARSING,
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
        private const val APPS_CACHE_VERSION = 2
        private const val DETAILS_CACHE_SIZE = 16
    }
}
