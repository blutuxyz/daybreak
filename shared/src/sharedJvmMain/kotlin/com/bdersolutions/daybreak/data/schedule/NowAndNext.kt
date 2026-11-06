package com.bdersolutions.daybreak.data.schedule

import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One block of the day: a lesson, or a run of consecutive periods of the same subject, room and
 * teacher (a double lesson) shown as one block, the way the timetable draws them.
 */
data class LessonRun(
    val lesson: UntisLesson,
    val start: LocalTime,
    val end: LocalTime,
    val periods: Int = 1,
) {
    val isCancelled: Boolean get() = lesson.isCancelled
}

/** Where in the school day it is right now. */
enum class DayPhase { NO_LESSONS, BEFORE_SCHOOL, IN_LESSON, BREAK, AFTER_SCHOOL }

/** A gap between two lessons long enough to count as a free period (Freistunde). */
data class FreePeriod(val start: LocalTime, val end: LocalTime)

/**
 * What the "Now and next" card shows. [current] and [next] only ever hold lessons that take place:
 * a cancelled lesson is a free period, not something to go to.
 */
data class DayStatus(
    val phase: DayPhase,
    val current: LessonRun? = null,
    val minutesLeft: Int = 0,
    /** How far through [current] it is, from 0 to 1. */
    val progress: Float = 0f,
    val next: LessonRun? = null,
    val minutesUntilNext: Int = 0,
    val schoolStart: LocalTime? = null,
    val schoolEnd: LocalTime? = null,
    /** The free period that is on now, or the next one still to come today. */
    val freePeriod: FreePeriod? = null,
    /** True while [freePeriod] is the one that is on right now. */
    val inFreePeriod: Boolean = false,
)

/** Works out the current lesson, the next one, when school ends and the next free period. */
object NowAndNext {
    /** A gap between lessons counts as a free period from this length on; shorter ones are breaks. */
    const val MIN_FREE_PERIOD_MINUTES = 30

    /**
     * The blocks of a day, in order. Consecutive periods that match are merged. Holiday blocks are
     * left out; cancelled lessons are kept only if [includeCancelled] is set.
     */
    fun runs(lessons: List<UntisLesson>, includeCancelled: Boolean = false): List<LessonRun> {
        val sorted = lessons
            .filter { !it.isHoliday && (includeCancelled || !it.isCancelled) }
            .sortedWith(compareBy({ it.start }, { it.end }))
        val result = mutableListOf<LessonRun>()
        for (lesson in sorted) {
            val last = result.lastOrNull()
            if (last != null && sameBlock(last.lesson, lesson) && !lesson.start.isAfter(last.end.plusMinutes(1))) {
                result[result.size - 1] = last.copy(end = maxOf(last.end, lesson.end), periods = last.periods + 1)
            } else {
                result += LessonRun(lesson, lesson.start, lesson.end)
            }
        }
        return result
    }

    fun compute(lessons: List<UntisLesson>, now: LocalDateTime): DayStatus {
        val runs = runs(lessons.filter { it.date == now.toLocalDate() })
        if (runs.isEmpty()) return DayStatus(DayPhase.NO_LESSONS)
        val time = now.toLocalTime()
        val schoolStart = runs.first().start
        val schoolEnd = runs.maxOf { it.end }

        val current = runs.firstOrNull { it.start <= time && time < it.end }
        val next = runs.firstOrNull { it.start > time && it != current }
        val freePeriod = freePeriods(runs).firstOrNull { it.end > time }

        val phase = when {
            current != null -> DayPhase.IN_LESSON
            time < schoolStart -> DayPhase.BEFORE_SCHOOL
            next == null -> DayPhase.AFTER_SCHOOL
            else -> DayPhase.BREAK
        }
        return DayStatus(
            phase = phase,
            current = current,
            minutesLeft = current?.let { minutesBetween(time, it.end) } ?: 0,
            progress = current?.let { progressOf(it, time) } ?: 0f,
            next = next,
            minutesUntilNext = next?.let { minutesBetween(time, it.start) } ?: 0,
            schoolStart = schoolStart,
            schoolEnd = schoolEnd,
            freePeriod = freePeriod,
            inFreePeriod = freePeriod != null && freePeriod.start <= time && time < freePeriod.end,
        )
    }

    /** The gaps of [MIN_FREE_PERIOD_MINUTES] or more between two blocks of the day. */
    fun freePeriods(runs: List<LessonRun>): List<FreePeriod> {
        val periods = mutableListOf<FreePeriod>()
        var cursor: LocalTime? = null
        for (run in runs.sortedBy { it.start }) {
            val end = cursor
            if (end != null && Duration.between(end, run.start).toMinutes() >= MIN_FREE_PERIOD_MINUTES) {
                periods += FreePeriod(end, run.start)
            }
            if (end == null || run.end > end) cursor = run.end
        }
        return periods
    }

    /** Whole minutes from [from] to [to], rounded up, so "1 min left" shows until it is really over. */
    fun minutesBetween(from: LocalTime, to: LocalTime): Int {
        val seconds = Duration.between(from, to).seconds
        return if (seconds <= 0) 0 else ((seconds + 59) / 60).toInt()
    }

    private fun progressOf(run: LessonRun, time: LocalTime): Float {
        val total = Duration.between(run.start, run.end).seconds.coerceAtLeast(1)
        val done = Duration.between(run.start, time).seconds.coerceIn(0, total)
        return done.toFloat() / total
    }

    private fun sameBlock(a: UntisLesson, b: UntisLesson): Boolean =
        a.subject == b.subject && a.room == b.room && a.teacher == b.teacher && a.status == b.status &&
            a.substitutionText == b.substitutionText
}
