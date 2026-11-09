package com.bdersolutions.daybreak.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.data.settings.MotionStyle
import com.bdersolutions.daybreak.ui.components.motionAnimationsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A ball that runs to and fro on a line with the app's own springs, so the motion choice can be seen
 * before it shows up anywhere else: "lively" overshoots and squashes a little, "calm" glides in.
 * It restarts when [style] changes, because the theme then hands out the other springs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MotionPreview(style: MotionStyle, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    val position = remember { Animatable(0f) }
    val squash = remember { Animatable(1f) }
    val animationsEnabled = motionAnimationsEnabled()
    LaunchedEffect(style, motion, animationsEnabled) {
        if (!animationsEnabled) {
            position.snapTo(0.5f)
            squash.snapTo(1f)
            return@LaunchedEffect
        }
        var target = if (position.value < 0.5f) 1f else 0f
        while (true) {
            // A short squeeze as it sets off, released with the fast spring.
            launch {
                squash.snapTo(0.8f)
                squash.animateTo(1f, motion.fastSpatialSpec())
            }
            position.animateTo(target, motion.defaultSpatialSpec())
            delay(500)
            target = 1f - target
        }
    }

    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val ballColor = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            // Room at both ends, so an overshoot stays visible instead of being cut off.
            .padding(horizontal = 10.dp)
            .clearAndSetSemantics { },
    ) {
        val ballSize = 22.dp
        val travel = constraints.maxWidth - with(androidx.compose.ui.platform.LocalDensity.current) { ballSize.toPx() }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = ballSize / 2)
                .height(4.dp)
                .clip(CircleShape)
                .background(track),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(ballSize)
                .graphicsLayer {
                    translationX = travel * position.value
                    scaleX = 2f - squash.value
                    scaleY = squash.value
                }
                .clip(CircleShape)
                .background(ballColor),
        )
    }
}
