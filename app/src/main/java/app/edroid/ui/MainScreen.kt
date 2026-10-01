package app.edroid.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.edroid.R
import app.edroid.ui.apps.AppListScreen
import app.edroid.ui.components.ExpressiveNavBar
import app.edroid.ui.components.NavBarItem
import app.edroid.ui.options.OptionsScreen
import app.edroid.ui.search.SearchScreen
import app.edroid.ui.theme.Layering

/** The three top-level tabs, in the order the nav bar shows them. */
private enum class HomeTab(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home(R.string.nav_home, Icons.Rounded.Home),
    Search(R.string.nav_search, Icons.Rounded.Search),
    Options(R.string.nav_options, Icons.Rounded.Settings),
}

/**
 * Extra bottom padding every tab's scrolling content needs so its last item clears the floating
 * nav bar (~64dp tall) plus its 16dp margin, with some breathing room. System bar insets are
 * handled by each tab's own Scaffold.
 */
private val NavBarClearance: Dp = 96.dp

private const val PAGE_TRANSITION_MS = 300

/**
 * Root of the in-app UI: the current tab's content under a bottom scrim, and a floating
 * expressive navigation bar switching between Home, Search and Options.
 */
@Composable
fun MainScreen() {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = HomeTab.entries
    val current = tabs[selectedIndex.coerceIn(0, tabs.lastIndex)]
    val haptics = LocalHapticFeedback.current
    val surface = Layering.page

    Box(modifier = Modifier.fillMaxSize().background(surface)) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                fadeIn(tween(PAGE_TRANSITION_MS)) togetherWith fadeOut(tween(PAGE_TRANSITION_MS))
            },
            label = "homeTab",
        ) { tab ->
            when (tab) {
                HomeTab.Home -> AppListScreen(bottomPadding = NavBarClearance)
                HomeTab.Search -> SearchScreen(bottomPadding = NavBarClearance)
                HomeTab.Options -> OptionsScreen(bottomPadding = NavBarClearance)
            }
        }

        // Soft scrim so scrolled content fades out beneath the floating bottom bar.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.7f to surface.copy(alpha = 0.7f),
                        1f to surface,
                    ),
                ),
        )

        ExpressiveNavBar(
            items = tabs.map { NavBarItem(stringResource(it.labelRes), it.icon) },
            selectedIndex = selectedIndex,
            onSelect = { index ->
                if (index != selectedIndex) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                selectedIndex = index
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        )
    }
}
