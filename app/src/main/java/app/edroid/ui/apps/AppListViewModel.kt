package app.edroid.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.edroid.EDroidApp
import app.edroid.data.fdroid.FDroidRepository
import app.edroid.data.fdroid.RepoInfo
import app.edroid.data.fdroid.RepoState
import app.edroid.data.fdroid.model.App
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppListUiState(
    val repoState: RepoState = RepoState.Idle,
    val selectedCategory: String? = null,
    /** Visible apps after category filtering. */
    val apps: List<App> = emptyList(),
    /** Category id → display name, sorted by name. */
    val categories: List<Pair<String, String>> = emptyList(),
    val repo: RepoInfo? = null,
    val totalApps: Int = 0,
)

class AppListViewModel(private val repository: FDroidRepository) : ViewModel() {

    private val selectedCategory = MutableStateFlow<String?>(null)
    private var refreshJob: Job? = null

    val uiState: StateFlow<AppListUiState> =
        combine(repository.state, selectedCategory) { repoState, cat ->
            val ready = repoState as? RepoState.Ready
            val all = ready?.apps.orEmpty()
            AppListUiState(
                repoState = repoState,
                selectedCategory = cat,
                apps = if (cat == null) all else all.filter { cat in it.categories },
                categories = ready?.repo?.categories?.toList()?.sortedBy { it.second }.orEmpty(),
                repo = ready?.repo,
                totalApps = all.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListUiState())

    init {
        refresh()
    }

    fun refresh(force: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { repository.refresh(force) }
    }

    /** Toggles [id]: selecting the active category clears the filter. */
    fun onCategorySelected(id: String?) = selectedCategory.update { if (it == id) null else id }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EDroidApp
                AppListViewModel(app.container.fdroidRepository)
            }
        }
    }
}
