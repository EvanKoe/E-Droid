package app.edroid.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Keeps the last few committed search queries, most recent first, in SharedPreferences.
 * Small enough that a database would be overkill.
 */
class RecentSearchesStore(
    context: Context,
    private val json: Json,
    private val maxEntries: Int = DEFAULT_MAX,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val serializer = ListSerializer(String.serializer())

    private val _recent = MutableStateFlow(load())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    /**
     * Moves [query] to the front, dropping duplicates (case-insensitive) and anything past the cap.
     */
    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            return
        }

        update { current -> (listOf(q) + current.filterNot { it.equals(q, ignoreCase = true) }).take(maxEntries) }
    }

    /**
     * Drops the entry exactly equal to [query], if present.
     */
    fun remove(query: String) = update { current -> current.filterNot { it == query } }

    /**
     * Forgets every recent search.
     */
    fun clear() = update { emptyList() }

    /**
     * Applies [transform] to the current list, publishes the result and persists it.
     */
    private fun update(transform: (List<String>) -> List<String>) {
        val next = transform(_recent.value)
        _recent.value = next
        prefs.edit { putString(KEY, json.encodeToString(serializer, next)) }
    }

    /**
     * Reads the persisted list, falling back to empty when nothing is stored or it is unreadable.
     */
    private fun load(): List<String> = runCatching {
        prefs.getString(KEY, null)?.let { json.decodeFromString(serializer, it) }
    }.getOrNull().orEmpty()

    companion object {
        /** Recent searches kept when the caller gives no cap. */
        const val DEFAULT_MAX = 3

        /** SharedPreferences file name. */
        private const val PREFS_NAME = "recent_searches"

        /** Preference key holding the JSON list. */
        private const val KEY = "queries"
    }
}
