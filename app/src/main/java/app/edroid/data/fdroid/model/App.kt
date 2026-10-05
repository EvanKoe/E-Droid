package app.edroid.data.fdroid.model

import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * Lean, display-ready projection of a [PackageV2]. The full index for f-droid.org is ~60 MB of
 * JSON; keeping every localized description in memory is wasteful, so the streaming parser maps
 * each package into this shape as it goes and drops the raw object.
 */
@Serializable
data class App(
    val packageName: String,
    val name: String,
    val summary: String?,
    val iconUrl: String?,
    val license: String?,
    val categories: List<String>,
    val authorName: String?,
    val added: Long,
    val lastUpdated: Long,
    val latestVersionName: String?,
    val latestVersionCode: Long?,
    val suggestedApkUrl: String?,
    /** Hex sha256 of [suggestedApkUrl], so Install can start without reading the full index back. */
    val suggestedApkSha256: String? = null,
    val suggestedApkSize: Long? = null,
    val antiFeatures: Set<String>,
    /** [Repository.id] of the repo this row was read from, so details are looked up there. */
    val repoId: String,
) {
    /**
     * The version [suggestedApkUrl] points at, in the shape the installer takes; the same one the
     * detail page preselects. Null when the row lacks anything Install needs.
     */
    fun suggestedVersion(): AppVersion? {
        val url = suggestedApkUrl ?: return null
        val sha256 = suggestedApkSha256 ?: return null
        return AppVersion(
            versionName = latestVersionName ?: return null,
            versionCode = latestVersionCode ?: return null,
            added = lastUpdated,
            apkUrl = url,
            sha256 = sha256,
            sizeBytes = suggestedApkSize,
            minSdk = null,
            whatsNew = null,
            antiFeatures = antiFeatures,
        )
    }

    companion object {
        fun from(
            packageName: String,
            pkg: PackageV2,
            repo: Repository,
            locale: Locale = Locale.getDefault(),
        ): App {
            val meta = pkg.metadata
            // Prefer the newest version signed by the preferred signer; fall back to the newest overall.
            val latest = pkg.versions.values
                .filter { v ->
                    val signer = meta.preferredSigner ?: return@filter true
                    v.manifest.signer?.sha256?.contains(signer) == true
                }
                .maxByOrNull { it.versionCode }
                ?: pkg.versions.values.maxByOrNull { it.versionCode }
            return App(
                packageName = packageName,
                name = meta.name?.localized(locale) ?: packageName,
                summary = meta.summary?.localized(locale),
                iconUrl = repo.resolve(meta.icon?.localized(locale)),
                license = meta.license,
                categories = meta.categories,
                authorName = meta.authorName,
                added = meta.added,
                lastUpdated = meta.lastUpdated,
                latestVersionName = latest?.versionName,
                latestVersionCode = latest?.versionCode,
                suggestedApkUrl = latest?.let { repo.resolve(it.file) },
                suggestedApkSha256 = latest?.file?.sha256,
                suggestedApkSize = latest?.file?.size,
                antiFeatures = latest?.antiFeatures?.keys ?: emptySet(),
                repoId = repo.id,
            )
        }
    }
}
