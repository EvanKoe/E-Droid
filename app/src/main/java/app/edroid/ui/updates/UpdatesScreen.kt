package app.edroid.ui.updates

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
 * the list follows the package manager. Pulling to refresh re-syncs the repositories.
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
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
            if (updates.isNullOrEmpty()) {
                item(key = "empty") {
                    Box(
                        animateListItem().fillParentMaxWidth().padding(vertical = 96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(if (updates == null) R.string.search_index_loading else R.string.updates_none),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            } else {
                itemsIndexed(updates, key = { _, update -> update.app.packageName }) { index, update ->
                    UpdateCard(
                        update,
                        shape = groupedShapeAt(index, updates.size),
                        reportFailures = update.app.packageName != detailPackageName,
                        onClick = { onAppClick(update.app) },
                        modifier = animateListItem(),
                    )
                }
            }
        }
    }
}

/**
 * One out-of-date app: icon, name and "installed → latest", with a compact Update button on the
 * end. While the update downloads, the text steps aside for the progress bar and Cancel, like the
 * Home spotlight's actions.
 */
@Composable
private fun UpdateCard(
    update: AppUpdate,
    shape: Shape,
    reportFailures: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
    val busy = actions.state.busy
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
                InstallActions(actions, Modifier.align(Alignment.CenterEnd).width(slot), height = UpdateButtonHeight)
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
