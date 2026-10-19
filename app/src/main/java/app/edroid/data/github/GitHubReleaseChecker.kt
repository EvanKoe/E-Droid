package app.edroid.data.github

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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
    fun apkFile(repo: GitHubRepo, assetName: String) =
        File(apkDir, "${repo.owner}_${repo.name}/${File(assetName).name}")

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
            ),
        )
    }

    /** Re-tracks every followed repo whose latest release tag changed; one failure never stops the rest. */
    suspend fun checkAll() {
        for (tracked in store.repos.value) {
            val latest = tracked.latest ?: continue
            try {
                val release = service.latestRelease(tracked.repo) ?: continue
                if (release.tagName == latest.tag) continue
                val asset = release.apkAssets.firstOrNull { it.name == latest.assetName }
                if (asset == null) {
                    store.markNeedsChoice(tracked.repo)
                    continue
                }
                (track(tracked.repo, asset) as? TrackResult.Tracked)?.let { store.update(tracked.repo, it.apk) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed for ${tracked.repo}", e)
            }
        }
    }

    private companion object {
        const val TAG = "GitHubReleaseChecker"
    }
}
