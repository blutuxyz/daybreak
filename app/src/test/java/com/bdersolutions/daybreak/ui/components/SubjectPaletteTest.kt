package com.bdersolutions.daybreak.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SubjectPaletteTest {

    // Daybreak's default primary (#235A9C), as an OKLCH hue.
    private val oceanPrimaryHue = srgbToOkLch(
        0x23 / 255.0,
        0x5A / 255.0,
        0x9C / 255.0,
    ).hueDegrees

    @Test
    fun subjectHashIsStableAcrossCalls() {
        assertEquals(stableSubjectHash("Mathematik"), stableSubjectHash("Mathematik"))
        assertEquals(stableSubjectHash("Deutsch"), stableSubjectHash("DEUTSCH"))
    }

    @Test
    fun subjectHueIsDeterministicAndWithinRange() {
        val first = subjectHueDegrees("Physik", oceanPrimaryHue)
        assertEquals(first, subjectHueDegrees("Physik", oceanPrimaryHue), 0.0)
        assertTrue(first >= 0.0 && first < 360.0)
    }

    @Test
    fun slotsSpreadAroundTheAccentHue() {
        val hues = (0 until SUBJECT_HUE_SLOTS).map { slot ->
            (oceanPrimaryHue + slot * 360.0 / SUBJECT_HUE_SLOTS) % 360.0
        }
        // Consecutive slots are 45 degrees apart, so no two slots collide.
        hues.zipWithNext { a, b ->
            val gap = ((b - a) + 360.0) % 360.0
            assertEquals(45.0, gap, 1e-6)
        }
    }

    @Test
    fun srgbRoundTripsThroughOkLch() {
        val original = doubleArrayOf(0.2, 0.55, 0.85)
        val lch = srgbToOkLch(original[0], original[1], original[2])
        val back = oklchToSrgb(lch)
        for (i in original.indices) {
            assertEquals(original[i], back[i], 1e-3)
        }
    }

    @Test
    fun stripeColoursMeetNonTextContrastOnWhite() {
        // WCAG 1.4.11: a stripe is a meaningful graphic, so it needs 3:1 against its card.
        val white = luminance(1.0, 1.0, 1.0)
        for (slot in 0 until SUBJECT_HUE_SLOTS) {
            val hue = (oceanPrimaryHue + slot * 360.0 / SUBJECT_HUE_SLOTS) % 360.0
            val rgb = oklchToSrgb(OkLch(lightness = 0.56, chroma = 0.14, hueDegrees = hue))
            val stripe = luminance(rgb[0], rgb[1], rgb[2])
            val contrast = (white + 0.05) / (stripe + 0.05)
            assertTrue("slot $slot contrast was $contrast", contrast >= 3.0)
        }
    }

    @Test
    fun lightnessRoundTripKeepsGreyAchromatic() {
        val grey = srgbToOkLch(0.5, 0.5, 0.5)
        assertTrue(abs(grey.chroma) < 1e-6)
    }

    private fun luminance(r: Double, g: Double, b: Double): Double {
        fun linear(c: Double): Double = if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }
}
