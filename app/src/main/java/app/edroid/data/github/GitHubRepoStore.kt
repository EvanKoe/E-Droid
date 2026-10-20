package app.edroid.data.github

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** The GitHub repositories the user follows on the Git tab, in the order added, in SharedPreferences. */
class GitHubRepoStore(context: Context, private val json: Json) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val serializer = ListSerializer(TrackedRepo.serializer())

    private val _repos = MutableStateFlow(load())
    val repos: StateFlow<List<TrackedRepo>> = _repos.asStateFlow()

    /** Adds [tracked] at the end; false (and no change) when its repo is already followed, ignoring case. */
    fun add(tracked: TrackedRepo): Boolean {
        var added = false
        persist { current ->
            added = current.none { it.key == tracked.key }
            if (added) current + tracked else current
        }
        return added
    }

    fun update(repo: GitHubRepo, latest: TrackedApk) = replace(repo) { it.copy(latest = latest, needsChoice = false) }

    fun markNeedsChoice(repo: GitHubRepo) = replace(repo) { it.copy(needsChoice = true) }

    private fun replace(repo: GitHubRepo, change: (TrackedRepo) -> TrackedRepo) {
        val key = TrackedRepo(repo).key
        persist { current -> current.map { if (it.key == key) change(it) else it } }
    }

    // Synchronized so the worker and the UI cannot persist out of order.
    @Synchronized
    private fun persist(change: (List<TrackedRepo>) -> List<TrackedRepo>) {
        _repos.update(change)
        prefs.edit { putString(KEY, json.encodeToString(serializer, _repos.value)) }
    }

    private fun load(): List<TrackedRepo> = runCatching {
        prefs.getString(KEY, null)?.let { json.decodeFromString(serializer, it) }
    }.getOrNull().orEmpty()

    private companion object {
        const val PREFS_NAME = "github_repos"
        const val KEY = "list"
    }
}
