package com.bdersolutions.daybreak.ui.assistant

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
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
 * The one-time full-screen page for people who already used the app: "Daybreak AI is now Dawn". Under the
 * words sits a small picture of the answer screen (a question, Dawn's answer typing itself out, the input bar),
 * with "Try now" (opens the assistant) and "Later". The sun rises behind it, the picture and the words follow
 * one after another, and closing plays the scene backwards.
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

    // Every step is its own value, so the sun, the title, the picture and the buttons each move on their own clock.
    val rise = remember { Animatable(if (animations) 0f else 1f) }
    val titleIn = remember { Animatable(if (animations) 0f else 1f) }
    val bodyIn = remember { Animatable(if (animations) 0f else 1f) }
    val screenIn = remember { Animatable(if (animations) 0f else 1f) }
    val questionIn = remember { Animatable(if (animations) 0f else 1f) }
    val typed = remember { Animatable(if (animations) 0f else 1f) }
    val buttonsIn = remember { Animatable(if (animations) 0f else 1f) }
    val exit = remember { Animatable(1f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!animations) return@LaunchedEffect
        launch { rise.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
        launch {
            delay(200)
            titleIn.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
            haptics.perform(HapticEvent.UPDATE_READY)
        }
        launch {
            delay(360)
            bodyIn.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        }
        launch {
            delay(560)
            screenIn.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 220f))
        }
        launch {
            delay(1000)
            questionIn.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f))
            delay(350)
            typed.animateTo(1f, tween(1800, easing = LinearEasing))
        }
        launch {
            delay(900)
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
            // Taps on the page do not reach the screen behind it.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.v2_dawn_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = titleIn.value
                    translationY = (1f - titleIn.value) * offsetPx
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.v2_dawn_body),
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
            Spacer(Modifier.height(28.dp))
            AnswerScreenGraphic(
                screenIn = { screenIn.value },
                questionIn = { questionIn.value },
                typed = { typed.value },
            )
            Spacer(Modifier.height(32.dp))
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
                TextButton(onClick = { close(onLater) }) { Text(stringResource(R.string.v2_dawn_later)) }
                Button(onClick = { close(onTry) }) { Text(stringResource(R.string.v2_dawn_try)) }
            }
        }
    }
}

/**
 * A picture of Dawn's answer screen: the top bar with the mark, a question, the answer typing itself out and
 * the input bar. Drawn with real composables in the app's own colours, so it is always the current look. The
 * three progress values (0 to 1) drive the entrance, the question and the typing.
 */
@Composable
private fun AnswerScreenGraphic(
    screenIn: () -> Float,
    questionIn: () -> Float,
    typed: () -> Float,
) {
    val colors = MaterialTheme.colorScheme
    val answer = stringResource(R.string.v2_dawn_demo_answer)
    val shown = (answer.length * typed()).toInt().coerceIn(0, answer.length)
    Column(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth()
            .graphicsLayer {
                val p = screenIn().coerceIn(0f, 1.2f)
                alpha = p.coerceIn(0f, 1f)
                translationY = (1f - p) * 60.dp.toPx()
                val scale = 0.92f + 0.08f * p
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(28.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DaybreakMark(size = 22, plain = true, pulses = 1, startDelayMillis = 900)
            Text(
                text = stringResource(R.string.assistant_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.End)
                .graphicsLayer {
                    val p = questionIn().coerceIn(0f, 1.1f)
                    alpha = p.coerceIn(0f, 1f)
                    translationX = (1f - p) * 40.dp.toPx()
                }
                .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                .background(colors.primary)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.v2_dawn_demo_question),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = questionIn().coerceIn(0f, 1f) }
                .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                .background(colors.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            // The full text sits invisibly underneath, so the bubble has its final size from the start.
            Text(text = answer, style = MaterialTheme.typography.bodyMedium, color = Color.Transparent)
            Text(
                text = answer.take(shown),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest)
                .padding(start = 16.dp, end = 6.dp)
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.assistant_input_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
