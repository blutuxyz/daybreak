package com.bdersolutions.daybreak.data.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticSettingsTest {
    @Test
    fun defaultIsPremiumAndEverythingIsOn() {
        val settings = HapticSettings()
        assertEquals(HapticPreset.PREMIUM, settings.preset)
        assertTrue(HapticEvent.entries.all { settings.isEventActive(it) })
        assertEquals(HapticPreset.PREMIUM, settings.normalized().preset)
    }

    @Test
    fun masterSwitchSilencesEveryEvent() {
        val off = HapticSettings(enabled = false)
        assertTrue(HapticEvent.entries.none { off.isEventActive(it) })
    }

    @Test
    fun disabledCategoryAndDisabledEventBothSilence() {
        val settings = HapticSettings(
            disabledCategories = setOf(HapticCategory.NAVIGATION),
            disabledEvents = setOf(HapticEvent.TAP),
        )
        assertFalse(settings.isEventActive(HapticEvent.TAB_SWITCH))
        assertFalse(settings.isEventActive(HapticEvent.TAP))
        assertTrue(settings.isEventActive(HapticEvent.SELECT))
        assertEquals(0, settings.activeCount(HapticCategory.NAVIGATION))
        assertEquals(HapticEvent.inCategory(HapticCategory.TOUCH).size - 1, settings.activeCount(HapticCategory.TOUCH))
    }

    @Test
    fun gainMultipliesMasterCategoryAndEvent() {
        val settings = HapticSettings(
            intensity = 0.5f,
            categoryIntensity = mapOf(HapticCategory.CONTROLS to 0.5f),
            eventIntensity = mapOf(HapticEvent.STEP to 0.8f),
        )
        assertEquals(0.2f, settings.gainFor(HapticEvent.STEP), 1e-6f)
        assertEquals(0.25f, settings.gainFor(HapticEvent.SELECT), 1e-6f)
        assertEquals(0.5f, settings.gainFor(HapticEvent.TAB_SWITCH), 1e-6f)
    }

    @Test
    fun applyingAPresetKeepsTheMasterSwitchAndSystemFollow() {
        val off = HapticSettings(enabled = false, followSystem = false)
        HapticPreset.entries.forEach { preset ->
            val applied = off.withPreset(preset)
            assertFalse(applied.enabled)
            assertFalse(applied.followSystem)
        }
    }

    @Test
    fun applyingAPresetThenNormalizingKeepsThatPreset() {
        listOf(HapticPreset.PREMIUM, HapticPreset.SUBTLE, HapticPreset.PUNCHY, HapticPreset.MINIMAL).forEach { preset ->
            assertEquals(preset, HapticSettings().withPreset(preset).normalized().preset)
        }
    }

    @Test
    fun anyEditThatMatchesNoPresetBecomesCustom() {
        val edited = HapticSettings().withPreset(HapticPreset.PREMIUM).copy(intensity = 0.85f).normalized()
        assertEquals(HapticPreset.CUSTOM, edited.preset)
    }

    @Test
    fun editingBackToAPresetNamesItAgain() {
        val custom = HapticSettings().copy(intensity = 0.85f).normalized()
        assertEquals(HapticPreset.CUSTOM, custom.preset)
        assertEquals(HapticPreset.PREMIUM, custom.copy(intensity = 1f).normalized().preset)
    }

    @Test
    fun masterSwitchIsNotPartOfAPreset() {
        val off = HapticSettings().withPreset(HapticPreset.SUBTLE).copy(enabled = false).normalized()
        assertEquals(HapticPreset.SUBTLE, off.preset)
    }

    @Test
    fun explicitFullGainEqualsNoEntry() {
        val withFull = HapticSettings(categoryIntensity = mapOf(HapticCategory.TOUCH to 1f)).normalized()
        assertEquals(HapticPreset.PREMIUM, withFull.preset)
    }

    @Test
    fun minimalKeepsOnlyConfirmationsAndErrors() {
        val minimal = HapticSettings().withPreset(HapticPreset.MINIMAL)
        assertTrue(minimal.isEventActive(HapticEvent.ERROR))
        assertTrue(minimal.isEventActive(HapticEvent.REFRESH_DONE))
        assertFalse(minimal.isEventActive(HapticEvent.TAB_SWITCH))
        assertFalse(minimal.isEventActive(HapticEvent.STEP))
        assertFalse(minimal.isEventActive(HapticEvent.DEBUG_TAP))
    }

    @Test
    fun everyCategoryHasEvents() {
        HapticCategory.entries.forEach { assertTrue(HapticEvent.inCategory(it).isNotEmpty()) }
    }
}

class HapticSettingsCodecTest {
    @Test
    fun roundTripKeepsEverything() {
        val settings = HapticSettings(
            enabled = false,
            preset = HapticPreset.CUSTOM,
            intensity = 1.25f,
            texture = HapticTexture.SOFT,
            categoryIntensity = mapOf(HapticCategory.TOUCH to 0.4f),
            eventIntensity = mapOf(HapticEvent.ERROR to 1.5f),
            disabledCategories = setOf(HapticCategory.MOMENTS),
            disabledEvents = setOf(HapticEvent.TAP, HapticEvent.BACK),
            spacing = 1.5f,
            minGapMs = 60,
            followSystem = false,
        )
        assertEquals(settings, HapticSettingsCodec.decode(HapticSettingsCodec.encode(settings)))
    }

    @Test
    fun emptyOrBrokenInputGivesDefaults() {
        assertEquals(HapticSettings(), HapticSettingsCodec.decode(null))
        assertEquals(HapticSettings(), HapticSettingsCodec.decode(""))
        assertEquals(HapticSettings(), HapticSettingsCodec.decode("not json"))
        assertEquals(HapticSettings(), HapticSettingsCodec.decode("[1,2]"))
    }

    @Test
    fun missingFieldsFallBackToDefaultsInsteadOfNull() {
        val decoded = HapticSettingsCodec.decode("""{"intensity":0.5}""")
        assertEquals(0.5f, decoded.intensity, 0f)
        assertEquals(HapticSettings().texture, decoded.texture)
        assertEquals(HapticSettings().minGapMs, decoded.minGapMs)
        assertTrue(decoded.enabled)
    }

    @Test
    fun unknownNamesAreDroppedAndValuesClamped() {
        val decoded = HapticSettingsCodec.decode(
            """{"intensity":9,"spacing":0.01,"minGapMs":5000,"texture":"GRAVEL","preset":"NOPE",
               "categoryIntensity":{"TOUCH":3,"MARS":0.5,"NAVIGATION":null},
               "eventIntensity":{"STEP":-2,"WARP":1},
               "disabledEvents":["TAP","BOGUS",null],"disabledCategories":["STATUS","X"]}""",
        )
        assertEquals(HapticSettings.MAX_INTENSITY, decoded.intensity, 0f)
        assertEquals(HapticSettings.MIN_SPACING, decoded.spacing, 0f)
        assertEquals(HapticSettings.MAX_MIN_GAP_MS, decoded.minGapMs)
        assertEquals(HapticTexture.CRISP, decoded.texture)
        assertEquals(HapticPreset.PREMIUM, decoded.preset)
        assertEquals(mapOf(HapticCategory.TOUCH to HapticSettings.MAX_INTENSITY), decoded.categoryIntensity)
        assertEquals(mapOf(HapticEvent.STEP to 0f), decoded.eventIntensity)
        assertEquals(setOf(HapticEvent.TAP), decoded.disabledEvents)
        assertEquals(setOf(HapticCategory.STATUS), decoded.disabledCategories)
    }
}
