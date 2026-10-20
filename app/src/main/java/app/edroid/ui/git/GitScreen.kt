package app.edroid.ui.git

import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.edroid.R
import app.edroid.data.github.GitHubAsset
import app.edroid.ui.components.ActionGap
import app.edroid.ui.components.AppInstallActions
import app.edroid.ui.components.GitIcon
import app.edroid.ui.components.InstallActions
import app.edroid.ui.components.PageTitle
import app.edroid.ui.components.appInstallActions
import app.edroid.ui.options.UiText
import app.edroid.ui.search.SearchField
import app.edroid.ui.theme.GroupedItemSpacing
import app.edroid.ui.theme.Layering
import app.edroid.ui.theme.groupedShapeAt

/** Room the list keeps at its end so the last card clears the Add button floating over it. */
private val AddButtonClearance = 72.dp

private fun Context.resolve(text: UiText) = text.arg?.let { getString(text.res, it) } ?: getString(text.res)

/**
 * The GitHub repositories the user follows, each with its latest release to install, and a button
 * at the bottom to add another by link. Pulling down re-checks every repository.
 *
 * @param contentPadding insets handed down by `MainScreen` so content clears both scrims.
 */
@Composable
fun GitScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: GitViewModel = viewModel(factory = GitViewModel.Factory),
) {
    val context = LocalContext.current
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val hasUpdates by viewModel.hasUpdates.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(rows, query) {
        val q = query.trim()
        rows.filter { it.tracked.repo.name.contains(q, ignoreCase = true) || it.tracked.repo.owner.contains(q, ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, context.resolve(it), Toast.LENGTH_LONG).show() }
    }

    // Only a pull drives the indicator, so background checks don't make it pop in on its own.
    var pullRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (!refreshing) pullRefreshing = false }
    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = pullRefreshing,
        onRefresh = {
            pullRefreshing = true
            viewModel.refresh()
        },
        modifier = modifier.fillMaxSize(),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(GroupedItemSpacing),
        ) {
            PageTitle(stringResource(R.string.git_title))
            SearchField(
                query = query,
                onQueryChange = { query = it },
                onSearch = {},
                onClear = { query = "" },
                hint = R.string.git_search_hint,
            )
            shown.forEachIndexed { index, row ->
                GitRepoCard(row, groupedShapeAt(index, shown.size), viewModel)
            }
            Spacer(Modifier.height(AddButtonClearance))
        }

        Surface(
            shape = CircleShape,
            color = Layering.item,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding()),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { adding = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) { ActionContent(Icons.Rounded.Add, R.string.git_add) }
                TextButton(onClick = viewModel::updateAll, enabled = hasUpdates) { ActionContent(Icons.Rounded.Refresh, R.string.git_update_all) }
            }
        }
    }

    if (adding) {
        AddGitRepoDialog(onAdd = viewModel::add, onDismiss = { adding = false })
    }

    rows.firstOrNull { it.state is TrackState.Choosing }?.let { row ->
        ChooseApkDialog(
            assets = (row.state as TrackState.Choosing).assets,
            onChoose = { viewModel.choose(row.tracked.repo, it) },
            onDismiss = { viewModel.dismissChoice(row.tracked.repo) },
        )
    }
}

@Composable
private fun GitRepoCard(row: GitRow, shape: Shape, viewModel: GitViewModel) {
    val tracked = row.tracked
    val latest = tracked.latest
    var expanded by rememberSaveable(tracked.key) { mutableStateOf(false) }
    val actions = latest?.let {
        appInstallActions(
            packageName = it.packageName,
            appName = it.appName,
            version = remember(it) { it.toAppVersion() },
            latestVersionCode = it.versionCode,
            allowUninstall = false,
            reportFailures = true,
            installSource = { viewModel.cachedApk(tracked) },
        )
    }
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Layering.item),
    ) {
        Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Icon(
                        GitIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tracked.repo.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp).size(6.dp),
                        ) {}
                        Text(
                            tracked.repo.owner,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    latest?.let { VersionLine(stringResource(R.string.git_latest, it.tag)) }
                    actions?.state?.installedVersionName?.let { VersionLine(stringResource(R.string.git_installed, it)) }
                }
            }
            when {
                row.state != null -> RowStatus(row.state, onRetry = { viewModel.retry(tracked.repo) })
                actions != null -> {
                    // Install, progress and Cancel keep the animated bar; an installed app folds its buttons away until tapped.
                    if (!actions.state.installed || actions.state.busy) {
                        InstallActions(actions, Modifier.fillMaxWidth())
                    } else if (expanded) {
                        InstalledActions(actions)
                    }
                }
                tracked.needsChoice -> StatusLine(
                    stringResource(R.string.git_choose_needed),
                    actionLabel = R.string.git_choose_apk,
                    onAction = { viewModel.retry(tracked.repo) },
                )
            }
        }
    }
}

@Composable
private fun VersionLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Delete, Open and Update side by side; Update turns into a disabled "Up to date" when nothing newer is out. */
@Composable
private fun InstalledActions(actions: AppInstallActions) {
    val state = actions.state
    val canUpdate = state.updateAvailable && state.canInstall
    val padding = PaddingValues(horizontal = 8.dp)
    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(ActionGap)) {
        FilledTonalButton(
            onClick = actions.onUninstall,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            contentPadding = padding,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { ActionContent(Icons.Rounded.Delete, R.string.action_delete) }
        FilledTonalButton(
            onClick = actions.onOpen,
            enabled = state.canOpen,
            contentPadding = padding,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { ActionContent(Icons.Rounded.PlayArrow, R.string.action_open) }
        Button(
            onClick = actions.onInstall,
            enabled = canUpdate,
            contentPadding = padding,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { ActionContent(Icons.Rounded.Download, if (canUpdate) R.string.action_update else R.string.git_up_to_date) }
    }
}

@Composable
private fun ActionContent(icon: ImageVector, @StringRes label: Int) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
}

/** What a tracking run is doing; Choosing has no inline status because its dialog takes over. */
@Composable
private fun RowStatus(state: TrackState, onRetry: () -> Unit) {
    when (state) {
        TrackState.Checking -> StatusLine(stringResource(R.string.git_checking), progress = { null })
        is TrackState.Downloading -> StatusLine(
            state.progress?.let { stringResource(R.string.git_downloading_percent, (it * 100).toInt()) }
                ?: stringResource(R.string.git_checking),
            progress = { state.progress },
        )
        is TrackState.Error -> StatusLine(
            LocalContext.current.resolve(state.message),
            isError = true,
            actionLabel = R.string.action_retry,
            onAction = onRetry,
        )
        is TrackState.Choosing -> Unit
    }
}

/** A line of text, with a progress bar when [progress] is given (null inside means indeterminate) and a button when [actionLabel] is. */
@Composable
private fun StatusLine(
    text: String,
    isError: Boolean = false,
    progress: (() -> Float?)? = null,
    actionLabel: Int? = null,
    onAction: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (progress != null) {
            val value = progress()
            if (value != null) {
                LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        if (actionLabel != null) {
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(stringResource(actionLabel)) }
        }
    }
}

/** Lists the release's APKs when several fit; [onChoose] gets the picked one. */
@Composable
private fun ChooseApkDialog(assets: List<GitHubAsset>, onChoose: (GitHubAsset) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<GitHubAsset?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.git_choose_apk)) },
        text = {
            Column {
                Text(stringResource(R.string.git_choose_apk_intro), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                assets.forEach { asset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = asset == selected, role = Role.RadioButton, onClick = { selected = asset })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = asset == selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(asset.name, style = MaterialTheme.typography.bodyLarge, overflow = TextOverflow.Ellipsis, maxLines = 2)
                            Text(
                                Formatter.formatShortFileSize(context, asset.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onChoose) }, enabled = selected != null) {
                Text(stringResource(R.string.action_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Asks for a GitHub link; [onAdd] returns the reason it was refused, or null once the repo is added. */
@Composable
private fun AddGitRepoDialog(onAdd: (String) -> UiText?, onDismiss: () -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<UiText?>(null) }
    val submit = { error = onAdd(link); if (error == null) onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.git_add_repo)) },
        text = {
            TextField(
                value = link,
                onValueChange = { link = it; error = null },
                label = { Text(stringResource(R.string.git_link_label)) },
                placeholder = { Text(stringResource(R.string.git_link_hint)) },
                isError = error != null,
                supportingText = error?.let { { Text(stringResource(it.res)) } },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    focusedContainerColor = Layering.item,
                    unfocusedContainerColor = Layering.item,
                    errorContainerColor = Layering.item,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = link.isNotBlank()) { Text(stringResource(R.string.git_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
