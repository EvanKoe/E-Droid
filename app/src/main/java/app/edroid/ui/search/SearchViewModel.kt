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
) {
    /** True when the user is actively filtering and results should replace the browse view. */
    val showResults: Boolean get() = query.isNotBlank()
}

class SearchViewModel(
    private val repository: RepoHub,
    private val recentSearches: RecentSearchesStore,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<SearchUiState> =
        combine(repository.snapshot, query, recentSearches.recent) { snapshot, q, recent ->
            val needle = q.trim()
            val results = if (needle.isEmpty()) emptyList() else snapshot?.apps.orEmpty().filter { it.matches(needle) }
            SearchUiState(
                query = q,
                recentSearches = recent,
                categories = snapshot?.repo?.categories?.toList()?.sortedBy { it.second }.orEmpty(),
                results = results,
                indexReady = snapshot != null,
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
