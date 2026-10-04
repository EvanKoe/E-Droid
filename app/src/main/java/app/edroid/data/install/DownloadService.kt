package app.edroid.data.install

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import app.edroid.EDroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps EDroid a foreground app while [ApkInstaller] downloads, so Android doesn't cut its network
 * once the user leaves. Shows the lead download's own progress notification rather than one of
 * its own, and stops once [ApkInstaller.foreground] runs out of downloads.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collector: Job? = null
    private var lastStartId = 0
    private var shownId: Int? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (collector == null) {
            val installer = (application as EDroidApp).container.apkInstaller
            // Immediate dispatch: the first value is handled before this returns, so startForeground() is never late.
            collector = scope.launch {
                installer.foreground.collect { lead ->
                    when {
                        lead != null -> show(lead)
                        // A download may have started between the update and this collection.
                        installer.foreground.value == null -> finish()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun show(lead: DownloadNotification) {
        if (lead.id == shownId) return
        ServiceCompat.startForeground(this, lead.id, lead.notification, FOREGROUND_TYPE)
        // The previous lead's download is over; its notification would otherwise linger.
        shownId?.let { NotificationManagerCompat.from(this).cancel(it) }
        shownId = lead.id
    }

    private fun finish() {
        // startForegroundService() demands one startForeground() even when the download already ended.
        if (shownId == null) {
            val placeholder = NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .build()
            ServiceCompat.startForeground(this, PLACEHOLDER_ID, placeholder, FOREGROUND_TYPE)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        shownId = null
        // Ignored if a newer start came in, which then finds its download in [ApkInstaller.foreground].
        stopSelfResult(lastStartId)
    }

    /** Android 15+ caps dataSync at 6 h a day; past that the service must stop or the app is killed. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val PLACEHOLDER_ID = Int.MIN_VALUE
        val FOREGROUND_TYPE = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
    }
}
