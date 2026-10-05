package app.edroid.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.edroid.EDroidApp
import app.edroid.data.RecentSearchesStore
import app.edroid.data.fdroid.RepoHub
import app.edroid.data.fdroid.model.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class SearchUiState(
    val query: String = "",
    val recentSearches: List<String> = emptyList(),
    /** Category id → display name, sorted by name. */
    val categories: List<Pair<String, String>> = emptyList(),
    val results: List<App> = emptyList(),
    val indexReady: Boolean = false,
    /** Enabled repos as id → name, in the order they were added; filter chips show when there are several. */
    val repos: List<Pair<String, String>> = emptyList(),
    /** Id of the repo results are narrowed to, or null to search every enabled repo. */
    val selectedRepoId: String? = null,
) {
    /** True when the user is actively filtering and results should replace the browse view. */
    val showResults: Boolean get() = query.isNotBlank()
}

class SearchViewModel(
    private val repository: RepoHub,
    private val recentSearches: RecentSearchesStore,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedRepo = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SearchUiState> =
        combine(
            repository.snapshot,
            repository.perRepo,
            query,
            selectedRepo,
            recentSearches.recent,
        ) { snapshot, perRepo, q, selected, recent ->
            // A repo that has since been turned off or removed no longer narrows anything.
            val repoId = selected?.takeIf { id -> perRepo.size > 1 && perRepo.any { it.first.id == id } }
            // Filtering reads the repo's own snapshot, so apps whose merged copy came from
            // another repo still show up under every repo that lists them.
            val source = if (repoId == null) snapshot else perRepo.first { it.first.id == repoId }.second
            val needle = q.trim()
            val results = if (needle.isEmpty()) emptyList() else source?.apps.orEmpty().filter { it.matches(needle) }
            SearchUiState(
                query = q,
                recentSearches = recent,
                categories = snapshot?.repo?.categories?.toList()?.sortedBy { it.second }.orEmpty(),
                results = results,
                indexReady = source != null,
                repos = perRepo.map { (repo, _) -> repo.id to repo.name },
                selectedRepoId = repoId,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) = query.update { value }

    /** Called when the user submits the query (IME search action): remembers it as a recent search. */
    fun commitSearch() = recentSearches.add(query.value)

    /** Re-runs a recent search. */
    fun onRecentSelected(value: String) {
        query.value = value
        recentSearches.add(value)
    }

    fun onRemoveRecent(value: String) = recentSearches.remove(value)

    fun clearQuery() = query.update { "" }

    /** Narrows results to [id], or back to every repo when [id] is null or already selected. */
    fun onRepoSelected(id: String?) = selectedRepo.update { current -> id.takeIf { it != current } }

    private fun App.matches(needle: String): Boolean =
        name.contains(needle, ignoreCase = true) ||
            packageName.contains(needle, ignoreCase = true) ||
            summary?.contains(needle, ignoreCase = true) == true

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EDroidApp
                SearchViewModel(app.container.repoHub, app.container.recentSearches)
            }
        }
    }
}
