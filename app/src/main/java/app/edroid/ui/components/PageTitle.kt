@file:OptIn(ExperimentalTextApi::class)

package app.edroid.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.edroid.R

/** Roboto Flex at display weight, widened on the `wdth` axis for the expressive page heading. */
private val RobotoFlexDisplay = FontFamily(
    Font(
        resId = R.font.roboto_flex,
        weight = FontWeight.W700,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.width(125f),
            FontVariation.opticalSizing(96.sp),
        ),
    ),
)

/**
 * The big typographic heading at the top of every page. It is ordinary scrolling content, not an
 * app bar: it lives as the first child of the page's scroll container and fades out under the top
 * scrim drawn by `MainScreen` as the page scrolls.
 */
@Composable
fun PageTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: TextUnit = MaterialTheme.typography.headlineLarge.fontSize,
) {
    val style: TextStyle = MaterialTheme.typography.headlineLarge.copy(
        fontFamily = RobotoFlexDisplay,
        fontWeight = FontWeight.W700,
    )

    Text(
        text = text,
        style = style,
        color = color,
        fontSize = size,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
