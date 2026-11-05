package com.bdersolutions.daybreak.data.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportPromptPolicyTest {
    /** A state that has been opened on [days] different days, one per day starting at day 100. */
    private fun opened(days: Int, start: Long = 100): SupportState {
        var state = SupportState()
        repeat(days) { state = SupportPromptPolicy.afterOpen(state, start + it) }
        return state
    }

    @Test
    fun theKofiLinkIsTheProfile() {
        assertEquals("https://ko-fi.com/blutuxyz", SupportLinks.KOFI_URL)
    }

    @Test
    fun neverShownToSomeoneWhoHasNotOpenedTheAppYet() {
        assertFalse(SupportPromptPolicy.shouldShow(SupportState(), today = 500))
    }

    @Test
    fun firstOpenRecordsTheDayAndCountsIt() {
        val state = SupportPromptPolicy.afterOpen(SupportState(), 100)
        assertEquals(100L, state.firstSeenDay)
        assertEquals(1, state.openDays)
    }

    @Test
    fun openingTwiceOnTheSameDayCountsOnce() {
        var state = SupportPromptPolicy.afterOpen(SupportState(), 100)
        state = SupportPromptPolicy.afterOpen(state, 100)
        assertEquals(1, state.openDays)
        assertEquals(100L, state.firstSeenDay)
    }

    @Test
    fun notShownBeforeEnoughDaysOfUse() {
        val state = opened(SupportPromptPolicy.MIN_OPEN_DAYS - 1)
        assertFalse(SupportPromptPolicy.shouldShow(state, today = 106))
    }

    @Test
    fun notShownTooSoonEvenWithManyOpens() {
        // Seven different days of use, but the app was installed less than a week ago.
        val state = SupportState(firstSeenDay = 100, openDays = 7, lastOpenDay = 103)
        assertFalse(SupportPromptPolicy.shouldShow(state, today = 103))
        assertTrue(SupportPromptPolicy.shouldShow(state, today = 107))
    }

    @Test
    fun shownAfterEnoughDaysAndTime() {
        // Opened on days 100..106, so the app is 7 days old on day 107.
        val state = opened(SupportPromptPolicy.MIN_OPEN_DAYS)
        assertFalse(SupportPromptPolicy.shouldShow(state, today = 106))
        assertTrue(SupportPromptPolicy.shouldShow(state, today = 107))
    }

    @Test
    fun afterShowingItWaitsAMonth() {
        val shown = SupportPromptPolicy.afterShown(opened(10), today = 110)
        assertEquals(1, shown.shownCount)
        assertFalse(SupportPromptPolicy.shouldShow(shown, today = 110))
        assertFalse(SupportPromptPolicy.shouldShow(shown, today = 110 + SupportPromptPolicy.REPEAT_AFTER_DAYS - 1))
        assertTrue(SupportPromptPolicy.shouldShow(shown, today = 110 + SupportPromptPolicy.REPEAT_AFTER_DAYS))
    }

    @Test
    fun neverMoreThanThreeTimes() {
        var state = opened(10)
        var day = 110L
        repeat(SupportPromptPolicy.MAX_PROMPTS) {
            assertTrue(SupportPromptPolicy.shouldShow(state, day))
            state = SupportPromptPolicy.afterShown(state, day)
            day += SupportPromptPolicy.REPEAT_AFTER_DAYS
        }
        assertFalse(SupportPromptPolicy.shouldShow(state, day + 1000))
    }

    @Test
    fun optingOutStopsItForGood() {
        val state = SupportPromptPolicy.afterOptOut(opened(10))
        assertTrue(state.optedOut)
        assertFalse(SupportPromptPolicy.shouldShow(state, today = 10_000))
    }

    @Test
    fun aClockSetBackDoesNotShowItEarly() {
        val shown = SupportPromptPolicy.afterShown(opened(10), today = 110)
        assertFalse(SupportPromptPolicy.shouldShow(shown, today = 50))
    }
}
