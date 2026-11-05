package com.bdersolutions.daybreak.data.support

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the [SupportState] in its own preferences, apart from the session, so logging out does not
 * reset it. Plain numbers and the random unlock code only.
 */
class SupportStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _paid = MutableStateFlow(preferences.getBoolean(KEY_PAID, false))

    /** True once a payment was confirmed. Lets the settings page hide its Ko-fi button at once. */
    val paid: StateFlow<Boolean> = _paid.asStateFlow()

    fun state(): SupportState = SupportState(
        firstSeenDay = preferences.getLong(KEY_FIRST_SEEN, SupportState.NEVER),
        openDays = preferences.getInt(KEY_OPEN_DAYS, 0),
        lastOpenDay = preferences.getLong(KEY_LAST_OPEN, SupportState.NEVER),
        shownCount = preferences.getInt(KEY_SHOWN_COUNT, 0),
        lastShownDay = preferences.getLong(KEY_LAST_SHOWN, SupportState.NEVER),
        optedOut = preferences.getBoolean(KEY_OPTED_OUT, false),
        paid = preferences.getBoolean(KEY_PAID, false),
    )

    /** This phone's unlock code. It is made on first use and then stays the same. */
    fun unlockCode(): String {
        preferences.getString(KEY_UNLOCK_CODE, null)?.takeIf { SupportCode.isValid(it) }?.let { return it }
        val code = SupportCode.generate()
        preferences.edit().putString(KEY_UNLOCK_CODE, code).apply()
        return code
    }

    fun markPaid(): SupportState = update { SupportPromptPolicy.afterPaid(it) }

    fun recordOpen(today: Long): SupportState = update { SupportPromptPolicy.afterOpen(it, today) }

    fun markShown(today: Long): SupportState = update { SupportPromptPolicy.afterShown(it, today) }

    fun optOut(): SupportState = update { SupportPromptPolicy.afterOptOut(it) }

    private fun update(transform: (SupportState) -> SupportState): SupportState {
        val next = transform(state())
        preferences.edit()
            .putLong(KEY_FIRST_SEEN, next.firstSeenDay)
            .putInt(KEY_OPEN_DAYS, next.openDays)
            .putLong(KEY_LAST_OPEN, next.lastOpenDay)
            .putInt(KEY_SHOWN_COUNT, next.shownCount)
            .putLong(KEY_LAST_SHOWN, next.lastShownDay)
            .putBoolean(KEY_OPTED_OUT, next.optedOut)
            .putBoolean(KEY_PAID, next.paid)
            .apply()
        _paid.value = next.paid
        return next
    }

    private companion object {
        const val PREFS_NAME = "daybreak_support"
        const val KEY_FIRST_SEEN = "first_seen_day"
        const val KEY_OPEN_DAYS = "open_days"
        const val KEY_LAST_OPEN = "last_open_day"
        const val KEY_SHOWN_COUNT = "shown_count"
        const val KEY_LAST_SHOWN = "last_shown_day"
        const val KEY_OPTED_OUT = "opted_out"
        const val KEY_PAID = "paid"
        const val KEY_UNLOCK_CODE = "unlock_code"
    }
}
