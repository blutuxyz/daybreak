package com.bdersolutions.daybreak.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppLanguage(val tag: String) {
    GERMAN("de"),
    ENGLISH("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: GERMAN

        fun systemLocale(): AppLanguage =
            if (Locale.getDefault().language.equals("en", ignoreCase = true)) ENGLISH else GERMAN
    }
}

data class AppSettings(
    val language: AppLanguage = AppLanguage.GERMAN,
    val syncEnabled: Boolean = true,
    val syncIntervalMinutes: Int = 30,
    val notificationsEnabled: Boolean = true,
)

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun current(): AppSettings = _settings.value

    fun setLanguage(language: AppLanguage) {
        update(_settings.value.copy(language = language))
    }

    fun setSyncEnabled(enabled: Boolean) {
        update(_settings.value.copy(syncEnabled = enabled))
    }

    fun setSyncInterval(minutes: Int) {
        val safe = if (minutes in SYNC_INTERVALS) minutes else 30
        update(_settings.value.copy(syncIntervalMinutes = safe))
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        update(_settings.value.copy(notificationsEnabled = enabled))
    }

    private fun update(value: AppSettings) {
        preferences.edit()
            .putString(KEY_LANGUAGE, value.language.tag)
            .putBoolean(KEY_SYNC_ENABLED, value.syncEnabled)
            .putInt(KEY_SYNC_INTERVAL, value.syncIntervalMinutes)
            .putBoolean(KEY_NOTIFICATIONS, value.notificationsEnabled)
            .apply()
        _settings.value = value
    }

    private fun load(): AppSettings = AppSettings(
        language = AppLanguage.fromTag(preferences.getString(KEY_LANGUAGE, null)),
        syncEnabled = preferences.getBoolean(KEY_SYNC_ENABLED, true),
        syncIntervalMinutes = preferences.getInt(KEY_SYNC_INTERVAL, 30)
            .takeIf { it in SYNC_INTERVALS } ?: 30,
        notificationsEnabled = preferences.getBoolean(KEY_NOTIFICATIONS, true),
    )

    companion object {
        val SYNC_INTERVALS = listOf(15, 30, 60, 120)
        private const val PREFS_NAME = "daybreak_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_SYNC_INTERVAL = "sync_interval"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
    }
}
