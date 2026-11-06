package com.bdersolutions.daybreak.desktop.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildInfoTest {
    @Test
    fun versionCodeFollowsTheServerFormula() {
        assertEquals(10203, BuildInfo.versionCodeOf("1.2.3"))
        assertEquals(20000, BuildInfo.versionCodeOf("2.0.0"))
        assertTrue(BuildInfo.versionCodeOf("1.10.0") > BuildInfo.versionCodeOf("1.9.9"))
    }

    @Test
    fun updatesNeedAnUpdateServerAddress() {
        assertFalse(BuildInfo("1.0.0", "").updatesEnabled)
        assertTrue(BuildInfo("1.0.0", "https://updates.example.com").updatesEnabled)
    }
}
