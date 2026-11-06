package com.bdersolutions.daybreak.untis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionErrorTest {
    @Test
    fun noRightForTimetableIsAPermissionProblemNotASignInProblem() {
        assertTrue(JsonRpcClient.isPermissionDenied(-8509, "no right for timetable"))
        assertTrue(JsonRpcClient.isPermissionDenied(null, "Keine Berechtigung für diese Ansicht"))
        assertTrue(JsonRpcClient.isPermissionDenied(-1, "Not allowed"))
    }

    @Test
    fun signInProblemsStaySignInProblems() {
        assertFalse(JsonRpcClient.isPermissionDenied(-8504, "bad credentials"))
        assertFalse(JsonRpcClient.isPermissionDenied(-8520, "not authenticated"))
        assertFalse(JsonRpcClient.isPermissionDenied(null, null))
    }
}
