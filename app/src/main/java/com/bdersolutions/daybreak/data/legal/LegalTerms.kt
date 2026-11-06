package com.bdersolutions.daybreak.data.legal

import androidx.annotation.StringRes
import com.bdersolutions.daybreak.R

/** One titled block of the privacy policy or the terms of use. */
data class LegalSection(
    @StringRes val title: Int,
    @StringRes val body: Int,
)

/**
 * The privacy policy and terms of use that ship inside the app. The text lives in the string
 * resources whose names start with `legal_` (German and English); nothing is loaded from a server.
 *
 * Whoever changes that text in a way that matters must raise [VERSION] in the same change. The app
 * then asks every user to accept again; users who already accepted the old version are asked once,
 * and nobody is asked on an ordinary update. `LegalTermsVersionTest` fails when the text changed
 * and [VERSION] did not.
 */
object LegalTerms {
    const val VERSION = 4

    val sections: List<LegalSection> = listOf(
        LegalSection(R.string.legal_what_title, R.string.legal_what_body),
        LegalSection(R.string.legal_privacy_title, R.string.legal_privacy_body),
        LegalSection(R.string.legal_stored_title, R.string.legal_stored_body),
        LegalSection(R.string.legal_sent_title, R.string.legal_sent_body),
        LegalSection(R.string.legal_rights_title, R.string.legal_rights_body),
        LegalSection(R.string.legal_terms_title, R.string.legal_terms_body),
        LegalSection(R.string.legal_liability_title, R.string.legal_liability_body),
        LegalSection(R.string.legal_changes_title, R.string.legal_changes_body),
        LegalSection(R.string.legal_contact_title, R.string.legal_contact_body),
    )
}

/** Decides whether an acceptance on record still covers the terms of this app version. */
object TermsAcceptance {
    /** Nothing accepted yet. */
    const val NONE = 0

    fun covers(acceptedVersion: Int, currentVersion: Int = LegalTerms.VERSION): Boolean =
        acceptedVersion != NONE && acceptedVersion >= currentVersion

    /** Accepted before, but a newer text has come since. */
    fun isUpdate(acceptedVersion: Int, currentVersion: Int = LegalTerms.VERSION): Boolean =
        acceptedVersion != NONE && acceptedVersion < currentVersion
}
