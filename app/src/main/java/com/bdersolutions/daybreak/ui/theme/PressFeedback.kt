package com.bdersolutions.daybreak.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch

/**
 * The press feedback of every clickable in the app, instead of a ripple: while a finger is down the
 * element gets a darker tint and shrinks a little, and springs back on release.
 */
class PressFeedback(private val tint: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressFeedbackNode(interactionSource, tint)

    override fun equals(other: Any?): Boolean = other is PressFeedback && other.tint == tint

    override fun hashCode(): Int = tint.hashCode()
}

private const val PRESSED_TINT_ALPHA = 0.12f
private const val PRESSED_SHRINK = 0.04f

private class PressFeedbackNode(
    private val source: InteractionSource,
    private val tint: Color,
) : Modifier.Node(), DrawModifierNode {
    private val progress = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> launch {
                        progress.animateTo(1f, tween(durationMillis = 80))
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                        progress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val p = progress.value
        if (p > 0f) drawRect(color = tint.copy(alpha = PRESSED_TINT_ALPHA * p))
        val s = 1f - PRESSED_SHRINK * p
        withTransform({ scale(s, s, pivot = size.center) }) {
            this@draw.drawContent()
        }
    }
}
