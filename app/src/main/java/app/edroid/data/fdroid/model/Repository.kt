package app.edroid.data.fdroid.model

import kotlinx.serialization.Serializable

/**
 * A configured F-Droid repository.
 *
 * @property address Base URL of the repo, without trailing slash (e.g. `https://f-droid.org/repo`).
 * @property fingerprint Upper-case hex SHA-256 of the repo's signing certificate. When null, the
 * entry.jar signature is only checked for integrity, not pinned to a known signer.
 * @property enabled Whether the repo's apps are listed and its index is fetched on refresh.
 * @property isDefault Built-in repos can be disabled but not removed.
 */
@Serializable
data class Repository(
    val address: String,
    val name: String,
    val fingerprint: String?,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
) {
    /** Stable, filesystem-safe identifier derived from the address. */
    val id: String get() = address.lowercase().hashCode().toUInt().toString(16)

    fun resolve(file: FileV2?): String? = file?.let { resolve(it.name) }
    fun resolve(file: FileV1): String = resolve(file.name)
    fun resolve(path: String): String = address.trimEnd('/') + "/" + path.trimStart('/')

    companion object {
        val FDROID = Repository(
            address = "https://f-droid.org/repo",
            name = "F-Droid",
            fingerprint = "43238D512C1E5EB2D6569F4A3AFBF5523418B82E0A3ED1552770ABB9A9C9CCAB",
            isDefault = true,
        )

        private val FINGERPRINT_PATTERN = Regex("^[0-9A-F]{64}$")

        /** Upper-cases [raw] and drops the spaces and colons fingerprints are often written with. */
        fun normalizeFingerprint(raw: String): String = raw.filterNot { it.isWhitespace() || it == ':' }.uppercase()

        fun isValidFingerprint(fingerprint: String): Boolean = FINGERPRINT_PATTERN.matches(fingerprint)

        /** Trims whitespace and trailing slashes, so `…/repo/` and `…/repo` map to the same [id]. */
        fun normalizeAddress(raw: String): String = raw.trim().trimEnd('/')
    }
}
