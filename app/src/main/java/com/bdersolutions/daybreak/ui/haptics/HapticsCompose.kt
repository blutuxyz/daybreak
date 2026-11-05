package com.bdersolutions.daybreak.ui.haptics

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.data.haptics.Haptics
import kotlin.math.roundToInt

/** The app's haptics engine. The default is silent, so previews and tests need no motor. */
val LocalHaptics = staticCompositionLocalOf { Haptics.None }

/**
 * A [Slider] that clicks into each notch like a physical dial. The notch size comes from [steps]
 * (the slider's own snapping points) or, for a smooth slider, from [tickEvery] in value units.
 * [tick] plays after [onValueChange] has run, so it can already use the new value.
 */
@Composable
fun HapticSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    tickEvery: Float = 0f,
    onValueChangeFinished: (() -> Unit)? = null,
    tick: (() -> Unit)? = null,
) {
    val haptics = LocalHaptics.current
    val unit = when {
        steps > 0 -> (valueRange.endInclusive - valueRange.start) / (steps + 1)
        tickEvery > 0f -> tickEvery
        else -> 0f
    }
    fun notch(v: Float): Int = if (unit > 0f) ((v - valueRange.start) / unit).roundToInt() else 0
    val lastNotch = remember { mutableIntStateOf(notch(value)) }
    val latestTick by rememberUpdatedState(tick)
    Slider(
        value = value,
        onValueChange = { newValue ->
            onValueChange(newValue)
            if (unit > 0f) {
                val now = notch(newValue)
                if (now != lastNotch.intValue) {
                    lastNotch.intValue = now
                    latestTick?.invoke() ?: haptics.perform(HapticEvent.STEP)
                }
            }
        },
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
    )
}

/**
 * [PullToRefreshBox] that ticks once when the pull reaches the point where letting go refreshes.
 * A refresh started by a button also animates the indicator, but does not tick.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HapticPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptics = LocalHaptics.current
    val refreshing by rememberUpdatedState(isRefreshing)
    LaunchedEffect(state) {
        var armed = false
        snapshotFlow { state.distanceFraction >= 1f }.collect { reached ->
            if (reached && !armed && !refreshing) haptics.perform(HapticEvent.REFRESH_PULL)
            armed = reached
        }
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        content = content,
    )
}
