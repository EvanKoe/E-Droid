package app.edroid.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Reads a repository's releases from the public GitHub REST API. No token is sent, so GitHub's
 * unauthenticated rate limit applies (60 requests an hour per IP).
 */
class GitHubReleaseService(
    private val client: OkHttpClient,
    private val json: Json,
) {
    /**
     * Newest first, as GitHub orders them. Throws [IOException] with a readable message when the
     * repo is missing or the rate limit is hit.
     */
    suspend fun releases(repo: GitHubRepo, perPage: Int = DEFAULT_PER_PAGE): List<GitHubRelease> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$API/repos/${repo.owner}/${repo.name}/releases?per_page=${perPage.coerceIn(MIN_PER_PAGE, MAX_PER_PAGE)}")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.code == HTTP_NOT_FOUND -> throw IOException("Repository $repo not found")

                    response.code == HTTP_FORBIDDEN || response.code == HTTP_TOO_MANY_REQUESTS ->
                        throw IOException("GitHub rate limit reached, try again later")

                    !response.isSuccessful -> throw IOException("HTTP ${response.code} for $repo")
                }

                json.decodeFromString(ListSerializer(GitHubRelease.serializer()), response.body.string())
            }
        }

    /**
     * The newest stable release of [repo] that ships an APK, or null when there is none.
     */
    suspend fun latestRelease(repo: GitHubRepo): GitHubRelease? = releases(repo).latestInstallable()

    /**
     * Streams [asset] into [target] via a `.part` file and returns the lowercase hex SHA-256.
     * Throws [IOException] when HTTP fails or GitHub's published digest does not match.
     */
    suspend fun downloadApk(
        asset: GitHubAsset,
        target: File,
        onProgress: (read: Long, total: Long) -> Unit = { _, _ -> },
    ): String = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        try {
            val request = Request.Builder().url(asset.downloadUrl).header("User-Agent", USER_AGENT).build()
            val digest = MessageDigest.getInstance("SHA-256")
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed (HTTP ${response.code})")
                }

                val total = response.body.contentLength()
                part.outputStream().use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read = 0L
                    val input = response.body.byteStream()
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) {
                            break
                        }

                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
            val hash = digest.digest().joinToString("") { HEX_BYTE_FORMAT.format(it) }
            if (asset.sha256?.equals(hash, ignoreCase = true) == false) {
                throw IOException("Checksum mismatch, download rejected")
            }

            target.delete()
            if (!part.renameTo(target)) {
                throw IOException("Could not save ${target.name}")
            }

            hash
        } finally {
            part.delete() // no-op after a successful rename
        }
    }

    /**
     * [releases] for `owner/name` or a github.com URL; throws [IllegalArgumentException] on anything else.
     */
    suspend fun releases(repo: String, perPage: Int = DEFAULT_PER_PAGE): List<GitHubRelease> =
        releases(requireNotNull(GitHubRepo.parse(repo)) { "Not a GitHub repository: $repo" }, perPage)

    private companion object {
        /** GitHub REST API base URL. */
        const val API = "https://api.github.com"

        /** Value of the `X-GitHub-Api-Version` header. */
        const val API_VERSION = "2022-11-28"

        /** User-Agent sent to GitHub. */
        const val USER_AGENT = "EDroid/0.1 (GitHub releases client)"

        /** Releases fetched per page when the caller gives no size. */
        const val DEFAULT_PER_PAGE = 30

        /** Smallest page size GitHub accepts. */
        const val MIN_PER_PAGE = 1

        /** Largest page size GitHub accepts. */
        const val MAX_PER_PAGE = 100

        /** Status for an unknown repo or release. */
        const val HTTP_NOT_FOUND = 404

        /** One of the two statuses GitHub answers an exhausted rate limit with. */
        const val HTTP_FORBIDDEN = 403

        /** The other status GitHub answers an exhausted rate limit with. */
        const val HTTP_TOO_MANY_REQUESTS = 429

        /** Read size while streaming an APK to disk. */
        const val BUFFER_SIZE = 64 * 1024

        /** Lower-case hex for one byte of a digest. */
        const val HEX_BYTE_FORMAT = "%02x"
    }
}
