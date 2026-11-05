package com.bdersolutions.daybreak.data.settings

import android.content.Context
import com.bdersolutions.daybreak.data.haptics.HapticSettings
import com.bdersolutions.daybreak.data.haptics.HapticSettingsCodec
import com.bdersolutions.daybreak.ui.absences.AbsenceCategory
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

/** How missed time is shown on the absences page: as hours and minutes, or in school lessons of 50 minutes. */
enum class AbsenceUnit { MINUTES, LESSONS }

/**
 * How lesson stripes pick their colour. [ACCENT_HUES] spreads subjects around the
 * accent hue; [ROLES] uses the primary, secondary and tertiary role families.
 */
enum class SubjectColorMode { ACCENT_HUES, ROLES }

/** App-wide text size, applied on top of the system font scale. */
enum class TextScale(val multiplier: Float) {
    STANDARD(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f),
}

/**
 * Lesson colour choices. Colours are ARGB values; a missing entry keeps the automatic
 * colour. [fillWholeCard] colours the whole lesson card instead of only its stripe.
 */
data class LessonColorSettings(
    val subjectColors: Map<String, Long> = emptyMap(),
    val cancelledColor: Long? = null,
    val examColor: Long? = null,
    val substitutionColor: Long? = null,
    val fillWholeCard: Boolean = false,
)

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
    val absenceUnit: AbsenceUnit = AbsenceUnit.MINUTES,
    val subjectColorMode: SubjectColorMode = SubjectColorMode.ACCENT_HUES,
    val textScale: TextScale = TextScale.STANDARD,
    val lessonColors: LessonColorSettings = LessonColorSettings(),
    val absenceCategories: List<AbsenceCategory> = emptyList(),
    val favoriteTargets: List<FavoriteTarget> = emptyList(),
    /** Look for new versions in the background and download them on Wi-Fi. */
    val autoCheckUpdates: Boolean = true,
    val haptics: HapticSettings = HapticSettings(),
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

    fun setAbsenceUnit(unit: AbsenceUnit) {
        update(_settings.value.copy(absenceUnit = unit))
    }

    fun setStartView(view: StartView) {
        update(_settings.value.copy(startView = view))
    }

    fun setSubjectColorMode(mode: SubjectColorMode) {
        update(_settings.value.copy(subjectColorMode = mode))
    }

    fun setTextScale(scale: TextScale) {
        update(_settings.value.copy(textScale = scale))
    }

    fun updateLessonColors(transform: (LessonColorSettings) -> LessonColorSettings) {
        update(_settings.value.copy(lessonColors = transform(_settings.value.lessonColors)))
    }

    fun updateAbsenceCategories(transform: (List<AbsenceCategory>) -> List<AbsenceCategory>) {
        update(_settings.value.copy(absenceCategories = transform(_settings.value.absenceCategories)))
    }

    /** Categories belong to one student's subjects, so they are dropped on logout. */
    fun clearAbsenceCategories() {
        updateAbsenceCategories { emptyList() }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        update(_settings.value.copy(autoCheckUpdates = enabled))
    }

    fun updateFavoriteTargets(transform: (List<FavoriteTarget>) -> List<FavoriteTarget>) {
        update(_settings.value.copy(favoriteTargets = transform(_settings.value.favoriteTargets)))
    }

    /** Favourites are elements of one school's master data, so they are dropped on logout. */
    fun clearFavoriteTargets() {
        updateFavoriteTargets { emptyList() }
    }

    fun updateHaptics(transform: (HapticSettings) -> HapticSettings) {
        update(_settings.value.copy(haptics = transform(_settings.value.haptics)))
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
            .putString(KEY_ABSENCE_UNIT, value.absenceUnit.name)
            .putString(KEY_SUBJECT_COLOR_MODE, value.subjectColorMode.name)
            .putString(KEY_TEXT_SCALE, value.textScale.name)
            .putString(KEY_LESSON_COLORS, gson.toJson(value.lessonColors))
            .putString(KEY_ABSENCE_CATEGORIES, gson.toJson(value.absenceCategories))
            .putString(KEY_FAVORITE_TARGETS, gson.toJson(value.favoriteTargets))
            .putBoolean(KEY_AUTO_CHECK_UPDATES, value.autoCheckUpdates)
            .putString(KEY_HAPTICS, HapticSettingsCodec.encode(value.haptics))
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
        absenceUnit = enumOrDefault(preferences.getString(KEY_ABSENCE_UNIT, null), AbsenceUnit.MINUTES),
        subjectColorMode = enumOrDefault(
            preferences.getString(KEY_SUBJECT_COLOR_MODE, null),
            SubjectColorMode.ACCENT_HUES,
        ),
        textScale = enumOrDefault(preferences.getString(KEY_TEXT_SCALE, null), TextScale.STANDARD),
        lessonColors = decodeLessonColors(preferences.getString(KEY_LESSON_COLORS, null)),
        absenceCategories = decodeAbsenceCategories(preferences.getString(KEY_ABSENCE_CATEGORIES, null)),
        favoriteTargets = decodeFavoriteTargets(preferences.getString(KEY_FAVORITE_TARGETS, null)),
        autoCheckUpdates = preferences.getBoolean(KEY_AUTO_CHECK_UPDATES, true),
        haptics = HapticSettingsCodec.decode(preferences.getString(KEY_HAPTICS, null)),
    )

    private fun decodeFavoriteTargets(json: String?): List<FavoriteTarget> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : com.google.gson.reflect.TypeToken<List<FavoriteTarget?>>() {}.type
        val decoded: List<FavoriteTarget?>? = runCatching { gson.fromJson<List<FavoriteTarget?>>(json, type) }
            .getOrNull()
        // A field missing from the JSON arrives as null despite the Kotlin type, which drops the entry.
        return decoded.orEmpty().filterNotNull().filter { favorite ->
            runCatching { favorite.name.isNotBlank() && favorite.toElement() != null }.getOrDefault(false)
        }.distinctBy { it.key }
    }

    private fun decodeAbsenceCategories(json: String?): List<AbsenceCategory> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : com.google.gson.reflect.TypeToken<List<AbsenceCategory?>>() {}.type
        val decoded: List<AbsenceCategory?>? = runCatching { gson.fromJson<List<AbsenceCategory?>>(json, type) }
            .getOrNull()
        // Gson ignores Kotlin defaults, so a field missing from the JSON arrives as null or 0.
        return decoded.orEmpty().filterNotNull().filter { category ->
            // A null String that slipped past the type system throws here, which drops the entry.
            runCatching {
                category.id.isNotEmpty() && category.subject.isNotBlank() &&
                    category.name.length >= 0 &&
                    (category.maxLessons > 0 || category.maxPercent in 1..100)
            }.getOrDefault(false)
        }
    }

    private fun decodeLessonColors(json: String?): LessonColorSettings {
        if (json.isNullOrBlank()) return LessonColorSettings()
        return runCatching { gson.fromJson(json, LessonColorSettings::class.java) }
            .getOrNull()
            ?.let { decoded ->
                // Gson can leave a missing map as null, so repair it instead of trusting it.
                val colors: Map<String, Long>? = decoded.subjectColors
                decoded.copy(subjectColors = colors.orEmpty())
            }
            ?: LessonColorSettings()
    }

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
        private const val KEY_ABSENCE_UNIT = "absence_unit"
        private const val KEY_SUBJECT_COLOR_MODE = "subject_color_mode"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_LESSON_COLORS = "lesson_colors"
        private const val KEY_ABSENCE_CATEGORIES = "absence_categories"
        private const val KEY_FAVORITE_TARGETS = "favorite_targets"
        private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
        private const val KEY_HAPTICS = "haptics"
        private val gson = com.google.gson.Gson()
    }
}
