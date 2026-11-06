package com.bdersolutions.daybreak.desktop

import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ExamMatchTest {
    private val day = LocalDate.of(2026, 11, 10)

    private fun lesson(subject: String, start: Int, end: Int) = UntisLesson(
        id = start.toLong(),
        date = day,
        start = LocalTime.of(start, 0),
        end = LocalTime.of(end, 0),
        subject = subject,
    )

    private fun exam(subject: String, start: Int, end: Int) = UntisExam(
        id = 1,
        date = day,
        start = LocalTime.of(start, 0),
        end = LocalTime.of(end, 0),
        subject = subject,
    )

    @Test
    fun examMatchesOverlappingLessonOfTheSameSubject() {
        val maths = lesson("M", 8, 9)
        assertEquals("M", examFor(maths, listOf(exam("M", 8, 10)))?.subject)
    }

    @Test
    fun examOfAnotherSubjectOrTimeDoesNotMatch() {
        val maths = lesson("M", 8, 9)
        assertNull(examFor(maths, listOf(exam("E", 8, 10))))
        assertNull(examFor(maths, listOf(exam("M", 10, 11))))
    }

    @Test
    fun examWithoutSubjectMatchesAnyOverlappingLesson() {
        assertEquals("", examFor(lesson("D", 8, 9), listOf(exam("", 8, 9)))?.subject)
    }
}
