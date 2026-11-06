package com.bdersolutions.daybreak.ui.absences

import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisLesson
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AbsenceCountedTimeTest {
    private val monday = LocalDate.of(2026, 10, 19)

    @Test
    fun wholeDayRecordedLongerThanTheLessonsCountsOnlyFirstToLastLesson() {
        // Lessons 08:00 to 13:00 (one 14:00 lesson is cancelled); the teacher wrote 07:55 to 17:30.
        val windows = SchoolDayWindows.from(
            listOf(
                lesson(monday, 8, 0, 13, 0),
                lesson(monday, 14, 0, 14, 50, LessonStatus.CANCELLED),
            ),
        )
        val absence = absence(at(monday, 7, 55), at(monday, 17, 30))

        assertEquals(9L * 60 + 35, absence.duration.toMinutes())
        assertEquals(300L, absence.countedDuration(windows).toMinutes())
    }

    @Test
    fun absenceInsideTheLessonsIsNotChanged() {
        val windows = SchoolDayWindows.from(listOf(lesson(monday, 8, 0, 15, 0)))

        assertEquals(60L, absence(at(monday, 9, 0), at(monday, 10, 0)).countedDuration(windows).toMinutes())
    }

    @Test
    fun withoutLessonsTheRecordedTimeStays() {
        assertEquals(
            575L,
            absence(at(monday, 7, 55), at(monday, 17, 30)).countedDuration(null).toMinutes(),
        )
        // A week that is not loaded is not guessed at either.
        val other = SchoolDayWindows.from(listOf(lesson(monday.plusWeeks(3), 8, 0, 13, 0)))
        assertEquals(
            575L,
            absence(at(monday, 7, 55), at(monday, 17, 30)).countedDuration(other).toMinutes(),
        )
    }

    @Test
    fun multiDayAbsenceCountsEachDayAndSkipsTheWeekend() {
        // Friday 07:55 to Monday 17:30, school 08:00 to 13:00 on both days.
        val friday = monday.plusDays(4)
        val nextMonday = monday.plusDays(7)
        val windows = SchoolDayWindows.from(
            listOf(lesson(friday, 8, 0, 13, 0), lesson(nextMonday, 8, 0, 13, 0)),
        )
        val absence = absence(at(friday, 7, 55), at(nextMonday, 17, 30))

        assertEquals(600L, absence.countedDuration(windows).toMinutes())
    }

    @Test
    fun aDayInALoadedWeekWithoutLessonsCountsNothing() {
        val windows = SchoolDayWindows.from(listOf(lesson(monday, 8, 0, 13, 0)))

        assertEquals(0L, absence(at(monday.plusDays(1), 8, 0), at(monday.plusDays(1), 12, 0)).countedDuration(windows).toMinutes())
    }

    @Test
    fun totalsUseTheCountedTime() {
        val windows = SchoolDayWindows.from(listOf(lesson(monday, 8, 0, 13, 0)))
        val totals = absenceTotals(
            listOf(
                absence(at(monday, 7, 55), at(monday, 17, 30), excused = true, id = 1),
                absence(at(monday, 8, 0), at(monday, 9, 0), excused = false, id = 2),
            ),
            windows,
        )

        assertEquals(360L, totals.total.toMinutes())
        assertEquals(300L, totals.excused.toMinutes())
    }

    @Test
    fun weekStartsOfAbsencesAreTheMondaysTheyTouch() {
        val friday = monday.plusDays(4)
        val weeks = absenceWeekStarts(listOf(absence(at(friday, 8, 0), at(monday.plusDays(8), 9, 0))))

        assertEquals(setOf(monday, monday.plusWeeks(1)), weeks)
    }

    private fun at(date: LocalDate, hour: Int, minute: Int): LocalDateTime =
        LocalDateTime.of(date, LocalTime.of(hour, minute))

    private fun lesson(
        date: LocalDate,
        sh: Int,
        sm: Int,
        eh: Int,
        em: Int,
        status: LessonStatus = LessonStatus.REGULAR,
    ) = UntisLesson(
        id = (date.toEpochDay() * 100 + sh).toLong(),
        date = date,
        start = LocalTime.of(sh, sm),
        end = LocalTime.of(eh, em),
        subject = "MA",
        status = status,
    )

    private fun absence(
        start: LocalDateTime,
        end: LocalDateTime,
        excused: Boolean = false,
        id: Long = 1,
    ) = UntisAbsence(id, start, end, excused, null, "", "")
}
