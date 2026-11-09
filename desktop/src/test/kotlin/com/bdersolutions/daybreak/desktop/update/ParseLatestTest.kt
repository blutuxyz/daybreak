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
    "installer": {
      "url": "https://updates.example.com/v1/setup/v1.2.3",
      "name": "Daybreak-1.2.3-setup.exe",
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
        assertEquals("https://updates.example.com/v1/setup/v1.2.3", release.installer.url)
        assertEquals("Daybreak-1.2.3-setup.exe", release.installer.name)
        assertEquals(4096L, release.installer.size)
        // The checksum is compared in lower case, as the server writes it.
        assertEquals("abcdef0123", release.installer.sha256)
    }

    @Test
    fun aReleaseWithoutAWindowsBuildIsNotOffered() {
        // A release whose APK is out but whose installer is still being built.
        val apkOnly = """{"tag": "v1.2.3", "versionName": "1.2.3", "versionCode": 10203, "changelog": ""}"""

        val error = assertThrows(IllegalStateException::class.java) { parseLatest(apkOnly) }

        assertEquals("The newest release has no Windows build yet.", error.message)
    }

    @Test
    fun theVersionCodeIsReadWithoutNeedingAWindowsBuild() {
        val apkOnly = """{"tag": "v1.5.1", "versionName": "1.5.1", "versionCode": 10501, "windows": null}"""

        assertEquals(10501, latestVersionCode(apkOnly))
        assertEquals(null, latestVersionCode("not json"))
        assertEquals(null, latestVersionCode("{}"))
    }

    @Test
    fun aReleaseWithOnlyTheOldMsiIsNotOffered() {
        // Releases before the one-click setup carry only an MSI, which this version cannot install.
        val msiOnly = COMPLETE.replace("\"installer\": {", "\"msi\": {")

        assertThrows(IllegalStateException::class.java) { parseLatest(msiOnly) }
    }

    @Test
    fun theSetupRunsSilentlyWithoutRebootOrDialogs() {
        assertEquals(listOf("/SILENT", "/SUPPRESSMSGBOXES", "/NORESTART"), SILENT_SETUP_ARGUMENTS.toList())
    }

    @Test
    fun anInstallerWithoutAnAddressIsNotOffered() {
        val noUrl = COMPLETE.replace("\"url\": \"https://updates.example.com/v1/setup/v1.2.3\",", "")

        assertThrows(IllegalStateException::class.java) { parseLatest(noUrl) }
    }

    @Test
    fun anInstallerWithoutAChecksumHasNoChecksum() {
        // The downloader refuses to install without a checksum, so the parser only has to report it.
        val noSha = COMPLETE.replace(",\n      \"sha256\": \"ABCDEF0123\"", "")

        assertNull(parseLatest(noSha).installer.sha256)
    }
}

class SetupCommandTest {
    @org.junit.Test
    fun theSetupStartsDetachedAndSilent() {
        val command = setupCommand("C:/Users/a b/Daybreak-2.0.0-setup.exe")
        org.junit.Assert.assertEquals(listOf("cmd.exe", "/c", "start", "\"\""), command.take(4))
        org.junit.Assert.assertEquals("C:/Users/a b/Daybreak-2.0.0-setup.exe", command[4])
        org.junit.Assert.assertEquals(listOf("/SILENT", "/SUPPRESSMSGBOXES", "/NORESTART"), command.drop(5))
    }
}
