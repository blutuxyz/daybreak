package com.bdersolutions.daybreak.desktop

import com.bdersolutions.daybreak.platform.KeyValueEditor
import com.bdersolutions.daybreak.platform.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** A key-value store in memory, so the tests need no files. */
private class MemoryStore : KeyValueStore {
    val values = mutableMapOf<String, Any?>()

    override fun getString(key: String, default: String?): String? = (values[key] as? String) ?: default
    override fun getBoolean(key: String, default: Boolean): Boolean = (values[key] as? Boolean) ?: default
    override fun getInt(key: String, default: Int): Int = (values[key] as? Int) ?: default
    override fun getLong(key: String, default: Long): Long = (values[key] as? Long) ?: default

    override fun edit(): KeyValueEditor = object : KeyValueEditor {
        override fun putString(key: String, value: String?): KeyValueEditor = apply { values[key] = value }
        override fun putBoolean(key: String, value: Boolean): KeyValueEditor = apply { values[key] = value }
        override fun putInt(key: String, value: Int): KeyValueEditor = apply { values[key] = value }
        override fun putLong(key: String, value: Long): KeyValueEditor = apply { values[key] = value }
        override fun remove(key: String): KeyValueEditor = apply { values.remove(key) }
        override fun clear(): KeyValueEditor = apply { values.clear() }
        override fun apply() = Unit
        override fun commit(): Boolean = true
    }
}

class SupportAndTermsTest {
    /** A confirmed payment is remembered, stops the popup for good and survives a restart. */
    @Test
    fun aConfirmedPaymentIsRememberedAndStopsThePopup() {
        val prefs = MemoryStore()
        val store = DesktopSupportStore(prefs)
        assertFalse(store.paid.value)

        store.markPaid()

        assertTrue(store.paid.value)
        assertTrue(store.state().paid)
        assertTrue(DesktopSupportStore(prefs).paid.value)
        assertFalse(com.bdersolutions.daybreak.data.support.SupportPromptPolicy.shouldShow(store.state(), 10_000L))
    }

    @Test
    fun theUnlockCodeIsValidAndStaysTheSame() {
        val prefs = MemoryStore()
        val first = DesktopSupportStore(prefs).unlockCode()

        assertTrue(com.bdersolutions.daybreak.data.support.SupportCode.isValid(first))
        assertEquals(first, DesktopSupportStore(prefs).unlockCode())
    }

    @Test
    fun newerTermsAskAgainAndOlderAcceptanceIsAnUpdate() {
        val prefs = MemoryStore()
        val terms = DesktopTermsStore(prefs)
        assertFalse(terms.isAccepted())
        assertFalse(terms.isUpdate())

        prefs.values["accepted_version"] = LEGAL_VERSION - 1
        val older = DesktopTermsStore(prefs)
        assertFalse(older.isAccepted())
        assertTrue(older.isUpdate())

        older.accept(now = 1L)
        assertTrue(older.isAccepted())
        assertFalse(older.isUpdate())
    }

    @Test
    fun rawLogKeepsTheLatestTwentyAndSurvivesARestart() {
        val dir: File = Files.createTempDirectory("daybreak-raw").toFile()
        val file = File(dir, "raw_api_responses.json")
        val log = DesktopRawLog(file)
        repeat(25) { index -> log.onResponse("method$index", 1_000L + index, "body $index") }
        assertEquals(20, log.entries.value.size)
        assertEquals("method24", log.entries.value.first().method)

        val reloaded = DesktopRawLog(file)
        assertEquals(20, reloaded.entries.value.size)
        assertEquals("body 24", reloaded.entries.value.first().body)

        reloaded.clear()
        assertTrue(reloaded.entries.value.isEmpty())
        dir.deleteRecursively()
    }

    @Test
    fun debugPageUnlocksAfterTenSystemThemeTaps() {
        val unlock = DebugUnlock()
        repeat(9) { unlock.tapSystemTheme() }
        assertFalse(unlock.unlocked.value)
        unlock.tapSystemTheme()
        assertTrue(unlock.unlocked.value)
    }
}
