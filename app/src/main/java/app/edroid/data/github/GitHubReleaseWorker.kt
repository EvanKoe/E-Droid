package app.edroid.data.github

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.edroid.EDroidApp
import app.edroid.MainActivity
import app.edroid.R
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Daily background re-check of every tracked GitHub repo. */
class GitHubReleaseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val checker = (applicationContext as EDroidApp).container.gitHubChecker
        checker.checkAll()
        notifyUpdates(checker.updatableCount())
        Result.success()
    } catch (e: IOException) {
        Result.retry()
    }

    /** Fixed id + only-alert-once: a changed count refreshes the notification without buzzing again. */
    private fun notifyUpdates(count: Int) {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (count == 0) return manager.cancel(NOTIFICATION_ID)
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(applicationContext.getString(R.string.notification_channel_git_updates))
                .build(),
        )
        val open = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_GIT, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(applicationContext.getString(R.string.notification_git_updates_title))
                .setContentText(applicationContext.resources.getQuantityString(R.plurals.updates_count, count, count))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    companion object {
        private const val UNIQUE_NAME = "github-release-check"
        private const val CHANNEL_ID = "git_updates"
        private const val NOTIFICATION_ID = 0x61177

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GitHubReleaseWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // KEEP so restarts and reinstalls never reset the cadence.
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
