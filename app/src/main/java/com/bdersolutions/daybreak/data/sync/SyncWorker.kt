package com.bdersolutions.daybreak.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bdersolutions.daybreak.DaybreakApplication
import com.bdersolutions.daybreak.untis.UntisNetworkException
import java.time.LocalDate

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? DaybreakApplication ?: return Result.success()
        val container = application.container
        if (!container.sessionStore.isLoggedIn()) {
            container.syncScheduler.cancelAll()
            return Result.success()
        }
        val settings = container.settingsRepository.current()
        if (!settings.syncEnabled) return Result.success()

        val today = LocalDate.now()
        val thisWeek = container.timetableRepository.weekStartFor(today)
        val nextWeek = thisWeek.plusWeeks(1)
        val result = runCatching {
            container.timetableRepository.refreshWeeks(listOf(thisWeek, nextWeek))
        }.getOrElse { error ->
            return if (error is UntisNetworkException) Result.retry() else Result.failure()
        }

        if (result.successCount > 0) {
            val changes = LessonChangeDetector.detect(result.previousWeeks, result.currentWeeks)
            if (changes.isNotEmpty()) {
                container.lessonNotificationManager.notifyChanges(changes)
            }
            runCatching { container.widgetUpdater.update() }
            return Result.success()
        }

        return when (result.error) {
            is UntisNetworkException -> Result.retry()
            null -> Result.success()
            else -> Result.failure()
        }
    }
}
