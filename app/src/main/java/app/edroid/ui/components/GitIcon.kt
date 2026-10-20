package app.edroid.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A git branch glyph (the Material icon set has none), stroked so it sits well beside the rounded icons. */
val GitIcon: ImageVector = ImageVector.Builder(
    name = "Git",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
    ) {
        // Trunk between the two left nodes.
        moveTo(6f, 8f)
        lineTo(6f, 16f)
        // Branch leaving the right node and merging into the trunk.
        moveTo(18f, 11f)
        verticalLineToRelative(1f)
        arcToRelative(3f, 3f, 0f, false, true, -3f, 3f)
        horizontalLineTo(6f)
        // Nodes.
        for ((cx, cy) in listOf(6f to 5.5f, 6f to 18.5f, 18f to 8.5f)) {
            moveTo(cx + 2.5f, cy)
            arcToRelative(2.5f, 2.5f, 0f, true, true, -5f, 0f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, 5f, 0f)
        }
    }
}.build()
