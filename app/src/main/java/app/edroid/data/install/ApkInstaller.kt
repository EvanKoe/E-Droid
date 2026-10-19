package app.edroid.data.install

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.edroid.R
import app.edroid.data.fdroid.model.AppVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val CHANNEL_ID = "installs"
internal const val DOWNLOAD_CHANNEL_ID = "downloads"

/** A download's progress notification, as [DownloadService] needs it to go foreground. */
internal class DownloadNotification(val id: Int, val notification: Notification)

/** Where one package's Install button stands. Whether it is installed comes from [installedVersionCode]. */
sealed interface InstallState {
    data object Idle : InstallState
    /** [progress] is null when neither the server nor the index gave a size. */
    data class Downloading(val progress: Float?) : InstallState
    /** Handed to the system; waiting on its confirmation dialog or the install itself. */
    data object Installing : InstallState
    data class Failed(val message: String) : InstallState
}

/**
 * Downloads APKs straight into a [PackageInstaller] session, checking the sha256 from the index
 * on the way, then commits it. The system shows its own confirmation dialog (and, the first
 * time, asks the user to allow EDroid to install unknown apps).
 *
 * Android only lets that dialog open while EDroid is in the foreground, so a download that
 * finishes in the background posts a notification instead, and the dialog also opens by itself
 * the next time EDroid comes back.
 *
 * Owns its scope so a download keeps going when the user leaves the detail page, and runs
 * [DownloadService] while any download is in flight: without a foreground service, Android cuts
 * a backgrounded app's network and the download dies with "Software caused connection abort".
 * Must be created on the main thread (it observes the process lifecycle).
 */
class ApkInstaller(private val context: Context, private val client: OkHttpClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _states = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val states: StateFlow<Map<String, InstallState>> = _states.asStateFlow()

    private class Session(val packageName: String, val appName: String)

    /** Session id → what it installs, to route results coming back through [InstallResultReceiver]. */
    private val sessions = ConcurrentHashMap<Int, Session>()

    /** Confirmation dialogs the system asked for while EDroid was in the background. */
    private val pendingConfirms = ConcurrentHashMap<Int, Intent>()

    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle

    init {
        NotificationManagerCompat.from(context).createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_installs), NotificationManager.IMPORTANCE_HIGH),
                // Low: progress updates shouldn't buzz or pop up.
                NotificationChannel(DOWNLOAD_CHANNEL_ID, context.getString(R.string.notification_channel_downloads), NotificationManager.IMPORTANCE_LOW),
            ),
        )
        processLifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                pendingConfirms.keys.toList().forEach { sessionId ->
                    pendingConfirms.remove(sessionId)?.let { confirm(sessionId, it) }
                }
            }
        })
    }

    /** A download in progress; [call] is cancelled alongside [job] so a blocked read returns at once. */
    private class Download(val target: Session, val notificationId: Int) {
        lateinit var job: Job
        @Volatile var call: Call? = null
        /** Latest progress notification, kept even without permission to post it: [DownloadService] still needs one. */
        @Volatile lateinit var notification: Notification

        fun shown() = DownloadNotification(notificationId, notification)
    }

    private val downloads = ConcurrentHashMap<String, Download>()

    /** Negative, so they never collide with the session ids the "ready to install" notifications use. */
    private val nextNotificationId = AtomicInteger(-1)

    private val _foreground = MutableStateFlow<DownloadNotification?>(null)
    /** The download whose notification [DownloadService] shows; null once none is left. */
    internal val foreground: StateFlow<DownloadNotification?> = _foreground.asStateFlow()

    /**
     * Starts installing (or updating to) [version]; ignored while that package is already in flight.
     * An existing [source] APK is installed instead of downloading, and deleted once committed.
     */
    fun install(packageName: String, appName: String, version: AppVersion, source: File? = null) {
        val current = _states.value[packageName]
        if (current is InstallState.Downloading || current is InstallState.Installing) return
        val download = Download(Session(packageName, appName), nextNotificationId.getAndDecrement())
        setDownloading(download, null)
        downloads[packageName] = download
        // Already running otherwise; it follows [foreground] from there.
        if (_foreground.compareAndSet(null, download.shown())) {
            // Install is tapped in the UI, but don't lose the download if Android refuses anyway.
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }
        download.job = scope.launch {
            try {
                downloadAndCommit(download.target, version, download, source)
            } catch (e: Exception) {
                // A cancelled call surfaces as an IOException; cancellation is reported below instead.
                if (isActive) set(packageName, InstallState.Failed(e.message ?: e.javaClass.simpleName))
                if (e is CancellationException) throw e
            } finally {
                downloads.remove(packageName, download)
                // Hand the service over to another download, or let it stop.
                _foreground.update { lead ->
                    if (lead?.id != download.notificationId) lead else downloads.values.firstOrNull()?.shown()
                }
                // Ignored while it is still the service's notification; the service removes it then.
                NotificationManagerCompat.from(context).cancel(download.notificationId)
            }
        }
        // Runs after the body, so nothing can overwrite this Idle.
        download.job.invokeOnCompletion { if (it is CancellationException) set(packageName, InstallState.Idle) }
    }

    /**
     * Stops [packageName]'s download, or withdraws its committed session if the system dialog
     * hasn't been accepted yet.
     */
    fun cancel(packageName: String) {
        downloads.remove(packageName)?.let {
            it.job.cancel()
            it.call?.cancel()
        }
        sessions.entries.firstOrNull { it.value.packageName == packageName }?.key?.let { sessionId ->
            runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
            finish(sessionId, InstallState.Idle)
        }
    }

    /** Copies [input] into the session's base.apk, reporting progress; returns the SHA-256 as lowercase hex. */
    private suspend fun writeApk(session: PackageInstaller.Session, input: InputStream, total: Long?, download: Download): String {
        val digest = MessageDigest.getInstance("SHA-256")
        session.openWrite("base.apk", 0, total ?: -1).use { out ->
            val buffer = ByteArray(64 * 1024)
            var read = 0L
            var lastPercent = -1
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                digest.update(buffer, 0, n)
                read += n
                // One update per percent, not per buffer.
                val percent = total?.let { (read * 100 / it).toInt() }
                if (percent != null && percent != lastPercent) {
                    lastPercent = percent
                    setDownloading(download, percent / 100f)
                }
            }
            session.fsync(out)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun downloadAndCommit(target: Session, version: AppVersion, download: Download, source: File?) {
        val packageName = target.packageName
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            version.sizeBytes?.let(::setSize)
            // Lets updates of apps EDroid installed itself skip the dialog.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            val local = source?.takeIf { it.exists() }
            val hash = if (local != null) {
                currentCoroutineContext().ensureActive()
                local.inputStream().use { writeApk(session, it, local.length(), download) }
            } else {
                val call = client.newCall(Request.Builder().url(version.apkUrl).build())
                download.call = call
                currentCoroutineContext().ensureActive()
                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
                    val body = response.body
                    writeApk(session, body.byteStream(), body.contentLength().takeIf { it > 0 } ?: version.sizeBytes, download)
                }
            }
            if (!hash.equals(version.sha256, ignoreCase = true)) throw IOException("Checksum mismatch, download rejected")
            currentCoroutineContext().ensureActive()
            set(packageName, InstallState.Installing)
            sessions[sessionId] = target
            session.commit(InstallResultReceiver.intentSender(context, sessionId))
            // Its bytes are in the session now; kept on any failure above so a retry can reuse it.
            if (local != null) runCatching { local.delete() }
        } catch (e: Throwable) {
            sessions.remove(sessionId)
            // Already gone if cancel() withdrew it first.
            runCatching { session.abandon() }
            throw e
        } finally {
            session.close()
        }
    }

    /** Called by [InstallResultReceiver] for every status the system reports on a session. */
    internal fun onSessionResult(intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val target = sessions[sessionId] ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            when {
                confirm == null -> finish(sessionId, InstallState.Failed("Installer gave no confirmation screen"))
                processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> confirm(sessionId, confirm)
                else -> {
                    pendingConfirms[sessionId] = confirm
                    notifyConfirm(sessionId, target.appName, confirm)
                }
            }
            return
        }
        finish(
            sessionId,
            when (status) {
                PackageInstaller.STATUS_SUCCESS -> InstallState.Idle
                // User dismissed the system dialog: nothing to report.
                PackageInstaller.STATUS_FAILURE_ABORTED -> InstallState.Idle
                else -> InstallState.Failed(
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed ($status)",
                )
            },
        )
    }

    private fun confirm(sessionId: Int, confirm: Intent) {
        NotificationManagerCompat.from(context).cancel(sessionId)
        context.startActivity(confirm)
    }

    private fun finish(sessionId: Int, state: InstallState) {
        val target = sessions.remove(sessionId) ?: return
        pendingConfirms.remove(sessionId)
        NotificationManagerCompat.from(context).cancel(sessionId)
        set(target.packageName, state)
    }

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun setDownloading(download: Download, progress: Float?) {
        set(download.target.packageName, InstallState.Downloading(progress))
        notifyProgress(download, progress)
    }

    /** Ongoing progress notification with a Cancel action; tapping it opens EDroid. Only built, not posted, without permission. */
    @SuppressLint("MissingPermission") // Checked by canNotify().
    private fun notifyProgress(download: Download, progress: Float?) {
        val target = download.target
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.notification_downloading_title, target.appName))
            .setProgress(100, ((progress ?: 0f) * 100).toInt(), progress == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.action_cancel), InstallResultReceiver.cancelIntent(context, target.packageName))
        if (progress != null) builder.setSubText("${(progress * 100).toInt()}%")
        download.notification = builder.build()
        if (canNotify()) NotificationManagerCompat.from(context).notify(download.notificationId, download.notification)
    }

    /** "Ready to install" notification whose tap opens the system dialog. Silently skipped without permission. */
    @SuppressLint("MissingPermission") // Checked by canNotify().
    private fun notifyConfirm(sessionId: Int, appName: String, confirm: Intent) {
        if (!canNotify()) return
        val manager = NotificationManagerCompat.from(context)
        val tap = PendingIntent.getActivity(
            context, sessionId, confirm, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.notification_install_ready_title, appName))
            .setContentText(context.getString(R.string.notification_install_ready_text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        manager.notify(sessionId, notification)
    }

    private fun set(packageName: String, state: InstallState) = _states.update { it + (packageName to state) }
}
