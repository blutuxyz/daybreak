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

/** Light, dark or whatever the system is currently using. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Fallback palettes used when Material You dynamic colour is off or unavailable. */
enum class AccentColor { OCEAN, FOREST, LAVENDER, SUNSET, ROSE }

/** How lively the motion is. Both use Material 3 springs; CALM uses fewer overshoots. */
enum class MotionStyle { EXPRESSIVE, CALM }

/** Which timetable view opens first when the app starts. */
enum class StartView { DAY, WEEK }

data class AppSettings(
    val language: AppLanguage = AppLanguage.GERMAN,
    val syncEnabled: Boolean = true,
    val syncIntervalMinutes: Int = 30,
    val notificationsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accent: AccentColor = AccentColor.OCEAN,
    val motionStyle: MotionStyle = MotionStyle.EXPRESSIVE,
    val startView: StartView = StartView.DAY,
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

    fun setThemeMode(mode: ThemeMode) {
        update(_settings.value.copy(themeMode = mode))
    }

    fun setDynamicColor(enabled: Boolean) {
        update(_settings.value.copy(dynamicColor = enabled))
    }

    fun setAccent(accent: AccentColor) {
        update(_settings.value.copy(accent = accent))
    }

    fun setMotionStyle(style: MotionStyle) {
        update(_settings.value.copy(motionStyle = style))
    }

    fun setStartView(view: StartView) {
        update(_settings.value.copy(startView = view))
    }

    private fun update(value: AppSettings) {
        preferences.edit()
            .putString(KEY_LANGUAGE, value.language.tag)
            .putBoolean(KEY_SYNC_ENABLED, value.syncEnabled)
            .putInt(KEY_SYNC_INTERVAL, value.syncIntervalMinutes)
            .putBoolean(KEY_NOTIFICATIONS, value.notificationsEnabled)
            .putString(KEY_THEME_MODE, value.themeMode.name)
            .putBoolean(KEY_DYNAMIC_COLOR, value.dynamicColor)
            .putString(KEY_ACCENT, value.accent.name)
            .putString(KEY_MOTION_STYLE, value.motionStyle.name)
            .putString(KEY_START_VIEW, value.startView.name)
            .apply()
        _settings.value = value
    }

    private fun load(): AppSettings = AppSettings(
        language = AppLanguage.fromTag(preferences.getString(KEY_LANGUAGE, null)),
        syncEnabled = preferences.getBoolean(KEY_SYNC_ENABLED, true),
        syncIntervalMinutes = preferences.getInt(KEY_SYNC_INTERVAL, 30)
            .takeIf { it in SYNC_INTERVALS } ?: 30,
        notificationsEnabled = preferences.getBoolean(KEY_NOTIFICATIONS, true),
        themeMode = enumOrDefault(preferences.getString(KEY_THEME_MODE, null), ThemeMode.SYSTEM),
        dynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, true),
        accent = enumOrDefault(preferences.getString(KEY_ACCENT, null), AccentColor.OCEAN),
        motionStyle = enumOrDefault(preferences.getString(KEY_MOTION_STYLE, null), MotionStyle.EXPRESSIVE),
        startView = enumOrDefault(preferences.getString(KEY_START_VIEW, null), StartView.DAY),
    )

    private inline fun <reified E : Enum<E>> enumOrDefault(raw: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == raw } ?: default

    companion object {
        val SYNC_INTERVALS = listOf(15, 30, 60, 120)
        private const val PREFS_NAME = "daybreak_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_SYNC_INTERVAL = "sync_interval"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_ACCENT = "accent"
        private const val KEY_MOTION_STYLE = "motion_style"
        private const val KEY_START_VIEW = "start_view"
    }
}
