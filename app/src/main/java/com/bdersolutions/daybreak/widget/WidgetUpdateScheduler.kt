package com.bdersolutions.daybreak.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.bdersolutions.daybreak.DaybreakApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Widget-only refresh scheduling. Sync itself already updates the widget; this
 * adds the cheap time-based refreshes at midnight and at lesson boundaries.
 */
object WidgetUpdateScheduler {
    private const val ACTION = "com.bdersolutions.daybreak.widget.REFRESH"
    private const val REQUEST_CODE = 41

    fun schedule(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            scheduleNext(context.applicationContext)
        }
    }

    suspend fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextTrigger(context.applicationContext)
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent(context.applicationContext),
            )
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context.applicationContext))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private suspend fun nextTrigger(context: Context): Long {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val midnight = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val app = context.applicationContext as? DaybreakApplication
        val cache = withContext(Dispatchers.IO) {
            app?.container?.timetableCacheStore?.load()
        } ?: return midnight
        val today = LocalDate.now()
        val nextBoundary = cache.weeks.values
            .flatMap { it.lessons }
            .filter { it.date >= today }
            .flatMap { lesson -> listOf(lesson.startsAt, lesson.endsAt) }
            .map { it.atZone(zone).toInstant().toEpochMilli() }
            .filter { it > now + 1_000L }
            .minOrNull()
        return minOf(midnight, nextBoundary ?: midnight)
    }
}
