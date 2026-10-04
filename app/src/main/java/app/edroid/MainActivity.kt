package app.edroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.edroid.ui.MainScreen
import app.edroid.ui.theme.EDroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settings = (application as EDroidApp).container.settings
        setContent {
            val hapticsEnabled by settings.hapticsEnabled.collectAsStateWithLifecycle()
            // With haptics off, every LocalHapticFeedback consumer (ours and Compose's own) gets a no-op.
            val haptics = if (hapticsEnabled) LocalHapticFeedback.current else NoHapticFeedback
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                EDroidTheme {
                    MainScreen()
                }
            }
        }
    }
}

private object NoHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
