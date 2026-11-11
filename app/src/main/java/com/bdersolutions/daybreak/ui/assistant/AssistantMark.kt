package com.bdersolutions.daybreak.ui.assistant

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** A four-pointed star with concave sides, centred on ([cx], [cy]) with its points [r] away. */
private fun DrawScope.sparkle(cx: Float, cy: Float, r: Float, brush: Brush) {
    val k = r * 0.22f
    val path = Path().apply {
        moveTo(cx, cy - r)
        quadraticBezierTo(cx + k, cy - k, cx + r, cy)
        quadraticBezierTo(cx + k, cy + k, cx, cy + r)
        quadraticBezierTo(cx - k, cy + k, cx - r, cy)
        quadraticBezierTo(cx - k, cy - k, cx, cy - r)
        close()
    }
    drawPath(path, brush)
}

/**
 * Only the first mark composed in a process claims the launch shine, so the sparkle in the timetable bar
 * shines once when the app starts and not again when the screen comes back.
 */
internal object LaunchShine {
    private var claimed = false

    @Synchronized
    fun claim(): Boolean {
        if (claimed) return false
        claimed = true
        return true
    }
}

/**
 * The assistant's mark: a sun coming up over a horizon, because the app is called Daybreak. [plain] draws
 * only the glyph in the primary colour (the top bar); otherwise it sits on a rounded gradient tile.
 * [pulses] shines the glyph that many times when it enters the composition: it brightens and swells a little,
 * then settles, so it stands out.
 */
@Composable
internal fun DaybreakMark(
    size: Int,
    modifier: Modifier = Modifier,
    plain: Boolean = false,
    tint: Color? = null,
    pulses: Int = 0,
    /** Waits this long before the first sweep, e.g. until a page that slides in has settled. */
    startDelayMillis: Long = 0L,
) {
    val colors = MaterialTheme.colorScheme
    val ink = tint ?: if (plain) colors.primary else colors.onPrimary
    // Each pulse is one glint: a band of light that sweeps across the star from left to right. Between sweeps
    // the band sits off the mark, so the star is plain ink.
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (startDelayMillis > 0) delay(startDelayMillis)
        repeat(pulses) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
        sweep.snapTo(0f)
    }
    Canvas(modifier = modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        if (plain) {
            // The usual AI mark: one large four-point star with two smaller ones, each with the passing glint.
            // The band's centre travels along the diagonal from the top left to the bottom right.
            val t = -0.4f + 1.8f * sweep.value
            val cx = w * t
            val cy = h * t
            // Idle, the star is plain ink. During a sweep a diagonal band of light crosses it.
            val glint: Brush = if (sweep.value <= 0f || sweep.value >= 1f) {
                SolidColor(ink)
            } else {
                Brush.linearGradient(
                    colors = listOf(ink, Color.White.copy(alpha = 0.95f), ink),
                    start = Offset(cx - w * 0.25f, cy - h * 0.25f),
                    end = Offset(cx + w * 0.25f, cy + h * 0.25f),
                )
            }
            sparkle(w * 0.42f, h * 0.56f, w * 0.38f, glint)
            sparkle(w * 0.82f, h * 0.20f, w * 0.15f, glint)
            sparkle(w * 0.80f, h * 0.80f, w * 0.10f, glint)
            return@Canvas
        }
        if (!plain) {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(colors.primary, colors.tertiary), start = Offset.Zero, end = Offset(w, h)),
                cornerRadius = CornerRadius(w * 0.3f),
            )
        }
        val cx = w / 2f
        val horizon = h * 0.56f
        val r = w * 0.2f
        val stroke = w * 0.065f
        // The sun: the upper half of a disc resting on the horizon.
        drawArc(
            color = ink,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - r, horizon - r),
            size = Size(r * 2, r * 2),
        )
        // Three rays fanning out above the sun.
        listOf(210.0, 270.0, 330.0).forEach { degrees ->
            val rad = Math.toRadians(degrees)
            val dx = cos(rad).toFloat()
            val dy = sin(rad).toFloat()
            val from = r + w * 0.09f
            val to = r + w * 0.22f
            drawLine(
                color = ink,
                start = Offset(cx + dx * from, horizon + dy * from),
                end = Offset(cx + dx * to, horizon + dy * to),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        // The horizon, and a shorter line under it.
        drawLine(ink, Offset(w * 0.2f, horizon), Offset(w * 0.8f, horizon), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(ink, Offset(w * 0.34f, horizon + w * 0.14f), Offset(w * 0.66f, horizon + w * 0.14f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
