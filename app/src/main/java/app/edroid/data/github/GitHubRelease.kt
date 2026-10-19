package app.edroid.data.github

import app.edroid.data.fdroid.model.AppVersion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A GitHub repository, identified the way its URL names it: `github.com/<owner>/<name>`. */
@Serializable
data class GitHubRepo(val owner: String, val name: String) {
    override fun toString() = "$owner/$name"

    companion object {
        private val SEGMENT = Regex("^[A-Za-z0-9_.-]+$")
        private val URL_PREFIX = Regex("^(https?://)?(www\\.)?github\\.com/", RegexOption.IGNORE_CASE)

        /**
         * Accepts `owner/name`, or a github.com URL (`https://github.com/owner/name[.git][/...]`).
         * Returns null for anything else, so callers can report bad input before any request is made.
         */
        fun parse(raw: String): GitHubRepo? {
            val parts = raw.trim().replace(URL_PREFIX, "").trim('/').split('/')
            if (parts.size < 2) return null
            val name = parts[1].removeSuffix(".git")
            return if (SEGMENT.matches(parts[0]) && SEGMENT.matches(name)) GitHubRepo(parts[0], name) else null
        }
    }
}

/** One entry of `GET /repos/{owner}/{repo}/releases`, trimmed to what a releases list shows. */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    /** Markdown release notes. */
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GitHubAsset> = emptyList(),
) {
    val apkAssets: List<GitHubAsset> get() = assets.filter { it.isApk }
}

/** The newest stable release that ships an APK; GitHub lists newest first. */
fun List<GitHubRelease>.latestInstallable(): GitHubRelease? =
    firstOrNull { !it.draft && !it.prerelease && it.apkAssets.isNotEmpty() }

/** The APK a followed repo currently offers, with the manifest facts read from the downloaded file. */
@Serializable
data class TrackedApk(
    val tag: String,
    val assetName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val packageName: String,
    val appName: String,
    val versionCode: Long,
    val versionName: String,
) {
    fun toAppVersion() = AppVersion(
        versionName = versionName,
        versionCode = versionCode,
        added = 0,
        apkUrl = apkUrl,
        sha256 = sha256,
        sizeBytes = sizeBytes,
        minSdk = null,
        whatsNew = null,
        antiFeatures = emptySet(),
    )
}

@Serializable
data class TrackedRepo(
    val repo: GitHubRepo,
    val latest: TrackedApk? = null,
    val needsChoice: Boolean = false,
) {
    val key: String get() = repo.toString().lowercase()
}

@Serializable
data class GitHubAsset(
    val name: String,
    val size: Long,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("download_count") val downloadCount: Long = 0,
    /** `sha256:<hex>` when GitHub computed one. */
    @SerialName("digest") val digest: String? = null,
) {
    val isApk: Boolean get() = name.endsWith(".apk", ignoreCase = true)
    val sha256: String? get() = digest?.removePrefix("sha256:")
}
