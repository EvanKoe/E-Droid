package app.edroid.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User preferences from the Options tab, persisted in SharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    /** Whether the app plays haptic feedback at all; on by default. */
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        prefs.edit { putBoolean(KEY_HAPTICS, enabled) }
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_HAPTICS = "haptics_enabled"
    }
}
