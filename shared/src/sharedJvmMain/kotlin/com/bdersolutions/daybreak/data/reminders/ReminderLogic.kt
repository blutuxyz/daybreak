package com.bdersolutions.daybreak.data.reminders

import com.bdersolutions.daybreak.data.schedule.LessonRun
import com.bdersolutions.daybreak.data.schedule.NowAndNext
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisHomework
import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * What the morning notification says about one day. [periods] counts the lessons that take place;
 * [firstStart] and [lastEnd] are null when none does (everything cancelled).
 */
data class DaySummary(
    val date: LocalDate,
    val periods: Int,
    val firstStart: LocalTime?,
    val lastEnd: LocalTime?,
    val cancelled: List<LessonRun>,
    /** Lessons that still take place but in another room, with another teacher or as a substitution. */
    val changed: List<LessonRun>,
    val exams: List<UntisExam>,
) {
    val changeCount: Int get() = cancelled.size + changed.size
}

object DaySummaries {
    /** The summary of [date], or null when the timetable has no lesson for it at all. */
    fun build(date: LocalDate, lessons: List<UntisLesson>, exams: List<UntisExam>): DaySummary? {
        val ofDay = lessons.filter { it.date == date && !it.isHoliday }
        if (ofDay.isEmpty()) return null
        val active = NowAndNext.runs(ofDay)
        val all = NowAndNext.runs(ofDay, includeCancelled = true)
        return DaySummary(
            date = date,
            periods = ofDay.count { !it.isCancelled },
            firstStart = active.minOfOrNull { it.start },
            lastEnd = active.maxOfOrNull { it.end },
            cancelled = all.filter { it.isCancelled },
            changed = active.filter { it.lesson.hasRoomChange || it.lesson.hasTeacherChange || it.lesson.isIrregular },
            exams = exams.filter { it.date == date }.sortedBy { it.start },
        )
    }
}

/** What the evening notification lists for the next day: exams and homework that are due. */
data class EveningReminder(
    val date: LocalDate,
    val exams: List<UntisExam>,
    val homework: List<UntisHomework>,
)

object EveningReminders {
    /** The reminder for [date] (the day after the evening it is sent), or null when there is nothing due. */
    fun build(date: LocalDate, exams: List<UntisExam>, homework: List<UntisHomework>): EveningReminder? {
        val dueExams = exams.filter { it.date == date }.sortedBy { it.start }
        val dueHomework = homework.filter { it.due == date && !it.completed }
        if (dueExams.isEmpty() && dueHomework.isEmpty()) return null
        return EveningReminder(date, dueExams, dueHomework)
    }
}

/** When a notification of the morning summary should be sent. */
data class MorningPlan(val date: LocalDate, val at: LocalDateTime)

/** Decides when the next morning summary and the next evening reminder are due. */
object ReminderPlanner {
    /** How long before the first lesson the summary may come, to choose from. */
    val LEAD_MINUTES = listOf(30, 60, 90)
    const val DEFAULT_LEAD_MINUTES = 60
    const val EVENING_HOUR = 18
    private val EARLIEST_MORNING: LocalTime = LocalTime.of(5, 0)
    private const val LAST_EVENING_HOUR = 22
    private const val LOOK_AHEAD_DAYS = 7
    private const val SAFETY_MINUTES = 5L

    /**
     * The next school morning to send a summary for, looking up to a week ahead. [firstLessonStart]
     * gives the first lesson that takes place on a date, or null for a day without school. A morning
     * that was already summed up ([lastSent]) is skipped. If the planned time of today has passed but
     * the first lesson has not started yet, it is due in a minute, so switching the feature on at
     * breakfast still works.
     */
    fun nextMorning(
        now: LocalDateTime,
        firstLessonStart: (LocalDate) -> LocalTime?,
        leadMinutes: Int,
        lastSent: LocalDate?,
    ): MorningPlan? {
        for (offset in 0..LOOK_AHEAD_DAYS) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (date == lastSent) continue
            val first = firstLessonStart(date) ?: continue
            val planned = LocalDateTime.of(date, maxOf(first.minusMinutes(leadMinutes.toLong()), EARLIEST_MORNING))
            if (planned.isAfter(now)) return MorningPlan(date, planned)
            val lastUseful = LocalDateTime.of(date, first).minusMinutes(SAFETY_MINUTES)
            if (date == now.toLocalDate() && now.isBefore(lastUseful)) {
                return MorningPlan(date, now.plusMinutes(1))
            }
        }
        return null
    }

    /**
     * The next time to look for exams and homework due the next day: 18:00. Past 18:00 and not sent
     * yet, it is due in a minute (until 22:00); otherwise it is tomorrow evening.
     */
    fun nextEvening(now: LocalDateTime, lastSent: LocalDate?): LocalDateTime {
        val today = now.toLocalDate()
        val sixPm = LocalDateTime.of(today, LocalTime.of(EVENING_HOUR, 0))
        if (lastSent != today) {
            if (now.isBefore(sixPm)) return sixPm
            if (now.hour < LAST_EVENING_HOUR) return now.plusMinutes(1)
        }
        return LocalDateTime.of(today.plusDays(1), LocalTime.of(EVENING_HOUR, 0))
    }
}
