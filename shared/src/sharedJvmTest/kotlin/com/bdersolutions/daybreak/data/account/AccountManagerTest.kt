package com.bdersolutions.daybreak.data.account

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountManagerTest {
    private val api = FakeAccountApi()
    private val storage = FakeAccountStorage()
    private var now = 1_000_000_000_000L
    private val manager = AccountManager(api, storage) { now }

    @Test
    fun `a new device has no account and the free plan`() {
        val snapshot = manager.snapshot.value
        assertFalse(snapshot.signedIn)
        assertEquals(Plan.FREE, snapshot.access.plan)
        assertFalse(snapshot.access.allows(Entitlement.APP_NAME))
    }

    @Test
    fun `migration sends the old install id and Ko-fi code and then drops the legacy flag`() = runBlocking {
        storage.legacyData = LegacySupport(paid = true, unlockCode = "DB-ABCD-EFGH")
        api.legacyAnswer = "granted"
        manager.tick()
        // Until the account exists the old flag still unlocks the supporter extras.
        assertTrue(manager.snapshot.value.access.allows(Entitlement.CALENDAR_EXPORT))

        val result = manager.create(installId = "install-1")

        assertTrue(result is ApiResult.Ok)
        assertEquals("install-1", api.createdWithInstallId)
        assertEquals("DB-ABCD-EFGH", api.createdWithLegacyCode)
        assertEquals(LegacySupport(), storage.legacyData)
        val snapshot = manager.snapshot.value
        assertTrue(snapshot.signedIn)
        assertEquals("DA-K7M2-9QXP-4HTR", snapshot.credentials?.restoreCode)
        // Grandfathered by the server: the supporter extras stay, Pro things do not.
        assertTrue(snapshot.access.allows(Entitlement.APP_ICON))
        assertFalse(snapshot.access.allows(Entitlement.AMOLED))
        assertTrue(snapshot.state!!.proTrial.offered)
    }

    @Test
    fun `an unpaid old code does not unlock anything once the server has answered`() = runBlocking {
        storage.legacyData = LegacySupport(paid = true, unlockCode = "DB-ABCD-EFGH")
        api.legacyAnswer = "unpaid"
        manager.create("install-1")
        assertFalse(manager.snapshot.value.access.allows(Entitlement.APP_NAME))
    }

    @Test
    fun `deleting the account removes it on the server and signs this device out`() = runBlocking {
        manager.restore("DA-K7M2-9QXP-4HTR")
        assertTrue(manager.snapshot.value.signedIn)

        assertTrue(manager.deleteAccount() is ApiResult.Ok)
        assertTrue(api.deleted)
        assertFalse(manager.snapshot.value.signedIn)
        assertNull(storage.stored)
    }

    @Test
    fun `a failed deletion keeps the device signed in`() = runBlocking {
        manager.restore("DA-K7M2-9QXP-4HTR")
        api.offline = true

        assertTrue(manager.deleteAccount() is ApiResult.Fail)
        assertFalse(api.deleted)
        assertTrue(manager.snapshot.value.signedIn)
    }

    @Test
    fun `restoring signs in with a code typed in any form`() = runBlocking {
        val result = manager.restore("da k7m2 9qxp 4htr")
        assertTrue(result is ApiResult.Ok)
        assertEquals("DA-K7M2-9QXP-4HTR", storage.stored?.restoreCode)
        assertTrue(manager.snapshot.value.signedIn)
    }

    @Test
    fun `a wrong restore code fails without touching the device`() = runBlocking {
        val bad = manager.restore("DA-AAAA-AAAA-AAAA")
        assertEquals(ApiResult.Fail(AccountError.UNKNOWN_CODE), bad)
        assertEquals(ApiResult.Fail(AccountError.UNKNOWN_CODE), manager.restore("nonsense"))
        assertFalse(manager.snapshot.value.signedIn)
    }

    @Test
    fun `the plan comes from the server and survives being offline`() = runBlocking {
        manager.create("i")
        api.plan = Plan.PLUS
        assertNull(manager.refresh())
        assertTrue(manager.snapshot.value.access.allows(Entitlement.WIDGET_CUSTOMISATION))
        assertFalse(manager.snapshot.value.access.allows(Entitlement.DAWN_DRAFTS))

        api.offline = true
        assertEquals(AccountError.OFFLINE, manager.refresh())
        assertEquals(Plan.PLUS, manager.snapshot.value.access.plan)
    }

    @Test
    fun `a plan whose end has passed counts as free even from the cache`() = runBlocking {
        manager.create("i")
        api.plan = Plan.PRO
        api.planUntil = "2001-09-09T01:46:40Z" // 1_000_000_000 s
        manager.refresh()
        now = 1_000_000_000_001L
        manager.tick()
        assertEquals(Plan.FREE, manager.snapshot.value.access.plan)
        now = 999_999_999_999L
        manager.tick()
        assertEquals(Plan.PRO, manager.snapshot.value.access.plan)
    }

    @Test
    fun `a revoked token is renewed from the kept restore code`() = runBlocking {
        manager.create("i")
        api.validToken = "other" // the server forgot this device
        assertNull(manager.refresh())
        // restore() made a new token on the fake server and the device kept it
        assertTrue(manager.snapshot.value.signedIn)
        assertTrue(storage.stored!!.token.startsWith("token-"))
    }

    @Test
    fun `a revoked token with a code the server no longer knows signs the device out`() = runBlocking {
        manager.create("i")
        api.validToken = "other"
        api.restoreCode = "DA-ZZZZ-ZZZZ-ZZZZ"
        assertEquals(AccountError.UNAUTHORIZED, manager.refresh())
        assertFalse(manager.snapshot.value.signedIn)
        assertEquals(Plan.FREE, manager.snapshot.value.access.plan)
    }

    @Test
    fun `at most three WebUntis accounts and unbinding frees a slot`() = runBlocking {
        manager.create("i")
        repeat(3) { assertTrue(manager.bind("s", "school", 100L + it, "Konto $it") is ApiResult.Ok) }
        assertEquals(3, manager.snapshot.value.state!!.webuntis.size)
        assertEquals(ApiResult.Fail(AccountError.LIMIT), manager.bind("s", "school", 999L, "Viertes"))
        assertTrue(manager.unbind("slot2") is ApiResult.Ok)
        assertEquals(2, manager.snapshot.value.state!!.webuntis.size)
        assertTrue(manager.bind("s", "school", 999L, "Viertes") is ApiResult.Ok)
    }

    @Test
    fun `account calls without an account fail as unauthorized`() = runBlocking {
        assertEquals(ApiResult.Fail(AccountError.UNAUTHORIZED), manager.bind("s", "sc", 1L, "x"))
        assertEquals(ApiResult.Fail(AccountError.UNAUTHORIZED), manager.acceptTrial())
        assertEquals(0, api.calls)
    }

    @Test
    fun `accepting the trial turns on Pro and ends the offer`() = runBlocking {
        storage.legacyData = LegacySupport(paid = true, unlockCode = "DB-ABCD-EFGH")
        api.legacyAnswer = "granted"
        manager.create("i")
        assertTrue(manager.acceptTrial() is ApiResult.Ok)
        val snapshot = manager.snapshot.value
        assertEquals(Plan.PRO, snapshot.access.plan)
        assertFalse(snapshot.state!!.proTrial.offered)
        assertEquals(ApiResult.Fail(AccountError.NOT_OFFERED), manager.acceptTrial())
    }

    @Test
    fun `checkout and portal need an account and a subscription`() = runBlocking {
        assertEquals(ApiResult.Fail(AccountError.UNAUTHORIZED), manager.checkoutUrl(Plan.PLUS))
        manager.create("i")
        assertEquals(ApiResult.Ok("https://pay.example/pro"), manager.checkoutUrl(Plan.PRO))
        assertEquals(ApiResult.Fail(AccountError.NO_SUBSCRIPTION), manager.portalUrl())
        api.hasSubscription = true
        assertEquals(ApiResult.Ok("https://pay.example/portal"), manager.portalUrl())
    }

    @Test
    fun `signing out keeps the account on the server but not on the device`() = runBlocking {
        manager.create("i")
        api.plan = Plan.PRO
        manager.refresh()
        manager.signOut()
        assertFalse(manager.snapshot.value.signedIn)
        assertEquals(Plan.FREE, manager.snapshot.value.access.plan)
        assertTrue(manager.restore("DA-K7M2-9QXP-4HTR") is ApiResult.Ok)
        manager.refresh()
        assertEquals(Plan.PRO, manager.snapshot.value.access.plan)
    }

    @Test
    fun `trial offer policy shows a popup once then a two week banner`() {
        val day = 24L * 60 * 60 * 1000
        assertEquals(TrialOfferPolicy.Prompt.NONE, TrialOfferPolicy.prompt(false, null, 0))
        assertEquals(TrialOfferPolicy.Prompt.POPUP, TrialOfferPolicy.prompt(true, null, 0))
        assertEquals(TrialOfferPolicy.Prompt.BANNER, TrialOfferPolicy.prompt(true, 1000, 1000 + 13 * day))
        assertEquals(TrialOfferPolicy.Prompt.NONE, TrialOfferPolicy.prompt(true, 1000, 1000 + 14 * day))
        // Taking the offer (the server stops offering it) ends the reminder at once.
        assertEquals(TrialOfferPolicy.Prompt.NONE, TrialOfferPolicy.prompt(false, 1000, 1000 + day))
    }
}
