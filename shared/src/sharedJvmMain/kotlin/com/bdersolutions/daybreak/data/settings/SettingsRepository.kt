package com.bdersolutions.daybreak.data.settings

import com.bdersolutions.daybreak.platform.KeyValueStore
import com.bdersolutions.daybreak.data.branding.AppIconStyle
import com.bdersolutions.daybreak.data.branding.AppNamePreset
import com.bdersolutions.daybreak.data.branding.AppNaming
import com.bdersolutions.daybreak.data.haptics.HapticSettings
import com.bdersolutions.daybreak.data.reminders.ReminderPlanner
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
    /** How often the app looks for a new version in the background, in hours. */
    val updateCheckHours: Int = 12,
    val haptics: HapticSettings = HapticSettings(),
    /** Supporter extra: the name under the app icon. Only used while a payment is confirmed. */
    val appNamePreset: AppNamePreset = AppNamePreset.DAYBREAK,
    /** Supporter extra: a name for the title in the app. Empty means the preset's name. */
    val customAppName: String = "",
    /** Supporter extra: the picture of the launcher icon. Only used while a payment is confirmed. */
    val appIconStyle: AppIconStyle = AppIconStyle.STANDARD,
    /** The "Now and next" card at the top of today's timetable. */
    val showNowNext: Boolean = true,
    /** A notification before the first lesson with the day's lessons, changes and exams. */
    val morningSummaryEnabled: Boolean = true,
    /** How many minutes before the first lesson the morning summary comes. */
    val morningLeadMinutes: Int = ReminderPlanner.DEFAULT_LEAD_MINUTES,
    /** An evening notification for exams and homework that are due the next day. */
    val examRemindersEnabled: Boolean = true,
    /** How the home screen widget looks and what it shows. */
    val widget: WidgetSettings = WidgetSettings(),
    /** Layout of the timetable screen: zoom, overlapping lessons, rolling over to next week. */
    val display: TimetableDisplay = TimetableDisplay(),
)

class SettingsRepository(private val preferences: KeyValueStore) {
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

    fun setAppNamePreset(preset: AppNamePreset) {
        update(_settings.value.copy(appNamePreset = preset))
    }

    fun setShowNowNext(show: Boolean) {
        update(_settings.value.copy(showNowNext = show))
    }

    fun setMorningSummaryEnabled(enabled: Boolean) {
        update(_settings.value.copy(morningSummaryEnabled = enabled))
    }

    fun setMorningLeadMinutes(minutes: Int) {
        val safe = if (minutes in ReminderPlanner.LEAD_MINUTES) minutes else ReminderPlanner.DEFAULT_LEAD_MINUTES
        update(_settings.value.copy(morningLeadMinutes = safe))
    }

    fun setExamRemindersEnabled(enabled: Boolean) {
        update(_settings.value.copy(examRemindersEnabled = enabled))
    }

    fun setAppIconStyle(style: AppIconStyle) {
        update(_settings.value.copy(appIconStyle = style))
    }

    fun setCustomAppName(name: String) {
        update(_settings.value.copy(customAppName = AppNaming.sanitizeCustom(name)))
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

    fun setUpdateCheckHours(hours: Int) {
        val safe = if (hours in UPDATE_CHECK_HOURS) hours else 12
        update(_settings.value.copy(updateCheckHours = safe))
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

    fun updateDisplay(transform: (TimetableDisplay) -> TimetableDisplay) {
        update(_settings.value.copy(display = transform(_settings.value.display).normalized()))
    }

    fun updateWidget(transform: (WidgetSettings) -> WidgetSettings) {
        update(_settings.value.copy(widget = transform(_settings.value.widget).normalized()))
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
            .putInt(KEY_UPDATE_CHECK_HOURS, value.updateCheckHours)
            .putString(KEY_HAPTICS, HapticSettingsCodec.encode(value.haptics))
            .putString(KEY_APP_NAME_PRESET, value.appNamePreset.name)
            .putString(KEY_CUSTOM_APP_NAME, value.customAppName)
            .putString(KEY_APP_ICON_STYLE, value.appIconStyle.name)
            .putBoolean(KEY_SHOW_NOW_NEXT, value.showNowNext)
            .putBoolean(KEY_MORNING_SUMMARY, value.morningSummaryEnabled)
            .putInt(KEY_MORNING_LEAD, value.morningLeadMinutes)
            .putBoolean(KEY_EXAM_REMINDERS, value.examRemindersEnabled)
            .putString(KEY_WIDGET_THEME, value.widget.theme.name)
            .putInt(KEY_WIDGET_OPACITY, value.widget.backgroundOpacity)
            .putString(KEY_WIDGET_DETAIL, value.widget.detail.name)
            .putBoolean(KEY_WIDGET_HEADLINE, value.widget.showHeadline)
            .putBoolean(KEY_WIDGET_CANCELLED, value.widget.showCancelled)
            .putBoolean(KEY_WIDGET_NEXT_DAY, value.widget.nextDayAfterSchool)
            .putBoolean(KEY_WIDGET_SYNC_TIME, value.widget.showSyncTime)
            .putBoolean(KEY_DISPLAY_NEXT_WEEK, value.display.autoNextWeek)
            .putInt(KEY_DISPLAY_DAY_SCALE, value.display.dayScale)
            .putInt(KEY_DISPLAY_WEEK_SCALE, value.display.weekScale)
            .putBoolean(KEY_DISPLAY_WEEK_FITS, value.display.weekFitsScreen)
            .putString(KEY_DISPLAY_CONCURRENT, value.display.concurrent.name)
            .putBoolean(KEY_DISPLAY_CANCELLED, value.display.showCancelled)
            .putBoolean(KEY_DISPLAY_FREE_BANDS, value.display.showFreeBands)
            .putString(KEY_DISPLAY_WEEK_DETAIL, value.display.weekDetail.name)
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
        updateCheckHours = preferences.getInt(KEY_UPDATE_CHECK_HOURS, 12).takeIf { it in UPDATE_CHECK_HOURS } ?: 12,
        haptics = HapticSettingsCodec.decode(preferences.getString(KEY_HAPTICS, null)),
        appNamePreset = AppNamePreset.fromName(preferences.getString(KEY_APP_NAME_PRESET, null)),
        customAppName = AppNaming.sanitizeCustom(preferences.getString(KEY_CUSTOM_APP_NAME, null).orEmpty()),
        appIconStyle = AppIconStyle.fromName(preferences.getString(KEY_APP_ICON_STYLE, null)),
        showNowNext = preferences.getBoolean(KEY_SHOW_NOW_NEXT, true),
        morningSummaryEnabled = preferences.getBoolean(KEY_MORNING_SUMMARY, true),
        morningLeadMinutes = preferences.getInt(KEY_MORNING_LEAD, ReminderPlanner.DEFAULT_LEAD_MINUTES)
            .takeIf { it in ReminderPlanner.LEAD_MINUTES } ?: ReminderPlanner.DEFAULT_LEAD_MINUTES,
        examRemindersEnabled = preferences.getBoolean(KEY_EXAM_REMINDERS, true),
        widget = WidgetSettings(
            theme = enumOrDefault(preferences.getString(KEY_WIDGET_THEME, null), WidgetTheme.APP),
            backgroundOpacity = preferences.getInt(KEY_WIDGET_OPACITY, 100),
            detail = enumOrDefault(preferences.getString(KEY_WIDGET_DETAIL, null), WidgetDetail.ROOM),
            showHeadline = preferences.getBoolean(KEY_WIDGET_HEADLINE, true),
            showCancelled = preferences.getBoolean(KEY_WIDGET_CANCELLED, true),
            nextDayAfterSchool = preferences.getBoolean(KEY_WIDGET_NEXT_DAY, true),
            showSyncTime = preferences.getBoolean(KEY_WIDGET_SYNC_TIME, true),
        ).normalized(),
        display = TimetableDisplay(
            autoNextWeek = preferences.getBoolean(KEY_DISPLAY_NEXT_WEEK, true),
            dayScale = preferences.getInt(KEY_DISPLAY_DAY_SCALE, 100),
            weekScale = preferences.getInt(KEY_DISPLAY_WEEK_SCALE, 100),
            weekFitsScreen = preferences.getBoolean(KEY_DISPLAY_WEEK_FITS, false),
            concurrent = enumOrDefault(preferences.getString(KEY_DISPLAY_CONCURRENT, null), ConcurrentLessons.COMBINED),
            showCancelled = preferences.getBoolean(KEY_DISPLAY_CANCELLED, true),
            showFreeBands = preferences.getBoolean(KEY_DISPLAY_FREE_BANDS, true),
            weekDetail = enumOrDefault(preferences.getString(KEY_DISPLAY_WEEK_DETAIL, null), WeekDetail.ROOM),
        ).normalized(),
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

        /** The choices for how often the app looks for updates, in hours. */
        val UPDATE_CHECK_HOURS = listOf(6, 12, 24, 72)
        const val PREFS_NAME = "daybreak_settings"
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
        private const val KEY_UPDATE_CHECK_HOURS = "update_check_hours"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_APP_NAME_PRESET = "app_name_preset"
        private const val KEY_CUSTOM_APP_NAME = "custom_app_name"
        private const val KEY_APP_ICON_STYLE = "app_icon_style"
        private const val KEY_SHOW_NOW_NEXT = "show_now_next"
        private const val KEY_MORNING_SUMMARY = "morning_summary"
        private const val KEY_MORNING_LEAD = "morning_lead_minutes"
        private const val KEY_EXAM_REMINDERS = "exam_reminders"
        private const val KEY_WIDGET_THEME = "widget_theme"
        private const val KEY_WIDGET_OPACITY = "widget_background_opacity"
        private const val KEY_WIDGET_DETAIL = "widget_detail"
        private const val KEY_WIDGET_HEADLINE = "widget_headline"
        private const val KEY_WIDGET_CANCELLED = "widget_show_cancelled"
        private const val KEY_WIDGET_NEXT_DAY = "widget_next_day_after_school"
        private const val KEY_WIDGET_SYNC_TIME = "widget_show_sync_time"
        private const val KEY_DISPLAY_NEXT_WEEK = "display_auto_next_week"
        private const val KEY_DISPLAY_DAY_SCALE = "display_day_scale"
        private const val KEY_DISPLAY_WEEK_SCALE = "display_week_scale"
        private const val KEY_DISPLAY_WEEK_FITS = "display_week_fits_screen"
        private const val KEY_DISPLAY_CONCURRENT = "display_concurrent"
        private const val KEY_DISPLAY_CANCELLED = "display_show_cancelled"
        private const val KEY_DISPLAY_FREE_BANDS = "display_free_bands"
        private const val KEY_DISPLAY_WEEK_DETAIL = "display_week_detail"
        private val gson = com.google.gson.Gson()
    }
}
