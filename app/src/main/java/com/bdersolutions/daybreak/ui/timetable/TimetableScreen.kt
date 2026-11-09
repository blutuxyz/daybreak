package com.bdersolutions.daybreak.ui.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Room
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import com.bdersolutions.daybreak.ui.haptics.HapticPullToRefreshBox
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.bdersolutions.daybreak.data.schedule.NowAndNext
import com.bdersolutions.daybreak.data.schedule.DayPhase
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.cache.TimetableCache
import com.bdersolutions.daybreak.data.repository.TimetableTarget
import com.bdersolutions.daybreak.untis.ElementType
import com.bdersolutions.daybreak.untis.UntisElement
import com.bdersolutions.daybreak.untis.UntisHoliday
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.res.pluralStringResource
import com.bdersolutions.daybreak.data.repository.TimetableSyncStatus
import com.bdersolutions.daybreak.data.settings.SubjectColorMode
import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisLesson
import com.bdersolutions.daybreak.ui.components.AccentBadge
import com.bdersolutions.daybreak.ui.components.LocalAppTitle
import com.bdersolutions.daybreak.ui.components.LocalLessonColors
import com.bdersolutions.daybreak.ui.components.LocalSubjectColorMode
import com.bdersolutions.daybreak.ui.components.cancelledAccent
import com.bdersolutions.daybreak.ui.components.onColorFor
import com.bdersolutions.daybreak.ui.components.rememberSubjectTone
import com.bdersolutions.daybreak.ui.components.SubjectTone
import com.bdersolutions.daybreak.ui.components.substitutionAccent
import com.bdersolutions.daybreak.ui.components.absentVeil
import com.bdersolutions.daybreak.ui.components.examAccent
import com.bdersolutions.daybreak.ui.components.examHighlight
import com.bdersolutions.daybreak.ui.components.examContainer
import com.bdersolutions.daybreak.ui.components.examContent
import com.bdersolutions.daybreak.ui.components.rememberPressScale
import com.bdersolutions.daybreak.ui.components.rememberStaggeredReveal
import com.bdersolutions.daybreak.ui.components.warningAccent
import com.bdersolutions.daybreak.ui.components.warningContainer
import com.bdersolutions.daybreak.ui.toUserMessage
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.max

private const val DAY_PAGE_COUNT = 731
private const val DAY_START_OFFSET = 365
private const val WEEK_PAGE_COUNT = 105
private const val WEEK_START_OFFSET = 52

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    initialDate: LocalDate? = null,
    initialMode: Int = 0,
    openSearchOnStart: Boolean = false,
    onOpenUpcoming: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cache by viewModel.displayCache.collectAsStateWithLifecycle()
    val target by viewModel.target.collectAsStateWithLifecycle()
    val directory by viewModel.directory.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    var showTargetPicker by rememberSaveable { mutableStateOf(false) }
    val status by viewModel.status.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val currentSession = session ?: return
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var mode by rememberSaveable { mutableIntStateOf(initialMode) }
    var showSearch by rememberSaveable { mutableStateOf(openSearchOnStart) }
    var showShareMenu by remember { mutableStateOf(false) }
    val appSettings by viewModel.settings.collectAsStateWithLifecycle()
    val appTitle = LocalAppTitle.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()


    // The pages are counted from the day the screen opened. "Today" itself follows the clock, so a
    // screen left open overnight moves its today marker, Now card and Today button to the new day.
    val startDay = remember { LocalDate.now() }
    val now = rememberNow()
    val today = now.toLocalDate()
    val initialDayPage = remember(initialDate, startDay) {
        initialDate?.let { date ->
            (DAY_START_OFFSET + ChronoUnit.DAYS.between(startDay, date).toInt())
                .coerceIn(0, DAY_PAGE_COUNT - 1)
        } ?: DAY_START_OFFSET
    }
    val dayOrigin = remember { startDay.minusDays(DAY_START_OFFSET.toLong()) }
    val weekOrigin = remember {
        startDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks(WEEK_START_OFFSET.toLong())
    }
    val todayDayPage = (ChronoUnit.DAYS.between(dayOrigin, today).toInt()).coerceIn(0, DAY_PAGE_COUNT - 1)
    val todayWeekPage = (ChronoUnit.WEEKS.between(weekOrigin, today).toInt()).coerceIn(0, WEEK_PAGE_COUNT - 1)
    val dayPager = rememberPagerState(
        initialPage = initialDayPage,
        pageCount = { DAY_PAGE_COUNT },
    )
    val weekPager = rememberPagerState(
        initialPage = WEEK_START_OFFSET,
        pageCount = { WEEK_PAGE_COUNT },
    )
    val pagerState = if (mode == 0) dayPager else weekPager
    val scope = rememberCoroutineScope()
    // A detent tick each time a day or week settles into place, not while the finger drags.
    val haptics = LocalHaptics.current
    LaunchedEffect(dayPager) {
        snapshotFlow { dayPager.settledPage }.drop(1).collect { haptics.perform(HapticEvent.PAGE_SNAP) }
    }
    LaunchedEffect(weekPager) {
        snapshotFlow { weekPager.settledPage }.drop(1).collect { haptics.perform(HapticEvent.PAGE_SNAP) }
    }
    val selectedDate = if (mode == 0) {
        dayOrigin.plusDays(pagerState.currentPage.toLong())
    } else {
        weekOrigin.plusWeeks(pagerState.currentPage.toLong())
    }
    val todayWeekStart = remember(today) {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val isOnToday = if (mode == 0) {
        selectedDate == today
    } else {
        selectedDate == todayWeekStart
    }
    val motion = MaterialTheme.motionScheme
    val locale = currentLocale(context)
    val weekNumber = remember(selectedDate, locale) {
        selectedDate.get(WeekFields.of(locale).weekOfWeekBasedYear())
    }
    val weekLabel = stringResource(R.string.timetable_calendar_week, weekNumber)
    val targetName = when (val current = target) {
        TimetableTarget.Own -> currentSession.studentName.ifBlank { currentSession.displayName }
        is TimetableTarget.Element -> targetLabel(current)
    }
    val retryLabel = stringResource(R.string.action_retry)
    // Shares what is on screen: the selected day, or the selected week.
    val shareTimetable: (Boolean) -> Unit = { asImage ->
        val allLessons = cache.weeks.values.flatMap { it.lessons }
        val dates = if (mode == 0) {
            listOf(selectedDate)
        } else {
            (0..6).map { selectedDate.plusDays(it.toLong()) }
                .filter { date -> date.dayOfWeek.value <= 5 || allLessons.any { it.date == date && !it.isHoliday } }
        }
        val days = ShareTimetable.days(context, dates, allLessons)
        if (asImage) {
            ShareTimetable.shareImage(context, targetName, appTitle, days)
        } else {
            ShareTimetable.shareText(context, targetName, days)
        }
    }
    // The names in a lesson's details (class, teacher, room) lead to that timetable.
    val findElement: (ElementType, String) -> UntisElement? = { type, name ->
        directory?.let { master ->
            when (type) {
                ElementType.CLASS -> master.classes
                ElementType.TEACHER -> master.teachers
                ElementType.ROOM -> master.rooms
            }.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }
    val openElement: (UntisElement) -> Unit = { viewModel.setTarget(TimetableTarget.Element(it)) }
    val examDates = remember(cache.exams) { cache.exams.map { it.date }.toSet() }
    val selectDay: (LocalDate) -> Unit = { targetDate ->
        scope.launch {
            dayPager.animateScrollToPage(
                page = ChronoUnit.DAYS.between(dayOrigin, targetDate).toInt().coerceIn(0, DAY_PAGE_COUNT - 1),
                animationSpec = motion.defaultSpatialSpec(),
            )
        }
    }
    val targetType = (target as? TimetableTarget.Element)?.element?.type
    // Read the date at the time of the tap, not the date from when this effect started.
    val latestDate by rememberUpdatedState(selectedDate)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { error ->
            haptics.perform(HapticEvent.ERROR)
            val result = snackbarHostState.showSnackbar(
                message = error.toUserMessage(context),
                actionLabel = retryLabel,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.refresh(latestDate)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.refreshSucceeded.collect {
            haptics.perform(HapticEvent.REFRESH_DONE)
            snackbarHostState.showSnackbar(context.getString(R.string.timetable_refreshed))
        }
    }

    LaunchedEffect(mode, dayPager.currentPage, weekPager.currentPage, target) {
        val activeDate = if (mode == 0) {
            dayOrigin.plusDays(dayPager.currentPage.toLong())
        } else {
            weekOrigin.plusWeeks(weekPager.currentPage.toLong())
        }
        viewModel.ensureWeek(activeDate)
    }

    CompositionLocalProvider(LocalTimetableTargetType provides targetType) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text = LocalAppTitle.current,
                            maxLines = 1,
                        )
                    },
                    subtitle = {
                        Text(
                            text = "$weekLabel · $targetName",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.timetable_search),
                            )
                        }
                        Box {
                            IconButton(onClick = { showShareMenu = true }) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = stringResource(R.string.timetable_share),
                                )
                            }
                            DropdownMenu(expanded = showShareMenu, onDismissRequest = { showShareMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_as_text)) },
                                    onClick = {
                                        showShareMenu = false
                                        shareTimetable(false)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_as_image)) },
                                    onClick = {
                                        showShareMenu = false
                                        shareTimetable(true)
                                    },
                                )
                            }
                        }
                        IconButton(onClick = onOpenUpcoming) {
                            Icon(
                                Icons.Outlined.Checklist,
                                contentDescription = stringResource(R.string.timetable_upcoming),
                            )
                        }
                        IconButton(onClick = { showTargetPicker = true }) {
                            Icon(
                                Icons.Outlined.SwapHoriz,
                                contentDescription = stringResource(R.string.target_picker_title),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !isOnToday,
                    enter = scaleIn(
                        animationSpec = motion.defaultSpatialSpec(),
                        initialScale = 0.8f,
                    ) + fadeIn(animationSpec = motion.fastEffectsSpec()),
                    exit = scaleOut(
                        animationSpec = motion.fastSpatialSpec(),
                        targetScale = 0.8f,
                    ) + fadeOut(animationSpec = motion.fastEffectsSpec()),
                    label = "todayFab",
                ) {
                    val todayInteraction = remember { MutableInteractionSource() }
                    val todayScale by rememberPressScale(todayInteraction)
                    ExtendedFloatingActionButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    if (mode == 0) todayDayPage else todayWeekPage,
                                    animationSpec = motion.defaultSpatialSpec(),
                                )
                            }
                        },
                        interactionSource = todayInteraction,
                        modifier = Modifier.graphicsLayer {
                            scaleX = todayScale
                            scaleY = todayScale
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Outlined.Today, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_today)) },
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                ViewModeSwitcher(
                    mode = mode,
                    onModeChange = { mode = it },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
                AnimatedContent(
                    targetState = mode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        val slide = motion.defaultSpatialSpec<IntOffset>()
                        val scale = motion.defaultSpatialSpec<Float>()
                        val fade = motion.fastEffectsSpec<Float>()
                        (
                            slideInHorizontally(slide) { width -> direction * width / 5 } +
                                fadeIn(fade) +
                                scaleIn(scale, initialScale = 0.97f)
                            )
                            .togetherWith(
                                slideOutHorizontally(slide) { width -> -direction * width / 5 } +
                                    fadeOut(fade) +
                                    scaleOut(scale, targetScale = 0.99f),
                            )
                            .using(SizeTransform(clip = false))
                    },
                    label = "timetableMode",
                ) { targetMode ->
                    val targetPager = if (targetMode == 0) dayPager else weekPager
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (targetMode == 0) {
                            DayStrip(
                                date = dayOrigin.plusDays(dayPager.currentPage.toLong()),
                                today = today,
                                onSelectDate = selectDay,
                                examDates = examDates,
                            )
                        }
                        HorizontalPager(
                            state = targetPager,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            flingBehavior = PagerDefaults.flingBehavior(
                                state = targetPager,
                                pagerSnapDistance = PagerSnapDistance.atMost(1),
                                snapAnimationSpec = motion.defaultSpatialSpec(),
                                snapPositionalThreshold = 0.35f,
                            ),
                        ) { page ->
                            // Pages that arrive by swiping are already in motion; only a page that appears in
                            // place (start, switching day/week) plays the reveal.
                            val animateReveal = remember { !targetPager.isScrollInProgress }
                            val pageDate = if (targetMode == 0) {
                                dayOrigin.plusDays(page.toLong())
                            } else {
                                weekOrigin.plusWeeks(page.toLong())
                            }
                            if (targetMode == 0) {
                                DayPage(
                                    date = pageDate,
                                    today = today,
                                    now = now,
                                    cache = cache,
                                    status = status,
                                    showNowNext = appSettings.showNowNext,
                                    animateReveal = animateReveal,
                                    onRetry = { viewModel.refresh(pageDate) },
                                    findElement = findElement,
                                    onOpenElement = openElement,
                                )
                            } else {
                                WeekPage(
                                    weekStart = pageDate,
                                    today = today,
                                    now = now,
                                    cache = cache,
                                    status = status,
                                    onRetry = { viewModel.refresh(pageDate) },
                                    animateReveal = animateReveal,
                                    findElement = findElement,
                                    onOpenElement = openElement,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showSearch) {
        LessonSearchSheet(
            cache = cache,
            now = now,
            onSelect = { date ->
                showSearch = false
                mode = 0
                scope.launch {
                    dayPager.scrollToPage(
                        ChronoUnit.DAYS.between(dayOrigin, date).toInt().coerceIn(0, DAY_PAGE_COUNT - 1),
                    )
                }
            },
            onDismiss = { showSearch = false },
        )
    }
    if (showTargetPicker) {
        TargetPickerSheet(
            directory = directory,
            selected = target,
            favorites = favorites,
            onSelect = viewModel::setTarget,
            onToggleFavorite = viewModel::toggleFavorite,
            onRemoveFavorite = viewModel::removeFavorite,
            onReorderFavorites = viewModel::reorderFavorites,
            onDismiss = { showTargetPicker = false },
        )
    }
}

/**
 * The kind of timetable on screen: null for the student's own, else class, teacher or room. The
 * lesson blocks use it to leave out what is the same on every block (the teacher in a teacher's
 * timetable) and show what differs instead.
 */
private val LocalTimetableTargetType = staticCompositionLocalOf<ElementType?> { null }

/** The short detail line of a block: room and teacher, or what fits the kind of timetable. */
private fun lessonDetailParts(lesson: UntisLesson, targetType: ElementType?): List<String> = when (targetType) {
    ElementType.TEACHER -> listOf(lesson.room, lesson.className)
    ElementType.ROOM -> listOf(lesson.teacher, lesson.className)
    ElementType.CLASS, null -> listOf(lesson.room, lesson.teacher)
}.filter { it.isNotBlank() }

// The newer overflow-aware ButtonGroup overload takes a non-composable scope
// and cannot host ToggleButtons directly. Keep the expressive composable-child
// overload so the toggle shape morph and spring motion stay intact.
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewModeSwitcher(
    mode: Int,
    onModeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayInteraction = remember { MutableInteractionSource() }
    val weekInteraction = remember { MutableInteractionSource() }
    val dayScale by rememberPressScale(dayInteraction)
    val weekScale by rememberPressScale(weekInteraction)
    ButtonGroup(
        modifier = modifier.fillMaxWidth(),
    ) {
        ToggleButton(
            checked = mode == 0,
            onCheckedChange = { if (it) onModeChange(0) },
            interactionSource = dayInteraction,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .graphicsLayer {
                    scaleX = dayScale
                    scaleY = dayScale
                },
        ) {
            Text(stringResource(R.string.timetable_day))
        }
        ToggleButton(
            checked = mode == 1,
            onCheckedChange = { if (it) onModeChange(1) },
            interactionSource = weekInteraction,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .graphicsLayer {
                    scaleX = weekScale
                    scaleY = weekScale
                },
        ) {
            Text(stringResource(R.string.timetable_week))
        }
    }
}

@Composable
private fun DayPage(
    date: LocalDate,
    today: LocalDate,
    now: LocalDateTime,
    cache: TimetableCache,
    status: TimetableSyncStatus,
    showNowNext: Boolean,
    animateReveal: Boolean,
    onRetry: () -> Unit,
    findElement: (ElementType, String) -> UntisElement?,
    onOpenElement: (UntisElement) -> Unit,
) {
    val context = LocalContext.current
    val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = cache.weeks[weekStart]
    val genericHoliday = stringResource(R.string.holiday_generic)
    val realLessons = remember(week, date) {
        week?.lessons?.filter { it.date == date }?.sortedBy { it.start }.orEmpty()
    }
    // A day without lessons inside a holiday shows the holiday as one block that fills the day,
    // like a subject, instead of a blank screen.
    val lessons = remember(realLessons, cache.holidays, date, week, genericHoliday) {
        val holiday = holidayOn(cache.holidays, date)
        if (realLessons.isEmpty() && holiday != null) {
            val range = timelineRange(week?.lessons.orEmpty())
            listOf(holidayLesson(holiday, date, range, genericHoliday))
        } else {
            realLessons
        }
    }
    val isToday = date == today
    var selectedLesson by remember { mutableStateOf<UntisLesson?>(null) }
    var overflowShown by remember { mutableStateOf<TimelineOverflow?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            week == null && status.isLoading -> LoadingPane()
            week == null && status.lastError != null -> ErrorPane(
                message = status.lastError!!.toUserMessage(context),
                isRefreshing = status.isLoading,
                onRetry = onRetry,
            )
            else -> {
                val layout = remember(lessons, cache.exams) {
                    layoutTimeline(mergeLessons(lessons), priority = { blockPriority(it, cache.exams) })
                }
                val range = remember(lessons) { timelineRange(lessons) }
                val periodStarts = remember(week) {
                    periodStartsFor(week?.lessons.orEmpty())
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    if (status.lastError != null) {
                        OfflineBanner(
                            status = status,
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    HapticPullToRefreshBox(
                        isRefreshing = status.isLoading,
                        onRefresh = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        if (lessons.isEmpty()) {
                            ScrollableCentered {
                                EmptyMessage(
                                    title = stringResource(
                                        if (isToday) {
                                            R.string.timetable_empty_today_title
                                        } else {
                                            R.string.timetable_empty_title
                                        },
                                    ),
                                    body = stringResource(R.string.timetable_empty_body),
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 112.dp),
                            ) {
                                if (isToday && showNowNext) {
                                    val nowNext = NowAndNext.compute(realLessons, now)
                                    if (nowNext.phase != DayPhase.NO_LESSONS) {
                                        NowNextCard(
                                            status = nowNext,
                                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                                DayTimeline(
                                    layout = layout,
                                    range = range,
                                    periodStarts = periodStarts,
                                    isToday = isToday,
                                    now = now,
                                    revealKey = weekStart,
                                    animateReveal = animateReveal,
                                    exams = cache.exams,
                                    absences = cache.absences,
                                    onLessonClick = { selectedLesson = it },
                                    onOverflowClick = { overflowShown = it },
                                )
                            }
                        }
                    }
                }
            }
        }
        overflowShown?.let { overflow ->
            OverflowSheet(
                overflow = overflow,
                onLessonClick = {
                    overflowShown = null
                    selectedLesson = it
                },
                onDismiss = { overflowShown = null },
            )
        }
        selectedLesson?.let { lesson ->
            if (lesson.isHoliday) {
                cache.holidays.firstOrNull { it.id == -lesson.id }?.let { holiday ->
                    HolidaySheet(holiday = holiday, onDismiss = { selectedLesson = null })
                }
            } else {
                // The detail sheet itself is implemented in LessonDetailsSheet.
                LessonDetailsSheet(
                    lesson = lesson,
                    exam = examFor(lesson, cache.exams),
                    findElement = findElement,
                    onOpenElement = onOpenElement,
                    onDismiss = { selectedLesson = null },
                )
            }
        }
    }
}

@Composable
private fun DayStrip(
    date: LocalDate,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    examDates: Set<LocalDate> = emptySet(),
) {
    val locale = currentLocale(LocalContext.current)
    val weekStart = remember(date) {
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    // The strip stays in place while the days swipe underneath; only the indicator glides. Moving
    // to another week slides the whole row in the direction of travel.
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = weekStart,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (
                slideInHorizontally(motion.defaultSpatialSpec()) { width -> direction * width / 3 } +
                    fadeIn(motion.defaultEffectsSpec())
                ).togetherWith(
                slideOutHorizontally(motion.defaultSpatialSpec()) { width -> -direction * width / 3 } +
                    fadeOut(motion.fastEffectsSpec()),
            ).using(SizeTransform(clip = false))
        },
        label = "dayStripWeek",
    ) { shownWeek ->
        DayStripRow(
            weekStart = shownWeek,
            date = date,
            today = today,
            onSelectDate = onSelectDate,
            examDates = examDates,
            locale = locale,
        )
    }
}

@Composable
private fun DayStripRow(
    weekStart: LocalDate,
    date: LocalDate,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    examDates: Set<LocalDate>,
    locale: Locale,
) {
    val days = remember(weekStart) { (0L..6L).map { weekStart.plusDays(it) } }
    // The outgoing week of a week change has no selected day any more; its indicator stays put.
    val selectedIndex = days.indexOf(date)
    var lastIndex by remember { mutableIntStateOf(selectedIndex.coerceAtLeast(0)) }
    if (selectedIndex >= 0) lastIndex = selectedIndex
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        val chipWidth = maxWidth / days.size
        val indicatorWidth = chipWidth * 0.34f
        val targetX = chipWidth * lastIndex + (chipWidth - indicatorWidth) / 2f
        val indicatorSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
        val animatedX by animateDpAsState(
            targetValue = targetX,
            animationSpec = indicatorSpec,
            label = "dayStripIndicator",
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                days.forEach { day ->
                    DayChip(
                        date = day,
                        selected = day == date,
                        isToday = day == today,
                        hasExam = day in examDates,
                        locale = locale,
                        onClick = { onSelectDate(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .offset(x = animatedX)
                    .width(indicatorWidth)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun DayChip(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    hasExam: Boolean,
    locale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primary else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "dayChipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            scheme.onPrimary
        } else if (isToday) {
            scheme.primary
        } else {
            scheme.onSurface
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "dayChipContent",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "dayChipScale",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interactionSource)
    Column(
        modifier = modifier
            .height(50.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = date.format(DateTimeFormatter.ofPattern("EEE", locale))
                    .replaceFirstChar { it.titlecase(locale) }
                    .take(3),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (hasExam) {
                // A small yellow dot marks a day with an exam. The text stays the signal for screen readers.
                Spacer(Modifier.width(3.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(examAccent(), CircleShape),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Surface(
            shape = CircleShape,
            color = container,
            contentColor = content,
            border = if (isToday && !selected) {
                BorderStroke(1.dp, scheme.primary)
            } else {
                null
            },
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * The "now" line: a thin line across the timetable at the current time, with the time in a
 * bubble at its end. It glides to its new position when the minute changes. Used by the day
 * view and the week view.
 */
@Composable
private fun NowLine(
    range: TimelineRange,
    now: LocalTime,
    pxPerMinute: Dp,
    modifier: Modifier = Modifier,
) {
    val animatedMinutes by animateFloatAsState(
        targetValue = minutesBetween(range.start, now).toFloat(),
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "nowLine",
    )
    Row(
        modifier = modifier
            .offset(y = pxPerMinute * animatedMinutes - 9.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = formatTime(now),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun DayTimeline(
    layout: TimelineLayout,
    range: TimelineRange,
    periodStarts: List<LocalTime>,
    isToday: Boolean,
    now: LocalDateTime,
    revealKey: Any?,
    animateReveal: Boolean,
    exams: List<UntisExam>,
    absences: List<UntisAbsence>,
    onLessonClick: (UntisLesson) -> Unit,
    onOverflowClick: (TimelineOverflow) -> Unit,
) {
    val blocks = layout.visible
    val pxPerMinute = 1.5.dp
    val contentHeight = (pxPerMinute.value * range.minutes).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 14.dp),
    ) {
        TimelineAxis(
            range = range,
            periodStarts = periodStarts,
            pxPerMinute = pxPerMinute,
            modifier = Modifier
                .width(56.dp)
                .height(contentHeight),
        )
        Spacer(Modifier.width(8.dp))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(contentHeight),
        ) {
            TimelineGrid(
                range = range,
                periodStarts = periodStarts,
                pxPerMinute = pxPerMinute,
            )
            FreeGapBands(
                blocks = layout.all,
                range = range,
                pxPerMinute = pxPerMinute,
            )
            val nowTime = now.toLocalTime()
            val currentBlock = if (isToday) {
                blocks.filter {
                    !nowTime.isBefore(it.start) && nowTime.isBefore(it.end)
                }.maxByOrNull { it.start }
            } else {
                null
            }
            blocks.forEachIndexed { index, block ->
                key(block.lesson.id, block.start, block.end) {
                    val targetTop = pxPerMinute * minutesBetween(range.start, block.start)
                    val targetHeight = max(
                        34f,
                        pxPerMinute.value * minutesBetween(block.start, block.end) - 3f,
                    ).dp
                    val animatedTop by animateDpAsState(
                        targetValue = targetTop,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "lessonTop",
                    )
                    val animatedHeight by animateDpAsState(
                        targetValue = targetHeight,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "lessonHeight",
                    )
                    val reveal by rememberStaggeredReveal(revealKey, index, enabled = animateReveal)
                    val columnWidth = maxWidth / block.columnCount
                    val current = block == currentBlock
                    DayLessonBlock(
                        block = block,
                        current = current,
                        exam = examFor(block.lesson, exams),
                        absent = remember(block, absences) { absentParts(block, absences) },
                        onClick = { onLessonClick(block.detailLesson) },
                        modifier = Modifier
                            .width(columnWidth - 4.dp)
                            .height(animatedHeight)
                            .offset(
                                x = columnWidth * block.column + 2.dp,
                                y = animatedTop,
                            )
                            .graphicsLayer {
                                alpha = reveal
                                val scale = 0.94f + 0.06f * reveal
                                scaleX = scale
                                scaleY = scale
                            },
                    )
                }
            }
            OverflowChips(
                overflows = layout.overflows,
                range = range,
                pxPerMinute = pxPerMinute,
                chipHeight = 26.dp,
                chipWidth = 40.dp,
                onClick = onOverflowClick,
            )
            if (isToday && nowTime >= range.start && nowTime < range.end) {
                NowLine(range = range, now = nowTime, pxPerMinute = pxPerMinute)
            }
        }
    }
}

@Composable
private fun TimelineAxis(
    range: TimelineRange,
    periodStarts: List<LocalTime>,
    pxPerMinute: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        periodStarts.forEachIndexed { index, time ->
            if (time >= range.start && time < range.end) {
                val y = pxPerMinute * minutesBetween(range.start, time)
                Column(
                    modifier = Modifier
                        .offset(y = y - 4.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = formatTime(time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // The end of the range sits at the very bottom. When the last period starts just above it,
        // the two labels would print over each other, so the end time is left out then.
        val lastStart = periodStarts.lastOrNull { it >= range.start && it < range.end }
        if (lastStart == null || minutesBetween(lastStart, range.end) >= AXIS_END_LABEL_GAP_MINUTES) {
            Text(
                text = formatTime(range.end),
                modifier = Modifier.align(Alignment.BottomEnd),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/** Minutes between the last period label and the end label needed for both to fit. */
private const val AXIS_END_LABEL_GAP_MINUTES = 30

@Composable
private fun TimelineGrid(
    range: TimelineRange,
    periodStarts: List<LocalTime>,
    pxPerMinute: Dp,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        periodStarts.forEach { time ->
            if (time >= range.start && time < range.end) {
                HorizontalDivider(
                    modifier = Modifier
                        .offset(y = pxPerMinute * minutesBetween(range.start, time))
                        .fillMaxWidth(),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
            }
        }
    }
}

@Composable
private fun FreeGapBands(
    blocks: List<TimelineBlock>,
    range: TimelineRange,
    pxPerMinute: Dp,
) {
    val gaps = remember(blocks, range) { freeGaps(blocks, range) }
    Box(modifier = Modifier.fillMaxSize()) {
        gaps.forEach { gap ->
            val top = pxPerMinute * minutesBetween(range.start, gap.start)
            val height = pxPerMinute * minutesBetween(gap.start, gap.end)
            Box(
                modifier = Modifier
                    .offset(y = top)
                    .fillMaxWidth()
                    .height(height)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)),
            ) {
                if (minutesBetween(gap.start, gap.end) >= 25) {
                    Text(
                        text = stringResource(R.string.timetable_free),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

/** How a lesson block is drawn once status and the user's colour choices are applied. */
private class LessonLook(
    /** The edge stripe, or null when the whole card is coloured instead. */
    val stripe: Color?,
    val fill: Color,
    val content: Color,
    /** Used for borders. */
    val accent: Color,
)

@Composable
private fun rememberLessonLook(
    block: TimelineBlock,
    exam: UntisExam?,
    tone: SubjectTone,
    subject: String,
): LessonLook {
    if (block.lesson.isHoliday) {
        // Holidays have their own calm colour, apart from every subject and status.
        val scheme = MaterialTheme.colorScheme
        return LessonLook(
            stripe = scheme.tertiary,
            // A tint of the tertiary colour over the surface, like the subject blocks, so it sits calmly
            // in both themes instead of shouting as a bright container.
            fill = holidayFill(),
            content = scheme.onSurface,
            accent = scheme.tertiary,
        )
    }
    val lessonColors = LocalLessonColors.current
    val roles = LocalSubjectColorMode.current == SubjectColorMode.ROLES
    val customSubject = lessonColors.subjectColors.containsKey(subject)
    val fullCard = lessonColors.fillWholeCard
    if (roles && !customSubject && !fullCard) {
        // The role palette fills the card itself, as before.
        return LessonLook(
            stripe = null,
            fill = tone.container,
            content = tone.onContainer,
            accent = tone.accent,
        )
    }
    val stripe = when {
        block.isCancelled -> cancelledAccent()
        exam != null -> examHighlight()
        block.hasRoomChange || block.hasSubstitution -> substitutionAccent()
        else -> tone.accent
    }
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    // Every block carries a faint tint of its subject colour, so the card is not plain black or
    // grey. Dark theme needs a little more tint to show up against the dark surface.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val tint = if (dark) 0.2f else 0.14f
    val fill = when {
        fullCard -> lerp(surface, stripe, 0.24f)
        exam != null -> lerp(surface, stripe, 0.4f)
        else -> lerp(surface, stripe, tint)
    }
    return LessonLook(
        stripe = if (fullCard) null else stripe,
        fill = fill,
        content = MaterialTheme.colorScheme.onSurface,
        accent = stripe,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayLessonBlock(
    block: TimelineBlock,
    current: Boolean,
    exam: UntisExam?,
    absent: List<ClosedFloatingPointRange<Float>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lesson = block.lesson
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interactionSource)
    val subject = lesson.subject.ifBlank {
        stringResource(R.string.lesson_unknown_subject)
    }
    val tone = rememberSubjectTone(subject)
    val look = rememberLessonLook(block, exam, tone, subject)
    val cancelled = block.isCancelled
    val border = when {
        // An exam gets a thick bright frame, so it is the first thing the eye finds.
        exam != null -> BorderStroke(4.5.dp, examHighlight())
        current -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        cancelled -> BorderStroke(1.5.dp, cancelledAccent())
        block.hasSubstitution -> BorderStroke(1.5.dp, substitutionAccent())
        else -> null
    }
    val badge = when {
        cancelled -> stringResource(R.string.lesson_cancelled)
        block.hasRoomChange -> stringResource(R.string.lesson_room_short)
        block.hasSubstitution -> stringResource(R.string.lesson_substitution_short)
        else -> null
    }
    val badgeAccent = if (cancelled) cancelledAccent() else substitutionAccent()
    Surface(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        },
        shape = RoundedCornerShape(if (block.lessons.size > 1) 18.dp else 14.dp),
        color = look.fill,
        contentColor = look.content,
        border = border,
        tonalElevation = if (current) 3.dp else 0.dp,
        interactionSource = interactionSource,
    ) {
        Row(modifier = Modifier.fillMaxSize().greyOutAbsent(absent, absentVeil())) {
            look.stripe?.let { stripe ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(stripe),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (cancelled) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (lesson.info.isNotBlank()) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp),
                        )
                    }
                    if (exam != null) {
                        Spacer(Modifier.width(4.dp))
                        AccentBadge(
                            text = stringResource(R.string.exam_badge),
                            containerColor = examHighlight(),
                            contentColor = onColorFor(examHighlight()),
                        )
                    }
                    if (badge != null) {
                        Spacer(Modifier.width(4.dp))
                        AccentBadge(
                            text = badge,
                            containerColor = lerp(
                                MaterialTheme.colorScheme.surface,
                                badgeAccent,
                                0.22f,
                            ),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (block.durationMinutes >= 35) {
                    // Room and teacher share one line so the block reads at a glance. In a teacher's
                    // or room's timetable the class replaces what is the same on every block.
                    val targetType = LocalTimetableTargetType.current
                    val details = lessonDetailParts(lesson, targetType).map { part ->
                        if (part == lesson.room) context.getString(R.string.room_value, part) else part
                    }.joinToString(" · ")
                    if (details.isNotBlank()) {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekPage(
    weekStart: LocalDate,
    today: LocalDate,
    now: LocalDateTime,
    cache: TimetableCache,
    status: TimetableSyncStatus,
    onRetry: () -> Unit,
    animateReveal: Boolean,
    findElement: (ElementType, String) -> UntisElement?,
    onOpenElement: (UntisElement) -> Unit,
) {
    val context = LocalContext.current
    val week = cache.weeks[weekStart]
    val genericHoliday = stringResource(R.string.holiday_generic)
    val realLessons = remember(week) { week?.lessons.orEmpty() }
    val days = remember(weekStart) { (0L..4L).map { weekStart.plusDays(it) } }
    val range = remember(realLessons) { timelineRange(realLessons) }
    val periodStarts = remember(realLessons) {
        periodStartsFor(realLessons)
    }
    // Days without lessons inside a holiday get the holiday as one block that fills the column.
    val allLessons = remember(realLessons, days, cache.holidays, range, genericHoliday) {
        realLessons + days.mapNotNull { day ->
            if (realLessons.any { it.date == day }) {
                null
            } else {
                holidayOn(cache.holidays, day)?.let { holidayLesson(it, day, range, genericHoliday) }
            }
        }
    }
    // Long events such as a school event stay in the grid, beside the lessons they replace.
    val gridLessonsByDay = remember(allLessons, days) {
        days.associateWith { day -> allLessons.filter { it.date == day } }
    }
    val pxPerMinute = 1.05.dp
    // One fade for the whole week when it is shown, instead of an animation per lesson. A week that
    // is swiped in is already moving, so it comes in fully drawn instead of fading mid-swipe.
    val gridReveal = remember(weekStart) { Animatable(if (animateReveal) 0f else 1f) }
    LaunchedEffect(weekStart) {
        gridReveal.animateTo(1f, animationSpec = tween(durationMillis = 220))
    }
    val contentHeight = (pxPerMinute.value * range.minutes).dp
    var selectedLesson by remember { mutableStateOf<UntisLesson?>(null) }
    var overflowShown by remember { mutableStateOf<TimelineOverflow?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            week == null && status.isLoading -> LoadingPane()
            week == null && status.lastError != null -> ErrorPane(
                message = status.lastError!!.toUserMessage(context),
                isRefreshing = status.isLoading,
                onRetry = onRetry,
            )
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (status.lastError != null) {
                        OfflineBanner(
                            status = status,
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (allLessons.isEmpty()) {
                        HapticPullToRefreshBox(
                            isRefreshing = status.isLoading,
                            onRefresh = onRetry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            ScrollableCentered {
                                EmptyMessage(
                                    title = stringResource(R.string.timetable_empty_week_title),
                                    body = stringResource(R.string.timetable_empty_body),
                                )
                            }
                        }
                    } else {
                        WeekDayHeaderRow(
                            weekStart = weekStart,
                            today = today,
                        )
                        HapticPullToRefreshBox(
                            isRefreshing = status.isLoading,
                            onRefresh = onRetry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .graphicsLayer { alpha = gridReveal.value },
                            ) {
                                TimelineAxis(
                                    range = range,
                                    periodStarts = periodStarts,
                                    pxPerMinute = pxPerMinute,
                                    modifier = Modifier
                                        .width(52.dp)
                                        .height(contentHeight),
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(contentHeight),
                                ) {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        days.forEach { day ->
                                            WeekDayColumn(
                                                date = day,
                                                today = today,
                                                now = now,
                                                lessons = gridLessonsByDay[day].orEmpty(),
                                                range = range,
                                                periodStarts = periodStarts,
                                                pxPerMinute = pxPerMinute,
                                                revealKey = weekStart,
                                                exams = cache.exams,
                                                absences = cache.absences,
                                                onLessonClick = { selectedLesson = it },
                                                onOverflowClick = { overflowShown = it },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                    // The same time line as the day view, shown only in the current week.
                                    val nowTime = now.toLocalTime()
                                    val isCurrentWeek = weekStart ==
                                        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                    if (isCurrentWeek && nowTime >= range.start && nowTime < range.end) {
                                        NowLine(range = range, now = nowTime, pxPerMinute = pxPerMinute)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        overflowShown?.let { overflow ->
            OverflowSheet(
                overflow = overflow,
                onLessonClick = {
                    overflowShown = null
                    selectedLesson = it
                },
                onDismiss = { overflowShown = null },
            )
        }
        selectedLesson?.let { lesson ->
            if (lesson.isHoliday) {
                cache.holidays.firstOrNull { it.id == -lesson.id }?.let { holiday ->
                    HolidaySheet(holiday = holiday, onDismiss = { selectedLesson = null })
                }
            } else {
                LessonDetailsSheet(
                    lesson = lesson,
                    exam = examFor(lesson, cache.exams),
                    findElement = findElement,
                    onOpenElement = onOpenElement,
                    onDismiss = { selectedLesson = null },
                )
            }
        }
    }
}

@Composable
private fun WeekDayHeaderRow(
    weekStart: LocalDate,
    today: LocalDate,
) {
    val locale = currentLocale(LocalContext.current)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(52.dp))
        Spacer(Modifier.width(8.dp))
        Row(modifier = Modifier.weight(1f)) {
            (0L..4L).forEach { offset ->
                val date = weekStart.plusDays(offset)
                val isToday = date == today
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("EEE", locale))
                            .replaceFirstChar { it.titlecase(locale) },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekDayColumn(
    date: LocalDate,
    today: LocalDate,
    now: LocalDateTime,
    lessons: List<UntisLesson>,
    range: TimelineRange,
    periodStarts: List<LocalTime>,
    pxPerMinute: Dp,
    revealKey: Any?,
    exams: List<UntisExam>,
    absences: List<UntisAbsence>,
    onLessonClick: (UntisLesson) -> Unit,
    onOverflowClick: (TimelineOverflow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isToday = date == today
    val layout = remember(lessons, exams) {
        layoutTimeline(mergeLessons(lessons), priority = { blockPriority(it, exams) })
    }
    val blocks = layout.visible
    val nowTime = now.toLocalTime()
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(
                if (isToday) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    Color.Transparent
                },
            ),
    ) {
        TimelineGrid(
            range = range,
            periodStarts = periodStarts,
            pxPerMinute = pxPerMinute,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val currentBlock = if (isToday) {
                blocks.filter {
                    !nowTime.isBefore(it.start) && nowTime.isBefore(it.end)
                }.maxByOrNull { it.start }
            } else {
                null
            }
            // Block geometry is set directly. Animating every block's position and height with
            // springs cost a lot while scrolling, so the week only fades in as a whole.
            blocks.forEach { block ->
                key(block.lesson.id, block.start, block.end) {
                    val top = pxPerMinute * minutesBetween(range.start, block.start)
                    val height = max(
                        24f,
                        pxPerMinute.value * minutesBetween(block.start, block.end) - 2f,
                    ).dp
                    val columnWidth = maxWidth / block.columnCount
                    val current = block == currentBlock
                    WeekLessonBlock(
                        block = block,
                        current = current,
                        exam = examFor(block.lesson, exams),
                        absent = remember(block, absences) { absentParts(block, absences) },
                        onClick = { onLessonClick(block.detailLesson) },
                        modifier = Modifier
                            .width(maxOf(columnWidth - 2.dp, 14.dp))
                            .height(height)
                            .offset(
                                x = columnWidth * block.column + 1.dp,
                                y = top,
                            ),
                    )
                }
            }
            OverflowChips(
                overflows = layout.overflows,
                range = range,
                pxPerMinute = pxPerMinute,
                chipHeight = 20.dp,
                chipWidth = 28.dp,
                onClick = onOverflowClick,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        )
    }
}

/**
 * Which lessons to keep when more run at once than fit beside each other: exams first, then
 * substitutions, then cancelled lessons, then the rest.
 */
private fun blockPriority(block: TimelineBlock, exams: List<UntisExam>): Int = when {
    examFor(block.lesson, exams) != null -> 4
    block.hasSubstitution && !block.isCancelled -> 3
    block.isCancelled -> 2
    else -> 1
}

/** One "+N" chip per run of lessons that did not fit, near the top of where the run starts. */
@Composable
private fun BoxScope.OverflowChips(
    overflows: List<TimelineOverflow>,
    range: TimelineRange,
    pxPerMinute: Dp,
    chipHeight: Dp,
    chipWidth: Dp,
    onClick: (TimelineOverflow) -> Unit,
) {
    overflows.forEach { overflow ->
        val top = pxPerMinute * minutesBetween(range.start, overflow.start)
        // The bottom of the run's first 50 minutes: the text of a block sits at its top, so the chip
        // covers the emptier part.
        val slot = minOf(minutesBetween(overflow.start, overflow.end), 50)
        val y = maxOf(top, top + pxPerMinute * slot - chipHeight - 3.dp)
        val count = overflow.hidden.size
        val description = stringResource(R.string.timetable_overflow_description, count)
        Surface(
            onClick = { onClick(overflow) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-2).dp, y = y)
                .size(width = chipWidth, height = chipHeight)
                .semantics { contentDescription = description },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "+$count",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (chipHeight > 22.dp) 13.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Every lesson that runs in the span of a "+N" chip, to pick one from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverflowSheet(
    overflow: TimelineOverflow,
    onLessonClick: (UntisLesson) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.timetable_overflow_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "${formatTime(overflow.start)} – ${formatTime(overflow.end)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            overflow.concurrent.forEach { block ->
                OverflowRow(block = block, onClick = { onLessonClick(block.detailLesson) })
            }
        }
    }
}

@Composable
private fun OverflowRow(block: TimelineBlock, onClick: () -> Unit) {
    val lesson = block.lesson
    val subject = lesson.subject.ifBlank { stringResource(R.string.lesson_unknown_subject) }
    val tone = rememberSubjectTone(subject)
    val cancelled = block.isCancelled
    val detail = listOf(lesson.room, lesson.teacher).filter { it.isNotBlank() }.joinToString(" · ")
    val status = when {
        cancelled -> stringResource(R.string.lesson_cancelled)
        block.hasSubstitution -> stringResource(R.string.lesson_substitution_short)
        else -> null
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = tone.container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(tone.accent, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (cancelled) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf("${formatTime(block.start)} – ${formatTime(block.end)}", detail)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (status != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun WeekLessonBlock(
    block: TimelineBlock,
    current: Boolean,
    exam: UntisExam?,
    absent: List<ClosedFloatingPointRange<Float>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lesson = block.lesson
    val subject = lesson.subject.ifBlank {
        stringResource(R.string.lesson_unknown_subject)
    }
    val tone = rememberSubjectTone(subject)
    val look = rememberLessonLook(block, exam, tone, subject)
    val cancelled = block.isCancelled
    val border = when {
        exam != null -> BorderStroke(3.5.dp, examHighlight())
        current -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        cancelled -> BorderStroke(1.dp, cancelledAccent())
        block.hasRoomChange || block.hasSubstitution -> BorderStroke(1.dp, substitutionAccent())
        else -> null
    }
    // Week columns are narrow, so status is written out as a line of text rather than a badge.
    val status: String? = when {
        cancelled -> stringResource(R.string.lesson_cancelled)
        exam != null -> stringResource(R.string.exam_badge)
        block.hasRoomChange || block.hasSubstitution -> stringResource(R.string.lesson_substitution_short)
        else -> null
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = look.fill,
        contentColor = look.content,
        border = border,
    ) {
        Row(modifier = Modifier.fillMaxSize().greyOutAbsent(absent, absentVeil())) {
            look.stripe?.let { stripe ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(stripe),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 5.dp, vertical = 4.dp),
            ) {
                Text(
                    text = subject,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    textDecoration = if (cancelled) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (block.durationMinutes >= 35) {
                    if (status != null) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = look.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // A status line and a room line only fit in a longer block, or with larger
                    // text they clip. Status wins when the block is short.
                    val detail = lessonDetailParts(lesson, LocalTimetableTargetType.current).firstOrNull().orEmpty()
                    val roomFits = status == null || block.durationMinutes >= 60
                    if (detail.isNotBlank() && roomFits) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner(status: TimetableSyncStatus, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val error = status.lastError ?: return
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.timetable_offline, error.toUserMessage(context)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun EmptyMessage(title: String, body: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(58.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingPane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ErrorPane(message: String, isRefreshing: Boolean, onRetry: () -> Unit) {
    // Pull-to-refresh also works from the error state, so a retry does not need the button.
    HapticPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRetry,
        modifier = Modifier.fillMaxSize(),
    ) {
        ScrollableCentered {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.timetable_load_error),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.Button(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

/**
 * Content that is centred when it is short but still scrolls when it is taller than the
 * space. Scrolling is what lets a pull-to-refresh start on an empty day.
 */
@Composable
private fun ScrollableCentered(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { androidx.compose.runtime.mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            now = LocalDateTime.now()
        }
    }
    return now
}

private data class TimelineRange(
    val start: LocalTime,
    val end: LocalTime,
) {
    val minutes: Int get() = minutesBetween(start, end)
}

private data class FreeGap(
    val start: LocalTime,
    val end: LocalTime,
)

private fun mergeLessons(lessons: List<UntisLesson>): List<TimelineBlock> {
    if (lessons.isEmpty()) return emptyList()
    val merged = mutableListOf<TimelineBlock>()
    lessons.groupBy {
        LessonMergeKey(
            subject = it.subject,
            room = it.room,
            teacher = it.teacher,
            status = it.status,
            substitutionText = it.substitutionText,
        )
    }.values.forEach { group ->
        val sorted = group.sortedBy { it.start }
        var run = mutableListOf<UntisLesson>()
        var runStart = LocalTime.MIN
        var runEnd = LocalTime.MIN
        for (lesson in sorted) {
            val continues = run.isNotEmpty() && !lesson.start.isAfter(runEnd.plusMinutes(1))
            if (continues) {
                run += lesson
                if (lesson.end > runEnd) runEnd = lesson.end
            } else {
                if (run.isNotEmpty()) {
                    merged += TimelineBlock(run.toList(), runStart, runEnd)
                }
                run = mutableListOf(lesson)
                runStart = lesson.start
                runEnd = lesson.end
            }
        }
        if (run.isNotEmpty()) {
            merged += TimelineBlock(run.toList(), runStart, runEnd)
        }
    }
    return merged
}

private data class LessonMergeKey(
    val subject: String,
    val room: String,
    val teacher: String,
    val status: LessonStatus,
    val substitutionText: String,
)

private fun periodStartsFor(lessons: List<UntisLesson>): List<LocalTime> {
    val sorted = lessons.map { it.start }.distinct().sorted()
    val collapsed = mutableListOf<LocalTime>()
    for (time in sorted) {
        if (collapsed.isEmpty() || minutesBetween(collapsed.last(), time) >= 25) {
            collapsed += time
        }
    }
    return collapsed
}

/**
 * The gaps between two lessons of the day. The time before the first and after the last lesson is
 * not a free period, so it gets no band (it used to read "Frei" before school and all afternoon).
 */
private fun freeGaps(blocks: List<TimelineBlock>, range: TimelineRange): List<FreeGap> {
    var cursor: LocalTime? = null
    val gaps = mutableListOf<FreeGap>()
    for (block in blocks.sortedBy { it.start }) {
        val last = cursor
        if (last != null && block.start > last) {
            val start = maxOf(last, range.start)
            val end = minOf(block.start, range.end)
            if (minutesBetween(start, end) >= 10) gaps += FreeGap(start, end)
        }
        if (last == null || block.end > last) cursor = block.end
    }
    return gaps
}

private fun timelineRange(lessons: List<UntisLesson>): TimelineRange {
    val defaultStart = 7 * 60 + 30
    val defaultEnd = 17 * 60
    val first = lessons.minOfOrNull { it.start.toSecondOfDay() / 60 } ?: defaultStart
    val last = lessons.maxOfOrNull { it.end.toSecondOfDay() / 60 } ?: defaultEnd
    val startMinute = (minOf(first, defaultStart) / 5) * 5
    val endMinute = ((maxOf(last, defaultEnd) + 4) / 5) * 5
    return TimelineRange(
        start = minuteToTime(startMinute),
        end = minuteToTime(endMinute),
    )
}

private fun minuteToTime(totalMinutes: Int): LocalTime =
    LocalTime.of((totalMinutes / 60) % 24, totalMinutes % 60)

private fun minutesBetween(start: LocalTime, end: LocalTime): Int =
    Duration.between(start, end).toMinutes().toInt().coerceAtLeast(0)

/** The tint of a holiday block and card: the tertiary colour over the surface, as for the subjects. */
@Composable
private fun holidayFill(): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return lerp(scheme.surfaceContainerLow, scheme.tertiary, if (dark) 0.32f else 0.24f)
}

/** The holiday that covers [date], if any. */
internal fun holidayOn(holidays: List<UntisHoliday>, date: LocalDate): UntisHoliday? =
    holidays.firstOrNull { !date.isBefore(it.start) && !date.isAfter(it.end) }

/**
 * A block that stands for [holiday] on [date] and fills the day's time range, so the timetable can
 * show it like a subject. Its id is the negative holiday id, which the details sheet reads back.
 */
private fun holidayLesson(holiday: UntisHoliday, date: LocalDate, range: TimelineRange, generic: String): UntisLesson =
    UntisLesson(
        id = -holiday.id,
        date = date,
        start = range.start,
        end = range.end,
        subject = if (holiday.hasDateName) generic else holiday.displayName,
        isHoliday = true,
    )

/**
 * The parts of [block] in which the student was absent, as fractions of its length from the top.
 * An absence from 7:55 to 8:05 over a lesson that starts at 7:55 gives the first tenth of a 100
 * minute lesson, not the whole block. A cancelled lesson has nothing to grey out.
 */
internal fun absentParts(block: TimelineBlock, absences: List<UntisAbsence>): List<ClosedFloatingPointRange<Float>> {
    if (absences.isEmpty() || block.isCancelled || block.lesson.isHoliday) return emptyList()
    val date = block.lesson.date
    val blockStart = date.atTime(block.start)
    val blockEnd = date.atTime(block.end)
    val total = Duration.between(blockStart, blockEnd).seconds.toFloat()
    if (total <= 0f) return emptyList()
    return absences.mapNotNull { absence ->
        val from = maxOf(absence.start, blockStart)
        val to = minOf(absence.end, blockEnd)
        if (!from.isBefore(to)) return@mapNotNull null
        val top = Duration.between(blockStart, from).seconds / total
        val bottom = Duration.between(blockStart, to).seconds / total
        top..bottom
    }
}

/** Draws [color] over the given fractions of the height, above the content. */
private fun Modifier.greyOutAbsent(parts: List<ClosedFloatingPointRange<Float>>, color: Color): Modifier =
    if (parts.isEmpty()) {
        this
    } else {
        drawWithContent {
            drawContent()
            parts.forEach { part ->
                drawRect(
                    color = color,
                    topLeft = Offset(0f, size.height * part.start),
                    size = Size(size.width, size.height * (part.endInclusive - part.start)),
                )
            }
        }
    }

/**
 * The exam that belongs to a lesson. The server sends exams with a subject id, not a
 * subject name, so the match is on the same day and an overlapping time. When an exam
 * does carry a subject name, that must match too.
 */
private fun examFor(lesson: UntisLesson, exams: List<UntisExam>): UntisExam? {
    if (exams.isEmpty() || lesson.isHoliday) return null
    return exams.firstOrNull { exam ->
        exam.date == lesson.date &&
            exam.start < lesson.end &&
            exam.end > lesson.start &&
            (exam.subject.isBlank() || exam.subject.equals(lesson.subject, ignoreCase = true))
    }
}

private fun formatTime(time: LocalTime): String =
    time.format(DateTimeFormatter.ofPattern("HH:mm"))

private fun currentLocale(context: android.content.Context): Locale =
    context.resources.configuration.locales[0] ?: Locale.GERMAN

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonDetailsSheet(
    lesson: UntisLesson,
    exam: UntisExam?,
    findElement: (ElementType, String) -> UntisElement?,
    onOpenElement: (UntisElement) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    LaunchedEffect(lesson.id) { haptics.perform(HapticEvent.SHEET_OPEN) }
    val locale = currentLocale(context)
    val subject = lesson.subject.ifBlank { stringResource(R.string.lesson_unknown_subject) }
    val tone = rememberSubjectTone(subject)
    val cancelled = lesson.isCancelled
    val warning = !cancelled && lesson.hasSubstitution
    val statusText = when {
        cancelled -> stringResource(R.string.lesson_cancelled)
        lesson.hasRoomChange -> stringResource(R.string.lesson_room_changed)
        lesson.isIrregular -> stringResource(R.string.lesson_substitution)
        lesson.hasSubstitution -> stringResource(R.string.lesson_changed)
        else -> stringResource(R.string.lesson_regular)
    }
    // The card is the lesson's colour, tinted strongly enough that the dark or light text on it
    // reads clearly against the sheet. The tint is lighter in dark theme so text stays legible.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cardContainer = lerp(
        MaterialTheme.colorScheme.surfaceContainerLow,
        tone.accent,
        if (dark) 0.2f else 0.3f,
    )
    val statusContainer = when {
        cancelled -> MaterialTheme.colorScheme.errorContainer
        warning -> warningContainer()
        else -> MaterialTheme.colorScheme.surface
    }
    val statusContent = when {
        cancelled -> MaterialTheme.colorScheme.onErrorContainer
        warning -> warningAccent()
        else -> MaterialTheme.colorScheme.onSurface
    }
    val dateLabel = remember(lesson.date, locale) {
        val pattern = if (locale.language == "de") {
            "EEEE, d. MMMM yyyy"
        } else {
            "EEEE, d MMMM yyyy"
        }
        lesson.date.format(DateTimeFormatter.ofPattern(pattern, locale))
            .replaceFirstChar { it.titlecase(locale) }
    }
    // One anchor only (fully expanded, or as tall as the content): with a half-height stop, content
    // that changes size made the sheet jump between the two. The content scrolls when it is taller
    // than the screen, and the sheet still closes by swiping down from the top.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = cardContainer,
                contentColor = tone.onContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = subject,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            textDecoration = if (cancelled) {
                                TextDecoration.LineThrough
                            } else {
                                TextDecoration.None
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 4.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        AccentBadge(
                            text = statusText,
                            containerColor = statusContainer,
                            contentColor = statusContent,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "$dateLabel · ${formatTime(lesson.start)} – ${formatTime(lesson.end)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = tone.onContainer,
                    )
                }
            }

            if (exam != null) {
                Spacer(Modifier.height(14.dp))
                ExamCard(exam = exam)
            }

            if (lesson.hasRoomChange) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = warningContainer(),
                    contentColor = warningAccent(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Room,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(
                                R.string.lesson_room_change_detail,
                                lesson.originalRoom,
                                lesson.room,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            // Room, teacher and class lead to their own timetable.
            val open: (UntisElement) -> Unit = { element ->
                onOpenElement(element)
                onDismiss()
            }
            NameRow(
                icon = Icons.Outlined.Room,
                label = stringResource(R.string.label_room),
                names = lesson.room,
                type = ElementType.ROOM,
                findElement = findElement,
                onOpen = open,
            )
            NameRow(
                icon = Icons.Outlined.Person,
                label = stringResource(R.string.label_teacher),
                names = lesson.teacher,
                type = ElementType.TEACHER,
                findElement = findElement,
                onOpen = open,
            )
            if (lesson.hasTeacherChange) {
                NameRow(
                    icon = Icons.Outlined.Person,
                    label = stringResource(R.string.label_previous_teacher),
                    names = lesson.originalTeacher,
                    type = ElementType.TEACHER,
                    findElement = findElement,
                    onOpen = open,
                )
            }
            if (lesson.className.isNotBlank()) {
                NameRow(
                    icon = Icons.Outlined.CalendarMonth,
                    label = stringResource(R.string.label_class),
                    names = lesson.className,
                    type = ElementType.CLASS,
                    findElement = findElement,
                    onOpen = open,
                )
            }
            if (lesson.group.isNotBlank()) {
                DetailRow(
                    icon = Icons.Outlined.Info,
                    label = stringResource(R.string.label_group),
                    value = lesson.group,
                )
            }
            if (lesson.lessonText.isNotBlank()) {
                DetailRow(
                    icon = Icons.Outlined.Info,
                    label = stringResource(R.string.label_topic),
                    value = lesson.lessonText,
                )
            }
            if (lesson.substitutionText.isNotBlank()) {
                DetailRow(
                    icon = Icons.Outlined.Info,
                    label = stringResource(R.string.label_substitution),
                    value = lesson.substitutionText,
                )
            }
            if (lesson.info.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                InfoNotice(text = lesson.info)
            }
        }
    }
}

/** An exam (Prüfung) with its time and whatever the school wrote about it. */
@Composable
private fun ExamCard(exam: UntisExam) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = examContainer(),
        contentColor = examContent(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.exam_badge),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${formatTime(exam.start)} – ${formatTime(exam.end)}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (exam.name.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(text = exam.name, style = MaterialTheme.typography.bodyLarge)
            }
            // The server often repeats the name as the text, so only show a different text.
            if (exam.text.isNotBlank() && exam.text != exam.name) {
                Spacer(Modifier.height(4.dp))
                Text(text = exam.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * The lesson's free-text information, shown as a notice rather than a plain detail row.
 * Long text is clamped and can be expanded.
 */
@Composable
private fun InfoNotice(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val clamp = text.length > INFO_CLAMP_LENGTH
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        // No animated resize: the sheet around it sizes itself to its content, and animating the
        // content made the sheet change height on every frame.
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.label_info),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (expanded || !clamp) Int.MAX_VALUE else INFO_CLAMP_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            if (clamp) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        stringResource(
                            if (expanded) R.string.action_show_less else R.string.action_show_more,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * A row of names (rooms, teachers or classes) as pills. A name that is in the master data opens that
 * timetable; one that is not stays plain text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameRow(
    icon: ImageVector,
    label: String,
    names: String,
    type: ElementType,
    findElement: (ElementType, String) -> UntisElement?,
    onOpen: (UntisElement) -> Unit,
) {
    val parts = remember(names) { names.split(',', ';').map { it.trim() }.filter { it.isNotBlank() } }
    if (parts.isEmpty()) {
        DetailRow(icon = icon, label = label, value = "–")
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                parts.forEach { name ->
                    val element = findElement(type, name)
                    Surface(
                        onClick = { element?.let(onOpen) },
                        enabled = element != null,
                        shape = CircleShape,
                        color = if (element != null) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (element != null) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (element != null) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

/** What a holiday block opens: its name and the period it covers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HolidaySheet(holiday: UntisHoliday, onDismiss: () -> Unit) {
    val locale = currentLocale(LocalContext.current)
    val name = if (holiday.hasDateName) stringResource(R.string.holiday_generic) else holiday.displayName
    val days = (java.time.temporal.ChronoUnit.DAYS.between(holiday.start, holiday.end) + 1).toInt()
    val period = remember(holiday, locale) {
        val full = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", locale)
        val short = DateTimeFormatter.ofPattern("d. MMMM yyyy", locale)
        if (holiday.start == holiday.end) {
            full.format(holiday.start)
        } else {
            short.format(holiday.start) + " – " + short.format(holiday.end)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = holidayFill(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 18.dp)) {
                    Text(text = name, style = MaterialTheme.typography.headlineSmallEmphasized)
                    Spacer(Modifier.height(6.dp))
                    Text(text = period, style = MaterialTheme.typography.titleSmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            DetailRow(
                icon = Icons.Outlined.CalendarMonth,
                label = stringResource(R.string.holiday_duration),
                value = pluralStringResource(R.plurals.holiday_days, days, days),
            )
        }
    }
}

private const val INFO_CLAMP_LENGTH = 180
private const val INFO_CLAMP_LINES = 4

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
