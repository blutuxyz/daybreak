package com.bdersolutions.daybreak.data.account

import com.bdersolutions.daybreak.data.settings.AccentColor
import com.bdersolutions.daybreak.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountParserTest {
    @Test
    fun `reads the account answer`() {
        val state = AccountParser.state(
            """{"accountId":"a1","plan":"plus","planUntil":"2030-01-01T00:00:00.000Z","grandfathered":true,
               "supporterExtras":true,"webuntis":[{"id":"w1","label":"Schule"}],
               "proTrial":{"offered":true,"accepted":false,"running":false,"until":null}}""",
        )!!
        assertEquals("a1", state.accountId)
        assertEquals(Plan.PLUS, state.plan)
        assertTrue(state.grandfathered)
        assertEquals(listOf(WebUntisSlot("w1", "Schule")), state.webuntis)
        assertTrue(state.proTrial.offered)
    }

    @Test
    fun `an unknown plan is free and a missing id is no account`() {
        assertEquals(Plan.FREE, AccountParser.state("""{"accountId":"a","plan":"platinum"}""")!!.plan)
        assertNull(AccountParser.state("""{"plan":"pro"}"""))
        assertNull(AccountParser.state("not json"))
        assertNull(AccountParser.state(null))
    }

    @Test
    fun `reads create, restore and bind answers`() {
        val created = AccountParser.created("""{"accountId":"a","plan":"free","restoreCode":"DA-K7M2-9QXP-4HTR","token":"t","legacy":"granted"}""")!!
        assertEquals("granted", created.legacy)
        assertEquals("t", created.token)
        assertNull(AccountParser.created("""{"accountId":"a","plan":"free"}"""))
        assertEquals("t2", AccountParser.signedIn("""{"accountId":"a","token":"t2"}""")!!.token)
        val bound = AccountParser.bound("""{"binding":{"id":"b","label":"L"},"sharedWithOthers":true,"account":{"accountId":"a","webuntis":[{"id":"b","label":"L"}]}}""")!!
        assertTrue(bound.sharedWithOthers)
        assertEquals("b", bound.slot.id)
    }

    @Test
    fun `maps server errors`() {
        assertEquals(AccountError.UNAUTHORIZED, AccountParser.errorFor(401, """{"error":"unauthorized"}"""))
        assertEquals(AccountError.UNKNOWN_CODE, AccountParser.errorFor(404, """{"error":"unknown_code"}"""))
        assertEquals(AccountError.LIMIT, AccountParser.errorFor(409, """{"error":"limit"}"""))
        assertEquals(AccountError.DUPLICATE, AccountParser.errorFor(409, """{"error":"duplicate"}"""))
        assertEquals(AccountError.NOT_OFFERED, AccountParser.errorFor(409, """{"error":"not_offered"}"""))
        assertEquals(AccountError.RATE_LIMITED, AccountParser.errorFor(429, null))
        assertEquals(AccountError.UNAVAILABLE, AccountParser.errorFor(500, null))
    }

    @Test
    fun `restore codes are normalised like the server does`() {
        assertEquals("DA-K7M2-9QXP-4HTR", AccountParser.normalizeRestoreCode(" da-k7m2 9qxp-4htr "))
        assertEquals("DA-K7M2-9QXP-4HTR", AccountParser.normalizeRestoreCode("DAK7M29QXP4HTR"))
        assertNull(AccountParser.normalizeRestoreCode("DA-K7M2-9QXP"))
        // 0, 1, I, L and O are not in the alphabet
        assertNull(AccountParser.normalizeRestoreCode("DA-K7M2-9QXP-4HT0"))
        assertNull(AccountParser.normalizeRestoreCode(null))
    }
}

class EntitlementTest {
    @Test
    fun `the plan table`() {
        val free = Access(Plan.FREE)
        val plus = Access(Plan.PLUS)
        val pro = Access(Plan.PRO)
        Entitlement.entries.forEach { assertFalse(free.allows(it)) }
        assertTrue(plus.allows(Entitlement.APP_NAME))
        assertTrue(plus.allows(Entitlement.CALENDAR_EXPORT))
        assertTrue(plus.allows(Entitlement.WIDGET_CUSTOMISATION))
        assertTrue(plus.allows(Entitlement.SYNC))
        assertFalse(plus.allows(Entitlement.DAWN_REASONING))
        assertFalse(plus.allows(Entitlement.HAPTICS_STUDIO))
        Entitlement.entries.forEach { assertTrue(pro.allows(it)) }
    }

    @Test
    fun `old supporters keep only the supporter extras`() {
        val supporter = Access(Plan.FREE, supporter = true)
        assertTrue(supporter.allows(Entitlement.APP_NAME))
        assertTrue(supporter.allows(Entitlement.APP_ICON))
        assertTrue(supporter.allows(Entitlement.CALENDAR_EXPORT))
        assertFalse(supporter.allows(Entitlement.WIDGET_CUSTOMISATION))
        assertFalse(supporter.allows(Entitlement.AMOLED))
    }

    @Test
    fun `locked appearance settings fall back to the free choice`() {
        val chosen = AppSettings(accent = AccentColor.ROSE, amoledBlack = true)
        val free = Access(Plan.FREE).apply(chosen)
        assertEquals(AccentColor.OCEAN, free.accent)
        assertFalse(free.amoledBlack)
        assertEquals(chosen, Access(Plan.PRO).apply(chosen))
    }
}
