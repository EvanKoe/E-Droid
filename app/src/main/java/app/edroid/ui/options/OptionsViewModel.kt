package app.edroid.ui.options

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.edroid.EDroidApp
import app.edroid.data.SettingsStore
import kotlinx.coroutines.flow.StateFlow

class OptionsViewModel(private val settings: SettingsStore) : ViewModel() {

    val hapticsEnabled: StateFlow<Boolean> = settings.hapticsEnabled

    fun setHapticsEnabled(enabled: Boolean) = settings.setHapticsEnabled(enabled)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EDroidApp
                OptionsViewModel(app.container.settings)
            }
        }
    }
}
