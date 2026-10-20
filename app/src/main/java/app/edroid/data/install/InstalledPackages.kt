package app.edroid.data.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Installed versionCode of [packageName] right now, or null when it isn't installed. */
fun Context.installedVersionCodeNow(packageName: String): Long? = runCatching {
    PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
}.getOrNull()

/** Installed versionName of [packageName] right now, or null when it isn't installed or has none. */
fun Context.installedVersionNameNow(packageName: String): String? = runCatching {
    packageManager.getPackageInfo(packageName, 0).versionName
}.getOrNull()

/** [installedVersionCodeNow] as a flow that follows installs and uninstalls. */
fun Context.installedVersionCode(packageName: String): Flow<Long?> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.data?.schemeSpecificPart == packageName) trySend(installedVersionCodeNow(packageName))
        }
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addDataScheme("package")
    }
    // System broadcasts still reach a not-exported receiver.
    ContextCompat.registerReceiver(this@installedVersionCode, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    trySend(installedVersionCodeNow(packageName))
    awaitClose { unregisterReceiver(receiver) }
}.distinctUntilChanged()

/** What the package manager reports for one installed package. */
data class InstalledPackage(val versionCode: Long, val versionName: String?)

/** Every installed package by name, re-read whenever any package is installed, updated or removed. */
fun Context.installedPackages(): Flow<Map<String, InstalledPackage>> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(Unit)
        }
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addDataScheme("package")
    }
    ContextCompat.registerReceiver(this@installedPackages, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    trySend(Unit)
    awaitClose { unregisterReceiver(receiver) }
}
    // An update broadcasts removed, added and replaced in a row; one re-read covers the burst.
    .conflate()
    .map { installedPackagesNow() }
    .flowOn(Dispatchers.IO)
    .distinctUntilChanged()

private fun Context.installedPackagesNow(): Map<String, InstalledPackage> =
    packageManager.getInstalledPackages(0).associate {
        it.packageName to InstalledPackage(PackageInfoCompat.getLongVersionCode(it), it.versionName)
    }

/** Whether [packageName] has a launcher activity [openApp] can start. */
fun Context.canOpenApp(packageName: String): Boolean = packageManager.getLaunchIntentForPackage(packageName) != null

/** Starts [packageName]'s launcher activity, if it has one. */
fun Context.openApp(packageName: String) {
    packageManager.getLaunchIntentForPackage(packageName)?.let { runCatching { startActivity(it) } }
}

/** Opens the system uninstall dialog for [packageName]. */
fun Context.requestUninstall(packageName: String) {
    startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)))
}
