package com.bdersolutions.daybreak.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.ui.components.motionAnimationsEnabled
import com.bdersolutions.daybreak.ui.components.rememberSubjectTone
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import com.bdersolutions.daybreak.data.settings.AccentColor
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.data.settings.StartView
import com.bdersolutions.daybreak.data.settings.ThemeMode
import com.bdersolutions.daybreak.ui.settings.AccentSwatch
import com.bdersolutions.daybreak.ui.settings.accentLabel
import com.bdersolutions.daybreak.ui.settings.startViewLabel
import com.bdersolutions.daybreak.ui.settings.themeLabel
import com.bdersolutions.daybreak.ui.theme.accentSwatchColors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.time.DayOfWeek
import java.time.format.TextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The introduction: a short tour through a demo of the app. Nothing in it is real data; every lesson,
 * absence and chat message is made up here, so it works before login and shows the same to everyone.
 *
 * The demo runs inside a phone frame. A spotlight dims everything but the part a step is about and glides
 * from one part to the next; scenes change with a slide, and a few things move on their own (days are
 * swiped, the week is pinched, the assistant types) so the gestures are seen, not just described.
 *
 * Everyone goes through it once ([com.bdersolutions.daybreak.data.tutorial.TutorialProgress]). From More it
 * can be replayed; then it can also be closed early ([onClose]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TutorialScreen(
    settingsRepository: SettingsRepository,
    onFinish: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val steps = TutorialStep.entries
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    val step = steps[stepIndex]
    val haptics = LocalHaptics.current
    val last = stepIndex == steps.lastIndex

    val goTo = { index: Int ->
        if (index in steps.indices && index != stepIndex) {
            forward = index > stepIndex
            haptics.perform(HapticEvent.STEP)
            stepIndex = index
        }
    }
    val next = {
        if (last) {
            haptics.perform(HapticEvent.LOGIN_SUCCESS)
            onFinish()
        } else {
            goTo(stepIndex + 1)
        }
    }
    // Back steps back. On the first step it is left to the system (or the page host, when replaying).
    BackHandler(enabled = stepIndex > 0) { goTo(stepIndex - 1) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Header(stepIndex = stepIndex, count = steps.size, onClose = onClose)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                DemoDevice(step = step, onAdvance = next, settingsRepository = settingsRepository)
            }
            Caption(step = step, forward = forward)
            Controls(
                stepIndex = stepIndex,
                last = last,
                onBack = { goTo(stepIndex - 1) },
                onNext = next,
            )
        }
    }
}

/** The steps, in order, with the scene they show and the part the spotlight is on. */
internal enum class TutorialStep(val scene: Scene, val target: Target?) {
    WELCOME(Scene.WELCOME, null),
    NOW_NEXT(Scene.DAY, Target.NOW_NEXT),
    SWIPE(Scene.DAY, Target.DAY_AREA),
    DETAILS(Scene.DAY, Target.SUB_LESSON),
    WEEK(Scene.WEEK, Target.WEEK_GRID),
    ABSENCES(Scene.ABSENCES, Target.ABSENCE_TOTAL),
    ASSISTANT(Scene.ASSISTANT, Target.CHAT),
    MORE(Scene.MORE, Target.TUTORIAL_ROW),
    APPEARANCE(Scene.APPEARANCE, null),
}

internal enum class Scene(val tab: Int) { WELCOME(-1), DAY(0), WEEK(0), ABSENCES(1), ASSISTANT(0), MORE(2), APPEARANCE(-1) }

internal enum class Target { NOW_NEXT, DAY_STRIP, DAY_AREA, SUB_LESSON, SHEET, WEEK_GRID, ABSENCE_TOTAL, CHAT, TUTORIAL_ROW }

@Composable
private fun Header(stepIndex: Int, count: Int, onClose: (() -> Unit)?) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(
                text = stringResource(R.string.tutorial_step_of, stepIndex + 1, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.tutorial_close))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        ProgressSegments(stepIndex = stepIndex, count = count, modifier = Modifier.padding(end = 8.dp))
    }
}

/** One segment per step; the current one fills with a spring, done ones stay full. */
@Composable
private fun ProgressSegments(stepIndex: Int, count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { index ->
            val fill by animateFloatAsState(
                targetValue = if (index <= stepIndex) 1f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "segment$index",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun Caption(step: TutorialStep, forward: Boolean) {
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val direction = if (forward) 1 else -1
            val slide = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 380f)
            (slideInHorizontally(slide) { direction * it / 4 } + fadeIn(tween(220, delayMillis = 60)))
                .togetherWith(slideOutHorizontally(slide) { -direction * it / 4 } + fadeOut(tween(140)))
        },
        label = "caption",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) { current ->
        // A steady height, so the demo above does not jump when a text is a line longer or shorter.
        Column(modifier = Modifier.fillMaxWidth().heightIn(min = 148.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(current.titleRes()),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(current.bodyRes()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Controls(stepIndex: Int, last: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            androidx.compose.animation.AnimatedVisibility(visible = stepIndex > 0, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.8f)) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.tutorial_back)) }
            }
        }
        Button(onClick = onNext, modifier = Modifier.heightIn(min = 52.dp)) {
            AnimatedContent(
                targetState = last,
                transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = 0.85f)).togetherWith(fadeOut(tween(120))) },
                label = "nextLabel",
            ) { isLast ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(if (isLast) R.string.tutorial_finish else R.string.tutorial_next),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(if (isLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun TutorialStep.titleRes(): Int = when (this) {
    TutorialStep.WELCOME -> R.string.tutorial_welcome_title
    TutorialStep.NOW_NEXT -> R.string.tutorial_now_title
    TutorialStep.SWIPE -> R.string.tutorial_swipe_title
    TutorialStep.DETAILS -> R.string.tutorial_details_title
    TutorialStep.WEEK -> R.string.tutorial_week_title
    TutorialStep.ABSENCES -> R.string.tutorial_absences_title
    TutorialStep.ASSISTANT -> R.string.tutorial_assistant_title
    TutorialStep.MORE -> R.string.tutorial_more_title
    TutorialStep.APPEARANCE -> R.string.tutorial_appearance_title
}

private fun TutorialStep.bodyRes(): Int = when (this) {
    TutorialStep.WELCOME -> R.string.tutorial_welcome_body
    TutorialStep.NOW_NEXT -> R.string.tutorial_now_body
    TutorialStep.SWIPE -> R.string.tutorial_swipe_body
    TutorialStep.DETAILS -> R.string.tutorial_details_body
    TutorialStep.WEEK -> R.string.tutorial_week_body
    TutorialStep.ABSENCES -> R.string.tutorial_absences_body
    TutorialStep.ASSISTANT -> R.string.tutorial_assistant_body
    TutorialStep.MORE -> R.string.tutorial_more_body
    TutorialStep.APPEARANCE -> R.string.tutorial_appearance_body
}

// ---------------------------------------------------------------------------------------------------
// The demo device and the spotlight
// ---------------------------------------------------------------------------------------------------

/** Where the spotlight targets are, in the device's own coordinates. */
private class SpotRegistry {
    var root: LayoutCoordinates? = null
    val rects = mutableStateMapOf<Target, Rect>()
}

private fun Modifier.spot(target: Target, registry: SpotRegistry): Modifier = onGloballyPositioned { coordinates ->
    val root = registry.root ?: return@onGloballyPositioned
    if (root.isAttached && coordinates.isAttached) {
        registry.rects[target] = root.localBoundingBoxOf(coordinates, clipBounds = true)
    }
}

@Composable
private fun DemoDevice(step: TutorialStep, onAdvance: () -> Unit, settingsRepository: SettingsRepository) {
    val registry = remember { SpotRegistry() }
    val colors = MaterialTheme.colorScheme
    var sheetOpen by remember { mutableStateOf(false) }
    LaunchedEffect(step) { if (step != TutorialStep.DETAILS) sheetOpen = false }
    val target = if (step == TutorialStep.DETAILS && sheetOpen) Target.SHEET else step.target

    Box(
        modifier = Modifier
            .widthIn(max = 380.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(32.dp))
            .border(BorderStroke(1.dp, colors.outlineVariant), RoundedCornerShape(32.dp))
            .background(colors.surfaceContainerLowest)
            .onGloballyPositioned { registry.root = it },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = step.scene,
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    val slide = spring<IntOffset>(dampingRatio = 0.92f, stiffness = 300f)
                    (slideInHorizontally(slide) { direction * it / 3 } + fadeIn(tween(260, delayMillis = 40)) + scaleIn(spring(stiffness = 300f), initialScale = 0.96f))
                        .togetherWith(slideOutHorizontally(slide) { -direction * it / 3 } + fadeOut(tween(160)))
                },
                label = "scene",
                modifier = Modifier.weight(1f),
            ) { scene ->
                when (scene) {
                    Scene.WELCOME -> WelcomeScene()
                    Scene.DAY -> DayScene(step = step, registry = registry, onOpenLesson = { sheetOpen = true })
                    Scene.WEEK -> WeekScene(registry)
                    Scene.ABSENCES -> AbsencesScene(registry)
                    Scene.ASSISTANT -> AssistantScene(registry)
                    Scene.MORE -> MoreScene(registry, onTutorial = onAdvance)
                    Scene.APPEARANCE -> AppearanceScene(settingsRepository)
                }
            }
            AnimatedVisibility(
                visible = step.scene.tab >= 0,
                enter = slideInVertically(spring(stiffness = 300f)) { it } + fadeIn(),
                exit = slideOutVertically(spring(stiffness = 300f)) { it } + fadeOut(),
            ) {
                MockBottomBar(selected = step.scene.tab)
            }
        }
        // The lesson details, sliding up over the demo like the real sheet.
        DetailsSheet(open = sheetOpen, registry = registry, onDismiss = { sheetOpen = false })
        Spotlight(target = target, scene = step.scene, registry = registry)
    }
}

/**
 * Dims the device except a rounded hole over [target], with a softly pulsing ring. Within a scene the hole
 * glides with a spring; when the scene changes it fades out, waits for the new scene to settle and fades in
 * at the new place, so it never chases a sliding layout.
 */
@Composable
private fun BoxScope.Spotlight(target: Target?, scene: Scene, registry: SpotRegistry) {
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val animations = motionAnimationsEnabled()
    val rect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val alpha = remember { Animatable(0f) }
    var shownScene by remember { mutableStateOf<Scene?>(null) }
    val targetRect = target?.let { registry.rects[it] }

    LaunchedEffect(scene, target) {
        if (target == null) {
            alpha.animateTo(0f, tween(200))
            shownScene = scene
            return@LaunchedEffect
        }
        if (shownScene != scene) {
            alpha.animateTo(0f, tween(150))
            if (animations) delay(420)
            shownScene = scene
            registry.rects[target]?.let { rect.snapTo(it) }
        }
        launch { alpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
    }
    LaunchedEffect(targetRect, shownScene) {
        val destination = targetRect ?: return@LaunchedEffect
        if (shownScene != scene) return@LaunchedEffect
        if (rect.value == Rect.Zero || !animations) {
            rect.snapTo(destination)
        } else {
            rect.animateTo(destination, spring(dampingRatio = 0.82f, stiffness = 210f))
        }
    }

    val transition = rememberInfiniteTransition(label = "spotPulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animations) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Canvas(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val a = alpha.value
        if (a <= 0.01f) return@Canvas
        val pad = with(density) { 6.dp.toPx() }
        val r = rect.value
        val hole = Rect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
        val corner = CornerRadius(with(density) { 18.dp.toPx() })
        drawRect(Color.Black.copy(alpha = 0.52f * a))
        drawRoundRect(Color.Black, topLeft = hole.topLeft, size = hole.size, cornerRadius = corner, blendMode = BlendMode.Clear)
        val grow = with(density) { (2.dp + 3.dp * pulse).toPx() }
        drawRoundRect(
            color = primary.copy(alpha = a * (0.95f - 0.45f * pulse)),
            topLeft = Offset(hole.left - grow / 2, hole.top - grow / 2),
            size = Size(hole.width + grow, hole.height + grow),
            cornerRadius = CornerRadius(corner.x + grow / 2),
            style = Stroke(width = with(density) { 2.5.dp.toPx() }),
        )
    }
}

// ---------------------------------------------------------------------------------------------------
// Demo data
// ---------------------------------------------------------------------------------------------------

private data class DemoLesson(
    val start: String,
    val end: String,
    val subject: Int,
    val teacher: String,
    val room: String,
    val substitute: Boolean = false,
    val cancelled: Boolean = false,
    val exam: Boolean = false,
)

private val demoDays: List<List<DemoLesson>> = listOf(
    listOf(
        DemoLesson("08:00", "09:30", R.string.tutorial_demo_math, "MÜL", "R101"),
        DemoLesson("09:50", "10:35", R.string.tutorial_demo_english, "KRA", "R201", substitute = true),
        DemoLesson("10:40", "11:25", R.string.tutorial_demo_history, "BEC", "R014", cancelled = true),
        DemoLesson("11:40", "13:10", R.string.tutorial_demo_biology, "HOF", "B2"),
    ),
    listOf(
        DemoLesson("08:00", "08:45", R.string.tutorial_demo_german, "SCH", "R101"),
        DemoLesson("08:50", "10:20", R.string.tutorial_demo_physics, "WEB", "P1", exam = true),
        DemoLesson("10:40", "12:10", R.string.tutorial_demo_sport, "LAN", "TH"),
        DemoLesson("12:15", "13:00", R.string.tutorial_demo_art, "ROT", "K3"),
    ),
)

// ---------------------------------------------------------------------------------------------------
// Scenes
// ---------------------------------------------------------------------------------------------------

@Composable
private fun MockTopBar(title: String, subtitle: String, actions: List<ImageVector> = emptyList()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        actions.forEachIndexed { index, icon ->
            Icon(
                icon,
                contentDescription = null,
                tint = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun MockBottomBar(selected: Int) {
    val items = listOf(
        Triple(Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth, R.string.nav_timetable),
        Triple(Icons.Outlined.EventBusy, Icons.Filled.EventBusy, R.string.nav_absences),
        Triple(Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz, R.string.nav_settings),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEachIndexed { index, (icon, selectedIcon, label) ->
            val isSelected = index == selected
            val container by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                spring(stiffness = 400f),
                label = "tab$index",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(container)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(if (isSelected) selectedIcon else icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WelcomeScene() {
    val animations = motionAnimationsEnabled()
    val logo = remember { Animatable(if (animations) 0f else 1f) }
    LaunchedEffect(Unit) { logo.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 180f)) }
    val transition = rememberInfiniteTransition(label = "rays")
    val angle by transition.animateFloat(0f, if (animations) 360f else 0f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "rayAngle")
    val sun = Color(0xFFFFB648)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            // Soft rays turning slowly behind the logo: a sunrise.
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = logo.value.coerceIn(0f, 1f) }) {
                val radius = size.minDimension / 2
                // Twelve soft wedges, fading out towards the edge, like the logo's sun rising.
                rotate(angle) {
                    repeat(12) { index ->
                        drawArc(
                            brush = Brush.radialGradient(
                                listOf(sun.copy(alpha = 0.30f), sun.copy(alpha = 0.06f), Color.Transparent),
                                center = center,
                                radius = radius,
                            ),
                            startAngle = index * 30f,
                            sweepAngle = 14f,
                            useCenter = true,
                        )
                    }
                }
                drawCircle(Brush.radialGradient(listOf(sun.copy(alpha = 0.35f), Color.Transparent), radius = radius * 0.8f), radius = radius * 0.8f)
            }
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        val s = logo.value
                        scaleX = s
                        scaleY = s
                        translationY = (1f - s.coerceIn(0f, 1f)) * 40.dp.toPx()
                    }
                    .clip(RoundedCornerShape(30.dp)),
            ) {
                Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Daybreak", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        val pills = listOf(
            Icons.Outlined.CalendarMonth to R.string.tutorial_pill_timetable,
            Icons.Outlined.Checklist to R.string.tutorial_pill_exams,
            Icons.Outlined.AutoAwesome to R.string.tutorial_pill_assistant,
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            pills.forEachIndexed { index, (icon, label) ->
                PopIn(delayMs = 350L + index * 110L) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }
    }
}

/** Scales and fades its content in after [delayMs], with a little overshoot. */
@Composable
private fun PopIn(delayMs: Long, content: @Composable () -> Unit) {
    val animations = motionAnimationsEnabled()
    val progress = remember { Animatable(if (animations) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animations) return@LaunchedEffect
        delay(delayMs)
        progress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 320f))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            scaleX = 0.7f + 0.3f * progress.value
            scaleY = 0.7f + 0.3f * progress.value
        },
    ) { content() }
}

@Composable
private fun weekdayShort(day: DayOfWeek): String {
    val locale = LocalConfiguration.current.locales[0]
    return day.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')
}

@Composable
private fun DayScene(step: TutorialStep, registry: SpotRegistry, onOpenLesson: () -> Unit) {
    var day by remember { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    val animations = motionAnimationsEnabled()
    val select = { index: Int ->
        if (index != day) {
            forward = index > day
            day = index
        }
    }
    // The swipe step swipes on its own, so the gesture is seen.
    LaunchedEffect(step) {
        if (step != TutorialStep.SWIPE) {
            select(0)
            return@LaunchedEffect
        }
        if (!animations) return@LaunchedEffect
        while (true) {
            delay(1500)
            select(1)
            delay(1900)
            select(0)
        }
    }
    // On the details step the lesson opens by itself after a moment, unless it was tapped.
    LaunchedEffect(step) {
        if (step == TutorialStep.DETAILS) {
            delay(2800)
            onOpenLesson()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MockTopBar(
            title = "Daybreak",
            subtitle = stringResource(R.string.tutorial_demo_week_label),
            actions = listOf(Icons.Outlined.AutoAwesome, Icons.Outlined.Search, Icons.Outlined.Checklist),
        )
        Column(modifier = Modifier.weight(1f).spot(Target.DAY_AREA, registry)) {
        // Weekday strip
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .spot(Target.DAY_STRIP, registry),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DayOfWeek.entries.take(5).forEachIndexed { index, weekday ->
                val selected = index == day
                val container by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    spring(stiffness = 500f),
                    label = "dayChip$index",
                )
                val content by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    spring(stiffness = 500f),
                    label = "dayChipText$index",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(container)
                        .clickable { if (index < demoDays.size) select(index) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(weekdayShort(weekday), style = MaterialTheme.typography.labelSmall, color = content)
                    Text("${9 + index}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = content)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AnimatedContent(
            targetState = day,
            transitionSpec = {
                val direction = if (forward) 1 else -1
                val slide = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 340f)
                (slideInHorizontally(slide) { direction * it } + fadeIn(tween(200)))
                    .togetherWith(slideOutHorizontally(slide) { -direction * it } + fadeOut(tween(160)))
            },
            label = "demoDay",
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            if (drag < -60f) select((day + 1).coerceAtMost(demoDays.lastIndex))
                            if (drag > 60f) select((day - 1).coerceAtLeast(0))
                        },
                    ) { _, amount -> drag += amount }
                },
        ) { shownDay ->
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (shownDay == 0) NowNextCard(Modifier.spot(Target.NOW_NEXT, registry))
                demoDays[shownDay].forEach { lesson ->
                    val modifier = if (shownDay == 0 && lesson.substitute) Modifier.spot(Target.SUB_LESSON, registry) else Modifier
                    LessonBlock(lesson = lesson, modifier = modifier, onClick = if (lesson.substitute) onOpenLesson else null)
                }
            }
        }
        // The finger that shows the swipe.
        if (step == TutorialStep.SWIPE && animations) SwipeHint()
        }
    }
}

@Composable
private fun NowNextCard(modifier: Modifier = Modifier) {
    val math = stringResource(R.string.tutorial_demo_math)
    val english = stringResource(R.string.tutorial_demo_english)
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.tutorial_demo_now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(math, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(stringResource(R.string.tutorial_demo_left, 25), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.tutorial_demo_next), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(english, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("09:50 · R201", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun LessonBlock(lesson: DemoLesson, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val subject = stringResource(lesson.subject)
    val tone = rememberSubjectTone(subject)
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (lesson.cancelled) colors.surfaceContainerHigh else tone.container)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (lesson.cancelled) colors.outline else tone.accent),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subject,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (lesson.cancelled) colors.outline else tone.onContainer,
                textDecoration = if (lesson.cancelled) TextDecoration.LineThrough else null,
            )
            val details = buildAnnotatedString {
                append("${lesson.start}–${lesson.end} · ")
                if (lesson.substitute) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(lesson.room) }
                } else {
                    append(lesson.room)
                }
                append(" · ${lesson.teacher}")
            }
            Text(details, style = MaterialTheme.typography.labelMedium, color = if (lesson.cancelled) colors.outline else tone.onContainer.copy(alpha = 0.85f))
        }
        when {
            lesson.substitute -> Badge(stringResource(R.string.tutorial_demo_substitute), colors.tertiary, colors.onTertiary)
            lesson.cancelled -> Badge(stringResource(R.string.tutorial_demo_cancelled), colors.error, colors.onError)
            lesson.exam -> Badge(stringResource(R.string.tutorial_demo_exam), colors.secondary, colors.onSecondary)
        }
    }
}

@Composable
private fun Badge(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = content, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** A fingertip that moves from right to left, fading in and out. */
@Composable
private fun SwipeHint() {
    val transition = rememberInfiniteTransition(label = "swipeHint")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing)),
        label = "swipeProgress",
    )
    Box(modifier = Modifier.fillMaxWidth().height(0.dp)) {
        val alpha = when {
            progress < 0.15f -> progress / 0.15f
            progress > 0.75f -> (1f - progress) / 0.25f
            else -> 1f
        }
        Icon(
            Icons.Outlined.TouchApp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (-150).dp)
                .graphicsLayer {
                    translationX = -progress * 160.dp.toPx() - 30.dp.toPx()
                    this.alpha = alpha.coerceIn(0f, 1f)
                    rotationZ = -12f
                }
                .size(40.dp),
        )
    }
}

@Composable
private fun BoxScope.DetailsSheet(open: Boolean, registry: SpotRegistry, onDismiss: () -> Unit) {
    val scrim by animateFloatAsState(if (open) 0.32f else 0f, tween(260), label = "sheetScrim")
    if (scrim > 0f) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = scrim))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = open,
        enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 320f)) { it },
        exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 400f)) { it },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val english = stringResource(R.string.tutorial_demo_english)
        val tone = rememberSubjectTone(english)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .spot(Target.SHEET, registry),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 32.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(tone.accent))
                    Spacer(Modifier.width(8.dp))
                    Text(english, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Badge(stringResource(R.string.tutorial_demo_substitute), MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
                }
                Text("09:50–10:35", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                DetailRow(stringResource(R.string.tutorial_demo_room), "R201", stringResource(R.string.tutorial_demo_instead, "R102"))
                DetailRow(stringResource(R.string.tutorial_demo_teacher), "KRA", stringResource(R.string.tutorial_demo_instead, "MÜL"))
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        stringResource(R.string.tutorial_demo_info),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, note: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun WeekScene(registry: SpotRegistry) {
    val animations = motionAnimationsEnabled()
    val zoom = remember { Animatable(1f) }
    // A slow pinch in and out, so the gesture is seen.
    LaunchedEffect(Unit) {
        if (!animations) return@LaunchedEffect
        while (true) {
            delay(900)
            zoom.animateTo(1.22f, spring(dampingRatio = 0.75f, stiffness = 60f))
            delay(700)
            zoom.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 70f))
        }
    }
    val subjects = listOf(
        R.string.tutorial_demo_math, R.string.tutorial_demo_german, R.string.tutorial_demo_english,
        R.string.tutorial_demo_biology, R.string.tutorial_demo_history, R.string.tutorial_demo_physics,
        R.string.tutorial_demo_sport, R.string.tutorial_demo_art,
    ).map { stringResource(it) }
    // Which subject sits in each slot (row by row); -1 is a free period.
    val grid = listOf(
        listOf(0, 1, 0, 6, 2),
        listOf(0, 5, 3, 6, 2),
        listOf(2, 5, 4, -1, 0),
        listOf(4, 7, 1, 3, 5),
        listOf(3, 7, -1, 1, -1),
        listOf(1, -1, 2, 4, 3),
        listOf(-1, -1, 2, 4, -1),
    )
    Column(modifier = Modifier.fillMaxSize()) {
        MockTopBar(title = "Daybreak", subtitle = stringResource(R.string.tutorial_demo_week_label), actions = listOf(Icons.Outlined.AutoAwesome, Icons.Outlined.Search))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .spot(Target.WEEK_GRID, registry),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom.value
                        scaleY = zoom.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.take(5).forEach { weekday ->
                        Text(
                            weekdayShort(weekday),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                grid.forEachIndexed { row, slots ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                        slots.forEachIndexed { column, index ->
                            if (index < 0) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight())
                            } else {
                                val subject = subjects[index]
                                val tone = rememberSubjectTone(subject)
                                val cancelled = row == 2 && column == 2
                                val exam = row == 1 && column == 1
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (cancelled) MaterialTheme.colorScheme.surfaceContainerHigh else tone.container)
                                        .then(if (exam) Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(10.dp)) else Modifier)
                                        .padding(4.dp),
                                ) {
                                    Text(
                                        text = subject.take(4),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = if (cancelled) MaterialTheme.colorScheme.outline else tone.onContainer,
                                        textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (animations) PinchHint(zoom.value)
        }
    }
}

/** Two fingertips that move apart and together with the zoom. */
@Composable
private fun BoxScope.PinchHint(zoom: Float) {
    val spread = (zoom - 1f) / 0.22f
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.align(Alignment.Center).size(140.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val distance = size.minDimension * (0.12f + 0.26f * spread)
        val dot = 12.dp.toPx()
        listOf(-1f, 1f).forEach { side ->
            val point = center + Offset(side * distance * 0.7f, -side * distance * 0.7f)
            drawCircle(color.copy(alpha = 0.25f), radius = dot * 1.6f, center = point)
            drawCircle(color.copy(alpha = 0.85f), radius = dot * 0.7f, center = point)
        }
    }
}

@Composable
private fun AbsencesScene(registry: SpotRegistry) {
    val animations = motionAnimationsEnabled()
    val count = remember { Animatable(if (animations) 0f else 14.5f) }
    LaunchedEffect(Unit) { count.animateTo(14.5f, tween(1400, delayMillis = 450, easing = FastOutSlowInEasing)) }
    Column(modifier = Modifier.fillMaxSize()) {
        MockTopBar(title = stringResource(R.string.nav_absences), subtitle = stringResource(R.string.tutorial_demo_school_year))
        Surface(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .spot(Target.ABSENCE_TOTAL, registry),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val ringColor = MaterialTheme.colorScheme.primary
                val track = MaterialTheme.colorScheme.surfaceContainerHighest
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = Stroke(width = 8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        drawArc(track, 0f, 360f, false, style = stroke)
                        drawArc(ringColor, -90f, 360f * (count.value / 14.5f) * 0.83f, false, style = stroke)
                    }
                    Text("83%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.tutorial_demo_hours, String.format(LocalConfiguration.current.locales[0], "%.1f", count.value)),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(stringResource(R.string.tutorial_demo_excused_share), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        listOf(
            Triple("Mo 03.11.", "08:00–13:10", true),
            Triple("Do 23.10.", "09:50–11:25", true),
            Triple("Di 14.10.", "11:40–13:10", false),
        ).forEachIndexed { index, (date, time, excused) ->
            PopIn(delayMs = 500L + index * 120L) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (excused) {
                        Badge(stringResource(R.string.tutorial_demo_excused), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Badge(stringResource(R.string.tutorial_demo_open), MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantScene(registry: SpotRegistry) {
    val question = stringResource(R.string.tutorial_demo_question)
    val answer = stringResource(R.string.tutorial_demo_answer)
    val animations = motionAnimationsEnabled()
    var typed by remember { mutableIntStateOf(if (animations) 0 else question.length) }
    var thinking by remember { mutableStateOf(false) }
    var words by remember { mutableIntStateOf(if (animations) 0 else Int.MAX_VALUE) }
    val answerWords = remember(answer) { answer.split(" ") }
    LaunchedEffect(Unit) {
        if (!animations) return@LaunchedEffect
        delay(500)
        while (typed < question.length) {
            typed++
            delay(28)
        }
        delay(250)
        thinking = true
        delay(1100)
        thinking = false
        while (words < answerWords.size) {
            words++
            delay(55)
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        MockTopBar(title = stringResource(R.string.assistant_title), subtitle = stringResource(R.string.assistant_subtitle))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .spot(Target.CHAT, registry),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            if (typed > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp), color = MaterialTheme.colorScheme.primary) {
                        Text(
                            question.take(typed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(max = 230.dp),
                        )
                    }
                }
            }
            AnimatedVisibility(visible = thinking || words > 0, enter = fadeIn() + scaleIn(initialScale = 0.9f)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.assistant_tool_exams), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        MiniOrb()
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            val shown = answerWords.take(words).joinToString(" ")
                            Text(
                                text = if (thinking) AnnotatedString("• • •") else boldMarkdown(shown),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(max = 230.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.assistant_input_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MiniOrb() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .drawBehind { drawCircle(Brush.sweepGradient(listOf(colors.primary, colors.tertiary, colors.secondary, colors.primary))) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(12.dp))
    }
}

private fun boldMarkdown(text: String) = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

@Composable
private fun MoreScene(registry: SpotRegistry, onTutorial: () -> Unit) {
    val animations = motionAnimationsEnabled()
    var amoled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!animations) return@LaunchedEffect
        delay(900)
        amoled = true
    }
    Column(modifier = Modifier.fillMaxSize()) {
        MockTopBar(title = stringResource(R.string.settings_title), subtitle = stringResource(R.string.more_group_settings))
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            MockRow(Icons.Outlined.Palette, stringResource(R.string.settings_appearance))
            MockRow(Icons.Outlined.Contrast, stringResource(R.string.settings_amoled)) {
                Switch(checked = amoled, onCheckedChange = { amoled = it }, modifier = Modifier.graphicsLayer { scaleX = 0.8f; scaleY = 0.8f })
            }
            MockRow(Icons.Outlined.Brush, stringResource(R.string.settings_lesson_colors))
            MockRow(Icons.Outlined.Widgets, stringResource(R.string.settings_widget))
            MockRow(Icons.Outlined.Notifications, stringResource(R.string.settings_notifications))
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            MockRow(
                Icons.Outlined.School,
                stringResource(R.string.settings_tutorial),
                modifier = Modifier
                    .spot(Target.TUTORIAL_ROW, registry)
                    .clickable(onClick = onTutorial)
                    .semantics { contentDescription = "tutorial" },
            )
        }
    }
}

@Composable
private fun MockRow(icon: ImageVector, title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(34.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------------------------------------------
// The last step: choose how the app looks. Every choice is saved at once and the whole app (this tour
// included) re-themes live, so the result is seen while choosing. All of it is under More → Appearance too.
// ---------------------------------------------------------------------------------------------------

private enum class ThemeChoice { SYSTEM, LIGHT, DARK, AMOLED }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AppearanceScene(settingsRepository: SettingsRepository) {
    val settings by settingsRepository.settings.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dynamicAvailable = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val theme = when {
        settings.themeMode == ThemeMode.DARK && settings.amoledBlack -> ThemeChoice.AMOLED
        settings.themeMode == ThemeMode.DARK -> ThemeChoice.DARK
        settings.themeMode == ThemeMode.LIGHT -> ThemeChoice.LIGHT
        else -> ThemeChoice.SYSTEM
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ChoiceSection(stringResource(R.string.tutorial_section_theme)) {
            ChoiceRow(
                options = ThemeChoice.entries.map { choice ->
                    choice to when (choice) {
                        ThemeChoice.SYSTEM -> themeLabel(ThemeMode.SYSTEM)
                        ThemeChoice.LIGHT -> themeLabel(ThemeMode.LIGHT)
                        ThemeChoice.DARK -> themeLabel(ThemeMode.DARK)
                        ThemeChoice.AMOLED -> stringResource(R.string.tutorial_theme_amoled)
                    }
                },
                selected = theme,
                onSelect = { choice ->
                    haptics.perform(HapticEvent.SELECT)
                    settingsRepository.setThemeMode(
                        when (choice) {
                            ThemeChoice.SYSTEM -> ThemeMode.SYSTEM
                            ThemeChoice.LIGHT -> ThemeMode.LIGHT
                            ThemeChoice.DARK, ThemeChoice.AMOLED -> ThemeMode.DARK
                        },
                    )
                    settingsRepository.setAmoledBlack(choice == ThemeChoice.AMOLED)
                },
            )
        }
        ChoiceSection(stringResource(R.string.tutorial_section_colors)) {
            if (dynamicAvailable) {
                ChoiceRow(
                    options = listOf(true to stringResource(R.string.tutorial_material_you), false to stringResource(R.string.settings_accent)),
                    selected = settings.dynamicColor,
                    onSelect = {
                        haptics.perform(HapticEvent.SELECT)
                        settingsRepository.setDynamicColor(it)
                    },
                )
            }
            androidx.compose.animation.AnimatedVisibility(visible = !dynamicAvailable || !settings.dynamicColor) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AccentColor.entries.forEach { accent ->
                        val (seed, onSeed) = accentSwatchColors(accent, dark)
                        AccentSwatch(
                            color = seed,
                            onColor = onSeed,
                            label = accentLabel(accent),
                            selected = settings.accent == accent,
                            onClick = {
                                settingsRepository.setDynamicColor(false)
                                settingsRepository.setAccent(accent)
                            },
                        )
                    }
                }
            }
        }
        ChoiceSection(stringResource(R.string.settings_start_view)) {
            ChoiceRow(
                options = StartView.entries.map { it to startViewLabel(it) },
                selected = settings.startView,
                onSelect = {
                    haptics.perform(HapticEvent.SELECT)
                    settingsRepository.setStartView(it)
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_now_next), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.settings_now_next_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = settings.showNowNext,
                onCheckedChange = {
                    haptics.perform(if (it) HapticEvent.TOGGLE_ON else HapticEvent.TOGGLE_OFF)
                    settingsRepository.setShowNowNext(it)
                },
            )
        }
    }
}

@Composable
private fun ChoiceSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** A row of equally wide options; the chosen one fills with the accent, with a spring. */
@Composable
private fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val chosen = value == selected
            val container by animateColorAsState(
                if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent,
                spring(stiffness = 500f),
                label = "choice",
            )
            val content by animateColorAsState(
                if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                spring(stiffness = 500f),
                label = "choiceText",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(container)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
