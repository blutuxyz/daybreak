package com.bdersolutions.daybreak.data.settings

import com.bdersolutions.daybreak.data.branding.AppIconStyle
import com.bdersolutions.daybreak.data.branding.AppNamePreset
import com.bdersolutions.daybreak.data.haptics.HapticCategory
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.data.haptics.HapticPreset
import com.bdersolutions.daybreak.data.haptics.HapticSettings
import com.bdersolutions.daybreak.data.haptics.HapticTexture
import com.bdersolutions.daybreak.data.reminders.ReminderPlanner
import java.util.Locale

/**
 * Every setting the assistant can change, one entry each, with its kind, the values it accepts and what it
 * means. The assistant reads them with get_settings and changes them with change_settings; the same catalogue
 * checks every value, so nothing outside it can be set. Pure, so it is tested.
 *
 * Not in the catalogue: the absence categories and the favourite timetable targets. Those are lists of items the
 * student makes on their own pages, not settings.
 */
sealed interface SettingKind {
    data object Bool : SettingKind
    data class Whole(val min: Int, val max: Int) : SettingKind
    data class Decimal(val min: Float, val max: Float) : SettingKind
    data class Choice(val options: List<String>) : SettingKind
    data class Text(val maxLength: Int) : SettingKind

    /** A colour as #RRGGBB, or "auto" for the app's own colour. */
    data object Color : SettingKind
}

/** One setting: its path (what the assistant writes), its kind, what it does, and how it is read and written. */
class SettingDef(
    val path: String,
    val kind: SettingKind,
    val description: String,
    /** Supporter extras change only with a confirmed payment, as in the Settings pages. */
    val supporterOnly: Boolean,
    val read: (AppSettings) -> String,
    val write: (AppSettings, String) -> AppSettings,
)

/** One value that changed: [from] and [to] as the assistant sees them. */
data class SettingChange(val path: String, val from: String, val to: String)

/** The outcome of checking a set of changes: the new settings and what changed, or the reasons it was refused. */
class SettingsPlan(val settings: AppSettings?, val changes: List<SettingChange>, val errors: List<String>) {
    val accepted: Boolean get() = settings != null
}

/** What the assistant's settings tools need from the app. Implemented on the phone; tests use a fake. */
interface AssistantSettingsPort {
    /** Every setting with its current value and the values it accepts, as text for the model. */
    fun describe(): String

    /** Checks and applies [changes] (path to value text) at once. Returns what changed. Refused changes change nothing. */
    fun apply(changes: Map<String, String>): SettingsPlan
}

object AgentSettings {
    private val all: List<SettingDef> by lazy { build() }

    /** The fixed settings and the families (one entry per haptic category and event). Subject colours are open-ended. */
    fun definitions(): List<SettingDef> = all

    /** The entry for [path], or null when the assistant may not set it. */
    fun find(path: String): SettingDef? {
        all.firstOrNull { it.path == path }?.let { return it }
        val prefix = SUBJECT_PREFIX
        if (path.startsWith(prefix) && path.length > prefix.length) return subjectColor(path.removePrefix(prefix))
        return null
    }

    /**
     * Checks [changes] against the catalogue and applies them to [current]. Nothing is applied when any change is
     * refused: an unknown path, a value the setting does not take, or a supporter extra without a payment.
     */
    fun plan(current: AppSettings, changes: Map<String, String>, paid: Boolean): SettingsPlan {
        val errors = mutableListOf<String>()
        val checked = LinkedHashMap<SettingDef, String>()
        for ((path, raw) in changes) {
            val def = find(path)
            if (def == null) {
                errors += "There is no setting \"$path\"."
                continue
            }
            if (def.supporterOnly && !paid) {
                errors += "\"$path\" is a supporter extra and needs a confirmed payment."
                continue
            }
            val value = checkValue(def.kind, raw)
            if (value == null) {
                errors += "\"$path\" does not take \"$raw\": ${allowed(def.kind)}."
                continue
            }
            checked[def] = value
        }
        if (errors.isNotEmpty()) return SettingsPlan(null, emptyList(), errors)

        var next = current
        for ((def, value) in checked) next = def.write(next, value)
        // Changing single haptic values turns the preset into Custom, or keeps the one that still matches.
        next = next.copy(haptics = next.haptics.normalized())

        val diffs = checked.keys.mapNotNull { def ->
            val from = def.read(current)
            val to = def.read(next)
            if (from == to) null else SettingChange(def.path, from, to)
        }
        return SettingsPlan(next, diffs, emptyList())
    }

    /** Every setting and its value as text, one per line, for the model. */
    fun describe(settings: AppSettings): String = buildString {
        appendLine("Settings as path = current value (accepted values in brackets):")
        for (def in all) appendLine("${def.path} = ${def.read(settings)} (${allowed(def.kind)})")
        for ((subject, color) in settings.lessonColors.subjectColors.toSortedMap()) {
            appendLine("${SUBJECT_PREFIX}$subject = ${hexOf(color)} (${allowed(SettingKind.Color)})")
        }
        appendLine("${SUBJECT_PREFIX}<subject name> = a colour for one subject, when the student names it.")
        appendLine("Not changeable here: the absence categories and the favourite timetable targets (lists the student makes).")
    }

    /** The accepted values as the model reads them, e.g. "0 to 100", "OCEAN, FOREST, ...". */
    fun allowed(kind: SettingKind): String = when (kind) {
        SettingKind.Bool -> "true or false"
        is SettingKind.Whole -> "whole number ${kind.min} to ${kind.max}"
        is SettingKind.Decimal -> "number ${kind.min} to ${kind.max}"
        is SettingKind.Choice -> kind.options.joinToString(" | ")
        is SettingKind.Text -> "text, up to ${kind.maxLength} characters, empty for the default"
        SettingKind.Color -> "#RRGGBB or auto"
    }

    /** The value in the form the catalogue writes it, or null when [raw] is not accepted. */
    internal fun checkValue(kind: SettingKind, raw: String): String? {
        val text = raw.trim()
        return when (kind) {
            SettingKind.Bool -> when (text.lowercase()) {
                "true", "on", "yes" -> "true"
                "false", "off", "no" -> "false"
                else -> null
            }
            is SettingKind.Whole -> text.toIntOrNull()?.takeIf { it in kind.min..kind.max }?.toString()
            is SettingKind.Decimal -> text.replace(',', '.').toFloatOrNull()?.takeIf { it in kind.min..kind.max }?.toString()
            is SettingKind.Choice -> kind.options.firstOrNull { it.equals(text, ignoreCase = true) }
            is SettingKind.Text -> text.takeIf { it.length <= kind.maxLength }
            SettingKind.Color -> when {
                text.equals("auto", ignoreCase = true) -> "auto"
                COLOR.matches(text) -> "#" + text.removePrefix("#").uppercase(Locale.ROOT)
                else -> null
            }
        }
    }

    private val COLOR = Regex("#?[0-9A-Fa-f]{6}")
    private const val SUBJECT_PREFIX = "lessonColors.subject."

    private fun hexOf(argb: Long): String = "#" + String.format(Locale.ROOT, "%06X", argb and 0xFFFFFFL)

    private fun argbOf(hex: String): Long = 0xFF000000L or hex.removePrefix("#").toLong(16)

    private fun subjectColor(name: String): SettingDef = SettingDef(
        path = SUBJECT_PREFIX + name,
        kind = SettingKind.Color,
        description = "The colour of the lessons of the subject \"$name\".",
        supporterOnly = false,
        read = { s -> s.lessonColors.subjectColors[name]?.let(::hexOf) ?: "auto" },
        write = { s, v ->
            val colors = if (v == "auto") s.lessonColors.subjectColors - name else s.lessonColors.subjectColors + (name to argbOf(v))
            s.copy(lessonColors = s.lessonColors.copy(subjectColors = colors))
        },
    )

    private fun build(): List<SettingDef> = buildList {
        // General.
        add(choice("language", "The app's language.", enumNames<AppLanguage>(), { it.language.name }) { s, v -> s.copy(language = AppLanguage.valueOf(v)) })
        add(bool("sync.enabled", "Keeps the timetable up to date in the background.", { it.syncEnabled }) { s, v -> s.copy(syncEnabled = v) })
        add(intChoice("sync.intervalMinutes", "How often the background sync runs, in minutes.", SettingsRepository.SYNC_INTERVALS, { it.syncIntervalMinutes }) { s, v -> s.copy(syncIntervalMinutes = v) })
        add(bool("notifications.enabled", "Notifications about lesson changes.", { it.notificationsEnabled }) { s, v -> s.copy(notificationsEnabled = v) })
        add(bool("updates.autoCheck", "Looks for new app versions in the background and downloads them on Wi-Fi.", { it.autoCheckUpdates }) { s, v -> s.copy(autoCheckUpdates = v) })
        add(intChoice("updates.checkHours", "How often the app looks for a new version, in hours.", SettingsRepository.UPDATE_CHECK_HOURS, { it.updateCheckHours }) { s, v -> s.copy(updateCheckHours = v) })
        add(bool("reminders.morningSummary", "A notification before the first lesson with the day's lessons and changes.", { it.morningSummaryEnabled }) { s, v -> s.copy(morningSummaryEnabled = v) })
        add(intChoice("reminders.morningLeadMinutes", "How many minutes before the first lesson the morning summary comes.", ReminderPlanner.LEAD_MINUTES, { it.morningLeadMinutes }) { s, v -> s.copy(morningLeadMinutes = v) })
        add(bool("reminders.examReminders", "An evening notification for exams and homework due the next day.", { it.examRemindersEnabled }) { s, v -> s.copy(examRemindersEnabled = v) })

        // Appearance.
        add(choice("appearance.theme", "Light, dark, or the phone's setting.", enumNames<ThemeMode>(), { it.themeMode.name }) { s, v -> s.copy(themeMode = ThemeMode.valueOf(v)) })
        add(bool("appearance.dynamicColor", "Colours from the phone's wallpaper (Material You), where the phone supports it.", { it.dynamicColor }) { s, v -> s.copy(dynamicColor = v) })
        add(bool("appearance.amoledBlack", "Pure black backgrounds in the dark theme.", { it.amoledBlack }) { s, v -> s.copy(amoledBlack = v) })
        add(choice("appearance.accent", "The accent colour used when the phone's wallpaper colours are off.", enumNames<AccentColor>(), { it.accent.name }) { s, v -> s.copy(accent = AccentColor.valueOf(v)) })
        add(choice("appearance.motion", "EXPRESSIVE: lively springs. CALM: fewer overshoots.", enumNames<MotionStyle>(), { it.motionStyle.name }) { s, v -> s.copy(motionStyle = MotionStyle.valueOf(v)) })
        add(choice("appearance.textScale", "The app's text size.", enumNames<TextScale>(), { it.textScale.name }) { s, v -> s.copy(textScale = TextScale.valueOf(v)) })

        // Timetable.
        add(choice("timetable.startView", "The view that opens first: DAY or WEEK.", enumNames<StartView>(), { it.startView.name }) { s, v -> s.copy(startView = StartView.valueOf(v)) })
        add(choice("timetable.subjectColorMode", "ACCENT_HUES spreads the subjects around the accent; ROLES uses the theme's colour roles.", enumNames<SubjectColorMode>(), { it.subjectColorMode.name }) { s, v -> s.copy(subjectColorMode = SubjectColorMode.valueOf(v)) })
        add(bool("timetable.showNowNext", "The \"now and next\" card at the top of today's timetable.", { it.showNowNext }) { s, v -> s.copy(showNowNext = v) })
        add(bool("timetable.autoNextWeek", "After the last lesson of the week, the timetable opens on the next week.", { it.display.autoNextWeek }) { s, v -> s.copy(display = s.display.copy(autoNextWeek = v)) })
        add(whole("timetable.dayScale", "The height of the day view, in percent.", TimetableDisplay.MIN_SCALE, TimetableDisplay.MAX_SCALE, { it.display.dayScale }) { s, v -> s.copy(display = s.display.copy(dayScale = v)) })
        add(whole("timetable.weekScale", "The height of the week view, in percent.", TimetableDisplay.MIN_SCALE, TimetableDisplay.MAX_SCALE, { it.display.weekScale }) { s, v -> s.copy(display = s.display.copy(weekScale = v)) })
        add(bool("timetable.weekFitsScreen", "The week view fits the whole school day on the screen instead of scrolling.", { it.display.weekFitsScreen }) { s, v -> s.copy(display = s.display.copy(weekFitsScreen = v)) })
        add(choice("timetable.concurrent", "Lessons at the same time: SIDE_BY_SIDE, or COMBINED into one block with a +N chip.", enumNames<ConcurrentLessons>(), { it.display.concurrent.name }) { s, v -> s.copy(display = s.display.copy(concurrent = ConcurrentLessons.valueOf(v))) })
        add(bool("timetable.showCancelled", "Cancelled lessons stay in the week view, struck through.", { it.display.showCancelled }) { s, v -> s.copy(display = s.display.copy(showCancelled = v)) })
        add(bool("timetable.showFreeBands", "Free periods between lessons get a band.", { it.display.showFreeBands }) { s, v -> s.copy(display = s.display.copy(showFreeBands = v)) })
        add(choice("timetable.weekDetail", "The line under the subject in the week view: ROOM, TEACHER or NONE.", enumNames<WeekDetail>(), { it.display.weekDetail.name }) { s, v -> s.copy(display = s.display.copy(weekDetail = WeekDetail.valueOf(v))) })

        // Absences.
        add(choice("absences.unit", "Missed time as MINUTES (hours and minutes) or LESSONS (school lessons of 50 minutes).", enumNames<AbsenceUnit>(), { it.absenceUnit.name }) { s, v -> s.copy(absenceUnit = AbsenceUnit.valueOf(v)) })

        // Supporter extras.
        add(choice("app.namePreset", "The name under the app icon (supporter extra).", enumNames<AppNamePreset>(), { it.appNamePreset.name }, supporter = true) { s, v -> s.copy(appNamePreset = AppNamePreset.valueOf(v)) })
        add(text("app.customName", "The title shown in the app (supporter extra). Empty for the preset's name.", 40, { it.customAppName }, supporter = true) { s, v -> s.copy(customAppName = v) })
        add(choice("app.iconStyle", "The picture of the app icon (supporter extra).", enumNames<AppIconStyle>(), { it.appIconStyle.name }, supporter = true) { s, v -> s.copy(appIconStyle = AppIconStyle.valueOf(v)) })

        // Home screen widget.
        add(choice("widget.theme", "APP follows the app's design, SYSTEM the phone, or LIGHT / DARK.", enumNames<WidgetTheme>(), { it.widget.theme.name }) { s, v -> s.copy(widget = s.widget.copy(theme = WidgetTheme.valueOf(v))) })
        add(whole("widget.backgroundOpacity", "How solid the widget background is, in percent.", 0, 100, { it.widget.backgroundOpacity }) { s, v -> s.copy(widget = s.widget.copy(backgroundOpacity = v)) })
        add(choice("widget.detail", "The small line under each lesson: ROOM, TEACHER or ROOM_AND_TEACHER.", enumNames<WidgetDetail>(), { it.widget.detail.name }) { s, v -> s.copy(widget = s.widget.copy(detail = WidgetDetail.valueOf(v))) })
        add(bool("widget.showHeadline", "The \"now until\" line above the widget's list.", { it.widget.showHeadline }) { s, v -> s.copy(widget = s.widget.copy(showHeadline = v)) })
        add(bool("widget.showCancelled", "Cancelled lessons stay in the widget, struck through.", { it.widget.showCancelled }) { s, v -> s.copy(widget = s.widget.copy(showCancelled = v)) })
        add(bool("widget.nextDayAfterSchool", "After the last lesson, the widget shows the next school day.", { it.widget.nextDayAfterSchool }) { s, v -> s.copy(widget = s.widget.copy(nextDayAfterSchool = v)) })
        add(bool("widget.showSyncTime", "The time of the last sync in the widget's corner.", { it.widget.showSyncTime }) { s, v -> s.copy(widget = s.widget.copy(showSyncTime = v)) })

        // Haptics.
        add(bool("haptics.enabled", "All vibration feedback on or off.", { it.haptics.enabled }) { s, v -> s.copy(haptics = s.haptics.copy(enabled = v)) })
        add(choice("haptics.preset", "Applying a preset sets the whole haptic tuning to it: PREMIUM, SUBTLE, PUNCHY or MINIMAL.", listOf("PREMIUM", "SUBTLE", "PUNCHY", "MINIMAL"), { it.haptics.preset.name }) { s, v -> s.copy(haptics = s.haptics.withPreset(HapticPreset.valueOf(v))) })
        add(decimal("haptics.intensity", "The overall strength of the vibration.", 0f, HapticSettings.MAX_INTENSITY, { it.haptics.intensity }) { s, v -> s.copy(haptics = s.haptics.copy(intensity = v)) })
        add(choice("haptics.texture", "SOFT feels gentler, CRISP is the standard, PUNCHY is harder.", enumNames<HapticTexture>(), { it.haptics.texture.name }) { s, v -> s.copy(haptics = s.haptics.copy(texture = HapticTexture.valueOf(v))) })
        add(decimal("haptics.spacing", "How far apart the pulses of one vibration are.", 0f, HapticSettings.MAX_SPACING, { it.haptics.spacing }) { s, v -> s.copy(haptics = s.haptics.copy(spacing = v)) })
        add(whole("haptics.minGapMs", "The shortest time between two quick vibrations, in milliseconds.", 0, HapticSettings.MAX_MIN_GAP_MS, { it.haptics.minGapMs }) { s, v -> s.copy(haptics = s.haptics.copy(minGapMs = v)) })
        add(bool("haptics.followSystem", "Stay silent when the phone's own touch feedback is off.", { it.haptics.followSystem }) { s, v -> s.copy(haptics = s.haptics.copy(followSystem = v)) })
        for (category in HapticCategory.entries) {
            val name = category.name
            add(decimal("haptics.category.$name.intensity", "The strength of the ${name.lowercase()} vibrations.", 0f, HapticSettings.MAX_INTENSITY, { it.haptics.categoryGain(category) }) { s, v ->
                s.copy(haptics = s.haptics.copy(categoryIntensity = s.haptics.categoryIntensity + (category to v)))
            })
            add(bool("haptics.category.$name.enabled", "Vibrations of the ${name.lowercase()} group.", { category !in it.haptics.disabledCategories }) { s, v ->
                s.copy(haptics = s.haptics.copy(disabledCategories = if (v) s.haptics.disabledCategories - category else s.haptics.disabledCategories + category))
            })
        }
        for (event in HapticEvent.entries) {
            val name = event.name
            add(decimal("haptics.event.$name.intensity", "The strength of the $name vibration.", 0f, HapticSettings.MAX_INTENSITY, { it.haptics.eventGain(event) }) { s, v ->
                s.copy(haptics = s.haptics.copy(eventIntensity = s.haptics.eventIntensity + (event to v)))
            })
            add(bool("haptics.event.$name.enabled", "The $name vibration.", { event !in it.haptics.disabledEvents }) { s, v ->
                s.copy(haptics = s.haptics.copy(disabledEvents = if (v) s.haptics.disabledEvents - event else s.haptics.disabledEvents + event))
            })
        }

        // Lesson colours.
        add(bool("lessonColors.fillWholeCard", "Colours the whole lesson card instead of only its stripe.", { it.lessonColors.fillWholeCard }) { s, v -> s.copy(lessonColors = s.lessonColors.copy(fillWholeCard = v)) })
        add(color("lessonColors.cancelled", "The colour of cancelled lessons.", { it.lessonColors.cancelledColor }) { s, v -> s.copy(lessonColors = s.lessonColors.copy(cancelledColor = v)) })
        add(color("lessonColors.exam", "The colour of exams.", { it.lessonColors.examColor }) { s, v -> s.copy(lessonColors = s.lessonColors.copy(examColor = v)) })
        add(color("lessonColors.substitution", "The colour of substitutions.", { it.lessonColors.substitutionColor }) { s, v -> s.copy(lessonColors = s.lessonColors.copy(substitutionColor = v)) })
    }

    private inline fun <reified E : Enum<E>> enumNames(): List<String> = enumValues<E>().map { it.name }

    private fun choice(path: String, description: String, options: List<String>, read: (AppSettings) -> String, supporter: Boolean = false, write: (AppSettings, String) -> AppSettings) =
        SettingDef(path, SettingKind.Choice(options), description, supporter, read, write)

    private fun intChoice(path: String, description: String, options: List<Int>, read: (AppSettings) -> Int, write: (AppSettings, Int) -> AppSettings) =
        SettingDef(
            path,
            SettingKind.Choice(options.map { it.toString() }),
            description,
            false,
            { read(it).toString() },
            { s, v -> write(s, v.toInt()) },
        )

    private fun bool(path: String, description: String, read: (AppSettings) -> Boolean, write: (AppSettings, Boolean) -> AppSettings) =
        SettingDef(path, SettingKind.Bool, description, false, { read(it).toString() }, { s, v -> write(s, v.toBoolean()) })

    private fun whole(path: String, description: String, min: Int, max: Int, read: (AppSettings) -> Int, write: (AppSettings, Int) -> AppSettings) =
        SettingDef(path, SettingKind.Whole(min, max), description, false, { read(it).toString() }, { s, v -> write(s, v.toInt()) })

    private fun decimal(path: String, description: String, min: Float, max: Float, read: (AppSettings) -> Float, write: (AppSettings, Float) -> AppSettings) =
        SettingDef(path, SettingKind.Decimal(min, max), description, false, { read(it).toString() }, { s, v -> write(s, v.toFloat()) })

    private fun text(path: String, description: String, maxLength: Int, read: (AppSettings) -> String, supporter: Boolean, write: (AppSettings, String) -> AppSettings) =
        SettingDef(path, SettingKind.Text(maxLength), description, supporter, read, write)

    private fun color(path: String, description: String, read: (AppSettings) -> Long?, write: (AppSettings, Long?) -> AppSettings) =
        SettingDef(
            path,
            SettingKind.Color,
            description,
            false,
            { read(it)?.let(::hexOf) ?: "auto" },
            { s, v -> write(s, if (v == "auto") null else argbOf(v)) },
        )
}
