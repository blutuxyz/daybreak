package com.bdersolutions.daybreak.data.schedule

import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.LocalDate
import java.time.LocalTime

/**
 * What the home screen widget shows at one moment. Lessons are merged into blocks the way the
 * timetable draws them, so a double lesson is one row.
 */
data class WidgetPlan(
    /** The day the list belongs to, or null when no lessons are known from today on. */
    val day: LocalDate?,
    /** True when [day] is today. */
    val isToday: Boolean,
    /** Every block of [day], in order. */
    val rows: List<LessonRun>,
    /** The block that is on right now. Only held lessons count. */
    val current: LessonRun?,
    /** The next held block: later today, or the first one of the next school day. */
    val next: LessonRun?,
    /** Today has no lessons at all. */
    val freeToday: Boolean,
    /** Today had lessons and all of them are over. */
    val schoolOverToday: Boolean,
) {
    /** The rows still worth showing: on a later day all of them, today the ones not over yet. */
    fun remainingRows(now: LocalTime): List<LessonRun> =
        if (isToday) rows.filter { it.end > now } else rows
}

object WidgetPlanner {
    fun plan(
        lessons: List<UntisLesson>,
        today: LocalDate,
        now: LocalTime,
        showCancelled: Boolean = true,
        nextDayAfterSchool: Boolean = true,
    ): WidgetPlan {
        val byDay = lessons
            .filter { !it.isHoliday && !it.date.isBefore(today) }
            .groupBy { it.date }
        fun rowsOf(day: LocalDate?): List<LessonRun> =
            day?.let { NowAndNext.runs(byDay[it].orEmpty(), includeCancelled = showCancelled) }.orEmpty()
        fun heldOf(day: LocalDate?): List<LessonRun> =
            day?.let { NowAndNext.runs(byDay[it].orEmpty(), includeCancelled = false) }.orEmpty()

        val todayRows = rowsOf(today)
        // The next day that has something to show. A day of only cancelled lessons counts when
        // cancelled lessons are shown, so the student sees why it is empty.
        val nextDay = byDay.keys.filter { it.isAfter(today) }.sorted().firstOrNull { rowsOf(it).isNotEmpty() }
        val schoolOver = todayRows.isNotEmpty() && todayRows.all { it.end <= now }
        val showNextDay = todayRows.isEmpty() || (schoolOver && nextDayAfterSchool && nextDay != null)
        val day = if (showNextDay) nextDay else today
        val isToday = day == today

        val heldToday = heldOf(today)
        val current = if (isToday) heldToday.firstOrNull { it.start <= now && now < it.end } else null
        val next = if (isToday) {
            heldToday.firstOrNull { it.start > now } ?: heldOf(nextDay).firstOrNull()
        } else {
            heldOf(day).firstOrNull()
        }
        return WidgetPlan(
            day = day,
            isToday = isToday,
            rows = if (isToday) todayRows else rowsOf(day),
            current = current,
            next = next,
            freeToday = todayRows.isEmpty(),
            schoolOverToday = schoolOver,
        )
    }
}
