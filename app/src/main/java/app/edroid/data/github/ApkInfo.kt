package app.edroid.data.github

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

data class ApkInfo(val packageName: String, val appName: String, val versionCode: Long, val versionName: String)

/** Manifest facts of an APK on disk, or null when it cannot be parsed. */
fun Context.readApkInfo(file: File): ApkInfo? {
    val info = packageManager.getPackageArchiveInfo(file.path, 0) ?: return null
    val app = info.applicationInfo
    // Without these paths loadLabel cannot read the archive's resources.
    app?.sourceDir = file.path
    app?.publicSourceDir = file.path
    return ApkInfo(
        packageName = info.packageName,
        appName = app?.loadLabel(packageManager)?.toString() ?: info.packageName,
        versionCode = PackageInfoCompat.getLongVersionCode(info),
        versionName = info.versionName ?: "",
    )
}
