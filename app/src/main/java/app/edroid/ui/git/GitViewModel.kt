package app.edroid.ui.git

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.edroid.EDroidApp
import app.edroid.R
import app.edroid.data.github.GitHubAsset
import app.edroid.data.github.GitHubReleaseChecker
import app.edroid.data.github.GitHubRepo
import app.edroid.data.github.GitHubRepoStore
import app.edroid.data.github.TrackResult
import app.edroid.data.github.TrackedRepo
import app.edroid.data.install.ApkInstaller
import app.edroid.data.install.installedVersionCodeNow
import app.edroid.ui.options.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** What a repo row is busy with right now; no state means idle. */
sealed interface TrackState {
    data object Checking : TrackState
    /** [progress] is 0..1, or null when the size is unknown. */
    data class Downloading(val progress: Float?) : TrackState
    data class Choosing(val assets: List<GitHubAsset>) : TrackState
    data class Error(val message: UiText) : TrackState
}

data class GitRow(val tracked: TrackedRepo, val state: TrackState?)

class GitViewModel(
    private val store: GitHubRepoStore,
    private val checker: GitHubReleaseChecker,
    private val installer: ApkInstaller,
    private val context: Context,
) : ViewModel() {

    private val states = MutableStateFlow<Map<String, TrackState>>(emptyMap())
    private val pending = MutableStateFlow<List<TrackedRepo>>(emptyList())
    private val jobs = mutableMapOf<String, Job>()
    private val _refreshing = MutableStateFlow(false)
    private val _messages = Channel<UiText>(Channel.BUFFERED)

    val rows: StateFlow<List<GitRow>> = combine(store.repos, pending, states) { stored, pend, st ->
        val storedKeys = stored.mapTo(HashSet()) { it.key }
        (stored + pend.filter { it.key !in storedKeys }).map { GitRow(it, st[it.key]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Install states are folded in so finishing an install re-checks which repos are still behind.
    val hasUpdates: StateFlow<Boolean> = combine(rows, installer.states) { list, _ -> list.any { isUpdatable(it.tracked) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val refreshing: StateFlow<Boolean> = _refreshing
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    /** Starts following the repo [link] names; returns why it can't, or null once tracking started. */
    fun add(link: String): UiText? {
        val repo = GitHubRepo.parse(link) ?: return UiText(R.string.git_error_invalid)
        val tracked = TrackedRepo(repo)
        if (store.repos.value.any { it.key == tracked.key } || pending.value.any { it.key == tracked.key }) {
            return UiText(R.string.git_error_duplicate)
        }
        pending.update { it + tracked }
        track(repo, null)
        return null
    }

    fun choose(repo: GitHubRepo, asset: GitHubAsset) = track(repo, asset)

    fun retry(repo: GitHubRepo) = track(repo, null)

    fun dismissChoice(repo: GitHubRepo) {
        val key = TrackedRepo(repo).key
        jobs.remove(key)?.cancel()
        if (isStored(key)) {
            setState(key, null)
            store.markNeedsChoice(repo)
        } else {
            drop(key)
        }
    }

    /** Pull-to-refresh; the only path that checks every repo, so opening the tab never hits the network. */
    fun refresh() {
        if (!_refreshing.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                checker.checkAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh failed", e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Installs the latest release of every installed repo that has a newer one than the device runs. */
    fun updateAll() {
        rows.value.map { it.tracked }.filter(::isUpdatable).forEach { tracked ->
            tracked.latest?.let { installer.install(it.packageName, it.appName, it.toAppVersion(), cachedApk(tracked)) }
        }
    }

    private fun isUpdatable(tracked: TrackedRepo): Boolean {
        val latest = tracked.latest ?: return false
        val installed = context.installedVersionCodeNow(latest.packageName) ?: return false
        return latest.versionCode > installed
    }

    /** The already-downloaded APK of [tracked], so installing it does not download it twice. */
    fun cachedApk(tracked: TrackedRepo): File? =
        tracked.latest?.let { checker.apkFile(tracked.repo, it.assetName) }?.takeIf { it.exists() }

    private fun isStored(key: String) = store.repos.value.any { it.key == key }

    private fun setState(key: String, state: TrackState?) =
        states.update { if (state == null) it - key else it + (key to state) }

    private fun drop(key: String) {
        pending.update { list -> list.filterNot { it.key == key } }
        setState(key, null)
    }

    /** Failure for a stored repo stays on its row; a pending one is dropped only when retrying cannot help. */
    private fun fail(repo: GitHubRepo, key: String, message: UiText, removePending: Boolean) {
        if (removePending && !isStored(key)) {
            drop(key)
            _messages.trySend(message)
        } else {
            setState(key, TrackState.Error(message))
        }
    }

    // One job per key: a newer request supersedes the running one.
    private fun track(repo: GitHubRepo, chosen: GitHubAsset?) {
        val key = TrackedRepo(repo).key
        jobs.remove(key)?.cancel()
        setState(key, TrackState.Checking)
        jobs[key] = viewModelScope.launch {
            try {
                when (val result = checker.track(repo, chosen) { setState(key, TrackState.Downloading(it)) }) {
                    is TrackResult.Tracked -> {
                        if (isStored(key)) store.update(repo, result.apk) else store.add(TrackedRepo(repo, result.apk))
                        drop(key)
                    }
                    TrackResult.NoApk -> fail(repo, key, UiText(R.string.git_error_no_apk), removePending = true)
                    is TrackResult.NeedsChoice -> setState(key, TrackState.Choosing(result.assets))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Tracking $repo failed", e)
                if (e.message == NOT_APK) {
                    fail(repo, key, UiText(R.string.git_error_not_apk), removePending = true)
                } else {
                    fail(repo, key, UiText(R.string.git_error_network, e.message ?: e.javaClass.simpleName), removePending = false)
                }
            }
        }
    }

    companion object {
        private const val TAG = "GitViewModel"
        // Message the checker throws for a download that is not an APK.
        private const val NOT_APK = "Not a valid APK"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EDroidApp
                GitViewModel(app.container.gitHubRepos, app.container.gitHubChecker, app.container.apkInstaller, app)
            }
        }
    }
}
