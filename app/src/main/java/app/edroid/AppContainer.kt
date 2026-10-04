package app.edroid

import android.content.Context
import app.edroid.data.RecentSearchesStore
import app.edroid.data.SettingsStore
import app.edroid.data.fdroid.FDroidRepository
import app.edroid.data.fdroid.model.Repository
import app.edroid.data.fdroid.net.FDroidRepoService
import app.edroid.data.fdroid.parser.IndexV2StreamParser
import app.edroid.data.install.ApkInstaller
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled dependency container. Small enough that a DI framework would be more ceremony
 * than value; swap for Hilt/Koin if the graph grows.
 */
class AppContainer(context: Context) {
    val json = IndexV2StreamParser.DEFAULT_JSON

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val repoService = FDroidRepoService(okHttpClient, json, tempDir = context.cacheDir.resolve("entry"))

    val recentSearches = RecentSearchesStore(context, json)

    val settings = SettingsStore(context)

    val apkInstaller = ApkInstaller(context, okHttpClient)

    val fdroidRepository = FDroidRepository(
        repo = Repository.FDROID,
        service = repoService,
        storageDir = context.filesDir,
        json = json,
    )
}
