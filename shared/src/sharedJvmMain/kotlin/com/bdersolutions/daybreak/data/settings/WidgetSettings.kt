package com.bdersolutions.daybreak.data.settings

/** Which colours the home screen widget uses. */
enum class WidgetTheme {
    /** Light or dark like the app's own design setting. */
    APP,

    /** Light or dark like the phone. */
    SYSTEM,
    LIGHT,
    DARK,
}

/** The small second line under each lesson in the widget. */
enum class WidgetDetail { ROOM, TEACHER, ROOM_AND_TEACHER }

/**
 * How the home screen widget looks and what it shows. Colours always follow the app (Material You or
 * the chosen accent, and the lesson colours), so the widget and the timetable match.
 */
data class WidgetSettings(
    val theme: WidgetTheme = WidgetTheme.APP,
    /** Opacity of the widget background in percent, one of [BACKGROUND_OPACITIES]. */
    val backgroundOpacity: Int = 100,
    val detail: WidgetDetail = WidgetDetail.ROOM,
    /** The "Now: … until …" line above the list. */
    val showHeadline: Boolean = true,
    /** Cancelled lessons stay in the list, struck through. */
    val showCancelled: Boolean = true,
    /** Once the last lesson of the day is over, the widget shows the next school day. */
    val nextDayAfterSchool: Boolean = true,
    /** The time of the last sync in the corner. */
    val showSyncTime: Boolean = true,
) {
    /** Snaps values that came from an older or broken store onto the allowed choices. */
    fun normalized(): WidgetSettings =
        if (backgroundOpacity in BACKGROUND_OPACITIES) {
            this
        } else {
            copy(backgroundOpacity = BACKGROUND_OPACITIES.minBy { kotlin.math.abs(it - backgroundOpacity) })
        }

    companion object {
        val BACKGROUND_OPACITIES = listOf(0, 25, 50, 75, 100)
    }
}
