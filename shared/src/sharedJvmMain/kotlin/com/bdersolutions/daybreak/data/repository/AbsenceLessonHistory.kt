package com.bdersolutions.daybreak.data.repository

import com.bdersolutions.daybreak.data.cache.AbsenceLessonData
import com.bdersolutions.daybreak.data.cache.AbsenceLessonStore
import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.untis.UntisApi
import com.bdersolutions.daybreak.untis.UntisAuthException
import com.bdersolutions.daybreak.untis.UntisLesson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/** The lessons of a date range. [failedWeeks] counts weeks that could not be loaded. */
data class LessonHistory(
    val lessons: List<UntisLesson>,
    val failedWeeks: Int,
)

/**
 * Gives the student's own lessons for a range that reaches further back than the 8-week
 * timetable cache. WebUntis absences have no subject, so absence categories need the lessons of
 * the whole school year.
 *
 * Weeks come from, in order: the weeks already stored here, the live timetable cache, and a
 * request. A finished week never changes, so it is stored and fetched only once. The current
 * week is never stored. Nothing here touches the timetable cache, sync, or widget.
 */
class AbsenceLessonHistory(
    private val store: AbsenceLessonStore,
    private val timetable: TimetableRepository,
    private val sessionStore: SessionStore,
    private val api: UntisApi,
) {
    private val mutex = Mutex()
    private var data: AbsenceLessonData? = null

    suspend fun lessons(from: LocalDate, to: LocalDate): LessonHistory {
        val weeks = mutableListOf<LocalDate>()
        var weekStart = timetable.weekStartFor(from)
        val lastWeek = timetable.weekStartFor(to)
        while (!weekStart.isAfter(lastWeek)) {
            weeks += weekStart
            weekStart = weekStart.plusWeeks(1)
        }
        return lessonsOfWeeks(weeks)
    }

    /**
     * The lessons of just the given weeks (Mondays). The absence list needs the days of its own
     * absences only, which is far fewer requests than a whole school year.
     */
    suspend fun lessonsOfWeeks(weekStarts: Collection<LocalDate>): LessonHistory = mutex.withLock {
        val session = sessionStore.timetableSession() ?: throw UntisAuthException("Not signed in.")
        var working = loadData(session.studentId)
        var changed = false
        var failed = 0

        val cache = timetable.cache.value
        val thisWeek = timetable.weekStartFor(LocalDate.now())
        val result = mutableListOf<UntisLesson>()

        var authFailed = false
        for (weekStart in weekStarts.sorted()) {
            val past = weekStart.isBefore(thisWeek)
            val stored = working.weeks[weekStart]
            val cached = cache.weeks[weekStart]
            val cachedUsable = cached != null &&
                (!past || cached.updatedAt >= weekEndMillis(weekStart))

            when {
                past && stored != null -> result += stored
                cachedUsable -> {
                    result += cached!!.lessons
                    if (past) {
                        working = working.copy(weeks = working.weeks + (weekStart to cached.lessons))
                        changed = true
                    }
                }
                authFailed -> failed++
                else -> try {
                    val fetched = api.fetchTimetable(session, weekStart, weekStart.plusDays(6)).lessons
                    result += fetched
                    if (past) {
                        working = working.copy(weeks = working.weeks + (weekStart to fetched))
                        changed = true
                    }
                } catch (error: UntisAuthException) {
                    // A bad key fails every later request too, so stop asking.
                    authFailed = true
                    failed++
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failed++
                }
            }
        }

        if (changed) {
            data = working
            store.save(working)
        }
        LessonHistory(lessons = result, failedWeeks = failed)
    }

    suspend fun clear() = mutex.withLock {
        data = AbsenceLessonData()
        store.clear()
    }

    private suspend fun loadData(studentId: Long): AbsenceLessonData {
        val current = data ?: store.load().also { data = it }
        if (current.studentId == studentId) return current
        // Another student signed in: the stored weeks belong to someone else.
        if (current.weeks.isNotEmpty()) store.clear()
        return AbsenceLessonData(studentId = studentId).also { data = it }
    }

    /** The first moment after the week, so a cache entry from then on holds the final status. */
    private fun weekEndMillis(weekStart: LocalDate): Long =
        weekStart.plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
