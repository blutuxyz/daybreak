package com.bdersolutions.daybreak.ui.components

/**
 * Where the slots of the floating bottom bar and its selection pill are in one frame. Pure, so the
 * layout pass and the draw pass of [DaybreakBottomBar] compute exactly the same numbers and nothing
 * has to chase anything else.
 *
 * Each slot has an expansion between 0 (a round icon button, [collapsed] wide) and 1 (the open pill
 * with its label, [expanded] wide). The slots are spread like `Arrangement.SpaceBetween`. The
 * selection pill is the expansion-weighted average of the slots' bounds: at rest it covers the
 * selected slot exactly, and while the selection changes it glides from the old slot to the new one.
 */
internal class PillBarFrame(
    val lefts: FloatArray,
    val widths: FloatArray,
    val indicatorLeft: Float,
    val indicatorRight: Float,
    val indicatorShown: Boolean,
)

internal fun pillBarFrame(
    width: Float,
    collapsed: Float,
    expanded: FloatArray,
    expansion: FloatArray,
): PillBarFrame {
    val count = expanded.size
    require(expansion.size == count) { "one expansion per slot" }
    // An open slot never pushes the others out of the bar.
    val widest = (width - (count - 1) * collapsed).coerceAtLeast(collapsed)
    val widths = FloatArray(count) { i ->
        val open = expanded[i].coerceIn(collapsed, widest)
        collapsed + (open - collapsed) * expansion[i].coerceIn(0f, 1f)
    }
    val gap = if (count > 1) ((width - widths.sum()) / (count - 1)).coerceAtLeast(0f) else 0f
    val lefts = FloatArray(count)
    var x = 0f
    for (i in 0 until count) {
        lefts[i] = x
        x += widths[i] + gap
    }
    var weight = 0f
    var left = 0f
    var right = 0f
    for (i in 0 until count) {
        val e = expansion[i].coerceIn(0f, 1f)
        weight += e
        left += e * lefts[i]
        right += e * (lefts[i] + widths[i])
    }
    val shown = weight > 0.0001f
    return PillBarFrame(
        lefts = lefts,
        widths = widths,
        indicatorLeft = if (shown) left / weight else 0f,
        indicatorRight = if (shown) right / weight else 0f,
        indicatorShown = shown,
    )
}
