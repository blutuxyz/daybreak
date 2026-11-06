package com.bdersolutions.daybreak.data.branding

/**
 * The names the app can have on the phone's home screen. Android reads the launcher name from the
 * APK, so free text is not possible there: each name is an `activity-alias` in the manifest (class
 * name [aliasName], label `launcher_name_*` in strings.xml) and choosing one enables that alias and
 * disables the others (see [LauncherNameSwitcher]). [displayName] must equal the alias label;
 * `AppNameTest` checks it.
 */
enum class AppNamePreset(val displayName: String, val aliasName: String) {
    DAYBREAK("Daybreak", "AliasDaybreak"),
    STUNDENPLAN("Stundenplan", "AliasStundenplan"),
    SCHULE("Schule", "AliasSchule"),
    PLAN("Plan", "AliasPlan"),
    KALENDER("Kalender", "AliasKalender"),
    TIMETABLE("Timetable", "AliasTimetable"),
    ;

    companion object {
        fun fromName(raw: String?): AppNamePreset = entries.firstOrNull { it.name == raw } ?: DAYBREAK
    }
}

/**
 * The picture of the launcher icon. [STANDARD] is the current logo; the others are supporter extras:
 * [CLASSIC] is the original logo, the rest are separate designs (`ic_launcher_<style>` in the
 * resources, in lower case). Like the names, an icon is chosen by enabling an `activity-alias`: there is
 * one per name and style, named [AppNamePreset.aliasName] plus [aliasSuffix].
 */
enum class AppIconStyle(val aliasSuffix: String) {
    STANDARD(""),
    CLASSIC("Classic"),
    GRID("Grid"),
    NIGHT("Night"),
    LETTER("Letter"),
    BLOOM("Bloom"),
    ;

    companion object {
        fun fromName(raw: String?): AppIconStyle = entries.firstOrNull { it.name == raw } ?: STANDARD
    }
}

/** The launcher alias for a name and an icon style. */
fun aliasNameFor(preset: AppNamePreset, style: AppIconStyle): String = preset.aliasName + style.aliasSuffix

/**
 * Which name the app shows. Changing the name is a supporter extra: without a confirmed payment the
 * app is always "Daybreak".
 */
object AppNaming {
    const val DEFAULT = "Daybreak"
    const val MAX_CUSTOM_LENGTH = 24

    /** Trims, drops control characters, collapses runs of spaces and cuts to [MAX_CUSTOM_LENGTH]. */
    fun sanitizeCustom(input: String): String =
        input.filterNot { it.isISOControl() }
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_CUSTOM_LENGTH)
            .trim()

    /** The preset that is really in use: the chosen one for supporters, else the default. */
    fun effectivePreset(paid: Boolean, preset: AppNamePreset): AppNamePreset =
        if (paid) preset else AppNamePreset.DAYBREAK

    /** The icon style that is really in use: the chosen one for supporters, else the standard logo. */
    fun effectiveIconStyle(paid: Boolean, style: AppIconStyle): AppIconStyle =
        if (paid) style else AppIconStyle.STANDARD

    /** The name shown in the app: the custom text if there is one, else the preset's name. */
    fun resolve(paid: Boolean, preset: AppNamePreset, custom: String): String {
        if (!paid) return DEFAULT
        return sanitizeCustom(custom).ifEmpty { preset.displayName }
    }
}
