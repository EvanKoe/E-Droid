package app.edroid.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radius on the outer edges of a grouped list. */
val GroupedOuterRadius: Dp = 24.dp

/** Corner radius between two stacked items of a grouped list. */
val GroupedInnerRadius: Dp = 4.dp

/** Vertical gap between stacked items of a grouped list. */
val GroupedItemSpacing: Dp = 4.dp

/**
 * Shape for an item in a grouped (segmented) list: large radius on the group's outer edges,
 * tight radius between stacked items, so a run of cards reads as one block.
 *
 * - `groupedShape(isFirst = true)` for the first item
 * - `groupedShape()` for items in the middle
 * - `groupedShape(isLast = true)` for the last item
 * - `groupedShape(isFirst = true, isLast = true)` for a single standalone item
 *
 * Pair it with [GroupedItemSpacing] between items. For indexed lists use [groupedShapeAt].
 */
fun groupedShape(isFirst: Boolean = false, isLast: Boolean = false) = RoundedCornerShape(
    topStart = if (isFirst) GroupedOuterRadius else GroupedInnerRadius,
    topEnd = if (isFirst) GroupedOuterRadius else GroupedInnerRadius,
    bottomStart = if (isLast) GroupedOuterRadius else GroupedInnerRadius,
    bottomEnd = if (isLast) GroupedOuterRadius else GroupedInnerRadius,
)

/** [groupedShape] for the item at [index] in a list of [count] items. */
fun groupedShapeAt(index: Int, count: Int) =
    groupedShape(isFirst = index == 0, isLast = index == count - 1)

/** [groupedShapeAt] for a horizontal row: outer radius on the row's start and end edges. */
fun horizontalGroupedShapeAt(index: Int, count: Int): RoundedCornerShape {
    val start = if (index == 0) GroupedOuterRadius else GroupedInnerRadius
    val end = if (index == count - 1) GroupedOuterRadius else GroupedInnerRadius
    return RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
}

/** Horizontal inset between a page's content and the screen edge. */
val PageHorizontalPadding: Dp = 16.dp
