package com.bdersolutions.daybreak.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Where the person is: a tab, and optionally a page opened over it. */
data class Place(val tab: MainTab, val sub: SubRoute? = null)

/**
 * Back and forward like a browser, for Escape, Alt+Left/Right and the mouse side buttons. Opening a
 * page or changing the tab is a step; going back to the same place again and again is not. Opening
 * something new drops the forward steps, as a browser does.
 */
class NavHistory(start: Place = Place(MainTab.TIMETABLE)) {
    var current: Place by mutableStateOf(start)
        private set

    private val backStack = ArrayDeque<Place>()
    private val forwardStack = ArrayDeque<Place>()

    val canGoBack: Boolean get() = backStack.isNotEmpty() || current.sub != null || current.tab != MainTab.TIMETABLE
    val canGoForward: Boolean get() = forwardStack.isNotEmpty()

    fun open(place: Place) {
        if (place == current) return
        backStack.addLast(current)
        while (backStack.size > MAX_STEPS) backStack.removeFirst()
        forwardStack.clear()
        current = place
    }

    /**
     * One step back. With nothing to go back to, a page closes to its tab and a tab returns to the
     * timetable, so Escape always does something sensible; false when already at the start.
     */
    fun back(): Boolean {
        val previous = backStack.removeLastOrNull()
        if (previous != null) {
            forwardStack.addLast(current)
            current = previous
            return true
        }
        val fallback = when {
            current.sub != null -> Place(current.tab)
            current.tab != MainTab.TIMETABLE -> Place(MainTab.TIMETABLE)
            else -> return false
        }
        forwardStack.addLast(current)
        current = fallback
        return true
    }

    fun forward(): Boolean {
        val next = forwardStack.removeLastOrNull() ?: return false
        backStack.addLast(current)
        current = next
        return true
    }

    private companion object {
        const val MAX_STEPS = 50
    }
}
