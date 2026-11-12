package com.bdersolutions.daybreak.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PillBarGeometryTest {
    // A phone bar at 2.75x: 56 dp slots, labels of about 60 to 100 dp.
    private val width = 858f
    private val collapsed = 154f
    private val expanded = floatArrayOf(330f, 440f, 300f)

    private fun frame(vararg expansion: Float) = pillBarFrame(width, collapsed, expanded, expansion)

    @Test
    fun atRestThePillCoversTheSelectedSlot() {
        for (selected in 0..2) {
            val f = frame(*FloatArray(3) { if (it == selected) 1f else 0f })
            assertEquals(expanded[selected], f.widths[selected], 0.001f)
            assertEquals(f.lefts[selected], f.indicatorLeft, 0.001f)
            assertEquals(f.lefts[selected] + f.widths[selected], f.indicatorRight, 0.001f)
            for (i in 0..2) if (i != selected) assertEquals(collapsed, f.widths[i], 0.001f)
        }
    }

    @Test
    fun theSlotsFillTheBarEdgeToEdge() {
        val f = frame(0.3f, 0f, 0.7f)
        assertEquals(0f, f.lefts[0], 0.001f)
        assertEquals(width, f.lefts[2] + f.widths[2], 0.01f)
        assertTrue(f.lefts[1] >= f.lefts[0] + f.widths[0])
        assertTrue(f.lefts[2] >= f.lefts[1] + f.widths[1])
    }

    @Test
    fun thePillGlidesWithoutJumps() {
        // Switching from the first to the last tab: the expansions are 1 - s and s.
        var previous = frame(1f, 0f, 0f)
        for (step in 1..200) {
            val s = step / 200f
            val f = frame(1f - s, 0f, s)
            assertTrue(abs(f.indicatorLeft - previous.indicatorLeft) < 8f)
            assertTrue(abs(f.indicatorRight - previous.indicatorRight) < 8f)
            assertTrue(f.indicatorRight - f.indicatorLeft >= collapsed - 0.01f)
            previous = f
        }
        assertEquals(previous.lefts[2], previous.indicatorLeft, 0.001f)
        assertEquals(width, previous.indicatorRight, 0.01f)
    }

    @Test
    fun anOpenSlotNeverPushesTheOthersOut() {
        val narrow = pillBarFrame(500f, collapsed, floatArrayOf(800f, 300f, 300f), floatArrayOf(1f, 0f, 0f))
        assertEquals(500f - 2 * collapsed, narrow.widths[0], 0.001f)
        assertEquals(500f, narrow.lefts[2] + narrow.widths[2], 0.01f)
    }

    @Test
    fun overshootIsClamped() {
        val f = frame(1.02f, -0.02f, 0f)
        assertEquals(expanded[0], f.widths[0], 0.001f)
        assertEquals(collapsed, f.widths[1], 0.001f)
    }

    @Test
    fun noExpansionMeansNoPill() {
        assertFalse(frame(0f, 0f, 0f).indicatorShown)
    }
}
