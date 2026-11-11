package com.bdersolutions.daybreak.ui.assistant

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.ui.components.Button
import com.bdersolutions.daybreak.ui.components.TextButton
import com.bdersolutions.daybreak.ui.components.motionAnimationsEnabled
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The one-time full-screen notice for people who already used the app: "Daylight AI is now Dawn", with
 * "Try it" (opens the assistant) and "Later". The sun rises behind it, the mark settles with a spring, and the
 * words and buttons follow one after another. Closing plays the same scene backwards.
 */
@Composable
fun DawnIntroOverlay(
    onTry: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val animations = motionAnimationsEnabled()
    val scope = rememberCoroutineScope()
    val offsetPx = with(LocalDensity.current) { 40.dp.toPx() }

    // Every step is its own value, so the sun, the mark, the words and the buttons each move on their own clock.
    val rise = remember { Animatable(if (animations) 0f else 1f) }
    val markIn = remember { Animatable(if (animations) 0f else 1f) }
    val titleIn = remember { Animatable(if (animations) 0f else 1f) }
    val bodyIn = remember { Animatable(if (animations) 0f else 1f) }
    val buttonsIn = remember { Animatable(if (animations) 0f else 1f) }
    val exit = remember { Animatable(1f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!animations) return@LaunchedEffect
        launch { rise.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
        launch {
            delay(180)
            markIn.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 170f))
            haptics.perform(HapticEvent.UPDATE_READY)
        }
        launch {
            delay(520)
            titleIn.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        }
        launch {
            delay(680)
            bodyIn.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        }
        launch {
            delay(880)
            buttonsIn.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 320f))
        }
    }

    fun close(then: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            exit.animateTo(0f, tween(260, easing = FastOutLinearInEasing))
            then()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = exit.value
                val scale = 0.96f + 0.04f * exit.value
                scaleX = scale
                scaleY = scale
            }
            .background(colors.surface)
            .drawBehind {
                // The sun rises from the bottom edge: a glow that climbs and widens while it rises.
                val r = rise.value
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.tertiary.copy(alpha = 0.42f * r),
                            colors.primary.copy(alpha = 0.12f * r),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height * (1.12f - 0.4f * r)),
                        radius = size.width * (0.45f + 0.95f * r),
                    ),
                )
            }
            // Taps on the notice do not reach the screen behind it.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = markIn.value.coerceIn(0f, 1f)
                    translationY = (1f - markIn.value) * offsetPx
                    val scale = 0.6f + 0.4f * markIn.value
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                DaybreakMark(size = 112)
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.dawn_intro_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = titleIn.value
                    translationY = (1f - titleIn.value) * offsetPx
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.dawn_intro_body),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = bodyIn.value
                        translationY = (1f - bodyIn.value) * offsetPx
                    },
            )
            Spacer(Modifier.height(40.dp))
            Row(
                modifier = Modifier.graphicsLayer {
                    alpha = buttonsIn.value.coerceIn(0f, 1f)
                    val scale = 0.9f + 0.1f * buttonsIn.value
                    scaleX = scale
                    scaleY = scale
                },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { close(onLater) }) { Text(stringResource(R.string.dawn_intro_later)) }
                Button(onClick = { close(onTry) }) { Text(stringResource(R.string.dawn_intro_try)) }
            }
        }
    }
}
