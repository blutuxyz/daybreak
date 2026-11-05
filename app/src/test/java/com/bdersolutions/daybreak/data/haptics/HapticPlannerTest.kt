package com.bdersolutions.daybreak.data.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPlannerTest {
    private val premium = HapticSettings()

    @Test
    fun everyEventHasADesign() {
        HapticEvent.entries.forEach { event ->
            val plan = HapticPlanner.plan(event, premium)
            assertTrue("$event has no pulses", plan.isNotEmpty())
            assertTrue(plan.all { it.scale in 0f..1f && it.delayMs >= 0 })
        }
    }

    @Test
    fun premiumPlansTheDesignedFeel() {
        val toggleOn = HapticPlanner.plan(HapticEvent.TOGGLE_ON, premium)
        assertEquals(listOf(HapticPrimitive.LOW_TICK, HapticPrimitive.CLICK), toggleOn.map { it.primitive })
        assertEquals(0.5f, toggleOn[0].scale, 1e-6f)
        assertEquals(45, toggleOn[1].delayMs)
        assertEquals(1, HapticPlanner.plan(HapticEvent.TOGGLE_OFF, premium).size)
    }

    @Test
    fun errorsAreTheOnlyDoubleThud() {
        val thuds = HapticEvent.entries.filter { event ->
            HapticPlanner.plan(event, premium).count { it.primitive == HapticPrimitive.THUD } >= 2
        }
        assertEquals(listOf(HapticEvent.ERROR), thuds)
    }

    @Test
    fun intensityScalesEveryPulse() {
        val half = HapticPlanner.plan(HapticEvent.ERROR, premium.copy(intensity = 0.5f))
        assertEquals(0.4f, half[0].scale, 1e-6f)
        assertEquals(0.3f, half[1].scale, 1e-6f)
    }

    @Test
    fun boostNeverGoesOverFullPower() {
        val boosted = HapticPlanner.plan(HapticEvent.DEBUG_UNLOCK, premium.copy(intensity = 1.5f, texture = HapticTexture.PUNCHY))
        assertTrue(boosted.all { it.scale <= 1f })
        assertEquals(1f, boosted[0].scale, 0f)
    }

    @Test
    fun pulsesTooWeakToFeelAreDropped() {
        assertTrue(HapticPlanner.plan(HapticEvent.TAP, premium.copy(intensity = 0.05f)).isEmpty())
        // Of a two-pulse effect, the weaker pulse can disappear while the stronger one stays.
        val plan = HapticPlanner.plan(HapticEvent.SHEET_OPEN, premium.copy(intensity = 0.12f))
        assertEquals(1, plan.size)
        assertEquals(HapticPrimitive.CLICK, plan[0].primitive)
    }

    @Test
    fun zeroIntensityPlaysNothing() {
        HapticEvent.entries.forEach {
            assertTrue(HapticPlanner.plan(it, premium.copy(intensity = 0f)).isEmpty())
        }
    }

    @Test
    fun categoryAndEventGainsApply() {
        val settings = premium.copy(
            categoryIntensity = mapOf(HapticCategory.NAVIGATION to 0.5f),
            eventIntensity = mapOf(HapticEvent.TAB_SWITCH to 0.5f),
        )
        assertEquals(0.55f * 0.25f, HapticPlanner.plan(HapticEvent.TAB_SWITCH, settings)[0].scale, 1e-6f)
        assertEquals(0.5f * 0.5f, HapticPlanner.plan(HapticEvent.BACK, settings)[0].scale, 1e-6f)
    }

    @Test
    fun spacingStretchesPausesButNotTheFirstPulse() {
        val tight = HapticPlanner.plan(HapticEvent.REFRESH_DONE, premium.copy(spacing = 0.5f))
        val wide = HapticPlanner.plan(HapticEvent.REFRESH_DONE, premium.copy(spacing = 2f))
        assertEquals(0, tight[0].delayMs)
        assertEquals(30, tight[1].delayMs)
        assertEquals(120, wide[1].delayMs)
    }

    @Test
    fun softMovesPrimitivesGentlerAndPunchyHarder() {
        val soft = HapticPlanner.plan(HapticEvent.REFRESH_PULL, premium.copy(texture = HapticTexture.SOFT))
        assertEquals(HapticPrimitive.TICK, soft[0].primitive)
        assertEquals(0.6f * 0.85f, soft[0].scale, 1e-6f)

        val punchy = HapticPlanner.plan(HapticEvent.TAB_SWITCH, premium.copy(texture = HapticTexture.PUNCHY))
        assertEquals(HapticPrimitive.CLICK, punchy[0].primitive)
        assertEquals(0.55f * 1.15f, punchy[0].scale, 1e-6f)

        // Rises keep their shape in every texture.
        listOf(HapticTexture.SOFT, HapticTexture.CRISP, HapticTexture.PUNCHY).forEach { texture ->
            val login = HapticPlanner.plan(HapticEvent.LOGIN_SUCCESS, premium.copy(texture = texture))
            assertEquals(HapticPrimitive.QUICK_RISE, login[0].primitive)
        }
    }

    @Test
    fun fractionFadesAnEventButNeverBelowAFloor() {
        val full = HapticPlanner.plan(HapticEvent.DEBUG_TAP, premium, fraction = 1f)[0].scale
        val first = HapticPlanner.plan(HapticEvent.DEBUG_TAP, premium, fraction = 0.1f)[0].scale
        val none = HapticPlanner.plan(HapticEvent.DEBUG_TAP, premium, fraction = -5f)[0].scale
        assertTrue(first < full)
        assertTrue(none in 0.2f..first)
        assertEquals(0.8f, full, 1e-6f)
    }

    @Test
    fun presetsFeelDifferentFromEachOther() {
        fun loudness(preset: HapticPreset): Float {
            val settings = HapticSettings().withPreset(preset)
            return HapticEvent.entries.filter { settings.isEventActive(it) }
                .sumOf { event -> HapticPlanner.plan(event, settings).sumOf { it.scale.toDouble() } }.toFloat()
        }
        assertTrue(loudness(HapticPreset.SUBTLE) < loudness(HapticPreset.PREMIUM))
        assertTrue(loudness(HapticPreset.PREMIUM) < loudness(HapticPreset.PUNCHY))
        assertTrue(loudness(HapticPreset.MINIMAL) < loudness(HapticPreset.PREMIUM))
    }

    @Test
    fun capabilityTiers() {
        assertEquals(HapticCapabilities.Tier.NONE, HapticCapabilities.None.tier)
        assertEquals(HapticCapabilities.Tier.ADVANCED, HapticCapabilities(true, true, 8, 8).tier)
        assertEquals(HapticCapabilities.Tier.BASIC, HapticCapabilities(true, true, 0, 8).tier)
        assertEquals(HapticCapabilities.Tier.MINIMAL, HapticCapabilities(true, false, 0, 8).tier)
        assertFalse(HapticCapabilities.None.hasVibrator)
    }
}

class HapticsEngineTest {
    private class FakeOutput : HapticOutput {
        override val capabilities = HapticCapabilities(true, true, 8, 8)
        val played = mutableListOf<List<HapticStep>>()
        var cancelled = 0
        override fun play(steps: List<HapticStep>) {
            played += steps
        }
        override fun cancel() {
            cancelled++
        }
    }

    private var now = 0L

    private fun engine(
        output: FakeOutput,
        settings: HapticSettings = HapticSettings(),
        system: Boolean = true,
    ) = Haptics(settings = { settings }, output = output, systemAllows = { system }, clock = { now })

    @Test
    fun playsAnActiveEvent() {
        val output = FakeOutput()
        engine(output).perform(HapticEvent.ERROR)
        assertEquals(1, output.played.size)
        assertEquals(2, output.played[0].size)
    }

    @Test
    fun skipsWhenMasterSwitchEventOrCategoryIsOff() {
        val off = FakeOutput()
        engine(off, HapticSettings(enabled = false)).perform(HapticEvent.ERROR)
        val event = FakeOutput()
        engine(event, HapticSettings(disabledEvents = setOf(HapticEvent.ERROR))).perform(HapticEvent.ERROR)
        val category = FakeOutput()
        engine(category, HapticSettings(disabledCategories = setOf(HapticCategory.STATUS))).perform(HapticEvent.ERROR)
        assertTrue(off.played.isEmpty() && event.played.isEmpty() && category.played.isEmpty())
    }

    @Test
    fun followsTheSystemSwitchOnlyWhenAsked() {
        val follows = FakeOutput()
        engine(follows, HapticSettings(followSystem = true), system = false).perform(HapticEvent.ERROR)
        val ignores = FakeOutput()
        engine(ignores, HapticSettings(followSystem = false), system = false).perform(HapticEvent.ERROR)
        assertTrue(follows.played.isEmpty())
        assertEquals(1, ignores.played.size)
    }

    @Test
    fun repeatLimitHoldsBackFastEventsButNotConfirmations() {
        val output = FakeOutput()
        val haptics = engine(output, HapticSettings(minGapMs = 40))
        haptics.perform(HapticEvent.STEP)
        now = 10
        haptics.perform(HapticEvent.STEP)
        assertEquals("second tick within the limit is dropped", 1, output.played.size)
        now = 15
        haptics.perform(HapticEvent.ERROR)
        assertEquals("errors are never dropped", 2, output.played.size)
        now = 100
        haptics.perform(HapticEvent.STEP)
        assertEquals(3, output.played.size)
    }

    @Test
    fun aDroppedEventDoesNotExtendTheLimit() {
        val output = FakeOutput()
        val haptics = engine(output, HapticSettings(minGapMs = 40))
        now = 1_000
        haptics.perform(HapticEvent.STEP)
        now = 1_030
        haptics.perform(HapticEvent.STEP) // dropped
        now = 1_045
        haptics.perform(HapticEvent.STEP) // 45 ms after the one that played
        assertEquals(2, output.played.size)
    }

    @Test
    fun zeroGapNeverThrottles() {
        val output = FakeOutput()
        val haptics = engine(output, HapticSettings(minGapMs = 0))
        repeat(5) { haptics.perform(HapticEvent.STEP) }
        assertEquals(5, output.played.size)
    }

    @Test
    fun previewIgnoresSwitchesSystemAndLimitButKeepsTheStrength() {
        val output = FakeOutput()
        val off = HapticSettings(
            enabled = false,
            followSystem = true,
            disabledEvents = setOf(HapticEvent.STEP),
            intensity = 0.5f,
        )
        val haptics = engine(output, off, system = false)
        haptics.preview(HapticEvent.STEP)
        haptics.preview(HapticEvent.STEP)
        assertEquals(2, output.played.size)
        assertEquals(0.45f * 0.5f, output.played[0][0].scale, 1e-6f)
    }

    @Test
    fun previewCanUseSettingsThatAreNotStoredYet() {
        val output = FakeOutput()
        engine(output, HapticSettings(intensity = 1f)).preview(HapticEvent.STEP, HapticSettings(intensity = 0.2f))
        assertEquals(0.45f * 0.2f, output.played[0][0].scale, 1e-6f)
    }

    @Test
    fun noneIsSilentAndSafe() {
        Haptics.None.perform(HapticEvent.ERROR)
        Haptics.None.preview(HapticEvent.ERROR)
        Haptics.None.cancel()
        assertEquals(HapticCapabilities.Tier.NONE, Haptics.None.capabilities.tier)
    }
}
