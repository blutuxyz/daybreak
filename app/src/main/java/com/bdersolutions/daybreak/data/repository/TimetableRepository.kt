package com.bdersolutions.daybreak.data.repository

import com.bdersolutions.daybreak.data.cache.CachedWeek
import com.bdersolutions.daybreak.data.cache.TimetableCache
import com.bdersolutions.daybreak.data.cache.TimetableCacheStore
import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.untis.UntisApi
import com.bdersolutions.daybreak.untis.UntisAuthException
import com.bdersolutions.daybreak.untis.UntisSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class TimetableSyncStatus(
    val isLoading: Boolean = false,
    val lastSyncAt: Long = 0L,
    val lastError: Throwable? = null,
)

data class RefreshResult(
    val previousWeeks: Map<LocalDate, CachedWeek>,
    val currentWeeks: Map<LocalDate, CachedWeek>,
    val successCount: Int,
    val error: Throwable?,
) {
    val hasData: Boolean get() = currentWeeks.isNotEmpty()
}

/**
 * Offline-first timetable store. A cached week is served immediately; callers can
 * request a background refresh or lazily load a week that is not in the cache yet.
 */
class TimetableRepository(
    private val cacheStore: TimetableCacheStore,
    private val sessionStore: SessionStore,
    private val api: UntisApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val cacheLoaded = CompletableDeferred<Unit>()

    private val _cache = MutableStateFlow(TimetableCache())
    val cache: StateFlow<TimetableCache> = _cache.asStateFlow()

    private val _status = MutableStateFlow(TimetableSyncStatus())
    val status: StateFlow<TimetableSyncStatus> = _status.asStateFlow()

    init {
        scope.launch {
            val loaded = runCatching { cacheStore.load() }.getOrDefault(TimetableCache())
            val studentId = sessionStore.session.value?.studentId ?: 0L
            if (loaded.studentId == studentId || loaded.studentId == 0L) {
                _cache.value = loaded
                _status.value = TimetableSyncStatus(lastSyncAt = loaded.lastSyncAt)
            } else {
                runCatching { cacheStore.clear() }
            }
            cacheLoaded.complete(Unit)
        }
    }

    fun weekStartFor(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekFor(date: LocalDate): CachedWeek? {
        val start = weekStartFor(date)
        return _cache.value.weeks[start]
    }

    fun lessonsFor(date: LocalDate): List<com.bdersolutions.daybreak.untis.UntisLesson> =
        weekFor(date)?.lessons?.filter { it.date == date }.orEmpty().sortedBy { it.start }

    suspend fun ensureWeek(date: LocalDate, force: Boolean = false): RefreshResult {
        cacheLoaded.await()
        val weekStart = weekStartFor(date)
        val cached = _cache.value.weeks[weekStart]
        val fresh = cached != null &&
            System.currentTimeMillis() - cached.updatedAt < FRESHNESS_MS &&
            _cache.value.studentId == (sessionStore.session.value?.studentId ?: 0L)
        if (!force && fresh) {
            return RefreshResult(
                previousWeeks = emptyMap(),
                currentWeeks = mapOf(weekStart to cached!!),
                successCount = 0,
                error = null,
            )
        }
        return fetchWeeks(listOf(weekStart), force = force)
    }

    suspend fun refreshWeeks(weekStarts: Collection<LocalDate>): RefreshResult {
        cacheLoaded.await()
        val normalized = weekStarts.map(::weekStartFor).distinct()
        return fetchWeeks(normalized, force = true)
    }

    suspend fun refreshCurrentWeek(): RefreshResult =
        refreshWeeks(listOf(weekStartFor(LocalDate.now())))

    suspend fun clear() {
        cacheLoaded.await()
        mutex.withLock {
            _cache.value = TimetableCache()
            _status.value = TimetableSyncStatus()
            cacheStore.clear()
        }
    }

    private suspend fun fetchWeeks(weeks: List<LocalDate>, force: Boolean): RefreshResult {
        cacheLoaded.await()
        return mutex.withLock {
            val credentials = sessionStore.currentCredentials()
            if (credentials == null) {
                if (sessionStore.session.value != null) {
                    sessionStore.clear()
                }
                throw UntisAuthException("The WebUntis key is missing. Please sign in again.")
            }
            val currentSession = credentials.session
            if (_cache.value.studentId != 0L &&
                _cache.value.studentId != currentSession.studentId
            ) {
                _cache.value = TimetableCache()
                _status.value = TimetableSyncStatus()
                cacheStore.clear()
            }

            val previousRequested = weeks.mapNotNull { week ->
                _cache.value.weeks[week]?.let { week to it }
            }.toMap()

            _status.value = _status.value.copy(isLoading = true, lastError = null)
            var workingCache = _cache.value.copy(studentId = currentSession.studentId)
            var nextError: Throwable? = null
            var successes = 0

            val untisSession = UntisSession(
                server = currentSession.server,
                school = currentSession.school,
                username = currentSession.username,
                secret = credentials.secret,
                studentId = currentSession.studentId,
                studentName = currentSession.studentName,
                studentType = currentSession.studentType,
            )

            for (weekStart in weeks) {
                val cached = workingCache.weeks[weekStart]
                val fresh = cached != null &&
                    System.currentTimeMillis() - cached.updatedAt < FRESHNESS_MS
                if (!force && fresh) continue

                try {
                    val timetable = api.fetchTimetable(
                        session = untisSession,
                        startDate = weekStart,
                        endDate = weekStart.plusDays(6),
                    )
                    workingCache = workingCache.copy(
                        subjectNames = workingCache.subjectNames + timetable.directory.subjectNamesById,
                        // Every timetable answer carries the whole holiday list, so the newest one wins.
                        holidays = timetable.directory.holidays.ifEmpty { workingCache.holidays },
                        weeks = workingCache.weeks + (
                            weekStart to CachedWeek(
                                weekStart = weekStart,
                                lessons = timetable.lessons,
                                updatedAt = System.currentTimeMillis(),
                            )
                            ),
                    )
                    successes++
                } catch (error: Throwable) {
                    if (nextError == null) nextError = error
                    if (error is UntisAuthException && error.apiCode == -8520) {
                        // Authentication failure affects every later request.
                        break
                    }
                }
            }

            // Exams are an extra. A failed exam request must not hide a good timetable, so it
            // is isolated, and the exams for this range are replaced only when it succeeds.
            // The exam window covers every cached week, not only the requested ones, so exams
            // stored before subject names were known are refreshed with the names too.
            val window = (workingCache.weeks.keys + weeks)
            if (window.isNotEmpty() && nextError !is UntisAuthException) {
                val from = window.min()
                val to = window.max().plusDays(6)
                runCatching {
                    api.fetchExams(
                        session = untisSession,
                        startDate = from,
                        endDate = to,
                        subjectNames = workingCache.subjectNames,
                    )
                }.onSuccess { exams ->
                    val kept = workingCache.exams.filterNot { it.date in from..to }
                    workingCache = workingCache.copy(exams = kept + exams)
                }
                // Absences work the same way: they only grey out the part of a lesson the student
                // missed, so a failure here must not hide the timetable either.
                runCatching {
                    api.fetchAbsences(session = untisSession, startDate = from, endDate = to)
                }.onSuccess { absences ->
                    val kept = workingCache.absences.filterNot {
                        it.start.toLocalDate() in from..to || it.end.toLocalDate() in from..to
                    }
                    workingCache = workingCache.copy(absences = kept + absences)
                }
            }

            val now = System.currentTimeMillis()
            val updatedCache = if (successes > 0) {
                workingCache.copy(lastSyncAt = now)
            } else {
                workingCache
            }
            _cache.value = updatedCache
            if (successes > 0) {
                cacheStore.save(updatedCache)
            }
            _status.value = TimetableSyncStatus(
                isLoading = false,
                lastSyncAt = updatedCache.lastSyncAt,
                lastError = nextError,
            )

            RefreshResult(
                previousWeeks = previousRequested,
                currentWeeks = weeks.mapNotNull { week ->
                    _cache.value.weeks[week]?.let { week to it }
                }.toMap(),
                successCount = successes,
                error = nextError,
            )
        }
    }

    private companion object {
        const val FRESHNESS_MS = 10 * 60 * 1000L
    }
}
