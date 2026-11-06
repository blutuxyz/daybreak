package com.bdersolutions.daybreak.desktop.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** The answer of /v1/latest, as the update server sends it for a release with a Windows installer. */
private const val COMPLETE = """
{
  "tag": "v1.2.3",
  "versionName": "1.2.3",
  "versionCode": 10203,
  "publishedAt": "2026-11-06T10:00:00Z",
  "changelog": "- Neu: Automatische Updates",
  "apk": { "url": "https://updates.example.com/v1/apk/v1.2.3", "size": 100, "sha256": "aa" },
  "windows": {
    "msi": {
      "url": "https://updates.example.com/v1/msi/v1.2.3",
      "name": "Daybreak-1.2.3.msi",
      "size": 4096,
      "sha256": "ABCDEF0123"
    }
  }
}
"""

class ParseLatestTest {
    @Test
    fun aCompleteAnswerBecomesAReleaseWithTheInstaller() {
        val release = parseLatest(COMPLETE)

        assertEquals("v1.2.3", release.tag)
        assertEquals("1.2.3", release.versionName)
        assertEquals(10203, release.versionCode)
        assertEquals("- Neu: Automatische Updates", release.changelog)
        assertEquals("https://updates.example.com/v1/msi/v1.2.3", release.msi.url)
        assertEquals("Daybreak-1.2.3.msi", release.msi.name)
        assertEquals(4096L, release.msi.size)
        // The checksum is compared in lower case, as the server writes it.
        assertEquals("abcdef0123", release.msi.sha256)
    }

    @Test
    fun aReleaseWithoutAWindowsBuildIsNotOffered() {
        // A release whose APK is out but whose installer is still being built.
        val apkOnly = """{"tag": "v1.2.3", "versionName": "1.2.3", "versionCode": 10203, "changelog": ""}"""

        val error = assertThrows(IllegalStateException::class.java) { parseLatest(apkOnly) }

        assertEquals("The newest release has no Windows build yet.", error.message)
    }

    @Test
    fun anInstallerWithoutAnAddressIsNotOffered() {
        val noUrl = COMPLETE.replace("\"url\": \"https://updates.example.com/v1/msi/v1.2.3\",", "")

        assertThrows(IllegalStateException::class.java) { parseLatest(noUrl) }
    }

    @Test
    fun anInstallerWithoutAChecksumHasNoChecksum() {
        // The downloader refuses to install without a checksum, so the parser only has to report it.
        val noSha = COMPLETE.replace(",\n      \"sha256\": \"ABCDEF0123\"", "")

        assertNull(parseLatest(noSha).msi.sha256)
    }
}
