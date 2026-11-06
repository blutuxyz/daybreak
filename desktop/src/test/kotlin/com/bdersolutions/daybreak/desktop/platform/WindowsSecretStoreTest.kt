package com.bdersolutions.daybreak.desktop.platform

import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WindowsSecretStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun keyIsProtectedByDpapiAndReadBack() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val store = WindowsSecretStore(folder.root)
        assertNull(store.getStoredSecret())
        assertTrue(store.storeSecret("MOBILE-KEY-XYZ"))
        assertEquals("MOBILE-KEY-XYZ", WindowsSecretStore(folder.root).getStoredSecret())
        val onDisk = folder.root.resolve("secret.dpapi").readBytes()
        assertFalse(String(onDisk, Charsets.UTF_8).contains("MOBILE-KEY-XYZ"))
        store.clear()
        assertNull(WindowsSecretStore(folder.root).getStoredSecret())
    }
}
