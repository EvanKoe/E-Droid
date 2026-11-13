package app.edroid.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Swiping the row towards the start uncovers a backdrop naming the action and, once settled, runs it.
 *
 * @param shape the row's own shape, so the backdrop never shows past its corners.
 * @param icon glyph drawn on the backdrop, beside [label].
 * @param label what the swipe does, shown on the backdrop.
 * @param onAction runs once the row has been swiped away.
 * @param containerColor backdrop colour; its content colour is derived from it.
 */
@Composable
fun SwipeAction(
    shape: Shape,
    icon: ImageVector,
    @StringRes label: Int,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val contentColor = contentColorFor(containerColor)

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onAction()
            }
        },
        // Action backdrop
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(containerColor)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = contentColor)
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = contentColor)
            }
        },
    ) {
        content()
    }
}
