package app.edroid.data.fdroid.parser

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import app.edroid.data.fdroid.model.PackageV2
import app.edroid.data.fdroid.model.RepoV2
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringWriter
import java.math.BigDecimal

/**
 * Streams an `index-v2.json` document so that only one package is materialised at a time.
 *
 * kotlinx.serialization can decode whole documents or lazy *arrays*, but `packages` is an object
 * keyed by package name, so we walk the top level with the platform [JsonReader], re-emit each
 * package subtree as a compact string and hand that to kotlinx for typed decoding.
 */
class IndexV2StreamParser(
    private val json: Json = DEFAULT_JSON,
) {
    /** Receives the parts of the index as [parse] walks it. */
    interface Visitor {
        /**
         * Called once with the decoded `repo` section.
         */
        fun onRepo(repo: RepoV2)

        /**
         * Called for each package, in file order, as soon as it is decoded.
         */
        fun onPackage(packageName: String, pkg: PackageV2)
    }

    /**
     * Walks [input] once, handing the repo section and every package to [visitor] without buffering the index.
     */
    fun parse(input: InputStream, visitor: Visitor) {
        JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    KEY_REPO -> visitor.onRepo(json.decodeFromString(RepoV2.serializer(), reader.readSubtree()))

                    KEY_PACKAGES -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val name = reader.nextName()
                            val pkg = json.decodeFromString(PackageV2.serializer(), reader.readSubtree())
                            visitor.onPackage(name, pkg)
                        }
                        reader.endObject()
                    }

                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
    }

    /**
     * Returns the package named [packageName], or null if the index doesn't list it. Every other
     * package is skipped without being copied or decoded, so this is one cheap sequential scan.
     * [checkCancelled] runs between packages and may throw to abort the scan.
     */
    fun findPackage(input: InputStream, packageName: String, checkCancelled: () -> Unit = {}): PackageV2? {
        JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() != KEY_PACKAGES) {
                    reader.skipValue()
                    continue
                }

                reader.beginObject()
                while (reader.hasNext()) {
                    checkCancelled()
                    if (reader.nextName() == packageName) {
                        return json.decodeFromString(PackageV2.serializer(), reader.readSubtree())
                    }

                    reader.skipValue()
                }
                return null
            }
        }
        return null
    }

    /**
     * Copies the value at the reader's position into a JSON string.
     */
    private fun JsonReader.readSubtree(): String {
        val out = StringWriter()
        JsonWriter(out).use { w -> copyValue(this, w) }
        return out.toString()
    }

    /**
     * Recursively copies the value at the position of [r] to [w], preserving numbers exactly.
     */
    private fun copyValue(r: JsonReader, w: JsonWriter) {
        when (r.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                r.beginObject(); w.beginObject()
                while (r.hasNext()) {
                    w.name(r.nextName())
                    copyValue(r, w)
                }
                r.endObject(); w.endObject()
            }

            JsonToken.BEGIN_ARRAY -> {
                r.beginArray(); w.beginArray()
                while (r.hasNext()) copyValue(r, w)
                r.endArray(); w.endArray()
            }

            JsonToken.STRING -> w.value(r.nextString())

            // nextString() on a NUMBER token yields the raw literal; BigDecimal preserves it losslessly.
            JsonToken.NUMBER -> w.value(BigDecimal(r.nextString()))

            JsonToken.BOOLEAN -> w.value(r.nextBoolean())

            JsonToken.NULL -> { r.nextNull(); w.nullValue() }

            else -> error("Unexpected token ${r.peek()}")
        }
    }

    companion object {
        /** Top-level index key holding the repo section. */
        private const val KEY_REPO = "repo"

        /** Top-level index key holding the packages map. */
        private const val KEY_PACKAGES = "packages"

        /** Tolerant of unknown keys, so newer index fields never break parsing. */
        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
