package com.bdersolutions.daybreak.data.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bdersolutions.daybreak.DaybreakApplication
import com.bdersolutions.daybreak.data.repository.TimetableRepository
import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Plans the next morning summary and the next evening reminder as one-time WorkManager jobs. Each job
 * plans its successor when it ends, and the app plans them again whenever it starts, a sync finishes or
 * a setting changes, so a missed run heals itself. Android may run a job a little late (Doze); the
 * summary therefore comes a lead time before the first lesson, not right at its start.
 */
class ReminderScheduler(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val timetable: TimetableRepository,
    private val sessionStore: SessionStore,
    private val store: ReminderStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Plans what is due and cancels what is switched off. Safe to call often: an existing plan is kept. */
    fun schedule() {
        scope.launch { runCatching { planNow() } }
    }

    /** Forgets the planned morning summary and plans it again, for when its lead time changed. */
    fun reschedule() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_MORNING)
        schedule()
    }

    fun cancelAll() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(TAG_MORNING)
        workManager.cancelAllWorkByTag(TAG_EVENING)
    }

    private suspend fun planNow() {
        val workManager = WorkManager.getInstance(context)
        val settings = settingsRepository.current()
        if (!sessionStore.isLoggedIn() || !settings.notificationsEnabled) {
            cancelAll()
            return
        }
        val now = LocalDateTime.now()

        if (settings.morningSummaryEnabled) {
            timetable.awaitLoaded()
            val plan = ReminderPlanner.nextMorning(
                now = now,
                firstLessonStart = { date -> firstLessonStart(date) },
                leadMinutes = settings.morningLeadMinutes,
                lastSent = store.lastMorning(),
            )
            if (plan != null) {
                enqueue<MorningSummaryWorker>(
                    workManager, "daybreak_morning_${plan.date}", TAG_MORNING, plan.at, plan.date,
                )
            }
        } else {
            workManager.cancelAllWorkByTag(TAG_MORNING)
        }

        if (settings.examRemindersEnabled) {
            val at = ReminderPlanner.nextEvening(now, store.lastEvening())
            enqueue<EveningReminderWorker>(
                workManager, "daybreak_evening_${at.toLocalDate()}", TAG_EVENING, at, at.toLocalDate(),
            )
        } else {
            workManager.cancelAllWorkByTag(TAG_EVENING)
        }
    }

    private fun firstLessonStart(date: LocalDate): LocalTime? =
        timetable.lessonsFor(date).filter { !it.isHoliday && !it.isCancelled }.minOfOrNull { it.start }

    private inline fun <reified W : CoroutineWorker> enqueue(
        workManager: WorkManager,
        name: String,
        tag: String,
        at: LocalDateTime,
        date: LocalDate,
    ) {
        val delay = Duration.between(LocalDateTime.now(), at).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<W>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(KEY_DATE, date.toEpochDay()).build())
            .addTag(tag)
            .build()
        // KEEP: a job that is already waiting for this day stays as it is.
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val KEY_DATE = "date"
        private const val TAG_MORNING = "daybreak_morning_summary"
        private const val TAG_EVENING = "daybreak_evening_reminder"
    }
}

/** Sends the morning summary for the day it was planned for, then plans the next one. */
class MorningSummaryWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? DaybreakApplication)?.container ?: return Result.success()
        if (!container.sessionStore.isLoggedIn()) return Result.success()
        val date = inputData.getLong(ReminderScheduler.KEY_DATE, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
        runCatching { container.reminderNotifier.sendMorningSummary(date) }
        container.reminderScheduler.schedule()
        return Result.success()
    }
}

/** Sends the evening reminder, then plans the next one. */
class EveningReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? DaybreakApplication)?.container ?: return Result.success()
        if (!container.sessionStore.isLoggedIn()) return Result.success()
        runCatching { container.reminderNotifier.sendEveningReminder(LocalDate.now()) }
        container.reminderScheduler.schedule()
        return Result.success()
    }
}
