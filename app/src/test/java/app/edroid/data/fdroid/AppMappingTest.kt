package app.edroid.data.fdroid

import app.edroid.data.fdroid.model.App
import app.edroid.data.fdroid.model.Entry
import app.edroid.data.fdroid.model.PackageV2
import app.edroid.data.fdroid.model.Repository
import app.edroid.data.fdroid.model.localized
import app.edroid.data.fdroid.parser.IndexV2StreamParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AppMappingTest {

    private val json = IndexV2StreamParser.DEFAULT_JSON

    @Test
    fun `entry json from f-droid parses`() {
        val entry = json.decodeFromString(
            Entry.serializer(),
            """
            {"timestamp":1790718180755,"version":30000,"maxAge":14,
             "index":{"name":"/index-v2.json","sha256":"e0fd","size":61862942,"numPackages":4486},
             "diffs":{"1789941281231":{"name":"/diff/1789941281231.json","sha256":"3a73","size":1234,"numPackages":12}}}
            """.trimIndent(),
        )
        assertEquals(4486, entry.index.numPackages)
        assertEquals("/diff/1789941281231.json", entry.diffFor(1789941281231)?.name)
        assertNull(entry.diffFor(1))
    }

    @Test
    fun `package maps to app with locale fallback and preferred signer`() {
        val pkg = json.decodeFromString(
            PackageV2.serializer(),
            """
            {"metadata":{
               "name":{"en-US":"Example","de":"Beispiel"},
               "summary":{"en-US":"An example app"},
               "icon":{"en-US":{"name":"/org.example/en-US/icon.png","sha256":"ab","size":10}},
               "added":1000,"lastUpdated":2000,"license":"GPL-3.0-only",
               "categories":["Development"],"preferredSigner":"aaaa",
               "unknownFutureField":{"x":1}
             },
             "versions":{
               "v1":{"added":1,"file":{"name":"/org.example_1.apk","sha256":"11"},
                     "manifest":{"versionName":"1.0","versionCode":1,"signer":{"sha256":["aaaa"]}}},
               "v2":{"added":2,"file":{"name":"/org.example_2.apk","sha256":"22"},
                     "manifest":{"versionName":"2.0","versionCode":2,"signer":{"sha256":["bbbb"]}},
                     "antiFeatures":{"NonFreeNet":{}}}
             }}
            """.trimIndent(),
        )
        val repo = Repository.FDROID

        val de = App.from("org.example", pkg, repo, Locale.GERMANY)
        assertEquals("Beispiel", de.name)
        assertEquals("An example app", de.summary) // falls back to en-US
        assertEquals("https://f-droid.org/repo/org.example/en-US/icon.png", de.iconUrl)
        // Version 2 is newer but signed by another key; the preferred signer's version wins.
        assertEquals("1.0", de.latestVersionName)
        assertEquals("https://f-droid.org/repo/org.example_1.apk", de.suggestedApkUrl)

        val fr = App.from("org.example", pkg, repo, Locale.FRANCE)
        assertEquals("Example", fr.name)
    }

    @Test
    fun `localized prefers exact tag then language then english`() {
        val map = mapOf("en" to "english", "pt-BR" to "brazil", "pt" to "portugal", "fr-CA" to "quebec")
        assertEquals("brazil", map.localized(Locale.forLanguageTag("pt-BR")))
        assertEquals("portugal", map.localized(Locale.forLanguageTag("pt-PT")))
        assertEquals("quebec", map.localized(Locale.FRANCE))
        assertEquals("english", map.localized(Locale.JAPAN))
        assertNull(emptyMap<String, String>().localized(Locale.US))
    }
}
