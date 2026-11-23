package app.edroid.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import app.edroid.R
import app.edroid.ui.components.BAR_TINT_ALPHA
import app.edroid.ui.components.BarBlurRadius
import app.edroid.ui.components.backdropBlur
import app.edroid.ui.components.supportsBackdropBlur
import app.edroid.ui.theme.Layering

/** Elevation of the floating bar. */
private val BarShadowElevation = 6.dp

/** Longest the title may grow before it is ellipsized. */
private val TitleMaxWidth = 220.dp

/**
 * Floating bar that replaces the nav bar on a detail page: a back button in the same light
 * circle the nav bar uses for its selected pill, followed by the page's title.
 */
@Composable
fun DetailBackBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // Same frosted look as the nav bar on Android 17 QPR2+, with its colours inverted the same way.
    val blurred = supportsBackdropBlur()
    Surface(
        onClick = onBack,
        modifier = modifier,
        shape = CircleShape,
        color = if (blurred) Color.Transparent else colors.primary,
        contentColor = if (blurred) colors.onSurface else colors.onPrimary,
        shadowElevation = if (blurred) 0.dp else BarShadowElevation,
    ) {
        Row(
            modifier = Modifier
                .then(if (blurred) Modifier.backdropBlur(colors.primary.copy(alpha = BAR_TINT_ALPHA), BarBlurRadius) else Modifier)
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            // Back button
            Surface(
                shape = CircleShape,
                color = if (blurred) colors.primary else Layering.navSelected,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = if (blurred) colors.onPrimary else colors.primary,
                    )
                }
            }

            // Page title
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = TitleMaxWidth),
            )
        }
    }
}
