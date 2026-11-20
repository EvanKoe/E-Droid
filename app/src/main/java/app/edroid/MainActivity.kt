package app.edroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.edroid.ui.MainScreen
import app.edroid.ui.theme.EDroidTheme

/** The single activity: hosts [MainScreen], applies the haptics setting and routes the Git-tab notification tap. */
class MainActivity : ComponentActivity() {
    /** Bumped each time a notification asks for the Git tab; MainScreen reacts to every change. */
    private var gitRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        consumeGitRequest(intent)

        val settings = (application as EDroidApp).container.settings

        setContent {
            val hapticsEnabled by settings.hapticsEnabled.collectAsStateWithLifecycle()
            // With haptics off, every LocalHapticFeedback consumer (ours and Compose's own) gets a no-op.
            val haptics = if (hapticsEnabled) LocalHapticFeedback.current else NoHapticFeedback

            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                EDroidTheme {
                    MainScreen(gitRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeGitRequest(intent)
    }

    /**
     * Requests the Git tab if [intent] asks for it. Removing the extra keeps a rotation (which reuses the intent) from redirecting again.
     */
    private fun consumeGitRequest(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_OPEN_GIT, false)) {
            return
        }

        intent.removeExtra(EXTRA_OPEN_GIT)
        gitRequest++
    }

    companion object {
        /** Boolean extra a notification sets to open the Git tab. */
        const val EXTRA_OPEN_GIT = "app.edroid.OPEN_GIT"
    }
}

/** Haptic feedback that does nothing, provided when the user turned haptics off. */
private object NoHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
