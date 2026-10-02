package app.edroid.data.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Installed versionCode of [packageName] right now, or null when it isn't installed. */
fun Context.installedVersionCodeNow(packageName: String): Long? = runCatching {
    PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
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

/** Opens the system uninstall dialog for [packageName]. */
fun Context.requestUninstall(packageName: String) {
    startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)))
}
