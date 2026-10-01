package app.edroid.data.fdroid.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * F-Droid "index-v2" repository format.
 *
 * Protocol (see https://f-droid.org/docs/ and the fdroidclient `libs/index` module):
 *  1. GET <repo>/entry.jar  — a signed JAR holding `entry.json`. The JAR signature is the
 *     trust anchor: the SHA-256 of the signing certificate must match the repo fingerprint.
 *  2. entry.json points at the full index (`/index-v2.json`, with sha256 + size) and at a
 *     set of JSON-merge-patch diffs keyed by the index timestamp they apply to.
 *  3. GET <repo>/index-v2.json — `{ "repo": {...}, "packages": { "<packageName>": {...} } }`.
 *
 * Localized values are maps keyed by BCP-47 locale ("en-US", "de", ...).
 * File names in the index start with "/" and are relative to the repo address.
 */

typealias LocalizedTextV2 = Map<String, String>
typealias LocalizedFileV2 = Map<String, FileV2>
typealias LocalizedFileListV2 = Map<String, List<FileV2>>

@Serializable
data class Entry(
    val timestamp: Long,
    val version: Long,
    /** Maximum age of the index in days before clients should consider it stale. */
    val maxAge: Int? = null,
    val index: EntryFileV2,
    val diffs: Map<String, EntryFileV2> = emptyMap(),
) {
    /** Returns the diff that upgrades an index with [timestamp] to the current one, if any. */
    fun diffFor(timestamp: Long): EntryFileV2? = diffs[timestamp.toString()]
}

@Serializable
data class EntryFileV2(
    val name: String,
    val sha256: String,
    val size: Long,
    @SerialName("ipfsCIDv1") val ipfsCidV1: String? = null,
    val numPackages: Int,
)

@Serializable
data class FileV2(
    val name: String,
    val sha256: String? = null,
    val size: Long? = null,
    @SerialName("ipfsCIDv1") val ipfsCidV1: String? = null,
)

/** Like [FileV2] but sha256 is mandatory (used for APK files). */
@Serializable
data class FileV1(
    val name: String,
    val sha256: String,
    val size: Long? = null,
    @SerialName("ipfsCIDv1") val ipfsCidV1: String? = null,
)

@Serializable
data class IndexV2(
    val repo: RepoV2,
    val packages: Map<String, PackageV2> = emptyMap(),
)

@Serializable
data class RepoV2(
    val name: LocalizedTextV2 = emptyMap(),
    val icon: LocalizedFileV2 = emptyMap(),
    val address: String,
    val webBaseUrl: String? = null,
    val description: LocalizedTextV2 = emptyMap(),
    val mirrors: List<MirrorV2> = emptyList(),
    val timestamp: Long,
    val antiFeatures: Map<String, AntiFeatureV2> = emptyMap(),
    val categories: Map<String, CategoryV2> = emptyMap(),
    val releaseChannels: Map<String, ReleaseChannelV2> = emptyMap(),
)

@Serializable
data class MirrorV2(
    val url: String,
    val countryCode: String? = null,
    val dnsA: List<String>? = null,
    val dnsAAAA: List<String>? = null,
)

@Serializable
data class AntiFeatureV2(
    val icon: LocalizedFileV2 = emptyMap(),
    val name: LocalizedTextV2,
    val description: LocalizedTextV2 = emptyMap(),
)

@Serializable
data class CategoryV2(
    val icon: LocalizedFileV2 = emptyMap(),
    val name: LocalizedTextV2,
    val description: LocalizedTextV2 = emptyMap(),
)

@Serializable
data class ReleaseChannelV2(
    val name: LocalizedTextV2,
    val description: LocalizedTextV2 = emptyMap(),
)

@Serializable
data class PackageV2(
    val metadata: MetadataV2,
    /** Keyed by the APK's sha256. */
    val versions: Map<String, PackageVersionV2> = emptyMap(),
)

@Serializable
data class MetadataV2(
    val name: LocalizedTextV2? = null,
    val summary: LocalizedTextV2? = null,
    val description: LocalizedTextV2? = null,
    val added: Long,
    val lastUpdated: Long,
    val webSite: String? = null,
    val changelog: String? = null,
    val license: String? = null,
    val sourceCode: String? = null,
    val issueTracker: String? = null,
    val translation: String? = null,
    val preferredSigner: String? = null,
    val categories: List<String> = emptyList(),
    val authorName: String? = null,
    val authorEmail: String? = null,
    val authorWebSite: String? = null,
    val authorPhone: String? = null,
    val donate: List<String> = emptyList(),
    val liberapayID: String? = null,
    val liberapay: String? = null,
    val openCollective: String? = null,
    val bitcoin: String? = null,
    val litecoin: String? = null,
    val flattrID: String? = null,
    val icon: LocalizedFileV2? = null,
    val featureGraphic: LocalizedFileV2? = null,
    val promoGraphic: LocalizedFileV2? = null,
    val tvBanner: LocalizedFileV2? = null,
    val video: LocalizedTextV2? = null,
    val screenshots: Screenshots? = null,
)

@Serializable
data class Screenshots(
    val phone: LocalizedFileListV2? = null,
    val sevenInch: LocalizedFileListV2? = null,
    val tenInch: LocalizedFileListV2? = null,
    val wear: LocalizedFileListV2? = null,
    val tv: LocalizedFileListV2? = null,
)

const val ANTI_FEATURE_KNOWN_VULNERABILITY = "KnownVuln"

@Serializable
data class PackageVersionV2(
    val added: Long,
    val file: FileV1,
    val src: FileV2? = null,
    val manifest: ManifestV2,
    val releaseChannels: List<String> = emptyList(),
    val antiFeatures: Map<String, LocalizedTextV2> = emptyMap(),
    val whatsNew: LocalizedTextV2 = emptyMap(),
) {
    val versionCode: Long get() = manifest.versionCode
    val versionName: String get() = manifest.versionName
    val hasKnownVulnerability: Boolean get() = ANTI_FEATURE_KNOWN_VULNERABILITY in antiFeatures
}

@Serializable
data class ManifestV2(
    val versionName: String,
    val versionCode: Long,
    val usesSdk: UsesSdkV2? = null,
    val maxSdkVersion: Int? = null,
    /** Null for non-app content such as fonts or media. */
    val signer: SignerV2? = null,
    val usesPermission: List<PermissionV2> = emptyList(),
    val usesPermissionSdk23: List<PermissionV2> = emptyList(),
    val nativecode: List<String> = emptyList(),
    val features: List<FeatureV2> = emptyList(),
)

@Serializable
data class UsesSdkV2(val minSdkVersion: Int, val targetSdkVersion: Int)

@Serializable
data class SignerV2(val sha256: List<String>, val hasMultipleSigners: Boolean = false)

@Serializable
data class PermissionV2(val name: String, val maxSdkVersion: Int? = null)

@Serializable
data class FeatureV2(val name: String)
