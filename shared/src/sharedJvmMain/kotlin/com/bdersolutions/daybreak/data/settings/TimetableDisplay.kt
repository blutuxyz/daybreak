package com.bdersolutions.daybreak.data.settings

/** What the week view does with lessons that run at the same time. */
enum class ConcurrentLessons {
    /** Narrow blocks beside each other, as many as fit. */
    SIDE_BY_SIDE,

    /** One full-width block (the lesson that takes place wins over a cancelled one) and a "+N" chip. */
    COMBINED,
}

/** The detail line under the subject in the narrow week blocks. */
enum class WeekDetail { ROOM, TEACHER, NONE }

/**
 * How the timetable screen is laid out. Scales are percent of the standard height per minute,
 * between [MIN_SCALE] and [MAX_SCALE]: the slider moves in [SCALE_STEP]s, pinching sets any value.
 */
data class TimetableDisplay(
    /** Once the last lesson of the week is over, the timetable opens on the next week. */
    val autoNextWeek: Boolean = true,
    val dayScale: Int = 100,
    val weekScale: Int = 100,
    /** The week view squeezes the whole school day into the screen instead of scrolling. */
    val weekFitsScreen: Boolean = false,
    val concurrent: ConcurrentLessons = ConcurrentLessons.COMBINED,
    val showCancelled: Boolean = true,
    /** Free periods between lessons get a band with "Frei". */
    val showFreeBands: Boolean = true,
    val weekDetail: WeekDetail = WeekDetail.ROOM,
) {
    fun normalized(): TimetableDisplay = copy(dayScale = clampScale(dayScale), weekScale = clampScale(weekScale))

    companion object {
        const val MIN_SCALE = 60
        const val MAX_SCALE = 180
        const val SCALE_STEP = 10

        fun clampScale(value: Int): Int = value.coerceIn(MIN_SCALE, MAX_SCALE)
    }
}
