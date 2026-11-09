package com.bdersolutions.daybreak.data.schedule

import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Which day the timetable opens on. Normally today; once the school week is over (after the last
 * lesson on Friday, or at the weekend) it is the Monday of the next week, so the student sees what
 * comes next instead of a finished week.
 */
object WeekRollover {
    fun startDate(lessons: List<UntisLesson>, now: LocalDateTime): LocalDate {
        val today = now.toLocalDate()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val nextMonday = monday.plusWeeks(1)
        val thisWeek = lessons.filter { !it.isHoliday && !it.date.isBefore(monday) && it.date.isBefore(nextMonday) }
        if (thisWeek.isEmpty()) {
            // Nothing known for this week: only the weekend counts as over.
            return if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) nextMonday else today
        }
        val lastEnd = thisWeek.maxOf { it.endsAt }
        return if (!now.isBefore(lastEnd)) nextMonday else today
    }
}
