package com.bdersolutions.daybreak.desktop

import com.bdersolutions.daybreak.data.account.AccountCredentials
import com.bdersolutions.daybreak.data.account.AccountError
import com.bdersolutions.daybreak.data.account.AccountState
import com.bdersolutions.daybreak.data.account.ApiResult
import com.bdersolutions.daybreak.data.account.LegacySupport
import com.bdersolutions.daybreak.data.account.Plan
import com.bdersolutions.daybreak.data.account.ProTrial
import com.bdersolutions.daybreak.data.account.WebUntisSlot
import com.bdersolutions.daybreak.platform.KeyValueEditor
import com.bdersolutions.daybreak.platform.SecretStore
import com.bdersolutions.daybreak.platform.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

/** A secret store in memory. */
private class MemorySecret(private val failToStore: Boolean = false) : SecretStore {
    var secret: String? = null
    override fun getStoredSecret(): String? = secret
    override fun storeSecret(secret: String): Boolean {
        if (failToStore) return false
        this.secret = secret
        return true
    }
    override fun clear() { secret = null }
}

class TermsAndAccountTest {
    @Test
    fun theAccountSurvivesARestartAndSigningOutClearsIt() {
        val prefs = MemoryStore()
        val vault = MemorySecret()
        val storage = DesktopAccountStorage(prefs, vault, MemoryStore())
        assertNull(storage.credentials())

        storage.saveCredentials(AccountCredentials("acc1", "token-1", "DA-K7M2-9QXP-4HTR"))
        storage.saveState(AccountState("acc1", Plan.PRO, planUntil = "2027-01-01T00:00:00Z", webuntis = listOf(WebUntisSlot("s1", "Schule")), proTrial = ProTrial(offered = true)))

        val again = DesktopAccountStorage(prefs, vault, MemoryStore())
        assertEquals(AccountCredentials("acc1", "token-1", "DA-K7M2-9QXP-4HTR"), again.credentials())
        val state = again.cachedState()!!
        assertEquals(Plan.PRO, state.plan)
        assertEquals("Schule", state.webuntis.single().label)
        assertTrue(state.proTrial.offered)
        // The token and the code live only in the vault, never in the plain preferences.
        assertFalse(prefs.values.values.any { it.toString().contains("token-1") || it.toString().contains("DA-K7M2") })

        again.saveCredentials(null)
        assertNull(again.credentials())
        assertNull(vault.secret)
    }

    @Test
    fun aVaultThatCannotStoreLeavesTheComputerSignedOut() {
        val storage = DesktopAccountStorage(MemoryStore(), MemorySecret(failToStore = true), MemoryStore())
        storage.saveCredentials(AccountCredentials("acc1", "token-1", null))
        assertNull(storage.credentials())
    }

    @Test
    fun theOldKofiDataIsReadOnceAndThenCleared() {
        val old = MemoryStore()
        old.edit().putBoolean("paid", true).putString("unlock_code", "DB-K7M2-9QXP").putLong("first_seen_day", 19_000L).commit()
        val storage = DesktopAccountStorage(MemoryStore(), MemorySecret(), old)

        assertTrue(storage.hasLegacyTraces())
        assertEquals(LegacySupport(paid = true, unlockCode = "DB-K7M2-9QXP"), storage.legacy())

        storage.clearLegacy()
        assertEquals(LegacySupport(), storage.legacy())
        assertFalse(storage.hasLegacyTraces())
    }

    @Test
    fun aFreshInstallHasNoLegacyTraces() {
        assertFalse(DesktopAccountStorage(MemoryStore(), MemorySecret(), MemoryStore()).hasLegacyTraces())
    }

    @Test
    fun theFlowPrefsKeepTheInstallIdAndDecideLegacyOnlyOnce() {
        val store = MemoryStore()
        val prefs = DesktopAccountPrefs(store)
        val id = prefs.installId()
        assertTrue(id.length >= 16)
        assertEquals(id, DesktopAccountPrefs(store).installId())

        assertTrue(prefs.isLegacyInstall { true })
        // Decided once: a later answer of the detector changes nothing.
        assertTrue(DesktopAccountPrefs(store).isLegacyInstall { false })

        val fresh = DesktopAccountPrefs(MemoryStore())
        assertFalse(fresh.isLegacyInstall { false })
        assertFalse(fresh.isLegacyInstall { true })
    }

    @Test
    fun theTrialDismissalIsRememberedAndShownAsAFlow() {
        val store = MemoryStore()
        val prefs = DesktopAccountPrefs(store)
        assertNull(prefs.trialDismissed.value)
        prefs.trialDismissedAt = 5_000L
        assertEquals(5_000L, prefs.trialDismissed.value)
        assertEquals(5_000L, DesktopAccountPrefs(store).trialDismissedAt)
    }

    @Test
    fun aBuildWithoutAServerCannotReachTheAccount() = kotlinx.coroutines.runBlocking {
        val result = DesktopOfflineAccountApi.create(null, null)
        assertTrue(result is ApiResult.Fail && result.error == AccountError.UNAVAILABLE)
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
