package app.edroid.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

/** One app in a grouped list: icon, name, summary and a version/date footer. */
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
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Layering.page, // icon placeholder reads as an inset into the page
                modifier = Modifier.size(56.dp),
            ) {
                AsyncImage(
                    model = app.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                app.summary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(app.latestVersionName, relativeTime(app.lastUpdated)).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** "Today", "3 days ago", or a short date for older timestamps. */
fun relativeTime(epochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        DateUtils.DAY_IN_MILLIS,
    ).toString()
