package app.edroid.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Start padding of the heading, aligning its text with the cards below. */
private val StartInset = 8.dp

/** Padding above the heading. */
private val TopInset = 12.dp

/** Padding below the heading. */
private val BottomInset = 8.dp

/**
 * Small primary-colored heading above a grouped list section.
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    // Section heading
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = StartInset, top = TopInset, bottom = BottomInset),
    )
}
