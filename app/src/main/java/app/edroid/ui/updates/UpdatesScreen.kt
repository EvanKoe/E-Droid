package app.edroid.ui.updates

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
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
import app.edroid.ui.components.ActionGap
import app.edroid.ui.components.CancelWidth
import app.edroid.ui.components.InstallActions
import app.edroid.ui.components.PageTitle
import app.edroid.ui.components.appInstallActions
import app.edroid.ui.components.bouncySpring
import app.edroid.ui.theme.GroupedItemSpacing
import app.edroid.ui.theme.Layering
import app.edroid.ui.theme.groupedShapeAt
import coil3.compose.AsyncImage

private val UpdateButtonHeight = 40.dp
/** Update's width at rest; the same as Cancel's, so only the progress bar moves in when it starts. */
private val UpdateButtonWidth = CancelWidth

/**
 * Updates tab: every installed app an enabled repository has a newer version of, each with its
 * own Update button. A row leaves the list on its own once the new version is installed, since
 * the list follows the package manager. Swiping a row away hides it; hidden rows collect in a
 * collapsed group under the list, where Unhide brings them back. Pulling to refresh re-syncs the
 * repositories.
 *
 * @param contentPadding insets handed down by `MainScreen` so content clears both scrims.
 * @param detailPackageName the app whose detail page is open over this tab, if any. That page
 *   toasts its own install failures, so its row here stays quiet to avoid a second toast.
 * @param onAppClick opens an app's detail page.
 */
@Composable
fun UpdatesScreen(
    contentPadding: PaddingValues,
    detailPackageName: String?,
    onAppClick: (App) -> Unit,
    viewModel: UpdatesViewModel = viewModel(factory = UpdatesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Same as Home: only a pull-initiated refresh drives the indicator.
    var pullRefreshing by remember { mutableStateOf(false) }
    val syncing = state.sync is SyncState.Loading
    LaunchedEffect(syncing) { if (!syncing) pullRefreshing = false }
    var showHidden by rememberSaveable { mutableStateOf(false) }

    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = pullRefreshing,
        onRefresh = {
            pullRefreshing = true
            viewModel.refresh()
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
        val updates = state.updates
        val hidden = state.hidden
        val hiddenExpanded = showHidden && hidden.isNotEmpty()
        val layout = LocalLayoutDirection.current
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // The toggle below takes the bottom inset, so the list stops just above it.
                    contentPadding = PaddingValues(
                        start = contentPadding.calculateStartPadding(layout),
                        top = contentPadding.calculateTopPadding(),
                        end = contentPadding.calculateEndPadding(layout),
                    ),
                    verticalArrangement = Arrangement.spacedBy(GroupedItemSpacing),
                ) {
                    item(key = "title") {
                        Column {
                            PageTitle(text = stringResource(R.string.updates_title))
                            if (!updates.isNullOrEmpty()) {
                                Text(
                                    pluralStringResource(R.plurals.updates_count, updates.size, updates.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    val visible = updates.orEmpty()
                    itemsIndexed(visible, key = { _, update -> update.app.packageName }) { index, update ->
                        val shape = groupedShapeAt(index, visible.size)
                        SwipeToHide(
                            shape = shape,
                            onHide = { viewModel.setHidden(update.app.packageName, true) },
                            modifier = animateListItem(),
                        ) {
                            UpdateCard(
                                update,
                                shape = shape,
                                reportFailures = update.app.packageName != detailPackageName,
                                onClick = { onAppClick(update.app) },
                            )
                        }
                    }
                    if (hiddenExpanded) {
                        item(key = "hidden-gap") { Spacer(animateListItem().height(12.dp)) }
                        // Keyed apart from the visible rows so a swiped row fades out and its hidden twin fades in.
                        itemsIndexed(hidden, key = { _, update -> "hidden:" + update.app.packageName }) { index, update ->
                            UpdateCard(
                                update,
                                shape = groupedShapeAt(index, hidden.size),
                                reportFailures = false,
                                onClick = { onAppClick(update.app) },
                                onUnhide = { viewModel.setHidden(update.app.packageName, false) },
                                modifier = animateListItem(),
                            )
                        }
                    }
                }
                // Sits over the list rather than in it so it centres on the screen; it takes no touches.
                // Qualified: inside Column the ColumnScope overload shadows this one and cannot be used here.
                androidx.compose.animation.AnimatedVisibility(
                    visible = updates.isNullOrEmpty() && !hiddenExpanded,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    EmptyState(loading = updates == null)
                }
            }
            if (hidden.isNotEmpty()) {
                HiddenToggle(
                    count = hidden.size,
                    expanded = showHidden,
                    onClick = { showHidden = !showHidden },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp),
                )
            }
        }
    }
}

/** Check mark and "All your apps are up to date", or the waiting text while the index loads. */
@Composable
private fun EmptyState(loading: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        if (!loading) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(96.dp),
            )
        }
        Text(
            stringResource(if (loading) R.string.search_index_loading else R.string.updates_none),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Swiping the row towards the start uncovers a "Hide" backdrop and, once settled, hides the app. */
@Composable
private fun SwipeToHide(
    shape: Shape,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onHide() },
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    stringResource(R.string.updates_hide),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
    ) {
        content()
    }
}

/** "N hidden updates" with a chevron, pinned under the list; tapping it shows the hidden rows at the list's end. */
@Composable
private fun HiddenToggle(
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Chevron flip and a slight grow ride the same overshooting spring as the row buttons.
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, bouncySpring(), label = "hiddenChevron")
    val scale by animateFloatAsState(if (expanded) 1.08f else 1f, bouncySpring(), label = "hiddenScale")
    // Expanded, the toggle fills in as a pill so it reads as the "on" state of the rows above it.
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        if (expanded) colors.primaryContainer else Color.Transparent, motion.defaultEffectsSpec(), label = "hiddenContainer",
    )
    val content by animateColorAsState(
        if (expanded) colors.primary else colors.onSurfaceVariant, motion.defaultEffectsSpec(), label = "hiddenContent",
    )
    TextButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(), // expressive press morph, same as the install buttons
        colors = ButtonDefaults.textButtonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 12.dp),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Text(
            pluralStringResource(R.plurals.updates_hidden_count, count, count),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, modifier = Modifier.rotate(chevron))
    }
}

/**
 * One out-of-date app: icon, name and "installed → latest", with a compact Update button on the
 * end. While the update downloads, the text steps aside for the progress bar and Cancel, like the
 * Home spotlight's actions.
 *
 * @param onUnhide when set, the row is a hidden one: Update gives way to an Unhide button.
 */
@Composable
private fun UpdateCard(
    update: AppUpdate,
    shape: Shape,
    reportFailures: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onUnhide: (() -> Unit)? = null,
) {
    val app = update.app
    val actions = appInstallActions(
        packageName = app.packageName,
        appName = app.name,
        version = remember(app) { app.suggestedVersion() },
        latestVersionCode = app.latestVersionCode,
        allowUninstall = false,
        reportFailures = reportFailures,
    )
    val busy = onUnhide == null && actions.state.busy
    val textAlpha by animateFloatAsState(
        targetValue = if (busy) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "updateText",
    )
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Layering.item),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Layering.page, // icon placeholder reads as an inset into the page
                modifier = Modifier.size(56.dp),
            ) {
                AsyncImage(
                    model = app.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                )
            }
            Spacer(Modifier.width(16.dp))
            BoxWithConstraints(Modifier.weight(1f)) {
                val slotTarget = if (busy) maxWidth else UpdateButtonWidth
                val slot = animateDpAsState(slotTarget, bouncySpring(), label = "updateSlot").value.coerceIn(0.dp, maxWidth)
                Column(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width((maxWidth - slot - ActionGap).coerceAtLeast(0.dp))
                        .graphicsLayer { alpha = textAlpha },
                ) {
                    Text(
                        app.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(update.installedVersionName, app.latestVersionName).joinToString("  →  "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val trailing = Modifier.align(Alignment.CenterEnd).width(slot)
                if (onUnhide != null) {
                    FilledTonalButton(
                        onClick = onUnhide,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = trailing.height(UpdateButtonHeight),
                    ) {
                        Text(stringResource(R.string.updates_unhide), maxLines = 1)
                    }
                } else {
                    InstallActions(actions, trailing, height = UpdateButtonHeight)
                }
            }
        }
    }
}

/** Fades rows in and out (an installed update leaves the list) and slides the rest into place. */
@Composable
private fun LazyItemScope.animateListItem(): Modifier {
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec(),
        fadeOutSpec = motion.fastEffectsSpec(),
    )
}
