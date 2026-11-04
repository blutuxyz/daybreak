package com.bdersolutions.daybreak.data.update

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bdersolutions.daybreak.DaybreakApplication
import java.util.concurrent.TimeUnit

/**
 * Looks for a new version in the background. If one is found and the connection is not metered,
 * it also downloads the APK, so the update is ready to install with one tap.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? DaybreakApplication)?.container ?: return Result.success()
        val manager = container.updateManager
        if (!manager.isConfigured || !container.settingsRepository.current().autoCheckUpdates) {
            return Result.success()
        }

        val state = manager.check()
        if (state is UpdateState.Failed) return Result.retry()

        val release = when (state) {
            is UpdateState.Available -> state.release
            is UpdateState.Ready -> state.release
            else -> return Result.success()
        }
        if (state is UpdateState.Available && !isMetered()) {
            manager.download()
        }
        if (manager.shouldNotify(release)) {
            container.updateNotificationManager.notifyUpdate(
                release = release,
                downloaded = manager.state.value is UpdateState.Ready,
            )
        }
        return Result.success()
    }

    private fun isMetered(): Boolean {
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
        return connectivity?.isActiveNetworkMetered ?: true
    }
}

/** Runs [UpdateCheckWorker] twice a day while the setting is on. */
class UpdateScheduler(private val context: Context) {
    fun schedule(enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "daybreak_update_check"
    }
}
