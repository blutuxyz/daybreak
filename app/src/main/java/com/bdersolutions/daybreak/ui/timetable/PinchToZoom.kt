package com.bdersolutions.daybreak.ui.timetable

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

/**
 * Two-finger pinch that reports the zoom factor of each move. It listens before the scrolling and
 * paging below it and takes the events only while two fingers are down, so one finger still
 * scrolls and swipes as before.
 */
internal fun Modifier.pinchToZoom(
    enabled: Boolean,
    onZoom: (Float) -> Unit,
    onEnd: () -> Unit,
): Modifier = composed {
    val currentOnZoom by rememberUpdatedState(onZoom)
    val currentOnEnd by rememberUpdatedState(onEnd)
    if (!enabled) return@composed this
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var zoomed = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    val factor = event.calculateZoom()
                    if (factor != 1f && factor.isFinite()) {
                        zoomed = true
                        currentOnZoom(factor)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (zoomed) currentOnEnd()
        }
    }
}
