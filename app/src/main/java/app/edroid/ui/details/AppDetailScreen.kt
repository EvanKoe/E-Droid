package app.edroid.ui.details

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.TextUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toString
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.edroid.EDroidApp
import app.edroid.R
import app.edroid.data.fdroid.model.App
import app.edroid.data.fdroid.model.AppDetails
import app.edroid.data.fdroid.model.AppVersion
import app.edroid.data.install.InstallState
import app.edroid.data.install.canOpenApp
import app.edroid.data.install.installedVersionCode
import app.edroid.data.install.installedVersionCodeNow
import app.edroid.data.install.openApp
import app.edroid.data.install.requestUninstall
import app.edroid.ui.components.PageTitle
import app.edroid.ui.components.SectionTitle
import app.edroid.ui.components.relativeTime
import app.edroid.ui.theme.GroupedOuterRadius
import app.edroid.ui.theme.Layering
import app.edroid.ui.theme.PageHorizontalPadding
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException

/** F-Droid feature graphics are 1024×500. */
private const val BANNER_ASPECT = 1024f / 500f
private val IconTileSize = 96.dp
/** How far the icon tile reaches up into the banner. */
private val IconOverlap = 32.dp
/** Width of the large carousel item; the medium and small ones are derived from it. */
private val MediaPreferredWidth = 200.dp
private val MediaHeight = 300.dp
private val MediaItemSpacing = 8.dp
private const val DESCRIPTION_COLLAPSED_LINES = 6

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

    // Re-keyed on the index timestamp so a refresh (or the first download) reloads the page.
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

    val installer = remember(context) { (context.applicationContext as EDroidApp).container.apkInstaller }
    val installStates by installer.states.collectAsStateWithLifecycle()
    val installState = installStates[packageName] ?: InstallState.Idle
    LaunchedEffect(installState) {
        (installState as? InstallState.Failed)?.let { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
    }
    // Seeded synchronously so an installed app doesn't open on Install and animate over to Uninstall.
    val initialInstalledVersion = remember(context, packageName) { context.installedVersionCodeNow(packageName) }
    val installedVersion by remember(context, packageName) {
        context.applicationContext.installedVersionCode(packageName)
    }.collectAsStateWithLifecycle(initialValue = initialInstalledVersion)
    // Re-checked per installed version, since an update can add or drop the launcher activity.
    val canOpen = remember(context, packageName, installedVersion) {
        installedVersion != null && context.canOpenApp(packageName)
    }
    // The App row already knows the newest version, so Update doesn't wait on the details.
    val targetVersionCode = selected?.versionCode ?: app?.latestVersionCode
    // Lets the "ready to install" notification show when a download finishes in the background.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val onInstall: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        selected?.let { installer.install(packageName, app?.name ?: packageName, it) }
    }

    val uriHandler = LocalUriHandler.current
    val openUri: (String) -> Unit = { runCatching { uriHandler.openUri(it) } }
    val inset = Modifier.padding(horizontal = PageHorizontalPadding)

    // Horizontal padding is applied per child so the media strip can bleed to the screen edges.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Layering.page)
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTitle(text = app?.name ?: packageName, modifier = inset)
        Header(
            bannerUrl = details?.bannerUrl,
            iconUrl = app?.iconUrl,
            modifier = inset,
        ) {
            InstallActions(
                state = ActionsState(
                    install = installState,
                    installed = installedVersion != null,
                    updateAvailable = installedVersion.let { it != null && targetVersionCode != null && targetVersionCode > it },
                    canInstall = selected != null,
                    canOpen = canOpen,
                ),
                onInstall = onInstall,
                onOpen = { context.openApp(packageName) },
                onUninstall = { context.requestUninstall(packageName) },
                onCancel = { installer.cancel(packageName) },
            )
        }

        InfoCard(app = app, state = state, modifier = inset)
        MediaStrip(state = state, onScreenshot = { viewerPage = it }, onVideo = openUri)

        if (versions.isNotEmpty() || app?.latestVersionName != null) {
            VersionPicker(
                versions = versions,
                selected = selected,
                fallbackVersionName = app?.latestVersionName,
                onSelect = { selectedVersionCode = it.versionCode },
                modifier = inset,
            )
        }
        selected?.let { VersionNotes(it, antiFeatureNames, inset) }
    }

    val page = viewerPage
    if (page != null && details != null && page in details.screenshotUrls.indices) {
        ScreenshotViewer(details.screenshotUrls, page, onDismiss = { viewerPage = null })
    }
}

/** The single button on the right. It morphs between these rather than being swapped out. */
private enum class TrailingAction { Install, Retry, Update, Open, Cancel, None }

/** What fills the room left of [TrailingAction]. */
private enum class LeadingContent { None, Uninstall, Progress }

/** Everything [InstallActions] draws from, so content fading out keeps the values it was shown with. */
private data class ActionsState(
    val install: InstallState,
    val installed: Boolean,
    val updateAvailable: Boolean,
    val canInstall: Boolean,
    val canOpen: Boolean,
) {
    private val busy get() = install is InstallState.Downloading || install is InstallState.Installing

    val leading: LeadingContent
        get() = when {
            busy -> LeadingContent.Progress
            installed -> LeadingContent.Uninstall
            else -> LeadingContent.None
        }

    val trailing: TrailingAction
        get() = when {
            busy -> TrailingAction.Cancel
            installed && updateAvailable -> TrailingAction.Update
            installed && canOpen -> TrailingAction.Open
            installed -> TrailingAction.None
            install is InstallState.Failed -> TrailingAction.Retry
            else -> TrailingAction.Install
        }
}

/** Holds the last trailing action that had a look, so a button shrinking to nothing keeps it. */
private class LastShown(var value: TrailingAction)

private val ActionGap = 8.dp
/** Width Install squishes down to when it turns into Cancel. */
private val CancelWidth = 112.dp
// Tighter than the default so two buttons fit beside the icon.
private val PairedButtonPadding = PaddingValues(horizontal = 12.dp)

/** How much a pressed button widens (and its neighbor narrows), as in an expressive ButtonGroup. */
private const val PRESS_EXPAND_RATIO = 0.15f

/**
 * Looser than the expressive scheme's spatial spring (damping 0.8) so the morphs visibly
 * overshoot and settle.
 */
private fun <T> bouncySpring() = spring<T>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)

/**
 * What sits beside the icon, as one leading slot and one trailing button:
 *  - not installed: Install (Retry after a failure) across the whole width
 *  - downloading or installing: the button squishes toward the right edge into Cancel, and the
 *    progress bar fades in on the left
 *  - installed: Uninstall on the left, sharing the row with Update when a newer version is out,
 *    otherwise with Open (alone only when the app has nothing to launch)
 *
 * Every switch is the trailing button's width riding [bouncySpring], with the leading slot taking
 * whatever is left, so Install → Cancel → Uninstall/Open reads as one continuous morph. Pressing
 * Uninstall or the trailing button widens it at the other's expense.
 */
@Composable
private fun RowScope.InstallActions(
    state: ActionsState,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onCancel: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val leadingInteractions = remember { MutableInteractionSource() }
    val trailingInteractions = remember { MutableInteractionSource() }
    val leadingPressed by leadingInteractions.collectIsPressedAsState()
    val trailingPressed by trailingInteractions.collectIsPressedAsState()

    val lastShown = remember { LastShown(state.trailing) }
    if (state.trailing != TrailingAction.None) lastShown.value = state.trailing
    val shown = lastShown.value

    val cancel = shown == TrailingAction.Cancel
    val containerColor by animateColorAsState(
        if (cancel) colors.secondaryContainer else colors.primary, motion.defaultEffectsSpec(), label = "trailingContainer",
    )
    val contentColor by animateColorAsState(
        if (cancel) colors.onSecondaryContainer else colors.onPrimary, motion.defaultEffectsSpec(), label = "trailingContent",
    )

    BoxWithConstraints(Modifier.weight(1f).height(56.dp)) {
        val full = maxWidth
        val half = (full - ActionGap) / 2
        val target = when (state.trailing) {
            TrailingAction.None -> 0.dp
            TrailingAction.Cancel -> CancelWidth
            TrailingAction.Install, TrailingAction.Retry -> full
            TrailingAction.Update, TrailingAction.Open -> half * when {
                trailingPressed -> 1f + PRESS_EXPAND_RATIO
                leadingPressed -> 1f - PRESS_EXPAND_RATIO
                else -> 1f
            }
        }
        // Clamped: past either end the overshoot would push a button out of the row.
        val trailingWidth = animateDpAsState(target, bouncySpring(), label = "trailingWidth").value.coerceIn(0.dp, full)
        // The gap only exists while both sides have room, so a lone button spans the full row.
        val gap = ActionGap * (trailingWidth / ActionGap).coerceIn(0f, 1f) * ((full - trailingWidth) / ActionGap).coerceIn(0f, 1f)
        val leadingWidth = (full - trailingWidth - gap).coerceAtLeast(0.dp)

        AnimatedContent(
            targetState = state,
            contentKey = { it.leading },
            modifier = Modifier.align(Alignment.CenterStart).width(leadingWidth).fillMaxHeight(),
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec())) },
            label = "leading",
        ) { s ->
            when (s.leading) {
                LeadingContent.Progress -> DownloadProgress(s.install, Modifier.fillMaxSize())
                LeadingContent.Uninstall -> FilledTonalButton(
                    onClick = onUninstall,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PairedButtonPadding,
                    interactionSource = leadingInteractions,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ActionLabel(R.string.action_uninstall)
                }
                LeadingContent.None -> Spacer(Modifier.fillMaxSize())
            }
        }

        if (trailingWidth > 1.dp) {
            Button(
                onClick = {
                    when (state.trailing) {
                        TrailingAction.Cancel -> onCancel()
                        TrailingAction.Install, TrailingAction.Retry, TrailingAction.Update -> onInstall()
                        TrailingAction.Open -> onOpen()
                        TrailingAction.None -> Unit
                    }
                },
                shapes = ButtonDefaults.shapes(),
                enabled = state.canInstall || state.trailing == TrailingAction.Cancel || state.trailing == TrailingAction.Open,
                colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
                contentPadding = PairedButtonPadding,
                interactionSource = trailingInteractions,
                modifier = Modifier.align(Alignment.CenterEnd).width(trailingWidth).fillMaxHeight(),
            ) {
                AnimatedContent(
                    targetState = shown,
                    transitionSpec = {
                        (fadeIn(motion.defaultEffectsSpec()) + scaleIn(bouncySpring(), initialScale = 0.7f))
                            .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.7f))
                    },
                    label = "trailingLabel",
                ) { action ->
                    ActionLabel(
                        when (action) {
                            TrailingAction.Retry -> R.string.action_retry
                            TrailingAction.Update -> R.string.action_update
                            TrailingAction.Open -> R.string.action_open
                            TrailingAction.Cancel -> R.string.action_cancel
                            TrailingAction.Install, TrailingAction.None -> R.string.action_install
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionLabel(@StringRes res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
}

/** Progress text over a wavy bar; indeterminate while the size is unknown or the system installs. */
@Composable
private fun DownloadProgress(install: InstallState, modifier: Modifier = Modifier) {
    val progress = (install as? InstallState.Downloading)?.progress
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = when {
                install is InstallState.Installing -> stringResource(R.string.action_installing)
                progress != null -> stringResource(R.string.action_downloading_percent, (progress * 100).toInt())
                else -> stringResource(R.string.action_downloading)
            },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        if (progress != null) {
            val animated by animateFloatAsState(progress, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "download")
            LinearWavyProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Banner with the icon tile overlapping its bottom-start corner and [actions] beside the icon. */
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
            if (bannerUrl != null && !bannerFailed) {
                Banner(
                    bannerUrl,
                    onError = { bannerFailed = true },
                    modifier = Modifier.fillMaxWidth().aspectRatio(BANNER_ASPECT),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconTile(iconUrl)
                Spacer(Modifier.width(16.dp))
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

/** The feature graphic, on an item-colored tile while it loads. */
@Composable
private fun Banner(url: String, onError: () -> Unit, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { onError() },
        modifier = modifier
            .clip(RoundedCornerShape(GroupedOuterRadius))
            .background(Layering.item),
    )
}

/** App icon on an item-colored tile, ringed in the page color so it cuts into the banner. */
@Composable
private fun IconTile(url: String?) {
    Surface(
        shape = RoundedCornerShape(28.dp),
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

/** Publisher, latest version and the description, or the summary while the description loads. */
@Composable
private fun InfoCard(app: App?, state: DetailsState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GroupedOuterRadius),
        colors = CardDefaults.cardColors(containerColor = Layering.item),
    ) {
        Column(
            modifier = Modifier.padding(20.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            app?.authorName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            app?.latestVersionName?.let { version ->
                Text(
                    stringResource(R.string.details_version_updated, version, relativeTime(app.lastUpdated)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            val description = (state as? DetailsState.Loaded)?.details?.description
            if (description != null) {
                ExpandableDescription(description)
            } else {
                app?.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
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

@Composable
private fun StatusText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** The description clamped to a few lines, with a toggle when it doesn't fit. */
@Composable
private fun ExpandableDescription(raw: String) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(raw, linkColor) { descriptionText(raw, linkColor) }
    var expanded by rememberSaveable(raw) { mutableStateOf(false) }
    var overflows by remember(raw) { mutableStateOf(false) }

    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_COLLAPSED_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
    )
    if (overflows || expanded) {
        // Shifted so the label lines up with the text rather than with the button's padding.
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
    // Paragraph tags leave trailing blank lines behind.
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
    if (!loading && video == null && shots.isEmpty()) return

    val count = if (loading) 3 else shots.size + (if (video != null) 1 else 0)
    val offset = if (video != null) 1 else 0
    val shape = RoundedCornerShape(GroupedOuterRadius)
    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { count },
        preferredItemWidth = MediaPreferredWidth,
        itemSpacing = MediaItemSpacing,
        userScrollEnabled = !loading,
        // Padding outside the carousel rather than contentPadding, so items clip at the margin.
        modifier = Modifier.padding(horizontal = PageHorizontalPadding).fillMaxWidth().height(MediaHeight),
    ) { index ->
        // maskClip lets the carousel morph each item's visible width as it moves between sizes.
        val tile = Modifier.fillMaxSize().maskClip(shape).background(Layering.item)
        when {
            loading -> Box(tile)
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
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    )
                }
            }
        }
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
                                ).joinToString("  ·  "),
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

/** Requirements, anti-features and release notes of the version Install would fetch. */
@Composable
private fun VersionNotes(version: AppVersion, antiFeatureNames: Map<String, String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier = modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val facts = listOfNotNull(
            version.minSdk?.let { stringResource(R.string.details_min_sdk, it) },
            version.sizeBytes?.let { Formatter.formatShortFileSize(context, it) },
        )
        if (facts.isNotEmpty()) {
            Text(
                facts.joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        version.whatsNew?.let {
            SectionTitle(stringResource(R.string.details_whats_new), Modifier.offset(x = (-8).dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Full-screen, swipeable screenshots. A tap anywhere closes it. */
@Composable
private fun ScreenshotViewer(urls: List<String>, initialPage: Int, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pager = rememberPagerState(initialPage) { urls.size }
        HorizontalPager(
            state = pager,
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)),
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
