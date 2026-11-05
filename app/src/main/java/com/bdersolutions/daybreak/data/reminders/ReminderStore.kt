package com.bdersolutions.daybreak.data.reminders

import android.content.Context
import java.time.LocalDate

/**
 * Remembers on which day each reminder was last handled, so the same morning or evening is never
 * announced twice. Epoch days only; nothing personal. Kept apart from the session and left in place
 * on logout, which is harmless: it holds no data about the account.
 */
class ReminderStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastMorning(): LocalDate? = read(KEY_MORNING)

    fun markMorning(date: LocalDate) = write(KEY_MORNING, date)

    fun lastEvening(): LocalDate? = read(KEY_EVENING)

    fun markEvening(date: LocalDate) = write(KEY_EVENING, date)

    private fun read(key: String): LocalDate? =
        preferences.getLong(key, NONE).takeIf { it != NONE }?.let { LocalDate.ofEpochDay(it) }

    private fun write(key: String, date: LocalDate) {
        preferences.edit().putLong(key, date.toEpochDay()).apply()
    }

    private companion object {
        const val PREFS_NAME = "daybreak_reminders"
        const val KEY_MORNING = "last_morning"
        const val KEY_EVENING = "last_evening"
        const val NONE = Long.MIN_VALUE
    }
}
