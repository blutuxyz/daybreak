package com.bdersolutions.daybreak.data.tutorial

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers which version of the introduction was finished. Plain values, kept apart from the session, so
 * signing out and in again does not show it again; a new install shows it once.
 */
class TutorialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _completedVersion = MutableStateFlow(preferences.getInt(KEY_VERSION, TutorialProgress.NONE))

    /** The finished version, or [TutorialProgress.NONE]. */
    val completedVersion: StateFlow<Int> = _completedVersion.asStateFlow()

    fun complete() {
        preferences.edit().putInt(KEY_VERSION, TutorialProgress.VERSION).apply()
        _completedVersion.value = TutorialProgress.VERSION
    }

    private companion object {
        const val PREFS_NAME = "daybreak_tutorial"
        const val KEY_VERSION = "completed_version"
    }
}
