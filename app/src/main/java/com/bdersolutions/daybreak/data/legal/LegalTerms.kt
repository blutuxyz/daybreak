package com.bdersolutions.daybreak.data.legal

import androidx.annotation.StringRes
import com.bdersolutions.daybreak.R

/** One titled block of the privacy policy or the terms of use. */
data class LegalSection(
    @StringRes val title: Int,
    @StringRes val body: Int,
)

/**
 * The privacy policy and terms of use. The app ships with the text in the string resources whose names
 * start with `legal_` (German and English). It also asks the update server for the current text
 * (`GET /v1/legal`, see [LegalTextRepository]); the server's copy is used when its version is at least
 * [VERSION].
 *
 * Whoever changes that text in a way that matters must raise [VERSION] in the same change, and the same
 * number in update-server/src/legal.js with the text in legal-texts.json. The app then asks every user to
 * accept again; users who already accepted the old version are asked once, and nobody is asked on an
 * ordinary update. `LegalTermsVersionTest` fails when the text changed and [VERSION] did not.
 */
object LegalTerms {
    const val VERSION = 9

    /** The keys of the sections in the order of [sections]; the server sends them under the same keys. */
    val SECTION_KEYS: List<String> = listOf("what", "privacy", "stored", "sent", "rights", "terms", "liability", "changes", "contact")

    /**
     * The e-mail address named in the contact section (`legal_contact_body`, German and English,
     * and the desktop texts). Keep it in sync with those places; `LegalTermsVersionTest` fails when the strings
     * and this constant differ. Changing only the address does not need a new [VERSION].
     */
    const val CONTACT_EMAIL = "contact@daybreak.playfrolic.xyz"

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
