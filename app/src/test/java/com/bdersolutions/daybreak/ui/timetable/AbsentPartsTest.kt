package com.bdersolutions.daybreak.ui.timetable

import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AbsentPartsTest {
    private val day = LocalDate.of(2026, 11, 2)

    private fun block(start: String, end: String, status: LessonStatus = LessonStatus.REGULAR): TimelineBlock {
        val lesson = UntisLesson(
            id = 1,
            date = day,
            start = LocalTime.parse(start),
            end = LocalTime.parse(end),
            subject = "ETE",
            status = status,
        )
        return TimelineBlock(listOf(lesson), lesson.start, lesson.end)
    }

    private fun absence(start: String, end: String) = UntisAbsence(
        id = 1,
        start = LocalDateTime.parse(start),
        end = LocalDateTime.parse(end),
        excused = false,
        reasonId = null,
        reasonName = "",
        text = "",
    )

    @Test
    fun aShortAbsenceGreysOnlyItsPartOfTheLesson() {
        // 07:55 to 08:05 of a 07:55 to 08:45 lesson is the first fifth.
        val parts = absentParts(block("07:55", "08:45"), listOf(absence("2026-11-02T07:55", "2026-11-02T08:05")))

        assertEquals(1, parts.size)
        assertEquals(0f, parts[0].start, 0.001f)
        assertEquals(0.2f, parts[0].endInclusive, 0.001f)
    }

    @Test
    fun anAbsenceThatEndsLaterOnlyCoversTheLessonsTail() {
        val parts = absentParts(block("07:55", "08:45"), listOf(absence("2026-11-02T08:20", "2026-11-02T12:00")))

        assertEquals(0.5f, parts.single().start, 0.001f)
        assertEquals(1f, parts.single().endInclusive, 0.001f)
    }

    @Test
    fun aMultiDayAbsenceCoversAWholeLessonInside() {
        val parts = absentParts(block("09:50", "10:40"), listOf(absence("2026-11-01T07:55", "2026-11-03T17:30")))

        assertEquals(0f, parts.single().start, 0.001f)
        assertEquals(1f, parts.single().endInclusive, 0.001f)
    }

    @Test
    fun anAbsenceOutsideTheLessonGreysNothing() {
        val before = absence("2026-11-02T07:00", "2026-11-02T07:55")
        val otherDay = absence("2026-11-03T07:55", "2026-11-03T08:45")

        assertTrue(absentParts(block("07:55", "08:45"), listOf(before, otherDay)).isEmpty())
    }

    @Test
    fun aCancelledLessonIsNotGreyedOut() {
        val parts = absentParts(
            block("07:55", "08:45", LessonStatus.CANCELLED),
            listOf(absence("2026-11-02T07:55", "2026-11-02T08:45")),
        )

        assertTrue(parts.isEmpty())
    }
}
