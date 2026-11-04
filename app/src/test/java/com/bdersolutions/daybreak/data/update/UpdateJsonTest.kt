package com.bdersolutions.daybreak.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateJsonTest {
    private val latest = """
        {
          "tag": "v1.2.0",
          "versionName": "1.2.0",
          "versionCode": 10200,
          "publishedAt": "2026-11-04T10:00:00Z",
          "changelog": "- New: updates",
          "apk": { "url": "https://updates.example.com/v1/apk/v1.2.0", "size": 5000000, "sha256": "ABCDEF" }
        }
    """.trimIndent()

    @Test
    fun latestIsParsedAndTheChecksumIsLowerCased() {
        val release = UpdateJson.parseRelease(latest)!!

        assertEquals("v1.2.0", release.tag)
        assertEquals("1.2.0", release.versionName)
        assertEquals(10200L, release.versionCode)
        assertEquals("- New: updates", release.changelog)
        assertEquals(5_000_000L, release.apk!!.size)
        assertEquals("abcdef", release.apk!!.sha256)
    }

    @Test
    fun aReleaseWithoutAnApkHasNoApk() {
        val release = UpdateJson.parseRelease(
            """{"tag":"v1.0.0","versionName":"1.0.0","versionCode":10000,"changelog":"","apk":null}""",
        )!!

        assertNull(release.apk)
    }

    @Test
    fun unusableAnswersAreRejected() {
        assertNull(UpdateJson.parseRelease("not json"))
        assertNull(UpdateJson.parseRelease("""{"error":"no_release"}"""))
        assertNull(UpdateJson.parseRelease("""{"tag":"v1","versionName":"1","versionCode":0}"""))
        assertNull(UpdateJson.parseRelease("""{"tag":"v1","versionName":"1","versionCode":"x"}"""))
    }

    @Test
    fun theHistoryAcceptsTheServersWrapperAndSkipsBadEntries() {
        val releases = UpdateJson.parseReleases(
            """{"releases":[$latest,{"nonsense":true},{"tag":"v1.0.0","versionName":"1.0.0","versionCode":10000}]}""",
        )

        assertEquals(listOf("v1.2.0", "v1.0.0"), releases.map { it.tag })
    }

    @Test
    fun theHistoryOfNothingIsEmpty() {
        assertTrue(UpdateJson.parseReleases(null).isEmpty())
        assertTrue(UpdateJson.parseReleases("").isEmpty())
        assertTrue(UpdateJson.parseReleases("{}").isEmpty())
        assertTrue(UpdateJson.parseReleases("garbage").isEmpty())
    }

    @Test
    fun whatIsStoredComesBackUnchanged() {
        val original = UpdateJson.parseReleases("""{"releases":[$latest]}""")

        val restored = UpdateJson.parseReleases(UpdateJson.toJson(original))

        assertEquals(original, restored)
    }
}
