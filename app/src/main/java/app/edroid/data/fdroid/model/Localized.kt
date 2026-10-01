package app.edroid.data.fdroid.model

import java.util.Locale

/**
 * Picks the best entry of an F-Droid localized map for [locale].
 *
 * Resolution order: exact tag ("de-AT"), language only ("de"), any variant of the language
 * ("de-DE"), then "en-US", "en", and finally whatever comes first.
 */
fun <T> Map<String, T>.localized(locale: Locale = Locale.getDefault()): T? {
    if (isEmpty()) return null
    val tag = locale.toLanguageTag()
    val lang = locale.language
    this[tag]?.let { return it }
    this[lang]?.let { return it }
    entries.firstOrNull { it.key.startsWith("$lang-") }?.let { return it.value }
    this["en-US"]?.let { return it }
    this["en"]?.let { return it }
    return values.first()
}
