package com.bdersolutions.daybreak.ui.assistant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.stringResource
import com.bdersolutions.daybreak.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics

/** One choice in a [PickerChip]. */
internal data class PickerOption(
    val id: String,
    val label: String,
    /** A badge next to the label, e.g. "Free"; null for none. */
    val tag: String? = null,
    val tagAlarm: Boolean = false,
)

/**
 * A small pill on the prompt bar showing the current choice; tapping it opens a menu that grows out of the
 * pill. The label slides when it changes, the chevron turns, and the menu's rows come in one after another.
 */
@Composable
internal fun PickerChip(
    current: String,
    options: List<PickerOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    var open by remember { mutableStateOf(false) }
    val menu = remember { MutableTransitionState(false) }
    menu.targetState = open
    val chevron by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
        label = "chipChevron",
    )
    Box {
        Surface(
            onClick = {
                haptics.perform(HapticEvent.TAP)
                open = true
            },
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            contentColor = colors.onSurfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        (fadeIn(tween(200, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 2 })
                            .togetherWith(fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 2 })
                            .using(SizeTransform(clip = false))
                    },
                    label = "chipLabel",
                ) { text ->
                    Text(text, style = MaterialTheme.typography.labelLarge)
                }
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = chevron },
                )
            }
        }
    }
    if (menu.currentState || menu.targetState) {
        val density = LocalDensity.current
        val gap = with(density) { 8.dp.roundToPx() }
        val inset = with(density) { MENU_SHADOW_INSET.roundToPx() }
        val position = remember(gap, inset) { AbovePosition(gap, inset) }
        Popup(
            popupPositionProvider = position,
            onDismissRequest = { open = false },
            properties = PopupProperties(focusable = true),
        ) {
            AnimatedVisibility(
                visibleState = menu,
                // The menu is right-aligned to the chip, so it grows out of its bottom-right corner and slides in
                // from the right.
                enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        initialScale = 0.86f,
                        transformOrigin = TransformOrigin(1f, 1f),
                    ) +
                    slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 8 },
                exit = fadeOut(tween(150, easing = LinearOutSlowInEasing)) +
                    scaleOut(
                        animationSpec = tween(180, easing = LinearOutSlowInEasing),
                        targetScale = 0.9f,
                        transformOrigin = TransformOrigin(1f, 1f),
                    ) +
                    slideOutHorizontally(tween(180, easing = LinearOutSlowInEasing)) { it / 8 },
            ) {
                // The padding gives the shadow room inside the popup window, where it would otherwise be cut off at the
                // edge and look hard. AbovePosition takes the padding into account, so the visible menu keeps its place.
                Surface(
                    modifier = Modifier.padding(MENU_SHADOW_INSET),
                    shape = RoundedCornerShape(22.dp),
                    color = colors.surfaceContainerHigh,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 210.dp).padding(vertical = 6.dp)) {
                        options.forEachIndexed { index, option ->
                            Reveal(index = index) {
                                PickerRow(
                                    label = option.label,
                                    tag = option.tag,
                                    tagAlarm = option.tagAlarm,
                                    selected = option.id == selectedId,
                                    onClick = {
                                        open = false
                                        haptics.perform(HapticEvent.TAP)
                                        onSelect(option.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, tag: String?, tagAlarm: Boolean, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) colors.secondaryContainer else Color.Transparent,
        animationSpec = tween(200),
        label = "pickerRow",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (tag != null) {
            ModelTagBadge(text = tag, alarm = tagAlarm)
            Spacer(Modifier.width(6.dp))
        }
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f), initialScale = 0.4f) + fadeIn(),
            exit = scaleOut(tween(120), targetScale = 0.4f) + fadeOut(tween(120)),
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onSecondaryContainer,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp),
            )
        }
    }
}

/**
 * The attach button on the prompt bar. A plus that turns into a cross while its menu is open; the menu grows out of
 * the button and its rows come in one after another: a picture from the gallery, a camera picture, or a file.
 */
@Composable
internal fun AttachMenu(
    enabled: Boolean,
    onPhoto: () -> Unit,
    onCamera: () -> Unit,
    onFile: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    var open by remember { mutableStateOf(false) }
    val menu = remember { MutableTransitionState(false) }
    menu.targetState = open
    val turn by animateFloatAsState(
        targetValue = if (open) 45f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "attachTurn",
    )
    Box {
        IconButton(
            onClick = {
                haptics.perform(HapticEvent.TAP)
                open = true
            },
            enabled = enabled,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.assistant_attach),
                modifier = Modifier.graphicsLayer { rotationZ = turn },
            )
        }
    }
    if (menu.currentState || menu.targetState) {
        val density = LocalDensity.current
        val gap = with(density) { 8.dp.roundToPx() }
        val inset = with(density) { MENU_SHADOW_INSET.roundToPx() }
        val position = remember(gap, inset) { AbovePosition(gap, inset, alignRight = false) }
        Popup(
            popupPositionProvider = position,
            onDismissRequest = { open = false },
            properties = PopupProperties(focusable = true),
        ) {
            AnimatedVisibility(
                visibleState = menu,
                enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        initialScale = 0.86f,
                        transformOrigin = TransformOrigin(0f, 1f),
                    ) +
                    slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 8 },
                exit = fadeOut(tween(150, easing = LinearOutSlowInEasing)) +
                    scaleOut(
                        animationSpec = tween(180, easing = LinearOutSlowInEasing),
                        targetScale = 0.9f,
                        transformOrigin = TransformOrigin(0f, 1f),
                    ) +
                    slideOutHorizontally(tween(180, easing = LinearOutSlowInEasing)) { -it / 8 },
            ) {
                Surface(
                    modifier = Modifier.padding(MENU_SHADOW_INSET),
                    shape = RoundedCornerShape(22.dp),
                    color = colors.surfaceContainerHigh,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 200.dp).padding(vertical = 6.dp)) {
                        val rows = listOf(
                            Triple(Icons.Outlined.AddPhotoAlternate, R.string.assistant_attach_photo, onPhoto),
                            Triple(Icons.Outlined.PhotoCamera, R.string.assistant_attach_camera, onCamera),
                            Triple(Icons.Outlined.AttachFile, R.string.assistant_attach_file, onFile),
                        )
                        rows.forEachIndexed { index, (icon, label, action) ->
                            Reveal(index = index) {
                                AttachRow(icon = icon, label = stringResource(label)) {
                                    open = false
                                    haptics.perform(HapticEvent.TAP)
                                    action()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
    }
}

/**
 * The badge a model can carry: "Free", "Beta", or a warning. An info badge is quiet, an alarm badge is red. A
 * tapped badge is what [onClick] is for: the model's description opens under the prompt.
 */
@Composable
internal fun ModelTagBadge(text: String, alarm: Boolean, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val container = if (alarm) colors.errorContainer else colors.secondaryContainer
    val content = if (alarm) colors.onErrorContainer else colors.onSecondaryContainer
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Room around the menu for its shadow. */
private val MENU_SHADOW_INSET = 20.dp

/**
 * Places the menu directly above its chip, its visible right edge on the chip's and its visible bottom [gap]
 * above the chip, kept on screen. [inset] is the shadow room that surrounds the visible menu.
 */
private class AbovePosition(private val gap: Int, private val inset: Int, private val alignRight: Boolean = true) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = if (alignRight) {
            (anchorBounds.right - popupContentSize.width + inset).coerceIn(0, maxX)
        } else {
            (anchorBounds.left - inset).coerceIn(0, maxX)
        }
        val y = (anchorBounds.top - gap - popupContentSize.height + inset).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
