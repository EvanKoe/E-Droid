package app.edroid

import android.app.Application
import app.edroid.data.github.GitHubReleaseWorker
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade

/** Coil disk cache directory under `cacheDir`. */
private const val ICON_CACHE_DIR = "icons"

/** Disk cache cap for app icons. */
private const val ICON_CACHE_MAX_BYTES = 64L * 1024 * 1024

/** Application: builds the [AppContainer], schedules background checks and configures Coil. */
class EDroidApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        GitHubReleaseWorker.schedule(this)
    }

    /**
     * Coil image loader for app icons, sharing the OkHttp client and caching icons on disk.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttpClient })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve(ICON_CACHE_DIR))
                    .maxSizeBytes(ICON_CACHE_MAX_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()
}
