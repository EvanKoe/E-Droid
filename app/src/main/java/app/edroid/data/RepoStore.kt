package app.edroid.data

import android.content.Context
import androidx.core.content.edit
import app.edroid.data.fdroid.model.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The configured repositories, in the order they were added, persisted in SharedPreferences.
 * Seeded with the built-in F-Droid repo on first run; built-in repos can be disabled but never
 * removed, so the list is never empty.
 */
class RepoStore(context: Context, private val json: Json) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val serializer = ListSerializer(Repository.serializer())

    private val _repos = MutableStateFlow(load())
    val repos: StateFlow<List<Repository>> = _repos.asStateFlow()

    /**
     * Adds [repo], or replaces the one with the same [Repository.id].
     */
    fun add(repo: Repository) = update { current ->
        val index = current.indexOfFirst { it.id == repo.id }
        if (index < 0) current + repo else current.toMutableList().also { it[index] = repo }
    }

    /**
     * Turns the repo with [id] on or off without touching its cache.
     */
    fun setEnabled(id: String, enabled: Boolean) = update { current ->
        current.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    /**
     * Takes the display name the repo's own index announces, once it has been read.
     */
    fun rename(id: String, name: String) = update { current ->
        current.map { if (it.id == id) it.copy(name = name) else it }
    }

    /**
     * Removes the repo with [id] unless it is built in.
     */
    fun remove(id: String) = update { current -> current.filterNot { it.id == id && !it.isDefault } }

    /**
     * Applies [transform] to the current list; publishes and persists only if it changed anything.
     */
    private fun update(transform: (List<Repository>) -> List<Repository>) {
        val next = transform(_repos.value)
        if (next == _repos.value) {
            return
        }

        _repos.value = next
        prefs.edit { putString(KEY, json.encodeToString(serializer, next)) }
    }

    /**
     * Reads the persisted list, falling back to [DEFAULTS] when it is missing, empty or unreadable.
     */
    private fun load(): List<Repository> = runCatching {
        prefs.getString(KEY, null)?.let { json.decodeFromString(serializer, it) }
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: DEFAULTS

    companion object {
        /** SharedPreferences file name. */
        private const val PREFS_NAME = "repos"

        /** Preference key holding the JSON list. */
        private const val KEY = "list"

        /** The list on first run or after an unreadable store. */
        private val DEFAULTS = listOf(Repository.FDROID)
    }
}
