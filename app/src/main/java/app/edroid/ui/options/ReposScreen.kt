package app.edroid.ui.options

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.edroid.R
import app.edroid.data.fdroid.SyncState
import app.edroid.data.fdroid.model.Repository
import app.edroid.ui.components.PageTitle
import app.edroid.ui.theme.GroupedItemSpacing
import app.edroid.ui.theme.Layering
import app.edroid.ui.theme.groupedShapeAt

/**
 * The configured repositories, each with a switch to include or hide its apps, and a button to
 * add a new one. Opened from the Options tab and drawn over it by `MainScreen`, which swaps the
 * nav bar for a back bar.
 *
 * Tapping a repo reveals its full signing-key fingerprint and, for user-added repos, a remove
 * button.
 *
 * @param contentPadding insets handed down by `MainScreen` so content clears both scrims.
 */
@Composable
fun ReposScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ReposViewModel = viewModel(factory = ReposViewModel.Factory),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Repository?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Layering.page)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(GroupedItemSpacing),
    ) {
        Column {
            PageTitle(text = stringResource(R.string.repos_title))
            Text(
                stringResource(R.string.repos_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        rows.forEachIndexed { index, row ->
            RepoCard(
                row = row,
                shape = groupedShapeAt(index, rows.size),
                onEnabledChange = { viewModel.setEnabled(row.repo, it) },
                onRemove = { removing = row.repo },
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                viewModel.startAdding()
                adding = true
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.repos_add))
        }
    }

    if (adding) AddRepoSheet(viewModel, onDismiss = { adding = false })

    removing?.let { repo ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.repos_remove)) },
            text = { Text(stringResource(R.string.repos_remove_confirm, repo.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(repo)
                    removing = null
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** One repo: name, address and status, its switch, and the fingerprint once expanded. */
@Composable
private fun RepoCard(
    row: RepoRow,
    shape: Shape,
    onEnabledChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val repo = row.repo
    var expanded by rememberSaveable(repo.id) { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Layering.item),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(repo.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        repo.address.substringAfter("://"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    RepoStatus(row)
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = repo.enabled, onCheckedChange = onEnabledChange)
            }
            val sync = row.sync
            if (repo.enabled && sync is SyncState.Loading) {
                Spacer(Modifier.height(12.dp))
                val progress = sync.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            AnimatedVisibility(visible = expanded) {
                RepoDetails(repo, onRemove)
            }
        }
    }
}

/** App count once loaded, or the last sync error; nothing while the repo is off and unread. */
@Composable
private fun RepoStatus(row: RepoRow) {
    val sync = row.sync
    when {
        row.repo.enabled && sync is SyncState.Error -> Text(
            sync.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        row.appCount != null -> Text(
            pluralStringResource(R.plurals.repos_app_count, row.appCount, row.appCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RepoDetails(repo: Repository, onRemove: () -> Unit) {
    Column(modifier = Modifier.padding(start = 56.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.repos_fingerprint),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        FingerprintText(repo.fingerprint)
        Spacer(Modifier.height(8.dp))
        if (repo.isDefault) {
            Text(
                stringResource(R.string.repos_built_in),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.repos_remove))
            }
        }
    }
}

/** A fingerprint in blocks of four, monospaced, so it can be compared against a published one. */
@Composable
private fun FingerprintText(fingerprint: String?) {
    Text(
        fingerprint?.chunked(4)?.joinToString(" ") ?: stringResource(R.string.repos_unpinned),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Collects an address and an optional fingerprint, then checks the repo before saving it. When no
 * fingerprint was given, the key the repo is signed with is shown and must be trusted explicitly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRepoSheet(viewModel: ReposViewModel, onDismiss: () -> Unit) {
    val state by viewModel.addState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(state.added) {
        if (state.added) {
            sheetState.hide()
            onDismiss()
        }
    }
    val pending = state.pendingTrust
    val confirm = if (pending != null) viewModel::trust else viewModel::submit

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.add_repo_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.add_repo_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextField(
                value = state.address,
                onValueChange = viewModel::onAddressChange,
                label = { Text(stringResource(R.string.add_repo_address)) },
                placeholder = { Text(stringResource(R.string.add_repo_address_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                shape = MaterialTheme.shapes.extraLarge,
                colors = sheetFieldColors(),
                isError = state.addressError != null,
                supportingText = state.addressError?.let { { Text(it.resolve()) } },
                singleLine = true,
                enabled = !state.verifying,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = state.fingerprint,
                onValueChange = viewModel::onFingerprintChange,
                label = { Text(stringResource(R.string.add_repo_fingerprint)) },
                leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                shape = MaterialTheme.shapes.extraLarge,
                colors = sheetFieldColors(),
                isError = state.fingerprintError != null,
                supportingText = {
                    Text(state.fingerprintError?.resolve() ?: stringResource(R.string.add_repo_fingerprint_supporting))
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                enabled = !state.verifying,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (pending != null) TrustCard(pending)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (state.verifying) {
                    Text(
                        stringResource(R.string.add_repo_verifying),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = confirm, enabled = !state.verifying && state.address.isNotBlank()) {
                    if (state.verifying) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(if (pending != null) R.string.add_repo_trust else R.string.add_repo_submit))
                    }
                }
            }
        }
    }
}

/** The signer of an unpinned repo, shown for the user to compare before trusting it. */
@Composable
private fun TrustCard(pending: PendingTrust) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.add_repo_confirm_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                stringResource(R.string.add_repo_confirm_body, pending.address.substringAfter("://")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                pending.fingerprint.chunked(4).joinToString(" "),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Filled, borderless fields on a raised surface, matching the Search tab's field. */
@Composable
private fun sheetFieldColors() = TextFieldDefaults.colors(
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
    focusedContainerColor = Layering.item,
    unfocusedContainerColor = Layering.item,
    disabledContainerColor = Layering.item,
    errorContainerColor = Layering.item,
)

@Composable
private fun UiText.resolve(): String = if (arg != null) stringResource(res, arg) else stringResource(res)
