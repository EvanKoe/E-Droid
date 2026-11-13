package app.edroid.data.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import app.edroid.EDroidApp

/**
 * Receives [android.content.pm.PackageInstaller] session statuses and the download notification's
 * Cancel action, and forwards them to [ApkInstaller].
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val installer = (context.applicationContext as EDroidApp).container.apkInstaller
        if (intent.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_PACKAGE)?.let(installer::cancel)
        } else {
            installer.onSessionResult(intent)
        }
    }

    companion object {
        /** Broadcast action of the download notification's Cancel button. */
        private const val ACTION_CANCEL = "app.edroid.action.CANCEL_DOWNLOAD"

        /** Extra naming the package whose download to cancel. */
        private const val EXTRA_PACKAGE = "package"

        /**
         * Broadcast the download notification's Cancel action fires for [packageName].
         */
        fun cancelIntent(context: Context, packageName: String): PendingIntent {
            val intent = Intent(context, InstallResultReceiver::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_PACKAGE, packageName)
            // Request code per package so each notification keeps its own extras.
            return PendingIntent.getBroadcast(
                context, packageName.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Where the system reports [sessionId]'s status; handed to [android.content.pm.PackageInstaller.Session.commit].
         */
        fun intentSender(context: Context, sessionId: Int): IntentSender {
            val intent = Intent(context, InstallResultReceiver::class.java)
            // Mutable: the system fills in the status extras.
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
                .intentSender
        }
    }
}
