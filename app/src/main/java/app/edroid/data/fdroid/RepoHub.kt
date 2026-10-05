package app.edroid.data.fdroid

import app.edroid.data.RepoStore
import app.edroid.data.fdroid.model.App
import app.edroid.data.fdroid.model.AppDetails
import app.edroid.data.fdroid.model.Repository
import app.edroid.data.fdroid.net.FDroidRepoService
import app.edroid.data.fdroid.net.SignedEntryReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-scoped view over every enabled repository in [RepoStore], exposing the same
 * `snapshot` / `sync` / `app` / `details` surface a single [FDroidRepository] does, so screens
 * don't need to know how many repos there are.
 *
 * Apps listed by several repos appear once: the copy with the newest version wins (the most
 * recently updated one on a tie). Turning a repo on fetches it straight away; turning it off only
 * hides its apps, keeping its cache for when it comes back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepoHub(
    private val store: RepoStore,
    private val service: FDroidRepoService,
    private val scope: CoroutineScope,
    private val createRepository: (Repository) -> FDroidRepository,
) {
    private val repositories = ConcurrentHashMap<String, FDroidRepository>()

    /** The enabled repos with their current settings, each paired with its loader. */
    private val enabled: StateFlow<List<Pair<Repository, FDroidRepository>>> = store.repos
        .map { repos -> repos.filter { it.enabled }.map { it to repository(it) } }
        .stateIn(scope, SharingStarted.Eagerly, store.repos.value.filter { it.enabled }.map { it to repository(it) })

    val snapshot: StateFlow<RepoSnapshot?> = enabled
        .flatMapLatest { list ->
            if (list.isEmpty()) flowOf(null) else combine(list.map { it.second.snapshot }) { merge(it.filterNotNull()) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, null)

    val sync: StateFlow<SyncState> = enabled
        .flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(SyncState.Idle)
            } else {
                combine(list.map { it.second.sync }) { states -> aggregate(list.map { it.first }, states.toList()) }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SyncState.Idle)

    init {
        // Fetch a repo as soon as it is turned on or added, rather than at the next pull-to-refresh.
        // The first emission is skipped: the Home tab refreshes everything on start.
        scope.launch {
            var known: Set<String>? = null
            enabled.map { list -> list.map { it.first.id }.toSet() }.distinctUntilChanged().collect { ids ->
                val previous = known
                known = ids
                if (previous != null) (ids - previous).forEach { id -> launch { refreshOne(id, force = false) } }
            }
        }
    }

    /** The loader for [repo], created on first use and kept even while the repo is disabled. */
    fun repository(repo: Repository): FDroidRepository =
        repositories.getOrPut(repo.id) { createRepository(repo) }

    /** Refreshes every enabled repo in parallel; each one's failures stay in its own sync state. */
    suspend fun refresh(force: Boolean = false) = coroutineScope {
        enabled.value.forEach { (repo, _) -> launch { refreshOne(repo.id, force) } }
    }

    private suspend fun refreshOne(id: String, force: Boolean) {
        val (repo, loader) = enabled.value.firstOrNull { it.first.id == id } ?: return
        loader.refresh(force)
        loader.snapshot.value?.repo?.name?.takeIf { it.isNotBlank() && it != repo.name }?.let { store.rename(id, it) }
    }

    /** The merged row for [packageName], if an enabled repo lists it. */
    fun app(packageName: String): App? = snapshot.value?.apps?.firstOrNull { it.packageName == packageName }

    /** Full details for [packageName], read from the repo whose copy won the merge. */
    suspend fun details(packageName: String): AppDetails? {
        checkNotNull(snapshot.value) { "Package index not downloaded yet" }
        val app = app(packageName) ?: return null
        return repositories[app.repoId]?.details(packageName)
    }

    /**
     * Downloads [address]'s signed entry without pinning a key, returning the fingerprint of the
     * certificate that signed it so the user can check it before trusting the repo.
     */
    suspend fun probe(address: String): SignedEntryReader.Result =
        service.fetchEntry(Repository(address = address, name = address, fingerprint = null))

    fun add(repo: Repository) = store.add(repo)

    fun setEnabled(id: String, enabled: Boolean) = store.setEnabled(id, enabled)

    /** Removes a user-added repo and deletes its cached index in the background. */
    fun remove(repo: Repository) {
        if (repo.isDefault) return
        store.remove(repo.id)
        repositories.remove(repo.id)?.let { scope.launch { it.deleteCache() } }
    }

    private fun merge(snapshots: List<RepoSnapshot>): RepoSnapshot? {
        if (snapshots.isEmpty()) return null
        if (snapshots.size == 1) return snapshots.single()
        val byPackage = HashMap<String, App>(snapshots.sumOf { it.apps.size })
        snapshots.forEach { snapshot ->
            snapshot.apps.forEach { app ->
                byPackage.merge(app.packageName, app) { a, b -> if (NEWEST_FIRST.compare(a, b) <= 0) a else b }
            }
        }
        val infos = snapshots.map { it.repo }
        val info = RepoInfo(
            name = infos.joinToString { it.name },
            description = null,
            iconUrl = null,
            timestamp = infos.maxOf { it.timestamp },
            // Earlier repos win name clashes, so F-Droid's wording is kept for shared ids.
            categories = infos.reversed().fold(emptyMap<String, String>()) { acc, it -> acc + it.categories },
            antiFeatures = infos.reversed().fold(emptyMap<String, String>()) { acc, it -> acc + it.antiFeatures },
        )
        val apps = byPackage.values.sortedByDescending { it.lastUpdated }
        return RepoSnapshot(info, apps, fromCache = snapshots.all { it.fromCache })
    }

    /** Loading while any repo loads; otherwise an error naming every repo that failed. */
    private fun aggregate(repos: List<Repository>, states: List<SyncState>): SyncState {
        val loading = states.filterIsInstance<SyncState.Loading>()
        if (loading.isNotEmpty()) {
            val progress = loading.map { it.progress }
            return SyncState.Loading(
                phase = loading.minOf { it.phase },
                progress = if (progress.any { it == null }) null else progress.filterNotNull().average().toFloat(),
            )
        }
        val errors = repos.zip(states).filter { it.second is SyncState.Error }
        if (errors.isEmpty()) return SyncState.Idle
        if (repos.size == 1) return errors.single().second
        return SyncState.Error(
            message = errors.joinToString("\n") { (repo, state) -> "${repo.name}: ${(state as SyncState.Error).message}" },
            cause = (errors.first().second as SyncState.Error).cause,
        )
    }

    private companion object {
        val NEWEST_FIRST: Comparator<App> =
            compareByDescending<App> { it.latestVersionCode ?: Long.MIN_VALUE }.thenByDescending { it.lastUpdated }
    }
}
