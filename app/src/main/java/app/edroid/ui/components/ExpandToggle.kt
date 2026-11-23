package app.edroid.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * A pill-style text button with a chevron that flips when expanded.
 *
 * @param opensBelow whether the content it reveals sits under it, so the chevron points down while collapsed.
 */
@Composable
internal fun ExpandToggle(
    text: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    opensBelow: Boolean = false,
) {
    // Chevron flip and a slight grow ride the same overshooting spring as the row buttons.
    val chevron by animateFloatAsState(if (expanded != opensBelow) 180f else 0f, bouncySpring(), label = "hiddenChevron")
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
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, modifier = Modifier.rotate(chevron))
    }
}
