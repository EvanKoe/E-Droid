package app.edroid.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.edroid.EDroidApp
import app.edroid.R
import app.edroid.data.fdroid.model.AppVersion
import app.edroid.data.install.InstallState
import app.edroid.data.install.canOpenApp
import app.edroid.data.install.installedVersionCode
import app.edroid.data.install.installedVersionCodeNow
import app.edroid.data.install.installedVersionNameNow
import app.edroid.data.install.openApp
import app.edroid.data.install.requestUninstall
import java.io.File

/** Everything [InstallActions] needs for one package: what to show and what each button does. */
internal class AppInstallActions(
    val state: ActionsState,
    val onInstall: () -> Unit,
    val onOpen: () -> Unit,
    val onUninstall: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * Wires [packageName] to the installer and the package manager, for the detail page and the Home
 * spotlight alike.
 *
 * @param version what Install fetches; Install stays disabled while it is null.
 * @param latestVersionCode the newest version known from the app row, so Update can show before
 *   [version] is known.
 * @param allowUninstall whether an installed app offers Uninstall beside Open or Update.
 * @param reportFailures toasts install failures. Only one caller per package should, or a page
 *   drawn over another showing the same app toasts twice.
 * @param installSource an already downloaded APK to install instead of fetching [version], if any.
 */
@Composable
internal fun appInstallActions(
    packageName: String,
    appName: String,
    version: AppVersion?,
    latestVersionCode: Long?,
    allowUninstall: Boolean = true,
    reportFailures: Boolean = true,
    installSource: () -> File? = { null },
): AppInstallActions {
    val context = LocalContext.current
    val installer = remember(context) { (context.applicationContext as EDroidApp).container.apkInstaller }
    val installStates by installer.states.collectAsStateWithLifecycle()
    val installState = installStates[packageName] ?: InstallState.Idle
    if (reportFailures) {
        LaunchedEffect(installState) {
            (installState as? InstallState.Failed)?.let { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
        }
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
    val installedVersionName = remember(context, packageName, installedVersion) { context.installedVersionNameNow(packageName) }
    val targetVersionCode =version?.versionCode ?: latestVersionCode
    // Lets the "ready to install" notification show when a download finishes in the background.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return AppInstallActions(
        state = ActionsState(
            install = installState,
            installed = installedVersion != null,
            updateAvailable = installedVersion.let { it != null && targetVersionCode != null && targetVersionCode > it },
            canInstall = version != null,
            canOpen = canOpen,
            allowUninstall = allowUninstall,
            installedVersionName = installedVersionName,
        ),
        onInstall = {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            version?.let { installer.install(packageName, appName, it, installSource()) }
        },
        onOpen = { context.openApp(packageName) },
        onUninstall = { context.requestUninstall(packageName) },
        onCancel = { installer.cancel(packageName) },
    )
}

/** The single button on the right. It morphs between these rather than being swapped out. */
internal enum class TrailingAction { Install, Retry, Update, Open, Cancel, None }

/** What fills the room left of [TrailingAction]. */
internal enum class LeadingContent { None, Uninstall, Progress }

/** Everything [InstallActions] draws from, so content fading out keeps the values it was shown with. */
internal data class ActionsState(
    val install: InstallState,
    val installed: Boolean,
    val updateAvailable: Boolean,
    val canInstall: Boolean,
    val canOpen: Boolean,
    val allowUninstall: Boolean = true,
    val installedVersionName: String? = null,
) {
    /** Downloading or handed to the system installer. */
    val busy get() = install is InstallState.Downloading || install is InstallState.Installing

    val leading: LeadingContent
        get() = when {
            busy -> LeadingContent.Progress
            installed && allowUninstall -> LeadingContent.Uninstall
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

internal val ActionGap = 8.dp
/** Width Install squishes down to when it turns into Cancel. */
internal val CancelWidth = 112.dp
// Tighter than the default so two buttons fit beside the icon.
private val PairedButtonPadding = PaddingValues(horizontal = 12.dp)

/** How much a pressed button widens (and its neighbor narrows), as in an expressive ButtonGroup. */
private const val PRESS_EXPAND_RATIO = 0.15f

/**
 * Looser than the expressive scheme's spatial spring (damping 0.8) so the morphs visibly
 * overshoot and settle.
 */
internal fun <T> bouncySpring() = spring<T>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)

/**
 * One leading slot and one trailing button, filling the width [modifier] gives them:
 *  - not installed: Install (Retry after a failure) across the whole width
 *  - downloading or installing: the button squishes toward the right edge into Cancel, and the
 *    progress bar fades in on the left
 *  - installed: Uninstall on the left, sharing the row with Update when a newer version is out,
 *    otherwise with Open (alone only when the app has nothing to launch). Without
 *    [ActionsState.allowUninstall], Update or Open takes the whole width instead.
 *
 * Every switch is the trailing button's width riding [bouncySpring], with the leading slot taking
 * whatever is left, so Install → Cancel → Uninstall/Open reads as one continuous morph. Pressing
 * Uninstall or the trailing button widens it at the other's expense.
 */
@Composable
internal fun InstallActions(
    actions: AppInstallActions,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
) {
    val state = actions.state
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

    BoxWithConstraints(modifier.height(height)) {
        val full = maxWidth
        val half = (full - ActionGap) / 2
        val target = when (state.trailing) {
            TrailingAction.None -> 0.dp
            TrailingAction.Cancel -> CancelWidth
            TrailingAction.Install, TrailingAction.Retry -> full
            TrailingAction.Update, TrailingAction.Open -> if (state.leading != LeadingContent.Uninstall) {
                full
            } else {
                half * when {
                    trailingPressed -> 1f + PRESS_EXPAND_RATIO
                    leadingPressed -> 1f - PRESS_EXPAND_RATIO
                    else -> 1f
                }
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
                    onClick = actions.onUninstall,
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
                        TrailingAction.Cancel -> actions.onCancel()
                        TrailingAction.Install, TrailingAction.Retry, TrailingAction.Update -> actions.onInstall()
                        TrailingAction.Open -> actions.onOpen()
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
internal fun ActionLabel(@StringRes res: Int) {
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
