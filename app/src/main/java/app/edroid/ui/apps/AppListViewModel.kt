package app.edroid.ui.apps

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.edroid.EDroidApp
import app.edroid.R
import app.edroid.data.fdroid.RepoHub
import app.edroid.data.fdroid.RepoInfo
import app.edroid.data.fdroid.SyncState
import app.edroid.data.fdroid.model.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import kotlin.random.Random

/** Orderings offered for the "Other apps" grid. */
enum class AppSort(@param:StringRes val labelRes: Int) {
    /** Most recently published first. */
    Newest(R.string.sort_newest),
    /** Most recently updated first; the order the repository already hands apps over in. */
    Updated(R.string.sort_updated),
    /** Alphabetical by the device locale's collation rules, ignoring leading symbols. */
    Name(R.string.sort_name),
}

data class AppListUiState(
    val sync: SyncState = SyncState.Idle,
    /** The "random app you may like" spotlight; null until an index is loaded. */
    val featured: App? = null,
    /** Every other app in the index, ordered by [sort], for the grid below the spotlight. */
    val apps: List<App> = emptyList(),
    val sort: AppSort = AppSort.Updated,
    /** Null until an index (cached or fresh) has been parsed. */
    val repo: RepoInfo? = null,
) {
    val hasData: Boolean get() = repo != null
}

/** Everything on the page that only changes with the index, the sort or the spotlight seed. */
private data class HomeContent(
    val featured: App?,
    val apps: List<App>,
    val sort: AppSort,
    val repo: RepoInfo?,
)

class AppListViewModel(private val repository: RepoHub) : ViewModel() {

    // Seeds the spotlight pick so it stays put across index refreshes and recompositions, and only
    // changes when the user asks for a new one by pulling to refresh.
    private val featuredSeed = MutableStateFlow(Random.nextLong())
    private val sort = MutableStateFlow(AppSort.Updated)
    private var refreshJob: Job? = null

    // Kept apart from the sync state, which ticks with download progress, so thousands of apps
    // aren't re-filtered and re-sorted on every tick.
    private val content = combine(repository.snapshot, featuredSeed, sort) { snapshot, seed, order ->
        val all = snapshot?.apps.orEmpty()
        val featured = pickFeatured(all, seed)
        HomeContent(
            featured = featured,
            apps = all.filter { it.packageName != featured?.packageName }.sortedWith(order.comparator()),
            sort = order,
            repo = snapshot?.repo,
        )
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<AppListUiState> =
        combine(content, repository.sync) { content, sync ->
            AppListUiState(
                sync = sync,
                featured = content.featured,
                apps = content.apps,
                sort = content.sort,
                repo = content.repo,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListUiState())

    init {
        refresh()
    }

    fun refresh(force: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { repository.refresh(force) }
    }

    /** Pull-to-refresh: re-syncs the index and draws a new spotlight app. */
    fun refreshAndShuffle() {
        featuredSeed.value = Random.nextLong()
        refresh()
    }

    /** Draws a new spotlight app without re-syncing. */
    fun shuffleFeatured() {
        featuredSeed.value = Random.nextLong()
    }

    fun onSortSelected(order: AppSort) {
        sort.value = order
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EDroidApp
                AppListViewModel(app.container.repoHub)
            }
        }
    }
}

/** A random app worth showing off: one with an icon and a summary when the index has any. */
private fun pickFeatured(apps: List<App>, seed: Long): App? {
    val presentable = apps.filter { it.iconUrl != null && !it.summary.isNullOrBlank() }
    return presentable.ifEmpty { apps }.randomOrNull(Random(seed))
}

private fun AppSort.comparator(): Comparator<App> = when (this) {
    AppSort.Newest -> compareByDescending { it.added }
    AppSort.Updated -> compareByDescending { it.lastUpdated }
    AppSort.Name -> {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        // Leading tags and brackets ("[ROOT] Night Light", "< polycule >") would otherwise
        // bunch up ahead of "A"; file those apps under their first letter instead.
        compareBy(collator) { app -> app.name.trimStart { !it.isLetterOrDigit() } }
    }
}
