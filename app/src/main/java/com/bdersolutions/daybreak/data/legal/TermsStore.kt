package com.bdersolutions.daybreak.data.legal

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers which version of the privacy policy and terms the user accepted. Plain values, no
 * secrets. It is kept apart from the session, so logging out does not ask again, and it is left
 * out of backups (see backup_rules.xml), so a restored or new device asks once.
 */
class TermsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _acceptedVersion = MutableStateFlow(preferences.getInt(KEY_VERSION, TermsAcceptance.NONE))

    /** The version on record, or [TermsAcceptance.NONE]. */
    val acceptedVersion: StateFlow<Int> = _acceptedVersion.asStateFlow()

    /** When it was accepted, in epoch milliseconds, or 0. */
    val acceptedAt: Long
        get() = preferences.getLong(KEY_AT, 0L)

    fun isAccepted(): Boolean = TermsAcceptance.covers(_acceptedVersion.value)

    /** Records the current version as accepted. */
    fun accept(now: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putInt(KEY_VERSION, LegalTerms.VERSION)
            .putLong(KEY_AT, now)
            .apply()
        _acceptedVersion.value = LegalTerms.VERSION
    }

    private companion object {
        const val PREFS_NAME = "daybreak_terms"
        const val KEY_VERSION = "accepted_version"
        const val KEY_AT = "accepted_at"
    }
}
