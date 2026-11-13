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

    /**
     * Turns haptic feedback on or off and persists the choice.
     */
    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        prefs.edit { putBoolean(KEY_HAPTICS, enabled) }
    }

    // Copied: SharedPreferences hands back its own mutable set, which must not be mutated.
    private val _hiddenUpdates = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN_UPDATES, null).orEmpty().toSet())
    /** Package names the Updates tab keeps out of the available list until unhidden. */
    val hiddenUpdates: StateFlow<Set<String>> = _hiddenUpdates.asStateFlow()

    /**
     * Hides or unhides [packageName] in the Updates tab and persists the set.
     */
    fun setUpdateHidden(packageName: String, hidden: Boolean) {
        val set = if (hidden) _hiddenUpdates.value + packageName else _hiddenUpdates.value - packageName
        _hiddenUpdates.value = set
        prefs.edit { putStringSet(KEY_HIDDEN_UPDATES, set) }
    }

    companion object {
        /** SharedPreferences file name. */
        private const val PREFS_NAME = "settings"

        /** Preference key of the haptics toggle. */
        private const val KEY_HAPTICS = "haptics_enabled"

        /** Preference key of the hidden-updates set. */
        private const val KEY_HIDDEN_UPDATES = "hidden_updates"
    }
}
