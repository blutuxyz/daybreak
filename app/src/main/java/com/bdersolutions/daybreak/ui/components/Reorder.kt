package com.bdersolutions.daybreak.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drag-to-reorder for the items of a `LazyColumn`. The list shows [order]. While a card is dragged,
 * [order] changes on screen first, and [onCommit] saves it when the finger lifts.
 *
 * Items must be keyed with [keyOf], include their own bottom gap in their height, and apply
 * [offsetOf] as a translation while dragged. Other items should use `Modifier.animateItem()`, so
 * they slide out of the way.
 */
@Stable
class ReorderState<T : Any>(
    initial: List<T>,
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    private val settleSpec: FiniteAnimationSpec<Float>,
) {
    var keyOf: (T) -> Any = { it }
    var onCommit: (List<T>) -> Unit = {}

    var order: List<T> by mutableStateOf(initial)
        private set
    var draggingKey: Any? by mutableStateOf(null)
        private set

    private var dragDistance by mutableFloatStateOf(0f)
    private var releasing by mutableStateOf(false)
    private val settle = Animatable(0f)

    fun isDragging(key: Any): Boolean = key == draggingKey

    /** The translation, in pixels, to apply to the item with [key]. Read it in a draw lambda. */
    fun offsetOf(key: Any): Float = when {
        key != draggingKey -> 0f
        releasing -> settle.value
        else -> dragDistance
    }

    /** Takes the source order, unless a drag is in progress. */
    fun sync(items: List<T>) {
        if (draggingKey == null) order = items
    }

    fun start(key: Any) {
        if (draggingKey != null) return
        draggingKey = key
        dragDistance = 0f
        releasing = false
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun drag(key: Any, dy: Float) {
        if (draggingKey != key || releasing) return
        dragDistance += dy
        swapIfPassed(key)
    }

    // Swaps the dragged item with a neighbour once its centre passes the neighbour's centre. The
    // finger offset is corrected by the neighbour's height, so the item stays under the finger.
    private fun swapIfPassed(key: Any) {
        val index = order.indexOfFirst { keyOf(it) == key }
        if (index < 0) return
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == key } ?: return
        // The list has not laid out the previous swap yet.
        if (dragged.index != index) return
        val centre = dragged.offset + dragDistance + dragged.size / 2f
        val below = items.firstOrNull { it.index == index + 1 }
        val above = items.firstOrNull { it.index == index - 1 }
        val target = when {
            below != null && centre > below.offset + below.size / 2f -> index + 1
            above != null && centre < above.offset + above.size / 2f -> index - 1
            else -> return
        }
        val neighbour = if (target > index) below!! else above!!
        order = order.toMutableList().apply { add(target, removeAt(index)) }
        dragDistance += if (target > index) -neighbour.size.toFloat() else neighbour.size.toFloat()
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun finish() {
        if (draggingKey == null || releasing) return
        onCommit(order)
        scope.launch {
            releasing = true
            settle.snapTo(dragDistance)
            settle.animateTo(0f, settleSpec)
            draggingKey = null
            releasing = false
            dragDistance = 0f
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T : Any> rememberReorderState(
    items: List<T>,
    listState: LazyListState,
    keyOf: (T) -> Any,
    onCommit: (List<T>) -> Unit,
): ReorderState<T> {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val state = remember { ReorderState(items, listState, scope, haptics, settleSpec) }
    SideEffect {
        state.keyOf = keyOf
        state.onCommit = onCommit
    }
    LaunchedEffect(items) { state.sync(items) }
    return state
}

/** Makes this element the drag handle of the item with [key]. */
fun <T : Any> Modifier.reorderHandle(state: ReorderState<T>, key: Any): Modifier =
    pointerInput(key) {
        detectDragGestures(
            onDragStart = { state.start(key) },
            onDragEnd = { state.finish() },
            onDragCancel = { state.finish() },
            onDrag = { change, amount ->
                change.consume()
                state.drag(key, amount.y)
            },
        )
    }

/**
 * Plays an exit when [removing] turns true: the content fades and collapses. Only then is
 * [onRemoved] called, so the caller deletes the data after the animation and the list never jumps.
 * Put the gap below the content inside it, so the gap collapses too.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollapseOnRemove(
    removing: Boolean,
    onRemoved: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val latestOnRemoved by rememberUpdatedState(onRemoved)
    val visibleState = remember { MutableTransitionState(true) }
    visibleState.targetState = !removing
    var notified by remember { mutableStateOf(false) }
    LaunchedEffect(removing, visibleState.currentState, visibleState.isIdle) {
        if (removing && !visibleState.currentState && visibleState.isIdle && !notified) {
            notified = true
            latestOnRemoved()
        }
    }
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = shrinkVertically(motion.defaultSpatialSpec(), shrinkTowards = Alignment.Top) +
            fadeOut(motion.fastEffectsSpec()),
    ) {
        content()
    }
}
