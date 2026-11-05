package com.bdersolutions.daybreak.data.legal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermsAcceptanceTest {
    @Test
    fun nothingAcceptedMeansTheUserIsAsked() {
        assertFalse(TermsAcceptance.covers(TermsAcceptance.NONE, currentVersion = 1))
        assertFalse(TermsAcceptance.isUpdate(TermsAcceptance.NONE, currentVersion = 1))
    }

    @Test
    fun theSameVersionIsNotAskedAgain() {
        assertTrue(TermsAcceptance.covers(acceptedVersion = 3, currentVersion = 3))
        assertFalse(TermsAcceptance.isUpdate(acceptedVersion = 3, currentVersion = 3))
    }

    @Test
    fun aNewerTextAsksAgainAndIsReportedAsAnUpdate() {
        assertFalse(TermsAcceptance.covers(acceptedVersion = 1, currentVersion = 2))
        assertTrue(TermsAcceptance.isUpdate(acceptedVersion = 1, currentVersion = 2))
    }

    @Test
    fun anOlderAppBuildDoesNotAskAgain() {
        // After a downgrade the record can be newer than the text in the app.
        assertTrue(TermsAcceptance.covers(acceptedVersion = 4, currentVersion = 3))
    }
}
