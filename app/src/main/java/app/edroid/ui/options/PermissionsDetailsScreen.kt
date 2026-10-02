package app.edroid.ui.options

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.edroid.R
import app.edroid.ui.components.PageTitle
import app.edroid.ui.theme.Layering

/**
 * One permission declared in the manifest, as explained to the user.
 *
 * @param manifestName the `android.permission.*` name, shown for users who want to cross-check.
 * @param whyRes when the user is asked for it and why the app needs it.
 * @param usesRes a string array of what the permission is used for, one bullet each.
 */
private data class PermissionInfo(
    val icon: ImageVector,
    @param:StringRes val titleRes: Int,
    val manifestName: String,
    @param:StringRes val whyRes: Int,
    @param:ArrayRes val usesRes: Int,
    val optional: Boolean = false,
)

/** Keep in sync with `AndroidManifest.xml`. Required permissions first, then optional ones. */
private val Permissions = listOf(
    PermissionInfo(
        Icons.Rounded.Language, R.string.permission_internet_title, "INTERNET",
        R.string.permission_internet_why, R.array.permission_internet_uses,
    ),
    PermissionInfo(
        Icons.Rounded.Apps, R.string.permission_query_packages_title, "QUERY_ALL_PACKAGES",
        R.string.permission_query_packages_why, R.array.permission_query_packages_uses,
    ),
    PermissionInfo(
        Icons.Rounded.InstallMobile, R.string.permission_install_title, "REQUEST_INSTALL_PACKAGES",
        R.string.permission_install_why, R.array.permission_install_uses,
    ),
    PermissionInfo(
        Icons.Rounded.Delete, R.string.permission_delete_title, "REQUEST_DELETE_PACKAGES",
        R.string.permission_delete_why, R.array.permission_delete_uses,
    ),
    PermissionInfo(
        Icons.Rounded.Notifications, R.string.permission_notifications_title, "POST_NOTIFICATIONS",
        R.string.permission_notifications_why, R.array.permission_notifications_uses, optional = true,
    ),
)

/**
 * Explains every permission EDroid declares: whether it is optional, when it is asked, and what
 * the app uses it for. Opened from the Options tab and drawn over it by `MainScreen`, which swaps
 * the nav bar for a back bar.
 *
 * @param contentPadding insets handed down by `MainScreen` so content clears both scrims.
 */
@Composable
fun PermissionsDetailsScreen(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Layering.page)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            PageTitle(text = stringResource(R.string.permissions_title))
            Text(
                stringResource(R.string.permissions_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Permissions.forEach { PermissionCard(it) }
    }
}

@Composable
private fun PermissionCard(permission: PermissionInfo) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(shape = MaterialTheme.shapes.extraLarge, color = Layering.item, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(permission.icon, contentDescription = null, tint = primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(permission.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = primary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (permission.optional) {
                    Spacer(Modifier.width(12.dp))
                    OptionalPill()
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                permission.manifestName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(permission.whyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.permissions_used_for), style = MaterialTheme.typography.titleMedium, color = primary)
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stringArrayResource(permission.usesRes).forEach { BulletRow(it) }
            }
        }
    }
}

@Composable
private fun OptionalPill() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            stringResource(R.string.permissions_optional),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun BulletRow(text: String) {
    Row {
        // Centers the dot on the first line of text, however many lines the bullet wraps to.
        Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        }
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
