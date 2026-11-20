package app.edroid.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.edroid.R

/** Tells the user an install failed, with the raw [message] behind a toggle and Retry / Close. */
@Composable
internal fun InstallFailedDialog(
    message: String,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.install_failed_title)) },
        text = {
            Column {
                Text(stringResource(R.string.install_failed_body), style = MaterialTheme.typography.bodyMedium)
                ExpandToggle(
                    text = stringResource(if (expanded) R.string.details_show_less else R.string.details_show_more),
                    expanded = expanded,
                    onClick = { expanded = !expanded },
                    opensBelow = true,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                )
                AnimatedVisibility(expanded) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        // Gap lives in here so it collapses with the box instead of padding the closed dialog.
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(max = 160.dp),
                    ) {
                        Box(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                            SelectionContainer {
                                Text(message, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry, enabled = retryEnabled) { Text(stringResource(R.string.action_retry)) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
        },
    )
}
