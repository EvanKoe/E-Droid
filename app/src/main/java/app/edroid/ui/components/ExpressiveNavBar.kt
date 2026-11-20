package app.edroid.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.edroid.ui.theme.Layering

/** Material 3 expressive "emphasized" easing: cubic-bezier(0.2, 0.0, 0.0, 1.0). */
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Corner radius of the floating bar. */
private val BarCornerRadius = 32.dp

/** Corner radius of a tab pill. */
private val PillCornerRadius = 24.dp

/** Shadow elevation of the floating bar. */
private val BarShadowElevation = 6.dp

/** Padding between the bar's edge and its pills. */
private val BarPadding = 8.dp

/** Gap between pills. */
private val PillGap = 4.dp

/** Gap between a pill's children. */
private val PillContentGap = 8.dp

/** Horizontal padding of a selected pill, which widens on selection. */
private val SelectedPillPadding = 22.dp

/** Horizontal padding of an unselected pill. */
private val UnselectedPillPadding = 16.dp

/** Vertical padding of a pill. */
private val PillVerticalPadding = 12.dp

/** Duration of the pill colour animations, in milliseconds. */
private const val PILL_COLOR_MS = 350

/** Scale of the icon in a selected pill. */
private const val SELECTED_ICON_SCALE = 1.2f

/** One destination in the [ExpressiveNavBar]. */
data class NavBarItem(
    val label: String,
    val icon: ImageVector,
)

/**
 * A Material 3 "expressive" floating bottom bar: a rounded primary container whose selected item
 * animates into a light pill. Purely presentational: selection state and
 * clicks are hoisted to the caller.
 */
@Composable
fun ExpressiveNavBar(
    items: List<NavBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Floating bar
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(BarCornerRadius),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tonalElevation = 0.dp,
        shadowElevation = BarShadowElevation,
    ) {
        Row(
            modifier = Modifier.padding(BarPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PillGap),
        ) {

            // Tab pills
            items.forEachIndexed { index, item ->
                NavBarPill(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

/**
 * One tab of the [ExpressiveNavBar]: an icon in a pill that widens, recolours and pops when [selected].
 */
@Composable
private fun NavBarPill(
    item: NavBarItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) Layering.navSelected else Color.Transparent,
        animationSpec = tween(durationMillis = PILL_COLOR_MS, easing = EmphasizedEasing),
        label = "navPillContainer",
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
        animationSpec = tween(durationMillis = PILL_COLOR_MS, easing = EmphasizedEasing),
        label = "navPillContent",
    )

    // Bouncy springs overshoot on selection: the pill widens and the icon pops.
    val horizontalPadding by animateDpAsState(
        targetValue = if (selected) SelectedPillPadding else UnselectedPillPadding,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "navPillPadding",
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) SELECTED_ICON_SCALE else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "navPillIconScale",
    )

    // Pill
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(PillCornerRadius))
            .background(containerColor)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            )
            .padding(horizontal = horizontalPadding, vertical = PillVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PillContentGap),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            },
        )
    }
}
