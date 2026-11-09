package com.bdersolutions.daybreak.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** No bounce, about the pace of an iOS push: most of the way in 0.2 s, settled after about 0.45 s. */
private val PushSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f)

/** How far the screen underneath moves to the left while a page covers it, as a share of the width. */
private const val BASE_SHIFT = 0.28f

/** How dark the screen underneath gets while a page covers it. */
private const val BASE_DIM = 0.16f

/**
 * Shows [base], and [content] for [page] pushed over it the way iOS does it: the page slides in from
 * the right edge while the screen underneath moves a little to the left and dims. Closing reverses
 * it. The system back gesture drags the page along with the finger and can be cancelled halfway,
 * like the swipe back on iOS.
 *
 * Every full-screen page uses this, so they all move alike; short pickers and details stay sheets.
 * Hosts can be nested: a page can hold its own host for a deeper page.
 */
@Composable
fun <T : Any> PushHost(
    page: T?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    base: @Composable () -> Unit,
    content: @Composable (T) -> Unit,
) {
    // The last page is kept while it slides out, after [page] went back to null.
    var shown by remember { mutableStateOf(page) }
    if (page != null && page != shown) shown = page
    val progress = remember { Animatable(if (page != null) 1f else 0f) }
    val scope = rememberCoroutineScope()
    val animationsEnabled = motionAnimationsEnabled()

    LaunchedEffect(page) {
        if (page != null) {
            if (animationsEnabled) {
                // The first frame of a new page is the expensive one (composing and laying it out).
                // The spring starts after it, off screen, so it does not lose its first part to that
                // frame and arrive almost in place.
                if (progress.value == 0f) repeat(2) { withFrameNanos { } }
                progress.animateTo(1f, PushSpring)
            } else {
                progress.snapTo(1f)
            }
        } else {
            if (animationsEnabled) progress.animateTo(0f, PushSpring) else progress.snapTo(0f)
            shown = null
        }
    }

    PredictiveBackHandler(enabled = page != null) { events ->
        try {
            events.collect { event -> progress.snapTo(1f - event.progress) }
            onBack()
        } catch (cancelled: CancellationException) {
            // The swipe was let go before it counted: the page settles back.
            scope.launch { progress.animateTo(1f, PushSpring) }
            throw cancelled
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = -width * BASE_SHIFT * progress.value },
        ) {
            base()
        }
        val current = shown
        if (current != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value }
                    .background(Color.Black.copy(alpha = BASE_DIM)),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = width * (1f - progress.value) }
                    // A soft shadow along the left edge. A gradient strip, since an elevation shadow on
                    // a full-screen layer cost so much per frame that the slide dropped most frames.
                    .drawBehind {
                        val edge = 14.dp.toPx()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)),
                                startX = -edge,
                                endX = 0f,
                            ),
                            topLeft = Offset(-edge, 0f),
                            size = Size(edge, size.height),
                        )
                    }
                    // Taps on empty parts of the page must not reach the screen underneath.
                    .pointerInput(Unit) {},
            ) {
                content(current)
            }
        }
    }
}
