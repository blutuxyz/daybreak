package com.bdersolutions.daybreak.data.reminders

import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisHomework
import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private val DAY: LocalDate = LocalDate.of(2026, 11, 5) // a Thursday

private fun lesson(
    start: String,
    end: String,
    subject: String,
    status: LessonStatus = LessonStatus.REGULAR,
    room: String = "",
    originalRoom: String = "",
    date: LocalDate = DAY,
) = UntisLesson(
    id = (start + subject).hashCode().toLong(), date = date, start = LocalTime.parse(start),
    end = LocalTime.parse(end), subject = subject, room = room, status = status, originalRoom = originalRoom,
)

class DaySummariesTest {
    private val lessons = listOf(
        lesson("07:55", "08:45", "M"),
        lesson("08:45", "09:35", "M"),
        lesson("09:50", "10:40", "PH", status = LessonStatus.CANCELLED),
        lesson("10:40", "11:30", "E", room = "105", originalRoom = "204"),
    )

    @Test
    fun itCountsLessonsThatTakePlaceAndListsTheChanges() {
        val summary = DaySummaries.build(DAY, lessons, emptyList())!!
        assertEquals(3, summary.periods)
        assertEquals(LocalTime.of(7, 55), summary.firstStart)
        assertEquals(LocalTime.of(11, 30), summary.lastEnd)
        assertEquals(listOf("PH"), summary.cancelled.map { it.lesson.subject })
        assertEquals(listOf("E"), summary.changed.map { it.lesson.subject })
        assertEquals(2, summary.changeCount)
    }

    @Test
    fun examsOfTheDayAreIncluded() {
        val exam = UntisExam(1, DAY, LocalTime.of(8, 0), LocalTime.of(9, 0), "M", name = "Test")
        val other = exam.copy(id = 2, date = DAY.plusDays(1))
        assertEquals(listOf(exam), DaySummaries.build(DAY, lessons, listOf(exam, other))!!.exams)
    }

    @Test
    fun aDayWithoutLessonsHasNoSummary() {
        assertNull(DaySummaries.build(DAY, emptyList(), emptyList()))
        assertNull(DaySummaries.build(DAY.plusDays(1), lessons, emptyList()))
    }

    @Test
    fun aDayWhereEverythingIsCancelledStillHasASummary() {
        val cancelled = lessons.map { it.copy(status = LessonStatus.CANCELLED) }
        val summary = DaySummaries.build(DAY, cancelled, emptyList())!!
        assertEquals(0, summary.periods)
        assertNull(summary.firstStart)
        assertEquals(3, summary.cancelled.size)
    }
}

class EveningRemindersTest {
    private val tomorrow = DAY.plusDays(1)
    private val exam = UntisExam(1, tomorrow, LocalTime.of(8, 0), LocalTime.of(9, 0), "M")

    private fun homework(due: LocalDate, completed: Boolean = false) =
        UntisHomework(id = due.toEpochDay() + if (completed) 1000 else 0, subject = "E", assigned = null, due = due, text = "Read", completed = completed)

    @Test
    fun itListsWhatIsDueTomorrow() {
        val reminder = EveningReminders.build(tomorrow, listOf(exam), listOf(homework(tomorrow), homework(DAY)))!!
        assertEquals(listOf(exam), reminder.exams)
        assertEquals(1, reminder.homework.size)
    }

    @Test
    fun finishedHomeworkIsLeftOut() {
        assertNull(EveningReminders.build(tomorrow, emptyList(), listOf(homework(tomorrow, completed = true))))
    }

    @Test
    fun nothingDueMeansNoReminder() {
        assertNull(EveningReminders.build(tomorrow, emptyList(), emptyList()))
        assertNotNull(EveningReminders.build(tomorrow, listOf(exam), emptyList()))
    }
}

class ReminderPlannerTest {
    private fun at(date: LocalDate, time: String) = LocalDateTime.of(date, LocalTime.parse(time))

    // School every day except Saturday and Sunday, starting at 07:55.
    private val school: (LocalDate) -> LocalTime? = { date ->
        if (date.dayOfWeek.value in 1..5) LocalTime.of(7, 55) else null
    }

    @Test
    fun theSummaryComesOneLeadBeforeTheFirstLesson() {
        val plan = ReminderPlanner.nextMorning(at(DAY, "20:00"), school, leadMinutes = 60, lastSent = null)!!
        assertEquals(DAY.plusDays(1), plan.date)
        assertEquals(at(DAY.plusDays(1), "06:55"), plan.at)
    }

    @Test
    fun aVeryEarlyLessonNeverWakesYouBeforeFive() {
        val early: (LocalDate) -> LocalTime? = { LocalTime.of(5, 30) }
        val plan = ReminderPlanner.nextMorning(at(DAY, "20:00"), early, leadMinutes = 90, lastSent = null)!!
        assertEquals(at(DAY.plusDays(1), "05:00"), plan.at)
    }

    @Test
    fun theWeekendIsSkipped() {
        val friday = LocalDate.of(2026, 11, 6)
        val plan = ReminderPlanner.nextMorning(at(friday, "20:00"), school, 60, null)!!
        assertEquals(LocalDate.of(2026, 11, 9), plan.date)
    }

    @Test
    fun aMorningAlreadySummedUpIsSkipped() {
        val plan = ReminderPlanner.nextMorning(at(DAY, "05:30"), school, 60, lastSent = DAY)!!
        assertEquals(DAY.plusDays(1), plan.date)
    }

    @Test
    fun ifTheTimeHasPassedButSchoolHasNotStartedItIsDueInAMinute() {
        val now = at(DAY, "07:20")
        val plan = ReminderPlanner.nextMorning(now, school, 60, null)!!
        assertEquals(DAY, plan.date)
        assertEquals(now.plusMinutes(1), plan.at)
    }

    @Test
    fun onceSchoolHasStartedTodayIsSkipped() {
        val plan = ReminderPlanner.nextMorning(at(DAY, "08:30"), school, 60, null)!!
        assertEquals(DAY.plusDays(1), plan.date)
    }

    @Test
    fun noSchoolForAWeekMeansNoPlan() {
        assertNull(ReminderPlanner.nextMorning(at(DAY, "08:30"), { null }, 60, null))
    }

    @Test
    fun theEveningReminderIsAtSixPm() {
        assertEquals(at(DAY, "18:00"), ReminderPlanner.nextEvening(at(DAY, "09:00"), null))
        assertEquals(at(DAY.plusDays(1), "18:00"), ReminderPlanner.nextEvening(at(DAY, "09:00"), lastSent = DAY))
    }

    @Test
    fun lateInTheEveningItIsDueRightAwayButNotAtNight() {
        val evening = at(DAY, "19:30")
        assertEquals(evening.plusMinutes(1), ReminderPlanner.nextEvening(evening, null))
        assertEquals(at(DAY.plusDays(1), "18:00"), ReminderPlanner.nextEvening(at(DAY, "23:00"), null))
    }
}
