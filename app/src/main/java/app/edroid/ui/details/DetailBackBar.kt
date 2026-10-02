package app.edroid.ui.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.edroid.R
import app.edroid.ui.theme.Layering

/**
 * Floating bar that replaces the nav bar on a detail page: a back button in the same light
 * circle the nav bar uses for its selected pill, followed by the page's title.
 */
@Composable
fun DetailBackBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onBack,
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = Layering.navSelected, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp).widthIn(max = 220.dp),
            )
        }
    }
}
