package com.bdersolutions.daybreak.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import org.junit.Assert.assertTrue
import org.junit.Test

class PushSpringTest {
    private val phoneWidthPx = 1080f
    private val frameNanos = 16_666_667L

    /** How many pixels the page still had to go on the last frame before the spring stopped and snapped. */
    private fun lastJumpPx(spec: androidx.compose.animation.core.AnimationSpec<Float>): Float {
        val animation = TargetBasedAnimation(spec, Float.VectorConverter, 0f, 1f)
        val lastFrame = animation.getValueFromNanos(animation.durationNanos - frameNanos)
        return (1f - lastFrame) * phoneWidthPx
    }

    @Test
    fun theSlideArrivesWithoutAVisibleJump() {
        val jump = lastJumpPx(PushSpring)
        assertTrue("the page jumped $jump px at the end", jump < 1f)
    }

    @Test
    fun theDefaultThresholdWasTheJump() {
        // The same spring with Compose's default threshold (0.01 of the way) stops several pixels short.
        val old = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f, visibilityThreshold = Spring.DefaultDisplacementThreshold)
        assertTrue(lastJumpPx(old) > 5f)
    }
}
