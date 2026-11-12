package com.bdersolutions.daybreak.data.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DawnNoticeTest {
    @Test
    fun everyoneBelowTheNoticeVersionSeesItEvenAfterAnEarlierDismissal() {
        // Before this version nothing was stored under the version key, so the notice is still due.
        assertTrue(DawnNotice.needed(0))
        assertTrue(DawnNotice.needed(DawnNotice.VERSION - 1))
        assertFalse(DawnNotice.needed(DawnNotice.VERSION))
    }

    @Test
    fun aFreshInstallIsToldApartFromAnUpdate() {
        assertTrue(DawnNotice.isFreshInstall(firstInstallTime = 100L, lastUpdateTime = 100L))
        assertFalse(DawnNotice.isFreshInstall(firstInstallTime = 100L, lastUpdateTime = 200L))
        assertEquals(2, DawnNotice.VERSION)
    }
}
