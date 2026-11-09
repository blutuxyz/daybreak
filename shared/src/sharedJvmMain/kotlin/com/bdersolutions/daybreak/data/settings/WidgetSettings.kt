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
    /** Opacity of the widget background in percent, 0 to 100. */
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
    /** Keeps values from an older or broken store in range. */
    fun normalized(): WidgetSettings = copy(backgroundOpacity = backgroundOpacity.coerceIn(0, 100))

    companion object {
        /** The opacity slider moves in steps of this many percent. */
        const val OPACITY_STEP = 10
    }
}
