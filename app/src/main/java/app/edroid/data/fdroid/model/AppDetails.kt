package app.edroid.data.fdroid.model

import java.util.Locale

/**
 * Everything the detail screen shows beyond an [App] row: the long description, media and every
 * installable version. Built on demand for a single package, so unlike [App] it can afford to
 * keep the heavy fields.
 */
data class AppDetails(
    val packageName: String,
    /** As published: usually light HTML, sometimes plain text with newlines. */
    val description: String?,
    /** Feature graphic, or the smaller promo graphic when the app has none. */
    val bannerUrl: String?,
    val videoUrl: String?,
    val screenshotUrls: List<String>,
    /** Newest first; same signer rule as [App.latestVersionName], so the first one matches it. */
    val versions: List<AppVersion>,
) {
    companion object {
        fun from(
            packageName: String,
            pkg: PackageV2,
            repo: Repository,
            locale: Locale = Locale.getDefault(),
        ): AppDetails {
            val meta = pkg.metadata
            val all = pkg.versions.values
            val signer = meta.preferredSigner
            val signed = if (signer == null) all else all.filter { it.manifest.signer?.sha256?.contains(signer) == true }
            // Phone screenshots read best in the portrait strip; tablets only as a fallback.
            val shots = meta.screenshots
            val screenshots = listOf(shots?.phone, shots?.sevenInch, shots?.tenInch)
                .firstNotNullOfOrNull { it?.localized(locale)?.takeIf(List<FileV2>::isNotEmpty) }
                .orEmpty()
            return AppDetails(
                packageName = packageName,
                description = meta.description?.localized(locale)?.trim()?.takeIf { it.isNotEmpty() },
                bannerUrl = repo.resolve(meta.featureGraphic?.localized(locale) ?: meta.promoGraphic?.localized(locale)),
                videoUrl = meta.video?.localized(locale),
                screenshotUrls = screenshots.map { repo.resolve(it.name) },
                versions = signed.ifEmpty { all }
                    .sortedByDescending { it.versionCode }
                    .map { AppVersion.from(it, repo, locale) },
            )
        }
    }
}

/** One installable APK of an app. */
data class AppVersion(
    val versionName: String,
    val versionCode: Long,
    val added: Long,
    val apkUrl: String,
    /** Hex sha256 of the APK, checked before it is handed to the system installer. */
    val sha256: String,
    val sizeBytes: Long?,
    val minSdk: Int?,
    val whatsNew: String?,
    val antiFeatures: Set<String>,
) {
    companion object {
        fun from(v: PackageVersionV2, repo: Repository, locale: Locale = Locale.getDefault()) = AppVersion(
            versionName = v.versionName,
            versionCode = v.versionCode,
            added = v.added,
            apkUrl = repo.resolve(v.file),
            sha256 = v.file.sha256,
            sizeBytes = v.file.size,
            minSdk = v.manifest.usesSdk?.minSdkVersion,
            whatsNew = v.whatsNew.localized(locale)?.trim()?.takeIf { it.isNotEmpty() },
            antiFeatures = v.antiFeatures.keys,
        )
    }
}
