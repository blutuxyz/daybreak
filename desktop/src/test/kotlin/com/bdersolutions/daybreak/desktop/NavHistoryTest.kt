package com.bdersolutions.daybreak.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavHistoryTest {
    @Test
    fun backAndForwardWalkThroughThePlacesVisited() {
        val nav = NavHistory()
        nav.open(Place(MainTab.MORE))
        nav.open(Place(MainTab.MORE, SubRoute.APPEARANCE))

        assertTrue(nav.back())
        assertEquals(Place(MainTab.MORE), nav.current)
        assertTrue(nav.forward())
        assertEquals(Place(MainTab.MORE, SubRoute.APPEARANCE), nav.current)
        assertFalse(nav.forward())
    }

    @Test
    fun openingSomethingNewDropsTheForwardSteps() {
        val nav = NavHistory()
        nav.open(Place(MainTab.ABSENCES))
        nav.back()
        nav.open(Place(MainTab.MORE))

        assertFalse(nav.canGoForward)
    }

    @Test
    fun escapeAtTheStartDoesNothingAndFromAPageClosesIt() {
        val nav = NavHistory()
        assertFalse(nav.back())
        // A page that was the start of the history still closes to its tab, then to the timetable.
        val deep = NavHistory(Place(MainTab.MORE, SubRoute.ABOUT))
        assertTrue(deep.back())
        assertEquals(Place(MainTab.MORE), deep.current)
        assertTrue(deep.back())
        assertEquals(Place(MainTab.TIMETABLE), deep.current)
        assertFalse(deep.back())
    }

    @Test
    fun openingTheCurrentPlaceAgainIsNotAStep() {
        val nav = NavHistory()
        nav.open(Place(MainTab.TIMETABLE))
        assertFalse(nav.canGoBack)
    }
}
