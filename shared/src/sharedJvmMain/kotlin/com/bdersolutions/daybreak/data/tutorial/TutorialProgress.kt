package com.bdersolutions.daybreak.data.tutorial

/**
 * Whether the introduction (the tutorial in a demo environment) still has to be shown. Everyone sees it
 * once, also users who were signed in before it existed. Raising [VERSION] shows a reworked tour to
 * everyone again, the way LegalTerms.VERSION asks for the terms again.
 */
object TutorialProgress {
    const val VERSION = 1

    /** Nothing finished yet. */
    const val NONE = 0

    fun isDone(completedVersion: Int, currentVersion: Int = VERSION): Boolean =
        completedVersion != NONE && completedVersion >= currentVersion
}
