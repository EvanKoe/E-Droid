package app.edroid.ui.details

import android.text.TextUtils
import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.edroid.EDroidApp
import app.edroid.R
import app.edroid.data.fdroid.model.App
import app.edroid.data.fdroid.model.AppDetails
import app.edroid.data.fdroid.model.AppVersion
import app.edroid.ui.components.InstallActions
import app.edroid.ui.components.PageTitle
import app.edroid.ui.components.appInstallActions
import app.edroid.ui.components.SectionTitle
import app.edroid.ui.components.relativeTime
import app.edroid.ui.theme.Layering
import app.edroid.ui.theme.PageHorizontalPadding
import app.edroid.ui.theme.groupedShape
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException

/** F-Droid feature graphics are 1024×500. */
private const val BANNER_ASPECT = 1024f / 500f

/** Side of the square app icon tile in the header. */
private val IconTileSize = 96.dp

/** How far the icon tile reaches up into the banner. */
private val IconOverlap = 32.dp

/** Width of the large carousel item; the medium and small ones are derived from it. */
private val MediaPreferredWidth = 200.dp

/** Height of the screenshot carousel. */
private val MediaHeight = 300.dp

/** Gap between carousel items. */
private val MediaItemSpacing = 8.dp

/** Lines of the description shown before the user expands it. */
private const val DESCRIPTION_COLLAPSED_LINES = 6

/** Placeholder tiles shown in the carousel while the media loads. */
private const val MEDIA_PLACEHOLDER_COUNT = 3

/** Rotation, in degrees, of the version picker arrow while its menu is open. */
private const val PICKER_ARROW_OPEN_DEGREES = 180f

/** Opacity of the scrim behind the full-screen screenshot viewer. */
private const val VIEWER_SCRIM_ALPHA = 0.92f

/** Separator between facts on one line, such as age and size. */
private const val FACT_SEPARATOR = "  ·  "

/** The heavy part of the page, read from the on-disk index after the screen opens. */
private sealed interface DetailsState {
    data object Loading : DetailsState
    data class Loaded(val details: AppDetails) : DetailsState
    data object NotFound : DetailsState
    data class Failed(val message: String) : DetailsState
}

/**
 * One app's page: banner with the icon and Install button, an info card (publisher, version,
 * description), a strip of screenshots and a version picker. Drawn by `MainScreen` over the
 * current tab, which supplies the back control in place of the nav bar.
 *
 * The [App] row already in memory fills the page straight away; description, media and the
 * version list follow once the package has been read back from the index.
 *
 * @param contentPadding insets handed down by `MainScreen` so content clears both scrims.
 */
@Composable
fun AppDetailScreen(
    packageName: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { (context.applicationContext as EDroidApp).container.repoHub }
    val snapshot by repository.snapshot.collectAsStateWithLifecycle()
    val app = remember(snapshot, packageName) { repository.app(packageName) }
    val antiFeatureNames = snapshot?.repo?.antiFeatures.orEmpty()

    val state by produceState<DetailsState>(DetailsState.Loading, packageName, snapshot?.repo?.timestamp) {
        value = try {
            repository.details(packageName)?.let { DetailsState.Loaded(it) } ?: DetailsState.NotFound
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DetailsState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    val details = (state as? DetailsState.Loaded)?.details
    val versions = details?.versions.orEmpty()
    var selectedVersionCode by rememberSaveable(packageName) { mutableStateOf<Long?>(null) }
    val selected = versions.firstOrNull { it.versionCode == selectedVersionCode } ?: versions.firstOrNull()
    var viewerPage by rememberSaveable(packageName) { mutableStateOf<Int?>(null) }

    val installActions = appInstallActions(
        packageName = packageName,
        appName = app?.name ?: packageName,
        version = selected,
        latestVersionCode = app?.latestVersionCode,
    )

    val uriHandler = LocalUriHandler.current
    val openUri: (String) -> Unit = { runCatching { uriHandler.openUri(it) } }
    val inset = Modifier.padding(horizontal = PageHorizontalPadding)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Layering.page)
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // Page title
        PageTitle(text = app?.name ?: packageName, modifier = inset)

        // Banner, icon and install actions
        Header(
            bannerUrl = details?.bannerUrl,
            iconUrl = app?.iconUrl,
            modifier = inset,
        ) {
            InstallActions(installActions, Modifier.weight(1f))
        }

        // Info card
        InfoCard(app = app, state = state, modifier = inset)

        // Media carousel
        MediaStrip(state = state, onScreenshot = { viewerPage = it }, onVideo = openUri)

        // Version picker
        if (versions.isNotEmpty() || app?.latestVersionName != null) {
            VersionPicker(
                versions = versions,
                selected = selected,
                fallbackVersionName = app?.latestVersionName,
                onSelect = { selectedVersionCode = it.versionCode },
                modifier = inset,
            )
        }

        // Version notes
        selected?.let { VersionNotes(it, antiFeatureNames, inset) }
    }

    // Screenshot viewer
    val page = viewerPage

    if (page != null && details != null && page in details.screenshotUrls.indices) {
        ScreenshotViewer(details.screenshotUrls, page, onDismiss = { viewerPage = null })
    }
}

/**
 * Banner with the icon tile overlapping its bottom-start corner and [actions] beside the icon.
 */
@Composable
private fun Header(
    bannerUrl: String?,
    iconUrl: String?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    // Collapses when the app has no feature graphic or it fails to load.
    var bannerFailed by remember(bannerUrl) { mutableStateOf(false) }

    Layout(
        modifier = modifier,
        content = {

            // Banner
            if (bannerUrl != null && !bannerFailed) {
                Banner(
                    bannerUrl,
                    onError = { bannerFailed = true },
                    modifier = Modifier.fillMaxWidth().aspectRatio(BANNER_ASPECT),
                )
            }

            // Icon and actions row
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconTile(iconUrl)
                actions()
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val banner = if (measurables.size > 1) measurables.first().measure(loose) else null
        val row = measurables.last().measure(loose)
        val rowY = banner?.let { it.height - IconOverlap.roundToPx() } ?: 0

        layout(constraints.maxWidth, rowY + row.height) {
            banner?.place(0, 0)
            row.place(0, rowY)
        }
    }
}

/**
 * The feature graphic, on an item-colored tile while it loads.
 */
@Composable
private fun Banner(url: String, onError: () -> Unit, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { onError() },
        modifier = modifier
            .clip(groupedShape(isFirst = true, isLast = true))
            .background(Layering.item),
    )
}

/**
 * App icon on an item-colored tile, ringed in the page color so it cuts into the banner.
 */
@Composable
private fun IconTile(url: String?) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = Layering.item,
        border = BorderStroke(5.dp, Layering.page),
        modifier = Modifier.size(IconTileSize),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.padding(16.dp).fillMaxSize(),
        )
    }
}

/**
 * Publisher, latest version and the description, or the summary while the description loads.
 */
@Composable
private fun InfoCard(app: App?, state: DetailsState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = groupedShape(isFirst = true, isLast = true),
        colors = CardDefaults.cardColors(containerColor = Layering.item),
    ) {
        Column(
            modifier = Modifier.padding(20.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {

            // Publisher
            app?.authorName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }

            // Version and age
            app?.latestVersionName?.let { version ->
                Text(
                    stringResource(R.string.details_version_updated, version, relativeTime(app.lastUpdated)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Description or summary
            val description = (state as? DetailsState.Loaded)?.details?.description

            if (description != null) {
                ExpandableDescription(description)
            } else {
                app?.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }

            // Load status
            when (state) {
                DetailsState.Loading -> {
                    Spacer(Modifier.height(8.dp))

                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                DetailsState.NotFound -> StatusText(stringResource(R.string.details_not_found))

                is DetailsState.Failed -> StatusText(stringResource(R.string.details_error, state.message))

                is DetailsState.Loaded -> Unit
            }
        }
    }
}

/**
 * Small error-coloured line reporting a load problem.
 */
@Composable
private fun StatusText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 *  The description clamped to a few lines, with a toggle when it doesn't fit.
 */
@Composable
private fun ExpandableDescription(raw: String) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(raw, linkColor) { descriptionText(raw, linkColor) }
    var expanded by rememberSaveable(raw) { mutableStateOf(false) }
    var overflows by remember(raw) { mutableStateOf(false) }

    // Description text
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_COLLAPSED_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = {
            if (!expanded) {
                overflows = it.hasVisualOverflow
            }
        },
    )

    // Show more/less button; shifted so the label lines up with the text rather than with the button's padding.
    if (overflows || expanded) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.offset(x = (-12).dp)) {
            Text(stringResource(if (expanded) R.string.details_show_less else R.string.details_show_more))
        }
    }
}

/**
 * F-Droid descriptions are mostly light HTML, but older ones are plain text whose line breaks
 * carry the layout, so those are escaped and their newlines kept.
 */
private fun descriptionText(raw: String, linkColor: Color): AnnotatedString {
    val html = if ('<' in raw) raw else TextUtils.htmlEncode(raw).replace("\n", "<br>")
    val text = AnnotatedString.fromHtml(
        html,
        linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
    )

    return text.subSequence(0, text.text.trimEnd().length)
}

/**
 * Video link and screenshots side by side. Placeholder tiles hold the space while loading so the
 * page doesn't jump; the strip disappears when the app has no media at all.
 */
@Composable
private fun MediaStrip(state: DetailsState, onScreenshot: (Int) -> Unit, onVideo: (String) -> Unit) {
    val details = (state as? DetailsState.Loaded)?.details
    val loading = state is DetailsState.Loading
    val video = details?.videoUrl
    val shots = details?.screenshotUrls.orEmpty()

    if (!loading && video == null && shots.isEmpty()) {
        return
    }

    val count = if (loading) MEDIA_PLACEHOLDER_COUNT else shots.size + (if (video != null) 1 else 0)
    val offset = if (video != null) 1 else 0
    val shape = groupedShape(isFirst = true, isLast = true)

    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { count },
        preferredItemWidth = MediaPreferredWidth,
        itemSpacing = MediaItemSpacing,
        userScrollEnabled = !loading,
        modifier = Modifier.padding(horizontal = PageHorizontalPadding).fillMaxWidth().height(MediaHeight),
    ) { index ->
        val tile = Modifier.fillMaxSize().maskClip(shape).background(Layering.item)

        when {
            // Placeholder tile
            loading -> Box(tile)

            // Video tile
            video != null && index == 0 -> Column(
                modifier = tile.clickable { onVideo(video) },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    stringResource(R.string.details_video),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            // Screenshot tile
            else -> {
                val shot = index - offset

                AsyncImage(
                    model = shots[shot],
                    contentDescription = stringResource(R.string.details_screenshot, shot + 1),
                    contentScale = ContentScale.Crop,
                    modifier = tile.clickable { onScreenshot(shot) },
                )
            }
        }
    }
}

/**
 * Pill showing the selected version, opening the full version list. Until the list has loaded it
 * shows the latest version from the [App] row and stays closed.
 */
@Composable
private fun VersionPicker(
    versions: List<AppVersion>,
    selected: AppVersion?,
    fallbackVersionName: String?,
    onSelect: (AppVersion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val canExpand = versions.size > 1

    /**
     * Version name text, flagged as the latest when [isLatest].
     */
    @Composable
    fun label(version: AppVersion?, isLatest: Boolean): String {
        val name = version?.versionName ?: fallbackVersionName.orEmpty()
        return stringResource(if (isLatest) R.string.details_version_latest else R.string.details_version, name)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && canExpand },
        modifier = modifier,
    ) {

        // Version pill
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = canExpand),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label(selected, isLatest = selected == null || selected == versions.firstOrNull()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (canExpand) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(if (expanded) PICKER_ARROW_OPEN_DEGREES else 0f),
                    )
                }
            }
        }

        // Version list
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            versions.forEachIndexed { index, version ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(label(version, isLatest = index == 0))
                            Text(
                                listOfNotNull(
                                    relativeTime(version.added),
                                    version.sizeBytes?.let { Formatter.formatShortFileSize(context, it) },
                                ).joinToString(FACT_SEPARATOR),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(version)
                        expanded = false
                    },
                    trailingIcon = if (version == selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * Requirements, anti-features and release notes of the version Install would fetch.
 */
@Composable
private fun VersionNotes(version: AppVersion, antiFeatureNames: Map<String, String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val facts = listOfNotNull(
            version.minSdk?.let { stringResource(R.string.details_min_sdk, it) },
            version.sizeBytes?.let { Formatter.formatShortFileSize(context, it) },
        )

        // Requirements line
        if (facts.isNotEmpty()) {
            Text(
                facts.joinToString(FACT_SEPARATOR),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Anti-features line
        if (version.antiFeatures.isNotEmpty()) {
            Text(
                stringResource(
                    R.string.details_anti_features,
                    version.antiFeatures.joinToString { antiFeatureNames[it] ?: it },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Release notes
        version.whatsNew?.let {
            SectionTitle(stringResource(R.string.details_whats_new), Modifier.offset(x = (-8).dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 *  Full-screen, swipeable screenshots. A tap anywhere closes it.
 */
@Composable
private fun ScreenshotViewer(urls: List<String>, initialPage: Int, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pager = rememberPagerState(initialPage) { urls.size }

        // Screenshot pager
        HorizontalPager(
            state = pager,
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = VIEWER_SCRIM_ALPHA)),
        ) { page ->
            AsyncImage(
                model = urls[page],
                contentDescription = stringResource(R.string.details_screenshot, page + 1),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = null, indication = null, onClick = onDismiss)
                    .padding(24.dp),
            )
        }
    }
}
