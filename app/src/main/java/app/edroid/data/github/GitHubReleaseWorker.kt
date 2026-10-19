package app.edroid.data.github

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.edroid.EDroidApp
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Daily background re-check of every tracked GitHub repo. */
class GitHubReleaseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        (applicationContext as EDroidApp).container.gitHubChecker.checkAll()
        Result.success()
    } catch (e: IOException) {
        Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "github-release-check"

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
