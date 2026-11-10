package com.bdersolutions.daybreak.ui.assistant

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingDotsTest {
    @Test
    fun everyDotRestsAtTheBottomWhenTheLoopRestarts() {
        repeat(3) { dot ->
            // The same start and target values the indicator animates between.
            val hop = TargetBasedAnimation(typingDotKeyframes(dot), Float.VectorConverter, 0f, 1f)
            val start = hop.getValueFromNanos(0)
            // The very end itself is never drawn (the loop wraps to the start), so look just before it.
            val lastMillisecond = hop.getValueFromNanos((TYPING_CYCLE_MS - 1) * 1_000_000L)
            val lastFrame = hop.getValueFromNanos((TYPING_CYCLE_MS - 16) * 1_000_000L)
            assertEquals("dot $dot starts at the bottom", 0f, start, 0.001f)
            assertEquals("dot $dot ends where the next loop starts", start, lastMillisecond, 0.001f)
            assertEquals("dot $dot does not creep up before the restart", 0f, lastFrame, 0.001f)
        }
    }
}
