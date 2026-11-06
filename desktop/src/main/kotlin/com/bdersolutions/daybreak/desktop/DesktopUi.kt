package com.bdersolutions.daybreak.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.bdersolutions.daybreak.data.settings.LessonColorSettings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The user's lesson colours, provided at the root so any page can colour a subject the way the timetable does. */
val LocalLessonColorSettings = compositionLocalOf { LessonColorSettings() }

/** The pointer becomes a hand over anything that can be clicked, as on the web and in other desktop apps. */
fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)

/**
 * The Android app's motion, expressed as plain springs and tweens so it behaves the same on every
 * platform. Layout moves on springs, colours and fades use short tweens.
 */
object DesktopMotion {
    fun <T> spatial() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    fun <T> fastSpatial() = spring<T>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
    fun <T> effects() = tween<T>(durationMillis = 220, easing = FastOutSlowInEasing)
    fun <T> fastEffects() = tween<T>(durationMillis = 140, easing = FastOutSlowInEasing)
}

/**
 * A dialog in the middle of the window, used where the Android app opens a bottom sheet. It scales
 * and fades in, and scales and fades out before it closes. The content scrolls when it is taller
 * than the window.
 */
/**
 * Every dialog of the desktop app. [content] receives `close`, which plays the exit animation
 * before the dialog goes. Use it for every button that closes the dialog (Cancel, Done, after an
 * action), so closing animates the same way as the scrim, Escape and the outside click do.
 */
@Composable
fun DesktopDialog(
    onDismiss: () -> Unit,
    maxWidth: Dp = 560.dp,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) { shown = true }
    val close: () -> Unit = {
        if (shown) {
            shown = false
            scope.launch {
                delay(DIALOG_EXIT_MILLIS)
                latestOnDismiss()
            }
        }
    }
    Dialog(onDismissRequest = close) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(DesktopMotion.effects()) + scaleIn(DesktopMotion.spatial(), initialScale = 0.92f),
            exit = fadeOut(DesktopMotion.fastEffects()) + scaleOut(DesktopMotion.fastSpatial(), targetScale = 0.94f),
        ) {
            Surface(
                modifier = Modifier.widthIn(min = 360.dp, max = maxWidth).heightIn(max = 760.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                ) {
                    content(close)
                }
            }
        }
    }
}

/** How long the exit animation runs before the dialog leaves the composition. */
private const val DIALOG_EXIT_MILLIS = 180L
