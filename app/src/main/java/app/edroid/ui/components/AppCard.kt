package app.edroid.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.edroid.data.fdroid.model.App
import app.edroid.ui.theme.Layering
import app.edroid.ui.theme.groupedShape
import coil3.compose.AsyncImage

/** Inner padding of the card. */
private val CardPadding = 12.dp

/** Size of the app icon tile. */
private val IconSize = 56.dp

/** Horizontal gap between the icon and the text column. */
private val IconTextGap = 16.dp

/** Vertical gap between the summary and the version footer. */
private val FooterGap = 4.dp

/** Separator between the version name and the update date in the footer. */
private const val FOOTER_SEPARATOR = "  ·  "

/** Number of summary lines shown before truncating. */
private const val SUMMARY_MAX_LINES = 2

/**
 * One app in a grouped list: icon, name, summary and a version/date footer.
 */
@Composable
fun AppCard(
    app: App,
    modifier: Modifier = Modifier,
    shape: Shape = groupedShape(),
    onClick: () -> Unit = {},
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Layering.item),
    ) {
        Row(
            modifier = Modifier.padding(CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IconTextGap),
        ) {

            // App icon
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Layering.page, // icon placeholder reads as an inset into the page
                modifier = Modifier.size(IconSize),
            ) {
                AsyncImage(
                    model = app.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                )
            }

            // Text column
            Column(Modifier.weight(1f)) {

                // App name
                Text(
                    app.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Summary
                app.summary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = SUMMARY_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(FooterGap))

                // Version and date footer
                Text(
                    listOfNotNull(app.latestVersionName, relativeTime(app.lastUpdated)).joinToString(FOOTER_SEPARATOR),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * "Today", "3 days ago", or a short date for older timestamps.
 */
fun relativeTime(epochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        DateUtils.DAY_IN_MILLIS,
    ).toString()
