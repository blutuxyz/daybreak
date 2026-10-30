package com.bdersolutions.daybreak.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.delay

/**
 * The platform animation scale is carried in the recomposer's coroutine context.
 * Returning false when the user disabled animations lets custom ambient motion
 * stop instead of silently running forever.
 */
@Composable
fun motionAnimationsEnabled(): Boolean {
    val scope = rememberCoroutineScope()
    return (scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
}

/**
 * A staggered fade/scale reveal driven by the expressive spatial spring.
 * Callers read the returned state inside graphicsLayer so the animation stays
 * in the draw phase and never triggers layout or recomposition.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberStaggeredReveal(
    key: Any?,
    index: Int,
    enabled: Boolean = true,
    staggerMillis: Int = 32,
): State<Float> {
    val animationsEnabled = motionAnimationsEnabled()
    val progress = remember(key, index, animationsEnabled) {
        Animatable(if (animationsEnabled && enabled) 0f else 1f)
    }
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(key, index, enabled, animationsEnabled) {
        if (!animationsEnabled || !enabled) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        delay((index * staggerMillis).toLong())
        progress.animateTo(1f, animationSpec = spring)
    }
    return progress.asState()
}

/** Spring press feedback intended for graphicsLayer scale. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    val animationsEnabled = motionAnimationsEnabled()
    val spring = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    return animateFloatAsState(
        targetValue = if (animationsEnabled && pressed) pressedScale else 1f,
        animationSpec = spring,
        label = "pressScale",
    )
}
