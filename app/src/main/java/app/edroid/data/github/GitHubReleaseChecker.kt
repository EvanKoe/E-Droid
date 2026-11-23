package app.edroid.data.github

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import app.edroid.data.install.installedVersionCodeNow
import java.io.File
import java.io.IOException

/** Outcome of [GitHubReleaseChecker.track]. */
sealed interface TrackResult {
    data class Tracked(val apk: TrackedApk) : TrackResult
    data object NoApk : TrackResult
    data class NeedsChoice(val assets: List<GitHubAsset>) : TrackResult
}

/** Downloads the latest APK of followed GitHub repos and reads its manifest; the store is written by callers. */
class GitHubReleaseChecker(
    private val context: Context,
    private val store: GitHubRepoStore,
    private val service: GitHubReleaseService,
    private val apkDir: File,
) {
    private val checkMutex = Mutex()

    /**
     * Where [assetName] of [repo] is stored on disk; [File.getName] strips any path from the asset name.
     */
    fun apkFile(repo: GitHubRepo, assetName: String) =
        File(repoDir(repo), File(assetName).name)

    /**
     * Directory holding every APK downloaded for [repo].
     */
    private fun repoDir(repo: GitHubRepo) = File(apkDir, "${repo.owner}_${repo.name}")

    /**
     * Deletes the APKs downloaded for [repo]; the installed app is not touched.
     */
    suspend fun deleteDownloads(repo: GitHubRepo) = withContext(Dispatchers.IO) {
        repoDir(repo).deleteRecursively()
    }

    /**
     * Downloads the latest release's APK for [repo] and reads its manifest. [chosen] overrides the
     * asset pick; without it the previously tracked asset, or the only APK, is used.
     * [onProgress] gets a 0..1 fraction, or null when the size is unknown.
     */
    suspend fun track(
        repo: GitHubRepo,
        chosen: GitHubAsset? = null,
        onProgress: (Float?) -> Unit = {},
    ): TrackResult {
        val release = service.latestRelease(repo) ?: return TrackResult.NoApk
        val storedName = store.repos.value.firstOrNull { it.key == TrackedRepo(repo).key }?.latest?.assetName
        val asset = chosen
            ?: release.apkAssets.firstOrNull { it.name == storedName }
            ?: release.apkAssets.singleOrNull()
            ?: return TrackResult.NeedsChoice(release.apkAssets)

        val file = apkFile(repo, asset.name)
        withContext(Dispatchers.IO) { file.parentFile?.listFiles()?.forEach { it.delete() } }
        val hash = service.downloadApk(asset, file) { read, total ->
            onProgress(if (total > 0) read.toFloat() / total else null)
        }
        val info = withContext(Dispatchers.IO) {
            context.readApkInfo(file) ?: run {
                file.delete()
                throw IOException("Not a valid APK")
            }
        }
        return TrackResult.Tracked(
            TrackedApk(
                tag = release.tagName,
                assetName = asset.name,
                apkUrl = asset.downloadUrl,
                sha256 = asset.sha256 ?: hash,
                sizeBytes = asset.size,
                packageName = info.packageName,
                appName = info.appName,
                versionCode = info.versionCode,
                versionName = info.versionName,
                apkCount = release.apkAssets.size,
            ),
        )
    }

    /**
     * Lists the APKs of [repo]'s latest release, without [track]'s automatic pick.
     */
    suspend fun apkAssets(repo: GitHubRepo): List<GitHubAsset> = service.latestRelease(repo)?.apkAssets.orEmpty()

    /**
     * Whether the latest release of [tracked] is newer than the installed app; false when not installed.
     */
    fun isUpdatable(tracked: TrackedRepo): Boolean {
        val latest = tracked.latest ?: return false
        val installed = context.installedVersionCodeNow(latest.packageName) ?: return false
        return latest.versionCode > installed
    }

    /**
     * Number of followed repos whose latest APK is newer than the installed app.
     */
    fun updatableCount() = store.repos.value.count(::isUpdatable)

    /**
     * Re-tracks every followed repo whose latest release tag changed and returns how many repos
     * could not be checked; one failure never stops the rest. A call made while another check
     * runs does nothing and returns 0, so the worker and a pull never download the same APK twice.
     */
    suspend fun checkAll(): Int {
        if (!checkMutex.tryLock()) {
            return 0
        }

        try {
            return countFailures(
                items = store.repos.value,
                onFailure = { tracked, e -> Log.w(TAG, "Update check failed for ${tracked.repo}", e) },
                action = ::check,
            )
        } finally {
            checkMutex.unlock()
        }
    }

    /**
     * Re-tracks [tracked] when its latest release tag changed; throws when GitHub or the download fails.
     */
    private suspend fun check(tracked: TrackedRepo) {
        val latest = tracked.latest ?: return
        val release = service.latestRelease(tracked.repo) ?: return
        if (release.tagName == latest.tag) {
            return
        }

        // Asset names often carry the version, so a lone APK is the same app under a new name.
        val asset = release.apkAssets.firstOrNull { it.name == latest.assetName }
            ?: release.apkAssets.singleOrNull()
        if (asset == null) {
            store.markNeedsChoice(tracked.repo)
            return
        }

        (track(tracked.repo, asset) as? TrackResult.Tracked)?.let { store.update(tracked.repo, it.apk) }
    }

    private companion object {
        /** Log tag. */
        const val TAG = "GitHubReleaseChecker"
    }
}

/**
 * Runs [action] on every item and returns how many threw. A failure is handed to [onFailure] and
 * never stops the rest; cancellation is rethrown.
 */
internal suspend fun <T> countFailures(
    items: List<T>,
    onFailure: (T, Exception) -> Unit = { _, _ -> },
    action: suspend (T) -> Unit,
): Int {
    var failures = 0
    for (item in items) {
        try {
            action(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures++
            onFailure(item, e)
        }
    }
    return failures
}
