package app.edroid.data.fdroid.net

import android.util.Log
import app.edroid.data.fdroid.model.Entry
import app.edroid.data.fdroid.model.EntryFileV2
import app.edroid.data.fdroid.model.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Low-level HTTP access to an F-Droid repository following the index-v2 protocol.
 *
 * Every call is a suspend function that performs blocking OkHttp I/O on [Dispatchers.IO] and
 * honours coroutine cancellation between chunks.
 */
class FDroidRepoService(
    private val client: OkHttpClient,
    json: Json,
    /** Scratch directory for the small signed `entry.jar` (needs random access to verify). */
    private val tempDir: File,
) {
    private val entryReader = SignedEntryReader(json)

    /** Downloads and verifies `entry.jar`, returning the parsed [Entry]. */
    suspend fun fetchEntry(repo: Repository): Entry = withContext(Dispatchers.IO) {
        tempDir.mkdirs()
        val jar = File.createTempFile("entry-", ".jar", tempDir)
        try {
            execute(repo.resolve(ENTRY_JAR)).use { response ->
                val body = response.body ?: throw IOException("Empty body for entry.jar")
                body.byteStream().use { input -> jar.outputStream().use { input.copyTo(it) } }
            }
            entryReader.read(jar, repo.fingerprint).entry
        } finally {
            jar.delete()
        }
    }

    /**
     * Downloads the file described by [indexFile] (normally `/index-v2.json`) into [target],
     * verifying its size and SHA-256 against the signed entry before the file is made visible.
     *
     * The transfer is resumable: a partial `.part` file is continued with an HTTP `Range` request
     * and dropped connections are retried up to [MAX_ATTEMPTS] times.
     *
     * @param onProgress called with (bytesRead, totalBytes) as data streams in.
     */
    suspend fun downloadIndex(
        repo: Repository,
        indexFile: EntryFileV2,
        target: File,
        onProgress: (read: Long, total: Long) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".part")
        val url = repo.resolve(indexFile.name)
        try {
            var attempt = 0
            while (true) {
                attempt++
                try {
                    transfer(url, tmp, indexFile.size, onProgress)
                    break
                } catch (e: IOException) {
                    if (attempt >= MAX_ATTEMPTS) throw e
                    Log.w(TAG, "Download attempt $attempt failed (${e.message}), resuming at ${tmp.length()} bytes")
                    delay(RETRY_DELAY_MS * attempt)
                }
            }

            if (tmp.length() != indexFile.size) {
                throw RepoVerificationException("Index size mismatch: expected ${indexFile.size}, got ${tmp.length()}")
            }
            val actual = sha256(tmp)
            if (!actual.equals(indexFile.sha256, ignoreCase = true)) {
                throw RepoVerificationException("Index SHA-256 mismatch for ${indexFile.name}")
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("Could not move ${tmp.name} into place")
        } catch (e: RepoVerificationException) {
            tmp.delete() // corrupt or tampered: never resume from it
            throw e
        } catch (e: CancellationException) {
            throw e // keep the .part file so the next refresh resumes
        }
    }

    /** Streams [url] into [tmp], resuming from its current length when the server supports ranges. */
    private suspend fun transfer(
        url: String,
        tmp: File,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        var offset = tmp.length()
        if (offset > total) {
            tmp.delete()
            offset = 0
        }
        if (offset == total) return

        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        if (offset > 0) builder.header("Range", "bytes=$offset-")
        val response = client.newCall(builder.build()).execute()
        response.use {
            val append = when (response.code) {
                206 -> true
                200 -> { offset = 0; false } // server ignored Range: start over
                else -> throw IOException("HTTP ${response.code} for $url")
            }
            val body = response.body ?: throw IOException("Empty body for $url")
            body.byteStream().use { input ->
                FileOutputStream(tmp, append).buffered().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = offset
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (read > total) throw RepoVerificationException("Index larger than announced size $total")
                        onProgress(read, total)
                    }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(256 * 1024).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun execute(url: String): Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} for $url")
        }
        return response
    }

    companion object {
        const val ENTRY_JAR = "entry.jar"
        private const val TAG = "FDroidRepoService"
        private const val USER_AGENT = "EDroid/0.1 (F-Droid index-v2 client)"
        private const val MAX_ATTEMPTS = 5
        private const val RETRY_DELAY_MS = 1_000L
    }
}
