package app.edroid.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.edroid.EDroidApp
import app.edroid.data.RecentSearchesStore
import app.edroid.data.fdroid.FDroidRepository
import app.edroid.data.fdroid.RepoState
import app.edroid.data.fdroid.model.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class SearchUiState(
    val query: String = "",
    /** Selected category id, or null. */
    val selectedCategory: String? = null,
    val selectedCategoryName: String? = null,
    val recentSearches: List<String> = emptyList(),
    /** Category id → display name, sorted by name. */
    val categories: List<Pair<String, String>> = emptyList(),
    val results: List<App> = emptyList(),
    val indexReady: Boolean = false,
) {
    /** True when the user is actively filtering and results should replace the browse view. */
    val showResults: Boolean get() = query.isNotBlank() || selectedCategory != null
}

class SearchViewModel(
    private val repository: FDroidRepository,
    private val recentSearches: RecentSearchesStore,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SearchUiState> =
        combine(repository.state, query, selectedCategory, recentSearches.recent) { repoState, q, cat, recent ->
            val ready = repoState as? RepoState.Ready
            val all = ready?.apps.orEmpty()
            val needle = q.trim()
            val results = if (needle.isEmpty() && cat == null) {
                emptyList()
            } else {
                all.asSequence()
                    .filter { cat == null || cat in it.categories }
                    .filter { needle.isEmpty() || it.matches(needle) }
                    .toList()
            }
            SearchUiState(
                query = q,
                selectedCategory = cat,
                selectedCategoryName = cat?.let { ready?.repo?.categories?.get(it) ?: it },
                recentSearches = recent,
                categories = ready?.repo?.categories?.toList()?.sortedBy { it.second }.orEmpty(),
                results = results,
                indexReady = ready != null,
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

    fun onCategorySelected(id: String?) = selectedCategory.update { id }

    fun clearQuery() = query.update { "" }

    private fun App.matches(needle: String): Boolean =
        name.contains(needle, ignoreCase = true) ||
            packageName.contains(needle, ignoreCase = true) ||
            summary?.contains(needle, ignoreCase = true) == true

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EDroidApp
                SearchViewModel(app.container.fdroidRepository, app.container.recentSearches)
            }
        }
    }
}
