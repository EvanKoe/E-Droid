package app.edroid.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/*
 * Single place that pins which Material 3 roles the app's layers use, so screens never pick
 * container tones ad hoc:
 *
 *  - page / Scaffold / top app bar   → surfaceContainer
 *  - list items, cards, text fields  → surfaceContainerHighest
 *  - selected floating-nav pill      → background
 */

/** Color of a screen's page background. */
val ColorScheme.pageColor: Color get() = surfaceContainerLow

/** Color of a grouped-list item, card or input field sitting on the page. */
val ColorScheme.itemColor: Color get() = surfaceContainerLowest

/** Color of the selected pill in the floating navigation bar. */
val ColorScheme.navSelectedColor: Color get() = background

/** Shorthands for the current theme. */
object Layering {
    val page: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.pageColor
    val item: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.itemColor
    val navSelected: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.navSelectedColor
}
