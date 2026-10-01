package app.edroid.data.fdroid.net

import app.edroid.data.fdroid.model.Entry
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.jar.JarFile

class RepoVerificationException(message: String) : Exception(message)

/**
 * Extracts and verifies `entry.json` from an F-Droid `entry.jar`.
 *
 * F-Droid signs the JAR with the repository key. The JAR must contain exactly one signed
 * `entry.json`; reading the entry to the end through a verifying [JarFile] triggers signature
 * verification, after which the signer certificates become available. The SHA-256 of the (single)
 * signing certificate is the repository fingerprint users pin when adding a repo.
 *
 * [JarFile] (random access) is used instead of `JarInputStream`, which silently skips
 * verification unless `META-INF/MANIFEST.MF` happens to be the first zip entry.
 */
class SignedEntryReader(private val json: Json) {

    data class Result(val entry: Entry, val fingerprint: String)

    fun read(jarFile: File, expectedFingerprint: String?): Result {
        JarFile(jarFile, /* verify = */ true).use { jar ->
            val entries = jar.entries().asSequence()
                .filter { !it.isDirectory && !it.name.startsWith("META-INF/") }
                .toList()
            val jsonEntry = entries.singleOrNull { it.name == ENTRY_FILE_NAME }
                ?: throw RepoVerificationException("entry.jar must contain exactly one $ENTRY_FILE_NAME")
            if (entries.size != 1) {
                throw RepoVerificationException("Unexpected files in entry.jar: ${entries.map { it.name }}")
            }

            // Must read fully before certificates are populated and the signature is checked.
            val bytes = jar.getInputStream(jsonEntry).use { it.readBytes() }
            val certs = jsonEntry.certificates.orEmpty().filterIsInstance<X509Certificate>()
            if (certs.isEmpty()) throw RepoVerificationException("$ENTRY_FILE_NAME is not signed")
            if (certs.size != 1) throw RepoVerificationException("$ENTRY_FILE_NAME has ${certs.size} signers, expected 1")

            val fingerprint = sha256Hex(certs.single().encoded)
            if (expectedFingerprint != null && !fingerprint.equals(expectedFingerprint, ignoreCase = true)) {
                throw RepoVerificationException(
                    "Repository fingerprint mismatch: expected $expectedFingerprint, got $fingerprint",
                )
            }
            val entry = json.decodeFromString(Entry.serializer(), bytes.decodeToString())
            return Result(entry, fingerprint)
        }
    }

    companion object {
        const val ENTRY_FILE_NAME = "entry.json"

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
    }
}
