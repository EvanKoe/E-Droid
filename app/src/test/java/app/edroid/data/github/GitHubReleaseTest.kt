package app.edroid.data.github

import app.edroid.data.fdroid.parser.IndexV2StreamParser
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseTest {

    @Test
    fun `repo parses from slug and urls`() {
        val expected = GitHubRepo("EvanKoe", "E-Droid")
        assertEquals(expected, GitHubRepo.parse("EvanKoe/E-Droid"))
        assertEquals(expected, GitHubRepo.parse(" https://github.com/EvanKoe/E-Droid.git "))
        assertEquals(expected, GitHubRepo.parse("github.com/EvanKoe/E-Droid/releases/latest"))
        assertNull(GitHubRepo.parse("EvanKoe"))
        assertNull(GitHubRepo.parse("https://example.org/a/b b"))
    }

    @Test
    fun `release json parses and ignores unknown fields`() {
        val releases = IndexV2StreamParser.DEFAULT_JSON.decodeFromString(
            ListSerializer(GitHubRelease.serializer()),
            """
            [{"tag_name":"v1.0","name":null,"body":"notes","prerelease":true,"published_at":"2026-10-01T10:00:00Z",
              "html_url":"https://github.com/o/r/releases/tag/v1.0","author":{"login":"x"},
              "assets":[{"name":"app.APK","size":10,"content_type":"application/vnd.android.package-archive",
                         "browser_download_url":"https://github.com/o/r/releases/download/v1.0/app.APK"}]}]
            """.trimIndent(),
        )
        val release = releases.single()
        assertEquals("v1.0", release.tagName)
        assertTrue(release.prerelease)
        assertTrue(release.assets.single().isApk)
        assertNull(release.assets.single().sha256)
    }

    private fun asset(name: String, digest: String? = null) =
        GitHubAsset(name = name, size = 1, downloadUrl = "https://x/$name", digest = digest)

    private fun release(
        tag: String,
        vararg assets: GitHubAsset,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ) = GitHubRelease(tag, draft = draft, prerelease = prerelease, htmlUrl = "https://x/$tag", assets = assets.toList())

    @Test
    fun `digest json parses with prefix stripped`() {
        val a = IndexV2StreamParser.DEFAULT_JSON.decodeFromString(
            GitHubAsset.serializer(),
            """{"name":"a.apk","size":1,"browser_download_url":"https://x/a.apk","digest":"sha256:abc123"}""",
        )
        assertEquals("abc123", a.sha256)
    }

    @Test
    fun `apkAssets keeps only apks`() {
        val r = release("v1", asset("a.apk"), asset("notes.txt"), asset("b.APK"))
        assertEquals(listOf("a.apk", "b.APK"), r.apkAssets.map { it.name })
    }

    @Test
    fun `latestInstallable skips draft prerelease and apkless`() {
        val apk = asset("a.apk")
        val releases = listOf(
            release("v4", apk, draft = true),
            release("v3", apk, prerelease = true),
            release("v2", asset("notes.txt")),
            release("v1", apk),
        )
        assertEquals("v1", releases.latestInstallable()?.tagName)
        assertNull(releases.take(3).latestInstallable())
    }

    @Test
    fun `tracked repo round trips and maps to app version`() {
        val apk = TrackedApk("v1", "a.apk", "https://x/a.apk", "abc", 5, "p.k", "App", 7, "1.0")
        val tracked = TrackedRepo(GitHubRepo("Own", "Name"), apk, needsChoice = true)
        val json = IndexV2StreamParser.DEFAULT_JSON
        val back = json.decodeFromString(TrackedRepo.serializer(), json.encodeToString(TrackedRepo.serializer(), tracked))
        assertEquals(tracked, back)
        assertEquals("own/name", back.key)
        val v = apk.toAppVersion()
        assertEquals("1.0", v.versionName)
        assertEquals(7L, v.versionCode)
        assertEquals("abc", v.sha256)
        assertEquals(5L, v.sizeBytes)
        assertEquals("https://x/a.apk", v.apkUrl)
    }
}
