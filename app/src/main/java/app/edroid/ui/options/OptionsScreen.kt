package app.edroid.ui.options

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.edroid.R
import app.edroid.ui.components.PageTitle
import app.edroid.ui.components.SectionTitle
import app.edroid.ui.theme.GroupedItemSpacing
import app.edroid.ui.theme.Layering
import app.edroid.ui.theme.groupedShape

/**
 * App settings, as a grouped list of entries that each open their own page.
 *
 * @param contentPadding insets handed down by `MainScreen` so content clears both scrims.
 * @param onPermissionsClick opens the permissions explanation page.
 */
@Composable
fun OptionsScreen(
    contentPadding: PaddingValues,
    onPermissionsClick: () -> Unit,
    viewModel: OptionsViewModel = viewModel(factory = OptionsViewModel.Factory),
) {
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openUrl = { url: String ->
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    val githubUrl = stringResource(R.string.dev_card_github_url)
    val coffeeUrl = stringResource(R.string.dev_card_coffee_url)
    val linkedInUrl = stringResource(R.string.dev_card_linkedin_url)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(GroupedItemSpacing),
    ) {
        PageTitle(text = stringResource(R.string.options_title))
        SectionTitle(stringResource(R.string.options_general))
        OptionItem(
            icon = Icons.Rounded.Security,
            title = stringResource(R.string.options_permissions),
            summary = stringResource(R.string.options_permissions_summary),
            shape = groupedShape(isFirst = true),
            onClick = onPermissionsClick,
        )
        SwitchOptionItem(
            icon = Icons.Rounded.Vibration,
            title = stringResource(R.string.options_haptics),
            summary = stringResource(R.string.options_haptics_summary),
            shape = groupedShape(isLast = true),
            checked = hapticsEnabled,
            onCheckedChange = viewModel::setHapticsEnabled,
        )
        SectionTitle(stringResource(R.string.options_about))
        DevCard(
            shape = groupedShape(isFirst = true, isLast = true),
            onOpenGitHub = { openUrl(githubUrl) },
            onOpenCoffee = { openUrl(coffeeUrl) },
            onOpenLinkedIn = { openUrl(linkedInUrl) },
        )
    }
}

/** One navigable row of the Options list: icon, title and summary, with a trailing chevron. */
@Composable
private fun OptionItem(
    icon: ImageVector,
    title: String,
    summary: String,
    shape: Shape,
    onClick: () -> Unit,
) {
    OptionCard(icon, title, summary, shape, onClick) {
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A boolean row of the Options list; tapping anywhere on it flips the trailing switch. */
@Composable
private fun SwitchOptionItem(
    icon: ImageVector,
    title: String,
    summary: String,
    shape: Shape,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    OptionCard(icon, title, summary, shape, onClick = { onCheckedChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Shared layout of an Options row: icon badge, title and summary, then [trailing]. */
@Composable
private fun OptionCard(
    icon: ImageVector,
    title: String,
    summary: String,
    shape: Shape,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Layering.item),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** The "about" card that closes the list: the developer's photo and the links out as filled buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DevCard(
    shape: Shape,
    onOpenGitHub: () -> Unit,
    onOpenCoffee: () -> Unit,
    onOpenLinkedIn: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Layering.item),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.dev_avatar),
                contentDescription = stringResource(R.string.dev_card_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(28.dp)),
            )

            Text(
                text = stringResource(R.string.dev_card_author),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 20.dp),
            )

            // Wraps to a second line on narrow screens rather than squeezing either label.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DevLinkButton(painterResource(R.drawable.ic_github), stringResource(R.string.dev_card_github), onOpenGitHub)
                Button(onClick = onOpenCoffee) {
                    Icon(Icons.Rounded.Coffee, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dev_card_coffee))
                }
                DevLinkButton(painterResource(R.drawable.ic_linkedin), stringResource(R.string.dev_card_linkedin), onOpenLinkedIn)
            }
        }
    }
}

/** A filled link button of [DevCard] with a drawable icon ahead of its label. */
@Composable
private fun DevLinkButton(icon: Painter,label: String, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
