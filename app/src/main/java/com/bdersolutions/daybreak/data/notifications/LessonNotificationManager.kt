package com.bdersolutions.daybreak.data.notifications

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
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.data.sync.LessonChange
import com.bdersolutions.daybreak.data.sync.LessonChangeType
import java.util.Locale

class LessonNotificationManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val localized = localizedContext(settingsRepository.current().language)
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = localized.getString(R.string.notification_channel_description)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyChanges(changes: List<LessonChange>) {
        if (changes.isEmpty()) return
        val settings = settingsRepository.current()
        if (!settings.notificationsEnabled) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        ensureChannel()
        val language = settings.language
        val localized = localizedContext(language)
        val lines = changes.take(MAX_LINES).map { describe(it, localized) }.toMutableList()
        val remaining = changes.size - lines.size
        if (remaining > 0) {
            lines += localized.getString(R.string.notification_more_changes, remaining)
        }
        val summary = localized.resources.getQuantityString(
            R.plurals.notification_summary,
            changes.size,
            changes.size,
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(localized.getString(R.string.notification_title))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun describe(change: LessonChange, localized: Context): String {
        val lesson = change.lesson
        val time = lesson.start.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        val subject = lesson.subject.ifBlank { localized.getString(R.string.lesson_unknown_subject) }
        return when (change.type) {
            LessonChangeType.CANCELLED ->
                localized.getString(R.string.notification_cancelled, subject, time)
            LessonChangeType.ROOM_CHANGED ->
                localized.getString(
                    R.string.notification_room_changed,
                    subject,
                    change.previousRoom.ifBlank { "–" },
                    lesson.room.ifBlank { "–" },
                )
            LessonChangeType.TEACHER_CHANGED ->
                localized.getString(
                    R.string.notification_teacher_changed,
                    subject,
                    lesson.teacher.ifBlank { "–" },
                    change.previousTeacher.ifBlank { "–" },
                )
            LessonChangeType.SUBSTITUTION ->
                localized.getString(R.string.notification_substitution, subject, time)
            LessonChangeType.ADDED ->
                localized.getString(R.string.notification_added, subject, time)
        }
    }

    private fun localizedContext(language: AppLanguage): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language.tag))
        return context.createConfigurationContext(config)
    }

    companion object {
        const val CHANNEL_ID = "lesson_changes"
        private const val NOTIFICATION_ID = 4101
        private const val MAX_LINES = 3
    }
}
