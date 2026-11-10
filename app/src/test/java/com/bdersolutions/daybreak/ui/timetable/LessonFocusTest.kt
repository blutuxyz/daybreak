package com.bdersolutions.daybreak.ui.timetable

import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonFocusTest {
    private val day = LocalDate.of(2026, 11, 10)

    private fun lesson(id: Long, start: String, end: String, subject: String, status: LessonStatus = LessonStatus.REGULAR) =
        UntisLesson(id, day, LocalTime.parse(start), LocalTime.parse(end), subject, status = status)

    @Test
    fun findsTheExamsLessonByTimeAndSubject() {
        val lessons = listOf(
            lesson(1, "10:40", "11:30", "D"),
            lesson(2, "11:35", "12:25", "SPORT", LessonStatus.CANCELLED),
            lesson(3, "11:35", "12:25", "E1"),
            lesson(4, "12:25", "13:15", "M"),
        )
        val exam = LessonFocus(day, LocalTime.of(11, 35), LocalTime.of(12, 25), "E1")
        assertEquals(3L, exam.match(lessons)?.id)
        assertEquals("no subject: the lesson that is held then", 3L, exam.copy(subject = "").match(lessons)?.id)
        assertNull("nothing runs then", exam.copy(start = LocalTime.of(15, 0), end = LocalTime.of(16, 0)).match(lessons))
        assertNull("another day", exam.copy(date = day.plusDays(1)).match(lessons))
    }
}
