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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

/**
 * What one swipe direction of a [SwipeAction] row shows and does.
 *
 * @param icon glyph drawn on the backdrop, beside [label].
 * @param label what the swipe does, shown on the backdrop.
 * @param containerColor backdrop colour, the secondary container when unspecified; its content colour is derived from it.
 * @param settlesBack true when the row stays in the list after the action, so it must slide back into place.
 * @param onAction runs once the row has been swiped all the way.
 */
class SwipeSide(
    val icon: ImageVector,
    @param:StringRes val label: Int,
    val containerColor: Color = Color.Unspecified,
    val settlesBack: Boolean = false,
    val onAction: () -> Unit,
)

/**
 * Swiping the row uncovers a backdrop naming that direction's action and, once settled, runs it.
 *
 * @param shape the row's own shape, so the backdrop never shows past its corners.
 * @param start revealed at the start edge, by swiping towards the end; null disables that direction.
 * @param end revealed at the end edge, by swiping towards the start; null disables that direction.
 */
@Composable
fun SwipeAction(
    shape: Shape,
    modifier: Modifier = Modifier,
    start: SwipeSide? = null,
    end: SwipeSide? = null,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = start != null,
        enableDismissFromEndToStart = end != null,
        onDismiss = { value ->
            val side = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> start
                SwipeToDismissBoxValue.EndToStart -> end
                SwipeToDismissBoxValue.Settled -> null
            }

            side?.onAction?.invoke()

            if (side?.settlesBack == true) {
                scope.launch { dismissState.reset() }
            }
        },
        // Action backdrop
        backgroundContent = {
            val atStart = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val side = if (atStart) start else end

            if (side != null && dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                val container = if (side.containerColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    side.containerColor
                }

                val contentColor = contentColorFor(container)

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(container)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, if (atStart) Alignment.Start else Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(side.icon, contentDescription = null, tint = contentColor)
                    Text(stringResource(side.label), style = MaterialTheme.typography.labelLarge, color = contentColor)
                }
            }
        },
    ) {
        content()
    }
}
