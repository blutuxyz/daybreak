package com.bdersolutions.daybreak.data.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.bdersolutions.daybreak.data.reminders.ReminderScheduler
import com.bdersolutions.daybreak.data.sync.SyncScheduler
import com.bdersolutions.daybreak.data.update.UpdateScheduler
import java.util.Locale

/**
 * The assistant's view of the settings: it reads them and changes them, and runs the same side effects the
 * Settings pages run after a change (schedules, notification channels, the app language). Supporter extras
 * change only while a payment is confirmed.
 */
class SettingsAssistantPort(
    private val settings: SettingsRepository,
    private val paid: () -> Boolean,
    private val syncScheduler: SyncScheduler,
    private val reminderScheduler: ReminderScheduler,
    private val updateScheduler: UpdateScheduler,
    private val ensureNotificationChannel: () -> Unit,
) : AssistantSettingsPort {

    override fun describe(): String = AgentSettings.describe(settings.current())

    override fun apply(changes: Map<String, String>): SettingsPlan {
        val before = settings.current()
        val plan = AgentSettings.plan(before, changes, paid())
        val next = plan.settings ?: return plan
        if (plan.changes.isEmpty()) return plan
        settings.replaceAll(next)
        sideEffects(before, next, plan.changes.map { it.path })
        return plan
    }

    private fun sideEffects(before: AppSettings, after: AppSettings, paths: List<String>) {
        if (paths.any { it.startsWith("sync.") }) syncScheduler.schedule(after)
        if (paths.any { it.startsWith("updates.") }) updateScheduler.schedule(after.autoCheckUpdates, after.updateCheckHours)
        if (paths.any { it.startsWith("reminders.morningLead") }) {
            reminderScheduler.reschedule()
        } else if (paths.any { it.startsWith("reminders.") }) {
            reminderScheduler.schedule()
        }
        if (paths.any { it.startsWith("notifications.") } || paths.contains("language")) ensureNotificationChannel()
        if (after.language != before.language) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(Locale.forLanguageTag(after.language.tag).toLanguageTag()),
            )
        }
    }
}
