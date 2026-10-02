package app.edroid.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.edroid.R
import app.edroid.data.fdroid.SyncState
import app.edroid.ui.components.AppCard
import app.edroid.ui.components.PageTitle
import app.edroid.ui.components.relativeTime
import app.edroid.ui.theme.GroupedItemSpacing
import app.edroid.ui.theme.PageHorizontalPadding
import app.edroid.ui.theme.groupedShapeAt

/**
 * Home tab: repository heading, category chips and the grouped list of apps. The heading is the
 * first item of the list so it scrolls away under the top scrim like any other content.
 *
 * While an index is already on screen, a refresh only adds a slim progress strip (or a compact
 * error row) above the list; the full-size loading and error views appear only on a cold start.
 *
 * @param contentPadding insets handed down by `MainScreen` so content clears both scrims.
 */
@Composable
fun AppListScreen(
    contentPadding: PaddingValues,
    viewModel: AppListViewModel = viewModel(factory = AppListViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Horizontal padding is applied per item rather than on the list, so the chip row can bleed
    // to the screen edges while scrolling sideways.
    val listPadding = PaddingValues(
        top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding(),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = listPadding,
        verticalArrangement = Arrangement.spacedBy(GroupedItemSpacing),
    ) {
        item(key = "header") {
            RepoHeader(state = state, onRefresh = { viewModel.refresh(force = false) })
        }
        if (state.categories.isNotEmpty()) {
            item(key = "categories") {
                CategoryRow(
                    categories = state.categories,
                    selected = state.selectedCategory,
                    onSelect = viewModel::onCategorySelected,
                )
            }
        }
        val sync = state.sync
        if (state.hasData) {
            when (sync) {
                is SyncState.Loading -> item(key = "sync") { SyncStrip(sync) }
                is SyncState.Error -> item(key = "sync") {
                    SyncErrorRow(sync.message) { viewModel.refresh(force = true) }
                }
                SyncState.Idle -> Unit
            }
            appItems(state.apps)
        } else {
            when (sync) {
                is SyncState.Loading -> item(key = "loading") { LoadingContent(sync) }
                is SyncState.Error -> item(key = "error") {
                    ErrorContent(sync.message) { viewModel.refresh(force = true) }
                }
                SyncState.Idle -> Unit
            }
        }
    }
}

/** Page title with the refresh action beside it, and the repository status line underneath. */
@Composable
private fun RepoHeader(state: AppListUiState, onRefresh: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = PageHorizontalPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PageTitle(
                text = state.repo?.name ?: stringResource(R.string.app_name),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
        }
        val repo = state.repo
        val subtitle = if (repo != null) {
            stringResource(R.string.subtitle_apps_updated, state.totalApps, relativeTime(repo.timestamp))
        } else {
            stringResource(R.string.subtitle_repo_address)
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun CategoryRow(
    categories: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = PageHorizontalPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.first }) { (id, label) ->
            FilterChip(
                selected = id == selected,
                onClick = { onSelect(id) },
                label = { Text(label) },
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

/** Slim status line shown above an already-loaded list while a refresh runs. */
@Composable
private fun SyncStrip(state: SyncState.Loading) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageHorizontalPadding + 12.dp, vertical = 4.dp),
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

/** Compact failure notice above an already-loaded list: the cached apps stay usable. */
@Composable
private fun SyncErrorRow(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageHorizontalPadding + 12.dp, vertical = 4.dp),
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
private fun LoadingContent(state: SyncState.Loading) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
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
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
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

/** The grouped app cards, or an empty-state line when the filter matches nothing. */
private fun LazyListScope.appItems(apps: List<app.edroid.data.fdroid.model.App>) {
    if (apps.isEmpty()) {
        item(key = "empty") {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_results), style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }
    itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
        AppCard(
            app,
            shape = groupedShapeAt(index, apps.size),
            modifier = Modifier.padding(horizontal = PageHorizontalPadding),
        )
    }
}
