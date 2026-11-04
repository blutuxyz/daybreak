package com.bdersolutions.daybreak.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bdersolutions.daybreak.MainActivity
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import java.util.Locale

/** Tells the user that an update is waiting. Tapping the notification opens the Updates screen. */
class UpdateNotificationManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val localized = localizedContext()
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = localized.getString(R.string.update_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    /** Posts the notification for [release]. [downloaded] says whether it can be installed at once. */
    fun notifyUpdate(release: ReleaseInfo, downloaded: Boolean) {
        if (!settingsRepository.current().notificationsEnabled) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        ensureChannel()
        val localized = localizedContext()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = localized.getString(
            if (downloaded) R.string.update_notification_ready else R.string.update_notification_available,
            release.versionName,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(localized.getString(R.string.update_notification_title))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun localizedContext(): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(settingsRepository.current().language.tag))
        return context.createConfigurationContext(config)
    }

    companion object {
        const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 4102
    }
}
