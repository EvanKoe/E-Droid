package app.edroid.ui.options

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.edroid.EDroidApp
import app.edroid.R
import app.edroid.data.RepoStore
import app.edroid.data.fdroid.RepoHub
import app.edroid.data.fdroid.SyncState
import app.edroid.data.fdroid.model.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI

/** One row of the repos page: the repo plus what its loader currently knows about it. */
data class RepoRow(val repo: Repository, val sync: SyncState, val appCount: Int?)

/** A string resource with an optional single argument, resolved by the UI. */
data class UiText(@param:StringRes val res: Int, val arg: String? = null)

/** A repo that answered and was signed by [fingerprint], waiting for the user to trust that key. */
data class PendingTrust(val address: String, val fingerprint: String)

data class AddRepoUiState(
    val address: String = "",
    val fingerprint: String = "",
    val verifying: Boolean = false,
    val addressError: UiText? = null,
    val fingerprintError: UiText? = null,
    val pendingTrust: PendingTrust? = null,
    /** Set once the repo is saved; the sheet closes when it sees it. */
    val added: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReposViewModel(
    private val store: RepoStore,
    private val hub: RepoHub,
) : ViewModel() {

    val rows: StateFlow<List<RepoRow>> = store.repos
        .flatMapLatest { repos ->
            if (repos.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    repos.map { repo ->
                        val loader = hub.repository(repo)
                        combine(loader.sync, loader.snapshot) { sync, snapshot -> RepoRow(repo, sync, snapshot?.apps?.size) }
                    },
                ) { it.toList() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _addState = MutableStateFlow(AddRepoUiState())
    val addState: StateFlow<AddRepoUiState> = _addState.asStateFlow()
    private var verifyJob: Job? = null

    fun setEnabled(repo: Repository, enabled: Boolean) = hub.setEnabled(repo.id, enabled)

    fun remove(repo: Repository) = hub.remove(repo)

    /** Clears the add form; called each time the sheet opens. */
    fun startAdding() {
        verifyJob?.cancel()
        _addState.value = AddRepoUiState()
    }

    /** A pasted share link (`…/repo?fingerprint=…`) fills in both fields. */
    fun onAddressChange(value: String) {
        val (address, fingerprint) = splitShareLink(value)
        _addState.update {
            it.copy(
                address = address,
                fingerprint = fingerprint ?: it.fingerprint,
                addressError = null,
                fingerprintError = if (fingerprint != null) null else it.fingerprintError,
                pendingTrust = null,
            )
        }
    }

    fun onFingerprintChange(value: String) =
        _addState.update { it.copy(fingerprint = value, fingerprintError = null, pendingTrust = null) }

    /**
     * Validates the form, then downloads the repo's signed entry. With a fingerprint typed in, a
     * matching signer adds the repo at once; without one, the signer is shown for the user to trust.
     */
    fun submit() {
        val form = _addState.value
        if (form.verifying) return
        val address = Repository.normalizeAddress(form.address)
        val fingerprint = Repository.normalizeFingerprint(form.fingerprint)
        val host = runCatching { URI(address).host }.getOrNull()
        val addressError = when {
            !address.startsWith("https://", ignoreCase = true) || host.isNullOrBlank() -> UiText(R.string.add_repo_error_https)
            store.repos.value.any { it.id == Repository(address, address, null).id } -> UiText(R.string.add_repo_error_duplicate)
            else -> null
        }
        val fingerprintError = if (fingerprint.isNotEmpty() && !Repository.isValidFingerprint(fingerprint)) {
            UiText(R.string.add_repo_error_fingerprint)
        } else {
            null
        }
        if (addressError != null || fingerprintError != null) {
            _addState.update { it.copy(addressError = addressError, fingerprintError = fingerprintError) }
            return
        }

        _addState.update { it.copy(verifying = true, pendingTrust = null) }
        verifyJob = viewModelScope.launch {
            try {
                val signer = hub.probe(address).fingerprint.uppercase()
                when {
                    fingerprint.isEmpty() -> _addState.update { it.copy(pendingTrust = PendingTrust(address, signer)) }
                    signer != fingerprint -> _addState.update {
                        it.copy(fingerprintError = UiText(R.string.add_repo_error_mismatch, signer))
                    }
                    else -> save(address, signer)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _addState.update {
                    it.copy(addressError = UiText(R.string.add_repo_error_unreachable, e.message ?: e.javaClass.simpleName))
                }
            } finally {
                _addState.update { it.copy(verifying = false) }
            }
        }
    }

    /** The user checked the key shown for an unpinned address and accepts it. */
    fun trust() {
        val pending = _addState.value.pendingTrust ?: return
        save(pending.address, pending.fingerprint)
    }

    private fun save(address: String, fingerprint: String) {
        hub.add(Repository(address = address, name = defaultName(address), fingerprint = fingerprint))
        _addState.update { it.copy(added = true) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EDroidApp
                ReposViewModel(app.container.repoStore, app.container.repoHub)
            }
        }
    }
}

/**
 * Splits an F-Droid share link into its base address and the fingerprint it carries, mapping the
 * `fdroidrepos://` scheme to https. Anything else is returned as typed, with no fingerprint.
 */
private fun splitShareLink(raw: String): Pair<String, String?> {
    val trimmed = raw.trim()
    val isShareScheme = trimmed.startsWith("fdroidrepos://", ignoreCase = true)
    if (!isShareScheme && '?' !in trimmed) return raw to null
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return raw to null
    if (uri.rawAuthority == null) return raw to null
    val fingerprint = uri.rawQuery
        ?.split('&')
        ?.map { it.split('=', limit = 2) }
        ?.firstOrNull { it.first().equals("fingerprint", ignoreCase = true) }
        ?.getOrNull(1)
    val scheme = if (isShareScheme) "https" else uri.scheme
    return "$scheme://${uri.rawAuthority}${uri.rawPath.orEmpty()}" to fingerprint
}

/** A readable placeholder name (`host/path`) until the repo's own index names it. */
private fun defaultName(address: String): String = address.substringAfter("://")
