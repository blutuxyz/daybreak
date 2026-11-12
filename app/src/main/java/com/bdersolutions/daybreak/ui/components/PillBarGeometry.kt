package com.bdersolutions.daybreak.ui.components

/**
 * Where the slots of the floating bottom bar and its selection pill are in one frame. Pure, so the
 * layout pass and the draw pass of [DaybreakBottomBar] compute exactly the same numbers and nothing
 * has to chase anything else.
 *
 * Each slot has an expansion between 0 (a round icon button, [collapsed] wide, whose icon sits [inset]
 * in from each side) and 1 (the open pill with its label, [expanded] wide).
 *
 * The slots are spread by what is *visible*, not by their boxes: the gaps between the pill's edge, the
 * icons and the bar's edges are all the same. An open pill at either end lies flush with the bar, and
 * an open pill in the middle sits exactly in the centre. Plain `SpaceBetween` on the boxes let the
 * middle icon drift towards whichever side had room and left uneven gaps around it.
 *
 * The selection pill is the expansion-weighted average of the slots' bounds: at rest it covers the
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
    inset: Float,
    expanded: FloatArray,
    expansion: FloatArray,
): PillBarFrame {
    val count = expanded.size
    require(expansion.size == count) { "one expansion per slot" }
    require(count > 0) { "at least one slot" }
    val e = FloatArray(count) { expansion[it].coerceIn(0f, 1f) }
    // A long label is cut before the pill crowds the icons: they keep a visible gap of at least half
    // an inset (their see-through margins may overlap then, which nobody sees).
    val widest = (width - (count - 1) * (collapsed - 2 * inset) - (count + 1) * inset / 2f)
        .coerceAtLeast(collapsed)
    val widths = FloatArray(count) { i ->
        val open = expanded[i].coerceIn(collapsed, widest)
        collapsed + (open - collapsed) * e[i]
    }
    // The see-through margin around each icon: all of it while the slot is a round button, none once
    // the slot is the filled pill.
    val margins = FloatArray(count) { inset * (1f - e[it]) }
    // How much of a visible gap the bar's ends get: a full one next to an icon, none next to the pill.
    val leftEdge = 1f - e[0]
    val rightEdge = 1f - e[count - 1]

    // Solve for the one visible gap G. Between two slots the box gap is G minus both margins; at an
    // end it is edge * (G - inset). Everything adds up to the bar's width.
    var marginSum = 0f
    for (i in 0 until count - 1) marginSum += margins[i] + margins[i + 1]
    val gaps = (count - 1) + leftEdge + rightEdge
    val free = width - widths.sum() + marginSum + inset * (leftEdge + rightEdge)
    val gap = if (gaps > 0.0001f) free / gaps else 0f

    val lefts = FloatArray(count)
    var x = leftEdge * (gap - inset)
    for (i in 0 until count) {
        lefts[i] = x
        if (i < count - 1) x += widths[i] + gap - margins[i] - margins[i + 1]
    }

    var weight = 0f
    var left = 0f
    var right = 0f
    for (i in 0 until count) {
        weight += e[i]
        left += e[i] * lefts[i]
        right += e[i] * (lefts[i] + widths[i])
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
