package com.bdersolutions.daybreak.data.support

/** Where people can support Daybreak. The link opens in the browser; the app sends nothing there. */
object SupportLinks {
    const val KOFI_URL = "https://ko-fi.com/blutuxyz"
}

/**
 * What the app remembers to decide when to ask for support. Days are epoch days (`LocalDate.toEpochDay`).
 * Nothing here leaves the phone.
 */
data class SupportState(
    val firstSeenDay: Long = NEVER,
    /** Different days the app was opened on, not how often. */
    val openDays: Int = 0,
    val lastOpenDay: Long = NEVER,
    val shownCount: Int = 0,
    val lastShownDay: Long = NEVER,
    /** Set by "Don't ask again" and after the person went to Ko-fi. */
    val optedOut: Boolean = false,
) {
    companion object {
        const val NEVER = Long.MIN_VALUE
    }
}

/**
 * When the support popup may appear. It is meant to be rare: only after the app has been used on
 * several days and some time has passed, at most [MAX_PROMPTS] times in total, a month apart, and
 * never again after "Don't ask again" or a visit to Ko-fi.
 */
object SupportPromptPolicy {
    const val MIN_OPEN_DAYS = 7
    const val MIN_AGE_DAYS = 7
    const val REPEAT_AFTER_DAYS = 30
    const val MAX_PROMPTS = 3

    /** Records that the app was opened on [today]. Opening it again on the same day changes nothing. */
    fun afterOpen(state: SupportState, today: Long): SupportState {
        val withFirst = if (state.firstSeenDay == SupportState.NEVER) state.copy(firstSeenDay = today) else state
        return if (withFirst.lastOpenDay == today) {
            withFirst
        } else {
            withFirst.copy(openDays = withFirst.openDays + 1, lastOpenDay = today)
        }
    }

    fun shouldShow(state: SupportState, today: Long): Boolean {
        if (state.optedOut || state.shownCount >= MAX_PROMPTS) return false
        if (state.firstSeenDay == SupportState.NEVER) return false
        if (state.openDays < MIN_OPEN_DAYS || today - state.firstSeenDay < MIN_AGE_DAYS) return false
        return state.shownCount == 0 || today - state.lastShownDay >= REPEAT_AFTER_DAYS
    }

    /** Called the moment the popup appears, so turning the phone does not show it twice. */
    fun afterShown(state: SupportState, today: Long): SupportState =
        state.copy(shownCount = state.shownCount + 1, lastShownDay = today)

    fun afterOptOut(state: SupportState): SupportState = state.copy(optedOut = true)
}
