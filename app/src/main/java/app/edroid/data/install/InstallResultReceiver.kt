package app.edroid.data.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import app.edroid.EDroidApp

/** Receives [android.content.pm.PackageInstaller] session statuses and forwards them to [ApkInstaller]. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as EDroidApp).container.apkInstaller.onSessionResult(intent)
    }

    companion object {
        fun intentSender(context: Context, sessionId: Int): IntentSender {
            val intent = Intent(context, InstallResultReceiver::class.java)
            // Mutable: the system fills in the status extras.
            val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
                .intentSender
        }
    }
}
