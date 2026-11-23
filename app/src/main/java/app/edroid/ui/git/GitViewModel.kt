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
    /** Looking up the repo's latest release. */
    data object Checking : TrackState

    /** [progress] is 0..1, or null when the size is unknown. */
    data class Downloading(val progress: Float?) : TrackState

    /** The release has several APKs and the user must pick one. */
    data class Choosing(val assets: List<GitHubAsset>) : TrackState

    /** The last attempt failed; [message] is shown on the row with a retry button. */
    data class Error(val message: UiText) : TrackState
}

/** A followed repo paired with what it is busy with, if anything. */
data class GitRow(val tracked: TrackedRepo, val state: TrackState?)

/** Immutable snapshot of everything the Git screen renders. */
data class GitUiState(
    val rows: List<GitRow> = emptyList(),
    /** True while a pull-to-refresh check of every repo runs. */
    val refreshing: Boolean = false,
    /** How many installed repos have a newer release than the device runs. */
    val updateCount: Int = 0,
)

/** Holds the Git screen state: followed repos, their release checks, downloads and installs. */
class GitViewModel(
    private val store: GitHubRepoStore,
    private val checker: GitHubReleaseChecker,
    private val installer: ApkInstaller,
    private val context: Context,
) : ViewModel() {

    private val states = MutableStateFlow<Map<String, TrackState>>(emptyMap())
    private val pending = MutableStateFlow<List<TrackedRepo>>(emptyList())
    private val jobs = mutableMapOf<String, Job>()

    /** Keys whose pending APK choice is a switch, not a first pick. */
    private val switching = mutableSetOf<String>()
    private val _refreshing = MutableStateFlow(false)
    private val _messages = Channel<UiText>(Channel.BUFFERED)

    private val rows = combine(store.repos, pending, states) { stored, pend, st ->
        val storedKeys = stored.mapTo(HashSet()) { it.key }
        (stored + pend.filter { it.key !in storedKeys }).map { GitRow(it, st[it.key]) }
    }

    // Install states are folded in so finishing an install re-checks which repos are still behind.
    /** Single state stream the screen collects. */
    val uiState: StateFlow<GitUiState> = combine(rows, installer.states, _refreshing) { list, _, refreshing ->
        GitUiState(list, refreshing, list.count { isUpdatable(it.tracked) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GitUiState())

    /** One-shot user messages (toasts) that must not replay on rotation. */
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    /**
     * Starts following the repo [link] names; returns why it can't, or null once tracking started.
     */
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

    /**
     * Resumes tracking [repo] with the APK [asset] the user picked.
     */
    fun choose(repo: GitHubRepo, asset: GitHubAsset) = track(repo, asset)

    /**
     * Retries tracking [repo] after a failure or a pending choice.
     */
    fun retry(repo: GitHubRepo) = track(repo, null)

    /**
     * Closes the APK picker for [repo]: a stored repo keeps its row, a pending one is dropped.
     */
    fun dismissChoice(repo: GitHubRepo) {
        val key = TrackedRepo(repo).key
        jobs.remove(key)?.cancel()
        // A cancelled switch keeps the previous choice, which is still valid.
        if (switching.remove(key)) {
            setState(key, null)
            return
        }

        if (isStored(key)) {
            setState(key, null)
            store.markNeedsChoice(repo)
        } else {
            drop(key)
        }
    }

    /**
     * Stops following [repo] and deletes its downloaded APKs. The app installed from it stays installed.
     */
    fun remove(repo: GitHubRepo) {
        val key = TrackedRepo(repo).key
        jobs.remove(key)?.cancel()
        switching.remove(key)
        drop(key)
        store.remove(repo)
        viewModelScope.launch { checker.deleteDownloads(repo) }
    }

    /**
     * Pull-to-refresh; the only path that checks every repo, so opening the tab never hits the network.
     */
    fun refresh() {
        if (!_refreshing.compareAndSet(expect = false, update = true)) {
            return
        }

        viewModelScope.launch {
            try {
                val failed = checker.checkAll()
                if (failed > 0) {
                    _messages.trySend(UiText(R.string.git_error_refresh, failed.toString()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh failed", e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    /**
     * Installs the latest release of every installed repo that has a newer one than the device runs.
     */
    fun updateAll() {
        uiState.value.rows.map { it.tracked }.filter(::isUpdatable).forEach(::reinstall)
    }

    /**
     * Installs the latest APK of [tracked], downloading it again when no cached file is left.
     */
    fun reinstall(tracked: TrackedRepo) {
        tracked.latest?.let { installer.install(it.packageName, it.appName, it.toAppVersion(), cachedApk(tracked)) }
    }

    /**
     * Lists the APKs of [repo]'s latest release so the user can pick another one to install.
     */
    fun switchPackage(repo: GitHubRepo) {
        val key = TrackedRepo(repo).key
        jobs.remove(key)?.cancel()
        setState(key, TrackState.Checking)
        jobs[key] = viewModelScope.launch {
            try {
                val assets = checker.apkAssets(repo)
                if (assets.size > 1) {
                    switching.add(key)
                    setState(key, TrackState.Choosing(assets))
                } else {
                    setState(key, null)
                    _messages.trySend(UiText(R.string.git_switch_single))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Listing APKs of $repo failed", e)
                fail(repo, key, UiText(R.string.git_error_network, e.message ?: e.javaClass.simpleName), removePending = false)
            }
        }
    }

    /**
     * Whether [tracked] has a release newer than the installed version.
     */
    private fun isUpdatable(tracked: TrackedRepo) = checker.isUpdatable(tracked)

    /**
     * The already-downloaded APK of [tracked], so installing it does not download it twice.
     */
    fun cachedApk(tracked: TrackedRepo): File? =
        tracked.latest?.let { checker.apkFile(tracked.repo, it.assetName) }?.takeIf { it.exists() }

    /**
     * Whether the repo with [key] is already persisted, as opposed to pending its first check.
     */
    private fun isStored(key: String) = store.repos.value.any { it.key == key }

    /**
     * Sets or, with null, clears the busy state of the row with [key].
     */
    private fun setState(key: String, state: TrackState?) =
        states.update { if (state == null) it - key else it + (key to state) }

    /**
     * Forgets a pending repo and its busy state.
     */
    private fun drop(key: String) {
        pending.update { list -> list.filterNot { it.key == key } }
        setState(key, null)
    }

    /**
     * Failure for a stored repo stays on its row; a pending one is dropped only when retrying cannot help.
     */
    private fun fail(repo: GitHubRepo, key: String, message: UiText, removePending: Boolean) {
        if (removePending && !isStored(key)) {
            drop(key)
            _messages.trySend(message)
        } else {
            setState(key, TrackState.Error(message))
        }
    }

    /**
     * Checks and downloads [repo]'s latest release; one job per key, so a newer request supersedes the running one.
     */
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
                        if (switching.remove(key)) {
                            reinstall(TrackedRepo(repo, result.apk))
                        }
                    }

                    TrackResult.NoApk -> {
                        switching.remove(key)
                        fail(repo, key, UiText(R.string.git_error_no_apk), removePending = true)
                    }

                    is TrackResult.NeedsChoice -> {
                        switching.remove(key)
                        setState(key, TrackState.Choosing(result.assets))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed switch must not turn a later plain retry into an install
                switching.remove(key)
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
        /** Log tag. */
        private const val TAG = "GitViewModel"

        /** How long state flows stay hot after the last subscriber leaves. */
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Message the checker throws for a download that is not an APK. */
        private const val NOT_APK = "Not a valid APK"

        /** Builds the ViewModel from the application's container. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EDroidApp
                GitViewModel(app.container.gitHubRepos, app.container.gitHubChecker, app.container.apkInstaller, app)
            }
        }
    }
}
