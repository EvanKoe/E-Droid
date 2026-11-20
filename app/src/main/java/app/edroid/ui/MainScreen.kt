package app.edroid.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.edroid.R
import app.edroid.data.fdroid.model.App
import app.edroid.ui.apps.AppListScreen
import app.edroid.ui.details.AppDetailScreen
import app.edroid.ui.details.DetailBackBar
import app.edroid.ui.components.EmphasizedEasing
import app.edroid.ui.components.ExpressiveNavBar
import app.edroid.ui.components.GitIcon
import app.edroid.ui.components.NavBarItem
import app.edroid.ui.git.GitScreen
import app.edroid.ui.options.OptionsScreen
import app.edroid.ui.options.PermissionsDetailsScreen
import app.edroid.ui.options.ReposScreen
import app.edroid.ui.search.CategoryScreen
import app.edroid.ui.search.SearchScreen
import app.edroid.ui.theme.Layering
import app.edroid.ui.theme.PageHorizontalPadding
import app.edroid.ui.updates.UpdatesScreen

/** The top-level tabs, in the order the nav bar shows them. */
private enum class HomeTab(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home(R.string.nav_home, Icons.Rounded.Home),
    Search(R.string.nav_search, Icons.Rounded.Search),
    Git(R.string.nav_git, GitIcon),
    Updates(R.string.nav_updates, Icons.Rounded.Update),
    Options(R.string.nav_options, Icons.Rounded.Settings),
}

/**
 * Extra bottom padding every tab's scrolling content needs so its last item clears the floating
 * nav bar (~64dp tall) plus its 16dp margin, with some breathing room.
 */
private val NavBarClearance: Dp = 96.dp

/** How far the top scrim extends below the status bar. */
private val TopScrimExtent: Dp = 40.dp

/** Gap between the top scrim and the first item of a page at rest. */
private val ContentTopGap: Dp = 8.dp

/** Height of the soft scrim fading content out beneath the floating bottom bar. */
private val BottomScrimHeight: Dp = 140.dp

/** Margin between the floating bottom bar and the navigation bar inset. */
private val BottomBarMargin: Dp = 16.dp

/** Duration of tab and page transitions, in milliseconds. */
private const val PAGE_TRANSITION_MS = 300

/** A pushed page slides by this fraction (1/n) of its height while entering or leaving. */
private const val PAGE_SLIDE_DIVISOR = 10

/** Scale the bottom bar shrinks from or to while it swaps between the nav bar and the back bar. */
private const val BAR_SWAP_SCALE = 0.9f

/** Opacity of the scrims at their mid stop. */
private const val SCRIM_MID_ALPHA = 0.7f

/** Gradient stop of the bottom scrim's mid colour. */
private const val BOTTOM_SCRIM_MID_STOP = 0.7f

/** Gradient stop of the top scrim's mid colour. */
private const val TOP_SCRIM_MID_STOP = 0.3f

/** The app whose detail page is open; its name labels the back bar before the page loads. */
private data class OpenApp(val packageName: String, val name: String)

/** Persists the open app across process death as `[packageName, name]`. */
private val OpenAppSaver = Saver<OpenApp?, List<String>>(
    save = { it?.let { listOf(it.packageName, it.name) } },
    restore = { OpenApp(it[0], it[1]) },
)

/** The category whose page is open over the Search tab; its name labels the back bar. */
private data class OpenCategory(val id: String, val name: String)

/** Persists the open category across process death as `[id, name]`. */
private val OpenCategorySaver = Saver<OpenCategory?, List<String>>(
    save = { it?.let { listOf(it.id, it.name) } },
    restore = { OpenCategory(it[0], it[1]) },
)

/** What the back bar is labelled with; [key] tells the bar's animation when to swap. */
private data class BackTarget(val key: String, val title: String)

/**
 * Enters a pushed page (category or detail) by rising over what's beneath, and exits by sinking.
 */
private fun <S> AnimatedContentTransitionScope<S?>.pageTransition(): ContentTransform {
    val slide = tween<IntOffset>(PAGE_TRANSITION_MS, easing = EmphasizedEasing)
    return if (targetState != null) {
        (fadeIn(tween(PAGE_TRANSITION_MS)) + slideInVertically(slide) { it / PAGE_SLIDE_DIVISOR }) togetherWith
            ExitTransition.None
    } else {
        EnterTransition.None togetherWith
            (fadeOut(tween(PAGE_TRANSITION_MS)) + slideOutVertically(slide) { it / PAGE_SLIDE_DIVISOR })
    } using null
}

/**
 * Root of the in-app UI: the current tab's content between a top and a bottom scrim, and a
 * floating expressive navigation bar switching between Home, Search, Updates, Git and Options.
 *
 * Tapping an app opens its detail page on top of the tab, which stays composed underneath so its
 * scroll position survives; the nav bar gives way to a back bar until the page is closed. A
 * category from the Search tab opens the same way, one level below any detail page, so back from
 * an app returns to its category and back from the category returns to Search. The repositories
 * and permissions pages open from the Options tab the same way.
 *
 * No tab has an app bar: each page carries its own heading as scrolling content (see
 * `PageTitle`), so a bar would only waste height at the top. A scrim mirroring the bottom one
 * stands in for it, fading scrolled content out under the status bar. Content padding is computed
 * once here so every tab clears both scrims without having to know about them.
 */
@Composable
fun MainScreen(gitRequest: Int = 0) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var openApp by rememberSaveable(stateSaver = OpenAppSaver) { mutableStateOf<OpenApp?>(null) }
    var openCategory by rememberSaveable(stateSaver = OpenCategorySaver) { mutableStateOf<OpenCategory?>(null) }
    var permissionsOpen by rememberSaveable { mutableStateOf(false) }
    var reposOpen by rememberSaveable { mutableStateOf(false) }

    // A notification tap: drop any pushed page and land on the Git tab.
    LaunchedEffect(gitRequest) {
        if (gitRequest == 0) {
            return@LaunchedEffect
        }

        openApp = null
        openCategory = null
        permissionsOpen = false
        reposOpen = false
        selectedIndex = HomeTab.Git.ordinal
    }

    // Bumped each time the already-selected Search tab is tapped again; the Search page focuses
    // its field whenever this changes. Reset on every tab change so returning to Search later
    // doesn't replay a stale request.
    var searchFocusRequest by remember { mutableIntStateOf(0) }
    val tabs = HomeTab.entries
    val current = tabs[selectedIndex.coerceIn(0, tabs.lastIndex)]

    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val onAppClick: (App) -> Unit = { app ->
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        focusManager.clearFocus()
        keyboard?.hide()
        openApp = OpenApp(app.packageName, app.name)
    }

    val onCategoryClick: (String, String) -> Unit = { id, name ->
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        focusManager.clearFocus()
        keyboard?.hide()
        openCategory = OpenCategory(id, name)
    }

    // Pops the topmost pushed page: the detail page first, then the category, repos or permissions page.
    // Covers both the back bar and the system back gesture, so both get the same light tick.
    val goBack: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        when {
            openApp != null -> openApp = null
            openCategory != null -> openCategory = null
            reposOpen -> reposOpen = false
            else -> permissionsOpen = false
        }
    }

    BackHandler(enabled = openApp != null || openCategory != null || reposOpen || permissionsOpen, onBack = goBack)

    val permissionsTitle = stringResource(R.string.permissions_title)
    val reposTitle = stringResource(R.string.repos_title)

    val backTarget = openApp?.let { BackTarget("app:${it.packageName}", it.name) }
        ?: openCategory?.let { BackTarget("category:${it.id}", it.name) }
        ?: if (reposOpen) BackTarget("repos", reposTitle) else null
        ?: if (permissionsOpen) BackTarget("permissions", permissionsTitle) else null

    val surface = Layering.page
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // The scrim is only a fade for *scrolled* content, so content at rest starts below it —
    // same relationship the bottom padding has with the bottom scrim.
    val topScrimHeight = statusBarTopInset + TopScrimExtent

    val contentPadding = PaddingValues(
        start = PageHorizontalPadding,
        end = PageHorizontalPadding,
        top = topScrimHeight + ContentTopGap,
        bottom = NavBarClearance + navBarBottomInset,
    )

    Box(Modifier.fillMaxSize().background(surface)) {

        // Current tab
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                fadeIn(tween(PAGE_TRANSITION_MS)) togetherWith fadeOut(tween(PAGE_TRANSITION_MS))
            },
            label = "homeTab",
        ) { tab ->
            when (tab) {
                HomeTab.Home -> AppListScreen(
                    contentPadding = contentPadding,
                    onAppClick = onAppClick,
                    onUpdatesClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        searchFocusRequest = 0
                        selectedIndex = HomeTab.Updates.ordinal
                    },
                )

                HomeTab.Search -> SearchScreen(
                    contentPadding = contentPadding,
                    focusRequest = searchFocusRequest,
                    onAppClick = onAppClick,
                    onCategoryClick = onCategoryClick,
                )

                HomeTab.Git -> GitScreen(contentPadding = contentPadding)

                HomeTab.Updates -> UpdatesScreen(
                    contentPadding = contentPadding,
                    detailPackageName = openApp?.packageName,
                    onAppClick = onAppClick,
                )

                HomeTab.Options -> OptionsScreen(
                    contentPadding = contentPadding,
                    onReposClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        reposOpen = true
                    },
                    onPermissionsClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        permissionsOpen = true
                    },
                )
            }
        }

        // Category page: pushed pages rise over the tab and sink back on close. Each direction
        // animates only the page, so whatever is underneath stays still.
        AnimatedContent(
            targetState = openCategory,
            contentKey = { it?.id },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { pageTransition() },
            label = "category",
        ) { category ->
            if (category != null) {
                CategoryScreen(
                    categoryId = category.id,
                    name = category.name,
                    contentPadding = contentPadding,
                    onAppClick = onAppClick,
                )
            }
        }

        // Permissions page
        AnimatedContent(
            targetState = permissionsOpen.takeIf { it },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { pageTransition() },
            label = "permissions",
        ) { open ->
            if (open != null) {
                PermissionsDetailsScreen(contentPadding = contentPadding)
            }
        }

        // Repositories page
        AnimatedContent(
            targetState = reposOpen.takeIf { it },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { pageTransition() },
            label = "repos",
        ) { open ->
            if (open != null) {
                ReposScreen(contentPadding = contentPadding)
            }
        }

        // App detail page
        AnimatedContent(
            targetState = openApp?.packageName,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { pageTransition() },
            label = "appDetail",
        ) { packageName ->
            if (packageName != null) {
                AppDetailScreen(packageName = packageName, contentPadding = contentPadding)
            }
        }

        // Bottom scrim: scrolled content fades out beneath the floating bottom bar.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BottomScrimHeight)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        BOTTOM_SCRIM_MID_STOP to surface.copy(alpha = SCRIM_MID_ALPHA),
                        1f to surface,
                    ),
                ),
        )

        // Top scrim: stands in for the app bar, so content fades out under the status bar as it
        // scrolls instead of colliding with it.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topScrimHeight)
                .background(
                    Brush.verticalGradient(
                        0f to surface,
                        TOP_SCRIM_MID_STOP to surface.copy(alpha = SCRIM_MID_ALPHA),
                        1f to Color.Transparent,
                    ),
                ),
        )

        // Bottom bar: the nav bar, or the back bar while a page is pushed
        AnimatedContent(
            targetState = backTarget,
            contentKey = { it?.key },
            contentAlignment = Alignment.BottomCenter,
            transitionSpec = {
                (fadeIn(tween(PAGE_TRANSITION_MS)) + scaleIn(initialScale = BAR_SWAP_SCALE)) togetherWith
                    (fadeOut(tween(PAGE_TRANSITION_MS / 2)) + scaleOut(targetScale = BAR_SWAP_SCALE))
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = BottomBarMargin),
            label = "bottomBar",
        ) { target ->
            if (target != null) {
                DetailBackBar(title = target.title, onBack = goBack)
            } else {
                ExpressiveNavBar(
                    items = tabs.map { NavBarItem(stringResource(it.labelRes), it.icon) },
                    selectedIndex = selectedIndex,
                    onSelect = { index ->
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        if (index != selectedIndex) {
                            searchFocusRequest = 0
                            selectedIndex = index
                        } else if (tabs[index] == HomeTab.Search) {
                            searchFocusRequest++
                        }
                    },
                )
            }
        }
    }
}
