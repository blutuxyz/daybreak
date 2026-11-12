package com.bdersolutions.daybreak.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.R
import kotlin.math.roundToInt

/** The bottom bar of the app, also used by the introduction, so the two look the same. */

/**
 * The bottom tabs, numbered by their place in the bar from left to right. The tab content slides by
 * these numbers too (a higher one comes in from the right), so keep them in bar order.
 */
internal object MainTab {
    const val ABSENCES = 0
    const val TIMETABLE = 1
    const val MORE = 2
}

private class BarTab(val icon: ImageVector, val selectedIcon: ImageVector, val label: String)

private val SlotHeight = 56.dp
private val SlotPadding = 16.dp

/**
 * Icons carry 2 to 4 dp of empty space inside their 24 dp box (the dots of "More" far more), so with 16 dp
 * on both sides the open pill looked pushed to the right. The label gets 4 dp more at its end.
 */
private val LabelEndPadding = 20.dp
private val IconSize = 24.dp
private val LabelGap = 8.dp

/**
 * One spring moves everything: the slot widths, their positions, the selection pill and the label.
 * No overshoot (like the Day/Week tray), and a fine visibility threshold: with the default of 0.01 the
 * pill stopped a few pixels short and snapped, the same jump `PushSpringTest` describes.
 */
private val BarSpring = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = 0.001f,
)

/** Same values as [pressFeedback], so the selection pill shrinks together with the pressed slot. */
private const val PRESS_SHRINK = 0.04f

@Composable
internal fun DaybreakBottomBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    // A floating pill, like the Material 3 Expressive toolbar: the selected tab opens into a filled
    // pill with its label, the others are round icon buttons.
    //
    // One filled pill slides from tab to tab instead of one fading out while another fades in. The
    // slots, the pill and the colours all come from [pillBarFrame], computed from the same animated
    // values in the layout and the draw pass, so nothing lags behind anything else and nothing
    // recomposes while it moves. Icons and labels are drawn in one colour and recoloured where the
    // pill covers them, so the colour changes exactly at the pill's edge.
    // In the order of [MainTab]: Absences on the left, Timetable in the centre, More on the right.
    val tabs = listOf(
        BarTab(Icons.Outlined.EventBusy, Icons.Filled.EventBusy, stringResource(R.string.nav_absences)),
        BarTab(Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth, stringResource(R.string.nav_timetable)),
        BarTab(Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz, stringResource(R.string.nav_settings)),
    )
    val expansion: List<State<Float>> = tabs.indices.map { i ->
        animateFloatAsState(
            targetValue = if (i == selectedTab) 1f else 0f,
            animationSpec = BarSpring,
            label = "bottomBarTab$i",
        )
    }
    val sources = remember { List(tabs.size) { MutableInteractionSource() } }
    val selectedPressed by sources[selectedTab.coerceIn(0, tabs.lastIndex)].collectIsPressedAsState()
    val press = animateFloatAsState(
        targetValue = if (selectedPressed) 1f else 0f,
        label = "bottomBarPress",
    )

    val labelStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val collapsedPx = with(density) { SlotHeight.toPx() }
    val insetPx = with(density) { ((SlotHeight - IconSize) / 2).toPx() }
    val chromePx = with(density) { (SlotPadding + IconSize + LabelGap + LabelEndPadding).toPx() }
    val expandedPx = FloatArray(tabs.size) { i ->
        chromePx + measurer.measure(tabs[i].label, labelStyle, maxLines = 1, softWrap = false).size.width
    }
    fun frame(width: Float) = pillBarFrame(
        width = width,
        collapsed = collapsedPx,
        inset = insetPx,
        expanded = expandedPx,
        expansion = FloatArray(tabs.size) { expansion[it].value },
    )

    val pillColor = MaterialTheme.colorScheme.primary
    val onPill = MaterialTheme.colorScheme.onPrimary
    val onBar = MaterialTheme.colorScheme.onSurfaceVariant
    val clip = remember { Path() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = onBar,
    ) {
        Layout(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .height(SlotHeight)
                .selectableGroup()
                .drawBehind {
                    val pill = pillBounds(frame(size.width), size, layoutDirection, press.value)
                        ?: return@drawBehind
                    drawRoundRect(
                        color = pillColor,
                        topLeft = pill.topLeft,
                        size = pill.size,
                        cornerRadius = CornerRadius(pill.height / 2f),
                    )
                }
                // The slots are drawn into their own layer and then recoloured: everything in the
                // bar's content colour, and the onPrimary colour inside the pill.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(onBar, blendMode = BlendMode.SrcIn)
                    val pill = pillBounds(frame(size.width), size, layoutDirection, press.value)
                        ?: return@drawWithContent
                    clip.reset()
                    clip.addRoundRect(RoundRect(pill, CornerRadius(pill.height / 2f)))
                    clipPath(clip) { drawRect(onPill, blendMode = BlendMode.SrcIn) }
                },
            content = {
                tabs.forEachIndexed { i, tab ->
                    BarSlot(
                        tab = tab,
                        selected = i == selectedTab,
                        expansion = expansion[i],
                        source = sources[i],
                        onClick = { onSelect(i) },
                    )
                }
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val slots = frame(width.toFloat())
            val placeables = measurables.mapIndexed { i, measurable ->
                measurable.measure(Constraints.fixed(slots.widths[i].roundToInt(), height))
            }
            layout(width, height) {
                placeables.forEachIndexed { i, placeable ->
                    placeable.placeRelative(slots.lefts[i].roundToInt(), 0)
                }
            }
        }
    }
}

/** The selection pill in the bar's own coordinates, mirrored for right-to-left and shrunk while pressed. */
private fun pillBounds(frame: PillBarFrame, size: Size, direction: LayoutDirection, press: Float): Rect? {
    if (!frame.indicatorShown) return null
    var left = frame.indicatorLeft
    var right = frame.indicatorRight
    if (direction == LayoutDirection.Rtl) {
        left = size.width - frame.indicatorRight
        right = size.width - frame.indicatorLeft
    }
    val scale = 1f - PRESS_SHRINK * press
    val center = Offset((left + right) / 2f, size.height / 2f)
    val halfWidth = (right - left) / 2f * scale
    val halfHeight = size.height / 2f * scale
    return Rect(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
}

/** 0 → 1 over the middle of the move, so filled and outlined icon are seldom seen on top of each other. */
private fun filledIconAlpha(expansion: Float) = ((expansion - 0.25f) / 0.5f).coerceIn(0f, 1f)

/** The label only shows in the last part of opening and is gone early when closing. */
private fun labelAlpha(expansion: Float) = ((expansion - 0.4f) / 0.6f).coerceIn(0f, 1f)

@Composable
private fun BarSlot(
    tab: BarTab,
    selected: Boolean,
    expansion: State<Float>,
    source: MutableInteractionSource,
    onClick: () -> Unit,
) {
    // The slot is as wide as the bar's layout says; its content keeps its natural width, starts at the
    // left edge and is cut off by the slot, so the icon never moves inside the slot and the label is
    // uncovered as the slot opens.
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .pressFeedback(source, CircleShape)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .padding(start = SlotPadding, end = LabelEndPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(IconSize)) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - filledIconAlpha(expansion.value) },
                )
                Icon(
                    imageVector = tab.selectedIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = filledIconAlpha(expansion.value) },
                )
            }
            Spacer(Modifier.width(LabelGap))
            // Always composed (also for TalkBack, which reads it as the tab's name), only faded.
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer { alpha = labelAlpha(expansion.value) },
            )
        }
    }
}
