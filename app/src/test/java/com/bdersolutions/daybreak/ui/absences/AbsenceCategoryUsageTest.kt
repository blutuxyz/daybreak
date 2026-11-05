package com.bdersolutions.daybreak.ui.absences

import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisLesson
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AbsenceCategoryUsageTest {
    private val monday = LocalDate.of(2026, 10, 19)

    @Test
    fun fullDayAbsenceCountsEveryLessonOfTheSubject() {
        // 9 x 50 minutes of WEPT in a row, absent the whole day = 9.
        val lessons = (0 until 9).map { i ->
            lesson(id = i.toLong(), start = LocalTime.of(7, 55).plusMinutes(50L * i))
        }
        val absence = absence(at(monday, 7, 55), at(monday, 17, 30), excused = true)

        val usage = countMissedLessons(listOf(absence), lessons, "WEPT")

        assertEquals(9, usage.total)
        assertEquals(9, usage.excused)
        assertEquals(0, usage.unexcused)
    }

    @Test
    fun heldLessonsGiveTheMissedPercentage() {
        // Four WEPT lessons are held, one more is cancelled and one is another subject.
        val lessons = (0 until 4).map { i -> lesson(id = i.toLong(), start = LocalTime.of(8, 0).plusMinutes(50L * i)) } +
            lesson(id = 10, start = LocalTime.of(14, 0), status = LessonStatus.CANCELLED) +
            lesson(id = 11, subject = "MA", start = LocalTime.of(15, 0))
        val absence = absence(at(monday, 8, 0), at(monday, 8, 50))

        val usage = countMissedLessons(listOf(absence), lessons, "WEPT")

        assertEquals(4, usage.held)
        assertEquals(1, usage.total)
        assertEquals(25.0, usage.percent, 0.001)
        // Without absences the lessons are still counted, and no lessons gives 0 %, not NaN.
        assertEquals(4, countMissedLessons(emptyList(), lessons, "WEPT").held)
        assertEquals(0.0, percentOf(0, 0), 0.0)
    }

    @Test
    fun otherSubjectsAreIgnored() {
        val lessons = listOf(lesson(id = 1, subject = "WEPT"), lesson(id = 2, subject = "MA"))
        val absence = absence(at(monday, 7, 0), at(monday, 18, 0))

        assertEquals(1, countMissedLessons(listOf(absence), lessons, "WEPT").total)
    }

    @Test
    fun subjectMatchIsCaseInsensitiveAndSplitsCommaJoinedSubjects() {
        val lessons = listOf(lesson(id = 1, subject = "Mat, WEPT"))
        val absence = absence(at(monday, 7, 0), at(monday, 18, 0))

        assertEquals(1, countMissedLessons(listOf(absence), lessons, "wept").total)
        assertEquals(1, countMissedLessons(listOf(absence), lessons, "Mat").total)
    }

    @Test
    fun cancelledLessonsAreNotMissed() {
        val lessons = listOf(
            lesson(id = 1, start = LocalTime.of(8, 0)),
            lesson(id = 2, start = LocalTime.of(9, 0), status = LessonStatus.CANCELLED),
        )
        val absence = absence(at(monday, 7, 0), at(monday, 18, 0))

        assertEquals(1, countMissedLessons(listOf(absence), lessons, "WEPT").total)
    }

    @Test
    fun multiDayAbsenceCountsLessonsOnEachDay() {
        val tuesday = monday.plusDays(1)
        val lessons = listOf(
            lesson(id = 1, date = monday),
            lesson(id = 2, date = tuesday),
            lesson(id = 3, date = tuesday.plusDays(1)),
        )
        val absence = absence(at(monday, 7, 55), at(tuesday, 17, 30))

        assertEquals(2, countMissedLessons(listOf(absence), lessons, "WEPT").total)
    }

    @Test
    fun partialOverlapCountsTheLesson() {
        val lessons = listOf(lesson(id = 1, start = LocalTime.of(8, 0))) // 08:00 - 08:50
        val absence = absence(at(monday, 8, 40), at(monday, 12, 0))

        assertEquals(1, countMissedLessons(listOf(absence), lessons, "WEPT").total)
    }

    @Test
    fun touchingIntervalsDoNotOverlap() {
        val lessons = listOf(lesson(id = 1, start = LocalTime.of(13, 15)))
        val absence = absence(at(monday, 11, 35), at(monday, 13, 15))

        assertEquals(0, countMissedLessons(listOf(absence), lessons, "WEPT").total)
    }

    @Test
    fun overlappingAbsencesCountALessonOnceAndExcusedWins() {
        val lessons = listOf(lesson(id = 1))
        val absences = listOf(
            absence(at(monday, 7, 0), at(monday, 9, 0), excused = false, id = 1),
            absence(at(monday, 8, 0), at(monday, 12, 0), excused = true, id = 2),
        )

        val usage = countMissedLessons(absences, lessons, "WEPT")

        assertEquals(1, usage.total)
        assertEquals(1, usage.excused)
    }

    @Test
    fun doubleLessonCountsTwice() {
        val lessons = listOf(
            lesson(id = 1, start = LocalTime.of(8, 0)),
            lesson(id = 2, start = LocalTime.of(8, 50)),
        )
        val absence = absence(at(monday, 7, 0), at(monday, 12, 0))

        assertEquals(2, countMissedLessons(listOf(absence), lessons, "WEPT").total)
    }

    @Test
    fun duplicateLessonEntriesAreCountedOnce() {
        val same = lesson(id = 1)
        val absence = absence(at(monday, 7, 0), at(monday, 12, 0))

        assertEquals(1, countMissedLessons(listOf(absence), listOf(same, same), "WEPT").total)
    }

    @Test
    fun blankSubjectOrNoAbsencesGivesZero() {
        val lessons = listOf(lesson(id = 1))
        val absence = absence(at(monday, 7, 0), at(monday, 12, 0))

        assertEquals(0, countMissedLessons(listOf(absence), lessons, " ").total)
        assertEquals(0, countMissedLessons(emptyList(), lessons, "WEPT").total)
    }

    @Test
    fun lessonsByAbsenceListsOnlyAbsencesThatMissedTheSubject() {
        val tuesday = monday.plusDays(1)
        val lessons = listOf(
            lesson(id = 1, date = monday),
            lesson(id = 2, date = monday, start = LocalTime.of(8, 50)),
            lesson(id = 3, date = tuesday, subject = "MA"),
            lesson(id = 4, date = tuesday.plusDays(1), start = LocalTime.of(8, 0)),
        )
        val absences = listOf(
            absence(at(monday, 7, 0), at(monday, 12, 0), id = 10),
            absence(at(tuesday, 7, 0), at(tuesday, 12, 0), id = 11), // only MA that day
            absence(at(tuesday.plusDays(1), 13, 0), at(tuesday.plusDays(1), 15, 0), id = 12), // after the lesson
        )

        val byAbsence = missedLessonsByAbsence(absences, lessons, "WEPT")

        assertEquals(mapOf(10L to 2), byAbsence)
    }

    @Test
    fun lessonsByAbsenceSkipsCancelledLessons() {
        val lessons = listOf(lesson(id = 1, status = LessonStatus.CANCELLED))
        val absence = absence(at(monday, 7, 0), at(monday, 12, 0), id = 10)

        assertEquals(emptyMap<Long, Int>(), missedLessonsByAbsence(listOf(absence), lessons, "WEPT"))
    }

    @Test
    fun reorderedByFollowsTheGivenIds() {
        val a = AbsenceCategory("a", "A", "MA", 10)
        val b = AbsenceCategory("b", "B", "D", 10)
        val c = AbsenceCategory("c", "C", "E1", 10)

        assertEquals(listOf(c, a, b), listOf(a, b, c).reorderedBy(listOf("c", "a", "b")))
    }

    @Test
    fun reorderedByIgnoresUnknownIdsAndKeepsLeftOutCategories() {
        val a = AbsenceCategory("a", "A", "MA", 10)
        val b = AbsenceCategory("b", "B", "D", 10)
        val c = AbsenceCategory("c", "C", "E1", 10)

        assertEquals(listOf(b, a, c), listOf(a, b, c).reorderedBy(listOf("b", "gone", "a", "b")))
        assertEquals(listOf(a, b, c), listOf(a, b, c).reorderedBy(emptyList()))
    }

    @Test
    fun studentSubjectsAreDistinctAndSorted() {
        val lessons = listOf(
            lesson(id = 1, subject = "WEPT"),
            lesson(id = 2, subject = "MA, Eng"),
            lesson(id = 3, subject = "wept"),
        )

        assertEquals(listOf("Eng", "MA", "WEPT"), studentSubjects(lessons))
    }

    @Test
    fun totalsSplitExcusedAndUnexcused() {
        val totals = absenceTotals(
            listOf(
                absence(at(monday, 8, 0), at(monday, 9, 0), excused = true, id = 1),
                absence(at(monday, 10, 0), at(monday, 10, 30), excused = false, id = 2),
            ),
        )

        assertEquals(90L, totals.total.toMinutes())
        assertEquals(60L, totals.excused.toMinutes())
        assertEquals(30L, totals.unexcused.toMinutes())
    }

    private fun at(date: LocalDate, hour: Int, minute: Int): LocalDateTime =
        LocalDateTime.of(date, LocalTime.of(hour, minute))

    private fun lesson(
        id: Long,
        subject: String = "WEPT",
        date: LocalDate = monday,
        start: LocalTime = LocalTime.of(8, 0),
        status: LessonStatus = LessonStatus.REGULAR,
    ): UntisLesson = UntisLesson(
        id = id,
        date = date,
        start = start,
        end = start.plusMinutes(50),
        subject = subject,
        status = status,
    )

    private fun absence(
        start: LocalDateTime,
        end: LocalDateTime,
        excused: Boolean = false,
        id: Long = 1,
    ): UntisAbsence = UntisAbsence(
        id = id,
        start = start,
        end = end,
        excused = excused,
        reasonId = null,
        reasonName = "",
        text = "",
    )
}
