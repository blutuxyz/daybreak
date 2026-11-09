package com.bdersolutions.daybreak.data.tutorial

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialProgressTest {
    @Test
    fun nothingFinishedShowsTheTour() {
        assertFalse(TutorialProgress.isDone(TutorialProgress.NONE))
    }

    @Test
    fun finishedCurrentVersionIsDone() {
        assertTrue(TutorialProgress.isDone(TutorialProgress.VERSION))
    }

    @Test
    fun aReworkedTourIsShownAgain() {
        assertFalse(TutorialProgress.isDone(completedVersion = 1, currentVersion = 2))
        assertTrue(TutorialProgress.isDone(completedVersion = 2, currentVersion = 2))
    }
}
