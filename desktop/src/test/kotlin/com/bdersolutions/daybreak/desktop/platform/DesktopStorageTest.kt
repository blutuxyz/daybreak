package com.bdersolutions.daybreak.desktop.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopStorageTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun propertiesSurviveAReopen() {
        val file = folder.root.resolve("settings.properties")
        val first = PropertiesStore(file)
        assertTrue(first.edit().putString("language", "de").putBoolean("sync", false).putLong("id", 42L).commit())

        val second = PropertiesStore(file)
        assertEquals("de", second.getString("language"))
        assertFalse(second.getBoolean("sync", true))
        assertEquals(42L, second.getLong("id", 0L))
        assertEquals(7, second.getInt("missing", 7))
    }

    @Test
    fun removeAndClearDeleteValues() {
        val file = folder.root.resolve("session.properties")
        val store = PropertiesStore(file)
        store.edit().putString("a", "1").putString("b", "2").commit()
        store.edit().remove("a").commit()
        assertNull(PropertiesStore(file).getString("a"))
        assertEquals("2", PropertiesStore(file).getString("b"))
        store.edit().clear().commit()
        assertNull(PropertiesStore(file).getString("b"))
    }

    @Test
    fun textFileRoundTripsAndDeletes() {
        val file = DesktopTextFile(folder.root.resolve("cache.json"))
        assertNull(file.read())
        assertTrue(file.write("{\"weeks\":[]}"))
        assertEquals("{\"weeks\":[]}", file.read())
        file.delete()
        assertNull(file.read())
    }

    @Test
    fun secretIsEncryptedOnDiskAndReadBack() {
        val store = DesktopSecretStore(folder.root)
        assertNull(store.getStoredSecret())
        assertTrue(store.storeSecret("MOBILE-KEY-123"))
        assertEquals("MOBILE-KEY-123", DesktopSecretStore(folder.root).getStoredSecret())
        val onDisk = folder.root.resolve("secret.bin").readText()
        assertFalse(onDisk.contains("MOBILE-KEY-123"))
        store.clear()
        assertNull(DesktopSecretStore(folder.root).getStoredSecret())
    }
}
