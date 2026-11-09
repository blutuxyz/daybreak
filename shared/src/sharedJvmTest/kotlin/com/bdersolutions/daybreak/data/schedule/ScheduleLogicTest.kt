package com.bdersolutions.daybreak.data.schedule

import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val DAY: LocalDate = LocalDate.of(2026, 11, 5)

private fun lesson(
    start: String,
    end: String,
    subject: String,
    room: String = "",
    teacher: String = "",
    status: LessonStatus = LessonStatus.REGULAR,
    date: LocalDate = DAY,
    id: Long = (start + subject).hashCode().toLong(),
) = UntisLesson(
    id = id, date = date, start = LocalTime.parse(start), end = LocalTime.parse(end),
    subject = subject, room = room, teacher = teacher, status = status,
)

private fun at(time: String): LocalDateTime = LocalDateTime.of(DAY, LocalTime.parse(time))

class NowAndNextTest {
    private val day = listOf(
        lesson("07:55", "08:45", "M", "204"),
        lesson("08:45", "09:35", "M", "204"),
        lesson("09:50", "10:40", "PH", "301"),
        lesson("11:30", "12:20", "E", "105"),
    )

    @Test
    fun doubleLessonsAreOneBlock() {
        val runs = NowAndNext.runs(day)
        assertEquals(3, runs.size)
        assertEquals(LocalTime.of(7, 55), runs[0].start)
        assertEquals(LocalTime.of(9, 35), runs[0].end)
        assertEquals(2, runs[0].periods)
    }

    @Test
    fun duringALessonItShowsTheTimeLeftAndTheNextLesson() {
        val status = NowAndNext.compute(day, at("08:10"))
        assertEquals(DayPhase.IN_LESSON, status.phase)
        assertEquals("M", status.current?.lesson?.subject)
        assertEquals(85, status.minutesLeft) // until 09:35, the end of the double lesson
        assertEquals("PH", status.next?.lesson?.subject)
        assertEquals(LocalTime.of(12, 20), status.schoolEnd)
    }

    @Test
    fun anAlmostFinishedLessonStillSaysOneMinute() {
        val status = NowAndNext.compute(day, LocalDateTime.of(DAY, LocalTime.of(10, 39, 30)))
        assertEquals(1, status.minutesLeft)
    }

    @Test
    fun betweenLessonsItIsABreak() {
        val status = NowAndNext.compute(day, at("09:40"))
        assertEquals(DayPhase.BREAK, status.phase)
        assertNull(status.current)
        assertEquals("PH", status.next?.lesson?.subject)
        assertEquals(10, status.minutesUntilNext)
    }

    @Test
    fun beforeSchoolAndAfterSchool() {
        assertEquals(DayPhase.BEFORE_SCHOOL, NowAndNext.compute(day, at("06:30")).phase)
        assertEquals(LocalTime.of(7, 55), NowAndNext.compute(day, at("06:30")).schoolStart)
        assertEquals(DayPhase.AFTER_SCHOOL, NowAndNext.compute(day, at("13:00")).phase)
    }

    @Test
    fun aGapOfThirtyMinutesIsAFreePeriodAndABreakIsNot() {
        val status = NowAndNext.compute(day, at("08:00"))
        assertEquals(FreePeriod(LocalTime.of(10, 40), LocalTime.of(11, 30)), status.freePeriod)
        // 09:35-09:50 is only a break.
        assertEquals(1, NowAndNext.freePeriods(NowAndNext.runs(day)).size)
    }

    @Test
    fun duringAFreePeriodItSaysSo() {
        val during = NowAndNext.compute(day, at("11:00"))
        assertEquals(DayPhase.BREAK, during.phase)
        assertTrue(during.inFreePeriod)
        assertTrue(!NowAndNext.compute(day, at("09:40")).inFreePeriod)
    }

    @Test
    fun aCancelledLessonIsNotSomethingToGoTo() {
        val withCancelled = day.map { if (it.subject == "PH") it.copy(status = LessonStatus.CANCELLED) else it }
        val status = NowAndNext.compute(withCancelled, at("09:40"))
        assertEquals("E", status.next?.lesson?.subject)
        // The cancelled Physics lesson leaves 09:35-11:30 free.
        assertEquals(FreePeriod(LocalTime.of(9, 35), LocalTime.of(11, 30)), status.freePeriod)
    }

    @Test
    fun aDayWithoutLessonsHasNoStatus() {
        assertEquals(DayPhase.NO_LESSONS, NowAndNext.compute(emptyList(), at("10:00")).phase)
        assertEquals(DayPhase.NO_LESSONS, NowAndNext.compute(day, at("10:00").plusDays(1)).phase)
    }
}

class LessonSearchTest {
    private val lessons = listOf(
        lesson("08:00", "08:50", "M", "204", "Huber", date = DAY.minusDays(7)),
        lesson("08:00", "08:50", "M", "204", "Huber", date = DAY.plusDays(2)),
        lesson("09:00", "09:50", "M", "105", "Maier", date = DAY),
        lesson("10:00", "10:50", "PH", "301", "Huber", date = DAY),
    )

    @Test
    fun upcomingComesBeforePastSoonestFirst() {
        val hits = LessonSearch.search(lessons, emptyList(), "m", at("09:30"))
        // Today 09:00 M is still running, then the one in two days, then last week's.
        assertEquals(
            listOf(DAY, DAY.plusDays(2), DAY.minusDays(7)),
            hits.filter { it.lesson.subject == "M" }.map { it.lesson.date },
        )
        assertTrue(hits.first().upcoming)
        assertTrue(!hits.last().upcoming)
    }

    @Test
    fun everyWordMustMatch() {
        assertEquals(2, LessonSearch.search(lessons, emptyList(), "huber m", at("07:00")).size)
        assertEquals(0, LessonSearch.search(lessons, emptyList(), "huber 105", at("07:00")).size)
        assertEquals(1, LessonSearch.search(lessons, emptyList(), "maier", at("07:00")).size)
    }

    @Test
    fun aBlankQueryFindsNothing() {
        assertTrue(LessonSearch.search(lessons, emptyList(), "  ", at("07:00")).isEmpty())
    }

    @Test
    fun anExamIsAttachedAndFoundByItsName() {
        val exam = UntisExam(1, DAY, LocalTime.of(9, 0), LocalTime.of(9, 50), "M", name = "Schularbeit")
        val hits = LessonSearch.search(lessons, listOf(exam), "schularbeit", at("07:00"))
        assertEquals(1, hits.size)
        assertEquals(exam, hits.single().exam)
    }

    @Test
    fun holidayBlocksAreNeverHits() {
        val holiday = lesson("08:00", "17:00", "Ferien").copy(isHoliday = true)
        assertTrue(LessonSearch.search(listOf(holiday), emptyList(), "ferien", at("07:00")).isEmpty())
    }
}

class ShareTextTest {
    private val labels = ShareLabels(
        noLessons = "No lessons",
        cancelled = "cancelled",
        room = { "room $it" },
        dateHeading = { "Day ${it.dayOfMonth}" },
    )

    @Test
    fun aDayIsOneLinePerBlockWithCancelledMarked() {
        val lessons = listOf(
            lesson("07:55", "08:45", "M", "204", "Huber"),
            lesson("08:45", "09:35", "M", "204", "Huber"),
            lesson("09:50", "10:40", "PH", "301", status = LessonStatus.CANCELLED),
        )
        val text = ShareText.format(listOf(ShareText.day(DAY, lessons, labels)), labels)
        assertEquals(
            "Day 5\n07:55–09:35  M · room 204 · Huber\n09:50–10:40  PH · room 301 (cancelled)",
            text,
        )
    }

    @Test
    fun daysAreSeparatedAndAnEmptyDaySaysSo() {
        val days = listOf(ShareText.day(DAY, emptyList(), labels), ShareText.day(DAY.plusDays(1), emptyList(), labels))
        assertEquals("Day 5\nNo lessons\n\nDay 6\nNo lessons", ShareText.format(days, labels))
    }

    @Test
    fun holidayBlocksAreNotShared() {
        val holiday = lesson("08:00", "17:00", "Ferien").copy(isHoliday = true)
        assertTrue(ShareText.day(DAY, listOf(holiday), labels).lines.isEmpty())
        assertNotNull(ShareText.format(listOf(ShareText.day(DAY, listOf(holiday), labels)), labels))
    }

    @Test
    fun aDoubleLessonIsOneHit() {
        val double = listOf(
            lesson("07:55", "08:45", "BESP", "Turnsaal", "Achl"),
            lesson("08:45", "09:35", "BESP", "Turnsaal", "Achl"),
        )
        val hits = LessonSearch.search(double, emptyList(), "besp", at("07:00"))
        assertEquals(1, hits.size)
        assertEquals(LocalTime.of(7, 55), hits.single().lesson.start)
        assertEquals(LocalTime.of(9, 35), hits.single().lesson.end)
    }
}

class WidgetPlannerTest {
    private val day = listOf(
        lesson("07:55", "08:45", "M", "204"),
        lesson("08:45", "09:35", "M", "204"),
        lesson("09:50", "10:40", "PH", "301", status = LessonStatus.CANCELLED),
        lesson("10:40", "11:30", "E", "105"),
    )
    private val tomorrow = listOf(lesson("08:00", "08:50", "D", "110", date = DAY.plusDays(1)))

    private fun plan(time: String, showCancelled: Boolean = true, nextDay: Boolean = true) =
        WidgetPlanner.plan(day + tomorrow, DAY, LocalTime.parse(time), showCancelled, nextDay)

    @Test
    fun doubleLessonsAreOneRowAndTheCurrentBlockIsFound() {
        val plan = plan("08:50")
        assertTrue(plan.isToday)
        assertEquals(3, plan.rows.size)
        assertEquals(LocalTime.of(9, 35), plan.current?.end)
        // The cancelled PH is no lesson to go to, so English is next.
        assertEquals("E", plan.next?.lesson?.subject)
    }

    @Test
    fun cancelledLessonsCanBeHidden() {
        assertEquals(2, plan("08:50", showCancelled = false).rows.size)
    }

    @Test
    fun remainingRowsDropWhatIsOver() {
        val plan = plan("10:45")
        assertEquals(listOf("E"), plan.remainingRows(LocalTime.parse("10:45")).map { it.lesson.subject })
    }

    @Test
    fun afterSchoolTheNextDayIsShownUnlessTurnedOff() {
        val after = plan("12:00")
        assertTrue(after.schoolOverToday)
        assertTrue(!after.isToday)
        assertEquals(DAY.plusDays(1), after.day)
        assertEquals("D", after.next?.lesson?.subject)

        val stay = plan("12:00", nextDay = false)
        assertTrue(stay.isToday)
        assertTrue(stay.remainingRows(LocalTime.parse("12:00")).isEmpty())
        assertEquals("D", stay.next?.lesson?.subject)
    }

    @Test
    fun aFreeDayShowsTheNextSchoolDay() {
        val plan = WidgetPlanner.plan(tomorrow, DAY, LocalTime.parse("09:00"))
        assertTrue(plan.freeToday)
        assertEquals(DAY.plusDays(1), plan.day)
        assertNull(plan.current)
    }

    @Test
    fun nothingKnownGivesNoDay() {
        val plan = WidgetPlanner.plan(emptyList(), DAY, LocalTime.parse("09:00"))
        assertNull(plan.day)
        assertTrue(plan.rows.isEmpty())
    }
}

class WeekRolloverTest {
    // DAY is Thursday 5 November 2026.
    private val friday = DAY.plusDays(1)
    private val nextMonday = DAY.plusDays(4)
    private val week = listOf(
        lesson("07:55", "08:45", "M"),
        lesson("11:35", "13:15", "E", date = friday),
    )

    @Test
    fun duringTheWeekItIsToday() {
        assertEquals(DAY, WeekRollover.startDate(week, at("10:00")))
        assertEquals(friday, WeekRollover.startDate(week, LocalDateTime.of(friday, LocalTime.of(12, 0))))
    }

    @Test
    fun afterTheLastLessonOfTheWeekItIsNextMonday() {
        assertEquals(nextMonday, WeekRollover.startDate(week, LocalDateTime.of(friday, LocalTime.of(13, 15))))
        assertEquals(nextMonday, WeekRollover.startDate(week, LocalDateTime.of(friday.plusDays(1), LocalTime.NOON)))
    }

    @Test
    fun withoutLessonsOnlyTheWeekendRollsOver() {
        assertEquals(DAY, WeekRollover.startDate(emptyList(), at("15:00")))
        assertEquals(nextMonday, WeekRollover.startDate(emptyList(), LocalDateTime.of(DAY.plusDays(3), LocalTime.NOON)))
    }

    @Test
    fun holidayBlocksDoNotCount() {
        val holiday = lesson("07:00", "18:00", "Ferien", date = friday).copy(isHoliday = true)
        assertEquals(nextMonday, WeekRollover.startDate(listOf(lesson("07:55", "08:45", "M"), holiday), at("09:00")))
    }
}
