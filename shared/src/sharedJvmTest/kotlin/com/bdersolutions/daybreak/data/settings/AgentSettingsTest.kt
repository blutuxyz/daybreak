package com.bdersolutions.daybreak.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSettingsTest {
    private val base = AppSettings()

    @Test
    fun aValidChangeIsAppliedAndReported() {
        val plan = AgentSettings.plan(base, mapOf("appearance.accent" to "forest"), paid = false)
        assertTrue(plan.accepted)
        assertEquals(AccentColor.FOREST, plan.settings?.accent)
        assertEquals(listOf(SettingChange("appearance.accent", "OCEAN", "FOREST")), plan.changes)
    }

    @Test
    fun anUnknownPathOrAWrongValueChangesNothing() {
        val plan = AgentSettings.plan(base, mapOf("appearance.accent" to "FOREST", "widget.backgroundOpacity" to "150", "nope.x" to "1"), paid = false)
        assertFalse(plan.accepted)
        assertNull(plan.settings)
        assertEquals(2, plan.errors.size)
    }

    @Test
    fun supporterExtrasNeedAPayment() {
        assertFalse(AgentSettings.plan(base, mapOf("app.customName" to "Mein Plan"), paid = false).accepted)
        val paid = AgentSettings.plan(base, mapOf("app.customName" to "Mein Plan"), paid = true)
        assertEquals("Mein Plan", paid.settings?.customAppName)
    }

    @Test
    fun everyHapticEventHasASwitchAndAStrength() {
        for (event in com.bdersolutions.daybreak.data.haptics.HapticEvent.entries) {
            assertNotNull(AgentSettings.find("haptics.event.${event.name}.enabled"))
            assertNotNull(AgentSettings.find("haptics.event.${event.name}.intensity"))
        }
    }

    @Test
    fun aSingleHapticChangeMakesThePresetCustom() {
        val plan = AgentSettings.plan(base, mapOf("haptics.intensity" to "0.5"), paid = false)
        assertEquals(0.5f, plan.settings?.haptics?.intensity ?: -1f, 0.0001f)
        assertEquals(com.bdersolutions.daybreak.data.haptics.HapticPreset.CUSTOM, plan.settings?.haptics?.preset)
    }

    @Test
    fun undoingPutsTheOldValuesBack() {
        val changed = AgentSettings.plan(base, mapOf("widget.theme" to "DARK", "timetable.dayScale" to "120"), paid = false)
        val back = AgentSettings.plan(
            changed.settings!!,
            changed.changes.associate { it.path to it.from },
            paid = false,
        )
        assertTrue(back.accepted)
        assertEquals(base, back.settings)
    }

    @Test
    fun coloursTakeHexOrAuto() {
        val set = AgentSettings.plan(base, mapOf("lessonColors.subject.Mathe" to "#3366ff"), paid = false)
        assertEquals(0xFF3366FFL, set.settings?.lessonColors?.subjectColors?.get("Mathe"))
        val cleared = AgentSettings.plan(set.settings!!, mapOf("lessonColors.subject.Mathe" to "auto"), paid = false)
        assertNull(cleared.settings?.lessonColors?.subjectColors?.get("Mathe"))
        assertFalse(AgentSettings.plan(base, mapOf("lessonColors.exam" to "red"), paid = false).accepted)
    }
}
