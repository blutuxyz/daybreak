package com.bdersolutions.daybreak.ui.timetable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ScrollState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The timetable's pinch: two fingers zoom, one finger still scrolls what is underneath. */
class PinchToZoomTest {
    @get:Rule
    val rule = createComposeRule()

    private val zoom = mutableFloatStateOf(1f)
    private var ended = 0
    private lateinit var scroll: ScrollState

    private fun setUp() {
        rule.setContent {
            scroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .testTag("area")
                    .pinchToZoom(enabled = true, onZoom = { zoom.floatValue *= it }, onEnd = { ended++ })
                    .verticalScroll(scroll),
            ) {
                Box(Modifier.fillMaxWidth().height(2000.dp))
            }
        }
    }

    @Test
    fun spreadingTwoFingersZoomsInAndDoesNotScroll() {
        setUp()
        rule.onNodeWithTag("area").performTouchInput {
            pinch(
                start0 = center + Offset(0f, -60f),
                end0 = center + Offset(0f, -260f),
                start1 = center + Offset(0f, 60f),
                end1 = center + Offset(0f, 260f),
            )
        }
        rule.waitForIdle()
        assertTrue("zoom was ${zoom.floatValue}", zoom.floatValue > 1.5f)
        assertEquals(1, ended)
        assertEquals(0, scroll.value)
    }

    @Test
    fun pinchingTogetherZoomsOut() {
        setUp()
        rule.onNodeWithTag("area").performTouchInput {
            pinch(
                start0 = center + Offset(0f, -260f),
                end0 = center + Offset(0f, -60f),
                start1 = center + Offset(0f, 260f),
                end1 = center + Offset(0f, 60f),
            )
        }
        rule.waitForIdle()
        assertTrue("zoom was ${zoom.floatValue}", zoom.floatValue < 0.6f)
    }

    @Test
    fun oneFingerStillScrolls() {
        setUp()
        rule.onNodeWithTag("area").performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertTrue(scroll.value > 0)
        assertEquals(1f, zoom.floatValue)
        assertEquals(0, ended)
    }
}
