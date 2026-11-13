package app.edroid.ui.options

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.edroid.EDroidApp
import app.edroid.data.SettingsStore
import kotlinx.coroutines.flow.StateFlow

/** Exposes the user-facing options (currently the haptics toggle) backed by [SettingsStore]. */
class OptionsViewModel(private val settings: SettingsStore) : ViewModel() {

    /** Whether navigation haptics are enabled. */
    val hapticsEnabled: StateFlow<Boolean> = settings.hapticsEnabled

    /**
     * Persists the haptics preference.
     */
    fun setHapticsEnabled(enabled: Boolean) = settings.setHapticsEnabled(enabled)

    companion object {
        /** Builds the ViewModel from the app container. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EDroidApp
                OptionsViewModel(app.container.settings)
            }
        }
    }
}
