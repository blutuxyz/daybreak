package com.bdersolutions.daybreak.ui.absences

import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * When the student has lessons on each day: the start of the first and the end of the last held
 * lesson. Teachers often record a whole-day absence as 07:55 to 17:30 whatever the timetable says;
 * this is what the time that really counts is clipped to.
 *
 * [coveredWeeks] are the Mondays of the weeks whose lessons are known. A day inside a covered week
 * without a window has no lessons (weekend, holiday), so nothing counts there; a day in an unknown
 * week is not guessed at and keeps the recorded time.
 */
class SchoolDayWindows(
    private val windows: Map<LocalDate, Pair<LocalTime, LocalTime>>,
    private val coveredWeeks: Set<LocalDate>,
) {
    /** The part of [from, to) on [day] that lies inside the school day. */
    internal fun clip(day: LocalDate, from: LocalDateTime, to: LocalDateTime): Duration {
        val window = windows[day]
        if (window == null) {
            return if (mondayOf(day) in coveredWeeks) Duration.ZERO else positive(from, to)
        }
        val open = LocalDateTime.of(day, window.first)
        val close = LocalDateTime.of(day, window.second)
        return positive(maxOf(from, open), minOf(to, close))
    }

    companion object {
        /** Cancelled lessons and the synthetic holiday blocks are not school time. */
        fun from(lessons: Collection<UntisLesson>, knownWeeks: Collection<LocalDate> = emptyList()): SchoolDayWindows {
            val windows = HashMap<LocalDate, Pair<LocalTime, LocalTime>>()
            val weeks = HashSet<LocalDate>()
            knownWeeks.forEach { weeks += mondayOf(it) }
            for (lesson in lessons) {
                weeks += mondayOf(lesson.date)
                if (lesson.isCancelled || lesson.isHoliday) continue
                val old = windows[lesson.date]
                windows[lesson.date] = if (old == null) {
                    lesson.start to lesson.end
                } else {
                    minOf(old.first, lesson.start) to maxOf(old.second, lesson.end)
                }
            }
            return SchoolDayWindows(windows, weeks)
        }

        internal fun mondayOf(date: LocalDate): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        private fun positive(from: LocalDateTime, to: LocalDateTime): Duration =
            if (to.isAfter(from)) Duration.between(from, to) else Duration.ZERO
    }
}

/** The Mondays of the weeks that hold any of [absences], so only those weeks' lessons need loading. */
fun absenceWeekStarts(absences: Collection<UntisAbsence>): Set<LocalDate> {
    val weeks = sortedSetOf<LocalDate>()
    for (absence in absences) {
        var day = absence.start.toLocalDate()
        val last = absence.end.toLocalDate()
        while (!day.isAfter(last)) {
            weeks += SchoolDayWindows.mondayOf(day)
            day = day.plusDays(7 - (day.dayOfWeek.value - 1).toLong())
        }
    }
    return weeks
}

/**
 * The time of this absence that counts: the recorded time, clipped on every day to the first and
 * last held lesson of that day. The recorded range itself is still what the list shows. Without
 * [windows] (lessons not loaded yet) it is the recorded time.
 */
fun UntisAbsence.countedDuration(windows: SchoolDayWindows?): Duration {
    if (windows == null) return duration
    var total = Duration.ZERO
    var day = start.toLocalDate()
    val last = end.toLocalDate()
    while (!day.isAfter(last)) {
        val from = maxOf(start, day.atStartOfDay())
        val to = minOf(end, day.plusDays(1).atStartOfDay())
        total = total.plus(windows.clip(day, from, to))
        day = day.plusDays(1)
    }
    return total
}
