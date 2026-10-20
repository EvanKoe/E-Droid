package app.edroid.ui.updates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.edroid.EDroidApp
import app.edroid.data.SettingsStore
import app.edroid.data.fdroid.RepoHub
import app.edroid.data.fdroid.SyncState
import app.edroid.data.fdroid.model.App
import app.edroid.data.install.installedPackages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator

/** An installed app the index has a newer version of. */
data class AppUpdate(
    val app: App,
    val installedVersionName: String?,
)

data class UpdatesUiState(
    val sync: SyncState = SyncState.Idle,
    /** Out-of-date apps, alphabetically; null until both the index and the installed list are read. */
    val updates: List<AppUpdate>? = null,
    /** Out-of-date apps the user swiped away; shown collapsed under the list so they can be unhidden. */
    val hidden: List<AppUpdate> = emptyList(),
)

class UpdatesViewModel(
    private val repository: RepoHub,
    private val settings: SettingsStore,
    context: Context,
) : ViewModel() {

    private var refreshJob: Job? = null

    // Kept apart from the sync state, which ticks with download progress.
    private val updates = combine(repository.snapshot, context.installedPackages()) { snapshot, installed ->
        snapshot ?: return@combine null
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        snapshot.apps
            .mapNotNull { app ->
                val local = installed[app.packageName] ?: return@mapNotNull null
                val latest = app.latestVersionCode ?: return@mapNotNull null
                if (latest > local.versionCode) AppUpdate(app, local.versionName) else null
            }
            .sortedWith(compareBy(collator) { it.app.name })
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<UpdatesUiState> =
        combine(updates, settings.hiddenUpdates, repository.sync) { updates, hiddenNames, sync ->
            val (hidden, visible) = updates.orEmpty().partition { it.app.packageName in hiddenNames }
            UpdatesUiState(sync, updates?.let { visible }, hidden)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdatesUiState())

    /** Pull-to-refresh: re-syncs every repo so newly published versions show up. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { repository.refresh() }
    }

    fun setHidden(packageName: String, hidden: Boolean) = settings.setUpdateHidden(packageName, hidden)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EDroidApp
                UpdatesViewModel(app.container.repoHub, app.container.settings, app)
            }
        }
    }
}
