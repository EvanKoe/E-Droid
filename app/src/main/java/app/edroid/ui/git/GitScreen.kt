package app.edroid.ui.git

import android.content.Context
import android.content.Intent
import android.util.Log
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Badge
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.edroid.R
import app.edroid.data.github.GitHubAsset
import app.edroid.data.github.GitHubRepo
import app.edroid.ui.components.ActionGap
import app.edroid.ui.components.AppInstallActions
import app.edroid.ui.components.GitIcon
import app.edroid.ui.components.InstallActions
import app.edroid.ui.components.PageTitle
import app.edroid.ui.components.SwipeAction
import app.edroid.ui.components.SwipeSide
import app.edroid.ui.components.appInstallActions
import app.edroid.ui.options.UiText
import app.edroid.ui.search.SearchField
import app.edroid.ui.theme.GroupedItemSpacing
import app.edroid.ui.theme.Layering
import app.edroid.ui.theme.groupedShapeAt
import coil3.compose.AsyncImage

/** Log tag. */
private const val TAG = "GitScreen"

/** Room the list keeps at its end so the last card clears the Add button floating over it. */
private val AddButtonClearance = 72.dp

/** GitHub base URL: `owner/name` completes a repo page, the owner and [AVATAR_URL_SUFFIX] an avatar. */
private const val GITHUB_URL_PREFIX = "https://github.com/"

/** Avatar size query, in pixels, requested from GitHub. */
private const val AVATAR_URL_SUFFIX = ".png?size=120"

/** Converts a 0..1 download fraction to a percentage. */
private const val PERCENT = 100

/**
 * Resolves a [UiText] into a localized string, applying its optional argument.
 */
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
    val haptics = LocalHapticFeedback.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rows = state.rows
    val refreshing = state.refreshing
    val updateCount = state.updateCount

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, context.resolve(it), Toast.LENGTH_LONG).show() }
    }

    var adding by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var removingKey by rememberSaveable { mutableStateOf<String?>(null) }

    val openRepoPage = { repo: GitHubRepo ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, (GITHUB_URL_PREFIX + repo).toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { e -> Log.w(TAG, "Could not open $repo", e) }
    }

    val shown = remember(rows, query) {
        val q = query.trim()
        rows.filter { it.tracked.repo.name.contains(q, ignoreCase = true) || it.tracked.repo.owner.contains(q, ignoreCase = true) }
    }

    // Only a pull drives the indicator, so background checks don't make it pop in on its own.
    var pullRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(refreshing) {
        if (!refreshing) {
            pullRefreshing = false
        }
    }

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

            // Page title
            PageTitle(stringResource(R.string.git_title))

            // Search field
            SearchField(
                query = query,
                onQueryChange = { query = it },
                onSearch = {},
                onClear = { query = "" },
                hint = R.string.git_search_hint,
            )

            // Repo cards
            shown.forEachIndexed { index, row ->
                val shape = groupedShapeAt(index, shown.size)

                // Keyed so a removed row's swipe state is not handed to the row that takes its place.
                key(row.tracked.key) {
                    SwipeAction(
                        shape = shape,
                        start = SwipeSide(
                            icon = Icons.Rounded.RemoveCircleOutline,
                            label = R.string.action_remove,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            settlesBack = true,
                            onAction = {
                                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                removingKey = row.tracked.key
                            },
                        ),
                        end = SwipeSide(
                            icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            label = R.string.git_open_github,
                            settlesBack = true,
                            onAction = {
                                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                openRepoPage(row.tracked.repo)
                            },
                        ),
                    ) {
                        GitRepoCard(row, shape, viewModel)
                    }
                }
            }

            // Swipe hint
            if (shown.isNotEmpty()) {
                Text(
                    stringResource(R.string.git_swipe_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(AddButtonClearance))
        }

        // Floating add and update-all bar
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

                // Add button
                Button(
                    onClick = { adding = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) { ActionContent(Icons.Rounded.Add, R.string.git_add) }

                // Update all button, with a pill counting the available updates
                TextButton(onClick = viewModel::updateAll, enabled = updateCount != 0) {
                    ActionContent(Icons.Rounded.Refresh, R.string.git_update_all)

                    if (updateCount != 0) {
                        Spacer(Modifier.width(8.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) { Text("$updateCount") }
                    }
                }
            }
        }
    }

    // Add repo dialog
    if (adding) {
        AddGitRepoDialog(onAdd = viewModel::add, onDismiss = { adding = false })
    }

    // Remove repo dialog
    rows.firstOrNull { it.tracked.key == removingKey }?.let { row ->
        RemoveRepoDialog(
            repoName = row.tracked.repo.toString(),
            onConfirm = {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                viewModel.remove(row.tracked.repo)
                removingKey = null
            },
            onDismiss = { removingKey = null },
        )
    }

    // APK chooser dialog
    rows.firstOrNull { it.state is TrackState.Choosing }?.let { row ->
        ChooseApkDialog(
            assets = (row.state as TrackState.Choosing).assets,
            onChoose = { viewModel.choose(row.tracked.repo, it) },
            onDismiss = { viewModel.dismissChoice(row.tracked.repo) },
        )
    }
}

/**
 * Asks before a repo is dropped from the list, reminding that the installed app stays.
 */
@Composable
private fun RemoveRepoDialog(repoName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.repos_remove)) },
        text = { Text(stringResource(R.string.git_remove_confirm, repoName)) },
        // Remove button
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.action_remove))
            }
        },
        // Cancel button
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Card for one followed repo: avatar, name, versions, then install actions or its tracking status.
 */
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

    val scheme = MaterialTheme.colorScheme
    val updatable = actions?.state?.updateAvailable == true

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = if (updatable) {
            CardDefaults.cardColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = Layering.item)
        },
    ) {
        Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Avatar and title row
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {

                // Owner avatar; on a highlighted card its fallback steps up to primary so it doesn't vanish into the container.
                Surface(
                    shape = CircleShape,
                    color = if (updatable) scheme.primary else scheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    // The glyph sits under the avatar: it shows while loading and whenever the avatar can't be fetched.
                    Box {
                        Icon(
                            GitIcon,
                            contentDescription = null,
                            tint = if (updatable) scheme.onPrimary else scheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp),
                        )
                        AsyncImage(
                            model = GITHUB_URL_PREFIX + tracked.repo.owner + AVATAR_URL_SUFFIX,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Name, owner and versions
                Column {

                    // Name and owner line
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
                            color = mutedColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }

                    // Latest version line
                    latest?.let { VersionLine(stringResource(R.string.git_latest, it.tag)) }

                    // Installed version line
                    actions?.state?.installedVersionName?.let { VersionLine(stringResource(R.string.git_installed, it)) }
                }
            }

            // Status, install actions or choice prompt
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

/**
 * Secondary text: onSurfaceVariant on a plain card, the card's own onPrimaryContainer on a highlighted (updatable) one.
 */
@Composable
private fun mutedColor(): Color {
    val scheme = MaterialTheme.colorScheme

    return if (LocalContentColor.current == scheme.onPrimaryContainer) scheme.onPrimaryContainer else scheme.onSurfaceVariant
}

/**
 * One muted single-line version label.
 */
@Composable
private fun VersionLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = mutedColor(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Delete, Open and Update side by side; Update turns into a disabled "Up to date" when nothing newer is out.
 */
@Composable
private fun InstalledActions(actions: AppInstallActions) {
    val state = actions.state
    val canUpdate = state.updateAvailable && state.canInstall
    val padding = PaddingValues(horizontal = 8.dp)

    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(ActionGap)) {

        // Delete button
        FilledTonalButton(
            onClick = actions.onUninstall,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            contentPadding = padding,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { ActionContent(Icons.Rounded.Delete, R.string.action_delete) }

        // Open button
        FilledTonalButton(
            onClick = actions.onOpen,
            enabled = state.canOpen,
            contentPadding = padding,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { ActionContent(Icons.Rounded.PlayArrow, R.string.action_open) }

        // Update button
        Button(
            onClick = actions.onInstall,
            enabled = canUpdate,
            contentPadding = padding,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { ActionContent(Icons.Rounded.Download, if (canUpdate) R.string.action_update else R.string.git_up_to_date) }
    }
}

/**
 * Icon and label for the inside of a button.
 */
@Composable
private fun ActionContent(icon: ImageVector, @StringRes label: Int) {
    // Icon
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))

    Spacer(Modifier.width(6.dp))

    // Label
    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
}

/**
 * What a tracking run is doing; Choosing has no inline status because its dialog takes over.
 */
@Composable
private fun RowStatus(state: TrackState, onRetry: () -> Unit) {
    when (state) {
        TrackState.Checking -> StatusLine(stringResource(R.string.git_checking), progress = { null })

        is TrackState.Downloading -> StatusLine(
            state.progress?.let { stringResource(R.string.git_downloading_percent, (it * PERCENT).toInt()) }
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

/**
 * A line of text, with a progress bar when [progress] is given (null inside means indeterminate) and a button when [actionLabel] is.
 */
@Composable
private fun StatusLine(
    text: String,
    isError: Boolean = false,
    progress: (() -> Float?)? = null,
    actionLabel: Int? = null,
    onAction: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Status text
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else mutedColor(),
        )

        // Progress bar
        if (progress != null) {
            val value = progress()

            if (value != null) {
                LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        // Action button
        if (actionLabel != null) {
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(stringResource(actionLabel)) }
        }
    }
}

/**
 * Lists the release's APKs when several fit; [onChoose] gets the picked one.
 */
@Composable
private fun ChooseApkDialog(assets: List<GitHubAsset>, onChoose: (GitHubAsset) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<GitHubAsset?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.git_choose_apk)) },
        text = {
            Column {

                // Intro text
                Text(stringResource(R.string.git_choose_apk_intro), style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.height(8.dp))

                // APK options
                assets.forEach { asset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = asset == selected, role = Role.RadioButton, onClick = { selected = asset })
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = asset == selected, onClick = null)
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
        // Install button
        confirmButton = {
            TextButton(onClick = { selected?.let(onChoose) }, enabled = selected != null) {
                Text(stringResource(R.string.action_install))
            }
        },
        // Cancel button
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Asks for a GitHub link; [onAdd] returns the reason it was refused, or null once the repo is added.
 */
@Composable
private fun AddGitRepoDialog(onAdd: (String) -> UiText?, onDismiss: () -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<UiText?>(null) }
    val submit = {
        error = onAdd(link)

        if (error == null) {
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.git_add_repo)) },
        // Link field
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
        // Add button
        confirmButton = {
            TextButton(onClick = submit, enabled = link.isNotBlank()) { Text(stringResource(R.string.git_add)) }
        },
        // Cancel button
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
