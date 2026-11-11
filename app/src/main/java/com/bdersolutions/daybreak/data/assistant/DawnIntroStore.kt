package com.bdersolutions.daybreak.data.assistant

import android.content.Context

/**
 * Whether the one-time "Daylight AI is now Dawn" notice has been shown. Set when the introduction is finished
 * (new users see the new name there) and when the notice is closed. Kept in its own preferences.
 */
class DawnIntroStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shown(): Boolean = preferences.getBoolean(KEY_SHOWN, false)

    fun markShown() {
        preferences.edit().putBoolean(KEY_SHOWN, true).apply()
    }

    private companion object {
        const val PREFS_NAME = "daybreak_dawn_intro"
        const val KEY_SHOWN = "shown"
    }
}
