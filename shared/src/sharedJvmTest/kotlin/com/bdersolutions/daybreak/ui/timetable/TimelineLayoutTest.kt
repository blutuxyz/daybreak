package com.bdersolutions.daybreak.ui.timetable

import com.bdersolutions.daybreak.untis.UntisLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimelineLayoutTest {
    private val day = LocalDate.of(2026, 11, 5)

    private fun block(id: Long, start: String, end: String): TimelineBlock {
        val lesson = UntisLesson(
            id = id,
            date = day,
            start = LocalTime.parse(start),
            end = LocalTime.parse(end),
            subject = "S$id",
        )
        return TimelineBlock(listOf(lesson), lesson.start, lesson.end)
    }

    /** The most blocks of [blocks] that run at one moment. */
    private fun busiest(blocks: List<TimelineBlock>): Int =
        blocks.maxOfOrNull { b -> blocks.count { it.start <= b.start && b.start < it.end } } ?: 0

    @Test
    fun twoAtOnceAreAllShownAndNothingOverflows() {
        val layout = layoutTimeline(listOf(block(1, "07:55", "08:45"), block(2, "07:55", "08:45")))

        assertEquals(2, layout.visible.size)
        assertTrue(layout.overflows.isEmpty())
        assertTrue(layout.visible.all { it.columnCount == 2 })
    }

    @Test
    fun sixAtOnceShowTwoAndOneChipForTheOtherFour() {
        val blocks = (1L..6L).map { block(it, "07:55", "08:45") }

        val layout = layoutTimeline(blocks)

        assertEquals(2, layout.visible.size)
        assertTrue(layout.visible.all { it.columnCount == 2 })
        assertEquals(1, layout.overflows.size)
        assertEquals(4, layout.overflows.single().hidden.size)
        // The list holds all six, shown or not.
        assertEquals(6, layout.overflows.single().concurrent.size)
    }

    @Test
    fun theHighestPriorityBlocksStayVisible() {
        val blocks = (1L..5L).map { block(it, "07:55", "08:45") }

        val layout = layoutTimeline(blocks, priority = { if (it.lesson.id == 4L || it.lesson.id == 5L) 9 else 1 })

        assertEquals(setOf(4L, 5L), layout.visible.map { it.lesson.id }.toSet())
    }

    @Test
    fun noMomentEverHasMoreThanTwoVisibleBlocks() {
        // A long block over several short ones that overlap each other in different ways.
        val blocks = listOf(
            block(1, "07:55", "13:15"),
            block(2, "07:55", "08:45"),
            block(3, "07:55", "09:40"),
            block(4, "08:45", "09:40"),
            block(5, "08:45", "10:30"),
            block(6, "09:50", "11:25"),
            block(7, "10:00", "12:15"),
            block(8, "10:40", "11:25"),
        )

        val layout = layoutTimeline(blocks)

        assertTrue(busiest(layout.visible) <= 2)
        assertTrue(layout.visible.all { it.columnCount <= 2 })
        // Nothing is lost: what is not drawn is reported.
        val reported = layout.overflows.flatMap { it.hidden }.map { it.lesson.id }
        assertEquals(blocks.map { it.lesson.id }.toSet(), (layout.visible.map { it.lesson.id } + reported).toSet())
        assertEquals(blocks.size, layout.visible.size + reported.size)
        assertEquals(blocks.size, layout.all.size)
    }

    @Test
    fun hiddenBlocksThatDoNotTouchGetTheirOwnChip() {
        val blocks = listOf(
            block(1, "07:55", "08:45"), block(2, "07:55", "08:45"), block(3, "07:55", "08:45"),
            block(4, "10:40", "11:30"), block(5, "10:40", "11:30"), block(6, "10:40", "11:30"),
        )

        val layout = layoutTimeline(blocks)

        assertEquals(2, layout.overflows.size)
        assertEquals(LocalTime.parse("07:55"), layout.overflows[0].start)
        assertEquals(LocalTime.parse("10:40"), layout.overflows[1].start)
    }

    @Test
    fun lessonsInARowAreNotMistakenForOverlap() {
        val blocks = listOf(
            block(1, "07:55", "08:45"),
            block(2, "08:45", "09:35"),
            block(3, "09:35", "10:25"),
        )

        val layout = layoutTimeline(blocks)

        assertTrue(layout.overflows.isEmpty())
        assertTrue(layout.visible.all { it.columnCount == 1 })
    }
}
