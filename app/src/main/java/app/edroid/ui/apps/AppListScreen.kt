package app.edroid.ui.apps

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import app.edroid.ui.components.ActionGap
import app.edroid.ui.components.CancelWidth
import app.edroid.ui.components.InstallActions
import app.edroid.ui.components.TrailingAction
import app.edroid.ui.components.appInstallActions
import app.edroid.ui.components.bouncySpring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.edroid.R
import app.edroid.data.fdroid.SyncState
import app.edroid.data.fdroid.model.App
import app.edroid.ui.components.EDroidLogo
import app.edroid.ui.components.SectionTitle
import app.edroid.ui.theme.GroupedItemSpacing
import app.edroid.ui.theme.Layering
import app.edroid.ui.theme.gridGroupedShapeAt
import app.edroid.ui.updates.AppUpdate
import app.edroid.ui.updates.UpdatesViewModel
import coil3.compose.AsyncImage

/** Apps per row in the "Other apps" grid. */
private const val GRID_COLUMNS = 3

/** Corner radius of the standalone spotlight card. */
private val FeaturedCardRadius = 32.dp

/** App icons previewed on the updates card. */
private const val UPDATE_ICONS_SHOWN = 3

/** Corner radius of the compact updates card; smaller than the spotlight's to match its height. */
private val UpdatesCardRadius = 24.dp

/**
 * Home tab: the EDroid wordmark, a random app in the spotlight, a compact card for pending updates
 * when installed apps have any, then every other app as a grid of icon tiles. The wordmark is the first item of the grid so it scrolls away under the top scrim
 * like any other content.
 *
 * While an index is already on screen, a refresh only adds a slim progress strip (or a compact
 * error row) below the wordmark; the full-size loading and error views appear only on a cold
 * start. Pulling to refresh also draws a new spotlight app.
 *
 * @param contentPadding insets handed down by `MainScreen` so content clears both scrims.
 * @param onAppClick opens an app's detail page.
 * @param onUpdatesClick switches to the Updates tab.
 */
@Composable
fun AppListScreen(
    contentPadding: PaddingValues,
    onAppClick: (App) -> Unit,
    onUpdatesClick: () -> Unit,
    viewModel: AppListViewModel = viewModel(factory = AppListViewModel.Factory),
    // The Updates tab's view model (same activity store), so the card matches that tab's list.
    updatesViewModel: UpdatesViewModel = viewModel(factory = UpdatesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updates = updatesViewModel.uiState.collectAsStateWithLifecycle().value.updates.orEmpty()

    // Only a pull-initiated refresh drives the pull indicator; background syncs keep using the
    // inline strip so the indicator doesn't pop in on its own. Cleared once the sync settles.
    var pullRefreshing by remember { mutableStateOf(false) }
    val syncing = state.sync is SyncState.Loading
    LaunchedEffect(syncing) { if (!syncing) pullRefreshing = false }

    // Re-checked on every resume, since the user grants it in system Settings and comes back.
    val context = LocalContext.current
    var canInstall by remember { mutableStateOf(true) }
    // Hidden until the app process restarts.
    var installCardHidden by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        canInstall = context.packageManager.canRequestPackageInstalls()
        onPauseOrDispose { }
    }

    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = pullRefreshing,
        onRefresh = {
            pullRefreshing = true
            viewModel.refreshAndShuffle()
        },
        modifier = Modifier.fillMaxSize(),
        state = pullState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = pullRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = contentPadding.calculateTopPadding()),
            )
        },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(GroupedItemSpacing),
            verticalArrangement = Arrangement.spacedBy(GroupedItemSpacing),
        ) {
            fullLineItem(key = "logo") { LogoHeader() }
            if (!canInstall && !installCardHidden) fullLineItem(key = "install-permission") {
                InstallPermissionCard(
                    animateGridItem(),
                    onHide = { installCardHidden = true },
                    onGrant = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
                        )
                    },
                )
            }
            // Loading and error variants use distinct keys so a switch between them crossfades
            // rather than swapping content inside the same slot.
            val sync = state.sync
            if (state.hasData) {
                when (sync) {
                    is SyncState.Loading -> fullLineItem(key = "sync-loading") {
                        SyncStrip(sync, animateGridItem())
                    }
                    is SyncState.Error -> fullLineItem(key = "sync-error") {
                        SyncErrorRow(sync.message, animateGridItem()) {
                            viewModel.refresh(force = true)
                        }
                    }
                    SyncState.Idle -> Unit
                }
                homeItems(
                    state.featured, updates, state.apps, state.sort,
                    viewModel::onSortSelected, viewModel::shuffleFeatured, onUpdatesClick, onAppClick,
                )
            } else {
                when (sync) {
                    is SyncState.Loading -> fullLineItem(key = "loading") {
                        LoadingContent(sync, animateGridItem())
                    }
                    is SyncState.Error -> fullLineItem(key = "error") {
                        ErrorContent(sync.message, animateGridItem()) {
                            viewModel.refresh(force = true)
                        }
                    }
                    SyncState.Idle -> Unit
                }
            }
        }
    }
}

/** An item spanning the whole row, for everything on the page that isn't an app tile. */
private fun LazyGridScope.fullLineItem(key: Any, content: @Composable LazyGridItemScope.() -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }, content = content)

/** The wordmark, centred with generous room above and below like a splash. */
@Composable
private fun LogoHeader() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        EDroidLogo(modifier = Modifier.fillMaxWidth(0.62f))
    }
}

/**
 * Tonal card shown while EDroid can't install apps: a plug badge beside the explanation, then Hide
 * and Grant it, which opens the grant screen.
 */
@Composable
private fun InstallPermissionCard(modifier: Modifier = Modifier, onHide: () -> Unit, onGrant: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeaturedCardRadius),
        colors = CardDefaults.cardColors(
            containerColor = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp)) {
            Row {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(colors.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Power, contentDescription = null, tint = colors.onSecondary)
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_install_permission_title), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.home_install_permission_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onHide) {
                    Text(stringResource(R.string.home_install_permission_hide), color = colors.secondary)
                }
                Button(
                    onClick = onGrant,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.secondary, contentColor = colors.onSecondary),
                ) {
                    Text(stringResource(R.string.home_install_permission_grant))
                }
            }
        }
    }
}

/** Section title and compact card for pending updates, between the spotlight and the grid. */
private fun LazyGridScope.updatesItems(updates: List<AppUpdate>, onClick: () -> Unit) {
    fullLineItem(key = "updates-title") {
        SectionTitle(stringResource(R.string.home_updates_title), modifier = animateGridItem().padding(top = 16.dp))
    }
    fullLineItem(key = "updates") {
        UpdatesCard(updates, onClick = onClick, modifier = animateGridItem())
    }
}

/**
 * One-row tonal card on the primary container, so it reads apart from the spotlight: a few of the
 * apps' icons, how many can be updated, and an arrow hinting that the card leads to the Updates tab.
 */
@Composable
private fun UpdatesCard(updates: List<AppUpdate>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UpdatesCardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                updates.take(UPDATE_ICONS_SHOWN).forEach { AppIcon(it.app, Modifier.size(32.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.updates_count, updates.size, updates.size),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.updates_see_more),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** The spotlight card, any pending updates, and the grid of every other app, or an empty-state line. */
private fun LazyGridScope.homeItems(
    featured: App?,
    updates: List<AppUpdate>,
    apps: List<App>,
    sort: AppSort,
    onSortSelected: (AppSort) -> Unit,
    onShuffle: () -> Unit,
    onUpdatesClick: () -> Unit,
    onAppClick: (App) -> Unit,
) {
    if (featured == null) {
        fullLineItem(key = "empty") {
            Box(
                animateGridItem().fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.empty_results), style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }
    fullLineItem(key = "featured-title") {
        SectionTitle(stringResource(R.string.home_featured_title))
    }
    fullLineItem(key = "featured") {
        FeaturedAppCard(featured, onClick = { onAppClick(featured) }, onShuffle = onShuffle, modifier = animateGridItem())
    }
    if (updates.isNotEmpty()) updatesItems(updates, onUpdatesClick)
    if (apps.isEmpty()) return
    fullLineItem(key = "others-title") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(
                stringResource(R.string.home_other_apps_title),
                modifier = Modifier.weight(1f),
            )
            SortMenu(sort, onSortSelected)
        }
    }
    itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
        AppTile(
            app,
            shape = gridGroupedShapeAt(index, apps.size, GRID_COLUMNS),
            modifier = animateGridItem(),
            onClick = { onAppClick(app) },
        )
    }
}

/** Compact pill beside the "Other apps" title: shows the current order, opens the choices. */
@Composable
private fun SortMenu(selected: AppSort, onSelect: (AppSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "sortArrow",
    )
    Box {
        Surface(
            onClick = { expanded = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(selected.labelRes), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.sort_by),
                    modifier = Modifier.size(20.dp).rotate(arrowRotation),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
            containerColor = Layering.item,
        ) {
            AppSort.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(stringResource(order.labelRes)) },
                    onClick = {
                        expanded = false
                        onSelect(order)
                    },
                    trailingIcon = if (order == selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * App icon on a tinted placeholder, so tiles keep their shape while icons load. The tint fades
 * away once the icon is in, so icons drawn without a background sit straight on the card.
 */
@Composable
private fun AppIcon(app: App, modifier: Modifier = Modifier) {
    var loaded by remember(app.iconUrl) { mutableStateOf(false) }
    val placeholder by animateColorAsState(
        targetValue = if (loaded) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "iconPlaceholder",
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = placeholder,
        modifier = modifier,
    ) {
        AsyncImage(
            model = app.iconUrl,
            contentDescription = null,
            onSuccess = { loaded = true },
            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large),
        )
    }
}

/**
 * Standalone card for the random app on a tonal (secondary container) surface, so it stands apart
 * from the neutral grid below: large icon beside its name and summary, then Install and Details,
 * with a shuffle button on the end drawing another app.
 */
@Composable
private fun FeaturedAppCard(app: App, onClick: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeaturedCardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp)) {
            // Only the app crossfades on shuffle; the buttons below stay put and morph instead.
            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = app,
                contentKey = { it.packageName },
                transitionSpec = { fadeIn(motion.defaultEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec())) },
                label = "featuredApp",
            ) { shown ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(shown, Modifier.size(80.dp))
                    Spacer(Modifier.width(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            shown.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        shown.summary?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            FeaturedActions(app, onDetails = onClick, onShuffle = onShuffle)
        }
    }
}

private val FeaturedButtonHeight = 48.dp
/** Install's width at rest; the same as Cancel's, so it only slides over when a download starts. */
private val FeaturedInstallWidth = CancelWidth

/**
 * The detail page's install button, compact here: while a download runs it slides over to make
 * room for the progress bar, and Details steps aside. Installed apps get Open or Update but no
 * Uninstall, which belongs on the detail page.
 */
@Composable
private fun FeaturedActions(app: App, onDetails: () -> Unit, onShuffle: () -> Unit) {
    val actions = appInstallActions(
        packageName = app.packageName,
        appName = app.name,
        version = remember(app) { app.suggestedVersion() },
        latestVersionCode = app.latestVersionCode,
        allowUninstall = false,
        // The detail page toasts failures; it may be open over Home for this very app.
        reportFailures = false,
    )
    val busy = actions.state.busy
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The shuffle button keeps its place, so everything else lives left of it.
        val room = maxWidth - FeaturedButtonHeight - ActionGap
        val slotTarget = when {
            busy -> room
            actions.state.trailing == TrailingAction.None -> 0.dp
            else -> FeaturedInstallWidth
        }
        val slot = animateDpAsState(slotTarget, bouncySpring(), label = "featuredInstall").value.coerceIn(0.dp, room)
        Row(Modifier.width(room), verticalAlignment = Alignment.CenterVertically) {
            InstallActions(actions, Modifier.width(slot), height = FeaturedButtonHeight)
            AnimatedVisibility(
                visible = !busy,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                TextButton(onClick = onDetails, modifier = Modifier.padding(start = ActionGap).height(FeaturedButtonHeight)) {
                    Text(stringResource(R.string.featured_details), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        FilledIconButton(
            onClick = onShuffle,
            shape = RoundedCornerShape(16.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Layering.item,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier.align(Alignment.CenterEnd).size(FeaturedButtonHeight),
        ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.featured_shuffle))
        }
    }
}

/** One cell of the "Other apps" grid: icon filling the tile width, name centred underneath. */
@Composable
private fun AppTile(app: App, shape: Shape, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Layering.item),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(app, Modifier.fillMaxWidth().aspectRatio(1f))
            Spacer(Modifier.height(8.dp))
            Text(
                app.name,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val SyncState.Loading.Phase.label: Int
    get() = when (this) {
        SyncState.Loading.Phase.FETCHING_ENTRY -> R.string.loading_entry
        SyncState.Loading.Phase.DOWNLOADING_INDEX -> R.string.loading_index
        SyncState.Loading.Phase.PARSING -> R.string.loading_parsing
    }

/** Slim status line shown below the wordmark while a refresh runs over a loaded index. */
@Composable
private fun SyncStrip(state: SyncState.Loading, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            stringResource(state.phase.label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        val progress = state.progress
        if (progress != null) {
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Compact failure notice over an already-loaded index: the cached apps stay usable. */
@Composable
private fun SyncErrorRow(message: String, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun LoadingContent(state: SyncState.Loading, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator(modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(state.phase.label), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        val progress = state.progress
        if (progress != null) {
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(0.7f))
        } else {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(0.7f))
        }
    }
}

@Composable
private fun ErrorContent(message: String, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.error_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

/**
 * Fades an item in and out as it enters or leaves the grid, and slides its neighbours into place,
 * using the theme's expressive motion so status rows don't pop in and push the tiles abruptly.
 */
@Composable
private fun LazyGridItemScope.animateGridItem(): Modifier {
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec(),
        fadeOutSpec = motion.fastEffectsSpec(),
    )
}
