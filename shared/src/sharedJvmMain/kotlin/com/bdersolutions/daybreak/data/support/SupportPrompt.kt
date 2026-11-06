package com.bdersolutions.daybreak.data.support

/** Where people can support Daybreak. The link opens in the browser; the app sends nothing there. */
object SupportLinks {
    const val KOFI_URL = "https://ko-fi.com/blutuxyz"
}

/**
 * What the app remembers to decide when to ask for support. Days are epoch days (`LocalDate.toEpochDay`).
 * Nothing here leaves the phone; only the unlock code (see [SupportCode]) is sent, and only when the
 * person asks the app to check their payment.
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
    /** Set once the update server confirmed a payment for this phone's unlock code. */
    val paid: Boolean = false,
) {
    companion object {
        const val NEVER = Long.MIN_VALUE
    }
}

/**
 * When the support popup may appear. It is meant to be rare: only after the app has been used on
 * several days and some time has passed, then once every [REPEAT_AFTER_DAYS] days for as long as
 * nothing was paid. It stops for good once a payment is confirmed (or "Don't ask again" in builds
 * without an update server).
 */
object SupportPromptPolicy {
    const val MIN_OPEN_DAYS = 7
    const val MIN_AGE_DAYS = 7
    const val REPEAT_AFTER_DAYS = 30

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
        if (state.paid || state.optedOut) return false
        if (state.firstSeenDay == SupportState.NEVER) return false
        if (state.openDays < MIN_OPEN_DAYS || today - state.firstSeenDay < MIN_AGE_DAYS) return false
        return state.shownCount == 0 || today - state.lastShownDay >= REPEAT_AFTER_DAYS
    }

    /** Called the moment the popup appears, so turning the phone does not show it twice. */
    fun afterShown(state: SupportState, today: Long): SupportState =
        state.copy(shownCount = state.shownCount + 1, lastShownDay = today)

    fun afterOptOut(state: SupportState): SupportState = state.copy(optedOut = true)

    /** The server confirmed a payment: the popup is hidden for good. */
    fun afterPaid(state: SupportState): SupportState = state.copy(paid = true)
}

/**
 * The unlock code a supporter writes into the Ko-fi message, so the server can tell which phone paid.
 * It is random and not derived from anything on the phone. The alphabet leaves out 0, 1, I, L and O
 * so the code can be read and typed without mistakes. It must match `update-server/src/support.js`.
 */
object SupportCode {
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private val FORMAT = Regex("^DB-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}$")

    fun generate(random: java.util.Random = java.security.SecureRandom()): String {
        fun block() = (1..4).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return "DB-${block()}-${block()}"
    }

    fun isValid(code: String): Boolean = FORMAT.matches(code)
}
