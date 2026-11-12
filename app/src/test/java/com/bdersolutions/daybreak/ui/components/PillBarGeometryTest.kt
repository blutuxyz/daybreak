package com.bdersolutions.daybreak.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PillBarGeometryTest {
    // A 360 dp phone at 3.75x: 336 dp inside the bar, 56 dp slots with 24 dp icons, real label widths.
    private val scale = 3.75f
    private val width = 336f * scale
    private val collapsed = 56f * scale
    private val inset = 16f * scale
    private val icon = 24f * scale
    private val expanded = floatArrayOf(147f * scale, 169f * scale, 101f * scale)

    private fun frame(vararg expansion: Float) = pillBarFrame(width, collapsed, inset, expanded, expansion)

    private fun selected(index: Int) = frame(*FloatArray(3) { if (it == index) 1f else 0f })

    /** The visible pieces from left to right: the bar's edges, the pill and the icons inside their slots. */
    private fun visibleGaps(f: PillBarFrame, open: Int): List<Float> {
        val pieces = (0..2).map { i ->
            if (i == open) f.lefts[i] to f.lefts[i] + f.widths[i]
            else (f.lefts[i] + inset) to (f.lefts[i] + inset + icon)
        }
        val gaps = mutableListOf<Float>()
        if (open != 0) gaps += pieces.first().first
        for (i in 0 until 2) gaps += pieces[i + 1].first - pieces[i].second
        if (open != 2) gaps += width - pieces.last().second
        return gaps
    }

    @Test
    fun atRestThePillCoversTheSelectedSlot() {
        for (index in 0..2) {
            val f = selected(index)
            assertEquals(expanded[index], f.widths[index], 0.001f)
            assertEquals(f.lefts[index], f.indicatorLeft, 0.001f)
            assertEquals(f.lefts[index] + f.widths[index], f.indicatorRight, 0.001f)
            for (i in 0..2) if (i != index) assertEquals(collapsed, f.widths[i], 0.001f)
        }
    }

    @Test
    fun everyVisibleGapIsTheSame() {
        for (index in 0..2) {
            val gaps = visibleGaps(selected(index), index)
            assertEquals(if (index == 1) 4 else 3, gaps.size)
            gaps.forEach { assertEquals("tab $index: $gaps", gaps.first(), it, 0.01f) }
        }
    }

    @Test
    fun aPillAtAnEndLiesFlushWithTheBar() {
        assertEquals(0f, selected(0).lefts[0], 0.01f)
        val last = selected(2)
        assertEquals(width, last.lefts[2] + last.widths[2], 0.01f)
    }

    @Test
    fun aPillInTheMiddleIsCentred() {
        val f = selected(1)
        assertEquals(width / 2f, f.lefts[1] + f.widths[1] / 2f, 0.01f)
    }

    @Test
    fun theMiddleIconStaysNearTheCentreOfItsSpace() {
        // With the first tab open, the middle icon sits halfway between the pill and the last icon.
        val f = selected(0)
        val pillEnd = f.lefts[0] + f.widths[0]
        val middle = f.lefts[1] + collapsed / 2f
        val lastIconStart = f.lefts[2] + inset
        assertEquals(middle - icon / 2f - pillEnd, lastIconStart - (middle + icon / 2f), 0.01f)
    }

    @Test
    fun onAPhoneTheSlotsNeverLeaveTheBarOrOverlap() {
        for (step in 0..100) {
            val s = step / 100f
            for (f in listOf(frame(1f - s, s, 0f), frame(1f - s, 0f, s), frame(0f, 1f - s, s))) {
                assertTrue(f.lefts[0] >= -0.01f)
                assertTrue(f.lefts[2] + f.widths[2] <= width + 0.01f)
                assertTrue(f.lefts[1] >= f.lefts[0] + f.widths[0] - 0.01f)
                assertTrue(f.lefts[2] >= f.lefts[1] + f.widths[1] - 0.01f)
            }
        }
    }

    @Test
    fun thePillGlidesWithoutJumps() {
        // Switching from the first to the last tab: the expansions are 1 - s and s.
        var previous = frame(1f, 0f, 0f)
        for (step in 1..200) {
            val s = step / 200f
            val f = frame(1f - s, 0f, s)
            assertTrue(abs(f.indicatorLeft - previous.indicatorLeft) < 10f)
            assertTrue(abs(f.indicatorRight - previous.indicatorRight) < 10f)
            for (i in 0..2) assertTrue(abs(f.lefts[i] - previous.lefts[i]) < 10f)
            assertTrue(f.indicatorRight - f.indicatorLeft >= collapsed - 0.01f)
            previous = f
        }
        assertEquals(previous.lefts[2], previous.indicatorLeft, 0.001f)
        assertEquals(width, previous.indicatorRight, 0.01f)
    }

    @Test
    fun aLongLabelIsCutBeforeItCrowdsTheIcons() {
        val narrow = 900f
        val f = pillBarFrame(narrow, collapsed, inset, floatArrayOf(800f, 300f, 300f), floatArrayOf(1f, 0f, 0f))
        assertEquals(narrow - 2 * icon - 4 * inset / 2f, f.widths[0], 0.001f)
        assertEquals(0f, f.lefts[0], 0.01f)
        val lastIconEnd = f.lefts[2] + inset + icon
        val gap = (f.lefts[1] + inset) - (f.lefts[0] + f.widths[0])
        assertTrue(gap >= inset / 2f - 0.01f)
        assertEquals(gap, narrow - lastIconEnd, 0.01f)
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
