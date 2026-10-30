package com.bdersolutions.daybreak.ui.timetable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Room
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.cache.TimetableCache
import com.bdersolutions.daybreak.data.repository.TimetableSyncStatus
import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisLesson
import com.bdersolutions.daybreak.ui.components.AccentBadge
import com.bdersolutions.daybreak.ui.components.rememberSubjectTone
import com.bdersolutions.daybreak.ui.components.warningAccent
import com.bdersolutions.daybreak.ui.components.warningContainer
import com.bdersolutions.daybreak.ui.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
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
    modifier: Modifier = Modifier,
) {
    val cache by viewModel.cache.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val currentSession = session ?: return
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { error ->
            snackbarHostState.showSnackbar(error.toUserMessage(context))
        }
    }

    key(mode) {
        val today = remember { LocalDate.now() }
        val dayOrigin = remember { today.minusDays(DAY_START_OFFSET.toLong()) }
        val weekOrigin = remember {
            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(WEEK_START_OFFSET.toLong())
        }
        val pagerState = rememberPagerState(
            initialPage = if (mode == 0) DAY_START_OFFSET else WEEK_START_OFFSET,
            pageCount = { if (mode == 0) DAY_PAGE_COUNT else WEEK_PAGE_COUNT },
        )
        val scope = rememberCoroutineScope()
        val now = rememberNow()
        val selectedDate = if (mode == 0) {
            dayOrigin.plusDays(pagerState.currentPage.toLong())
        } else {
            weekOrigin.plusWeeks(pagerState.currentPage.toLong())
        }

        LaunchedEffect(pagerState.currentPage, mode) {
            viewModel.ensureWeek(selectedDate)
        }

        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            maxLines = 1,
                        )
                    },
                    subtitle = {
                        Text(
                            text = currentSession.studentName.ifBlank {
                                currentSession.displayName
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        RefreshAction(
                            loading = status.isLoading,
                            onClick = { viewModel.refresh(selectedDate) },
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                if (mode == 0) DAY_START_OFFSET else WEEK_START_OFFSET,
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Outlined.Today, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_today)) },
                )
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
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                ) { page ->
                    val pageDate = when {
                        mode == 0 -> dayOrigin.plusDays(page.toLong())
                        else -> weekOrigin.plusWeeks(page.toLong())
                    }
                    if (mode == 0) {
                        DayPage(
                            date = pageDate,
                            today = today,
                            now = now,
                            cache = cache,
                            status = status,
                            onRetry = { viewModel.refresh(pageDate) },
                        )
                    } else {
                        WeekPage(
                            weekStart = pageDate,
                            today = today,
                            now = now,
                            cache = cache,
                            status = status,
                            onRetry = { viewModel.refresh(pageDate) },
                        )
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewModeSwitcher(
    mode: Int,
    onModeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ButtonGroup(
        modifier = modifier.fillMaxWidth(),
    ) {
        ToggleButton(
            checked = mode == 0,
            onCheckedChange = { if (it) onModeChange(0) },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) {
            Text(stringResource(R.string.timetable_day))
        }
        ToggleButton(
            checked = mode == 1,
            onCheckedChange = { if (it) onModeChange(1) },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) {
            Text(stringResource(R.string.timetable_week))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RefreshAction(
    loading: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier.size(48.dp),
    ) {
        if (loading) {
            LoadingIndicator(modifier = Modifier.size(24.dp))
        } else {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.action_refresh),
            )
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
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = cache.weeks[weekStart]
    val lessons = remember(week, date) {
        week?.lessons?.filter { it.date == date }?.sortedBy { it.start }.orEmpty()
    }
    val isToday = date == today
    var selectedLesson by remember { mutableStateOf<UntisLesson?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            week == null && status.isLoading -> LoadingPane()
            week == null && status.lastError != null -> ErrorPane(
                message = status.lastError!!.toUserMessage(context),
                onRetry = onRetry,
            )
            else -> {
                val blocks = remember(lessons) { layoutTimeline(mergeLessons(lessons)) }
                val range = remember(lessons) { timelineRange(lessons) }
                val periodStarts = remember(week) {
                    periodStartsFor(week?.lessons.orEmpty())
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    DayHeaderLine(
                        date = date,
                        isToday = isToday,
                        lessonCount = lessons.size,
                    )
                    if (status.lastError != null) {
                        OfflineBanner(
                            status = status,
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (lessons.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
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
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 112.dp),
                        ) {
                            DayTimeline(
                                blocks = blocks,
                                range = range,
                                periodStarts = periodStarts,
                                isToday = isToday,
                                now = now,
                                onLessonClick = { selectedLesson = it },
                            )
                        }
                    }
                }
            }
        }
        selectedLesson?.let { lesson ->
            // The detail sheet itself is implemented in LessonDetailsSheet.
            LessonDetailsSheet(
                lesson = lesson,
                onDismiss = { selectedLesson = null },
            )
        }
    }
}

@Composable
private fun DayHeaderLine(
    date: LocalDate,
    isToday: Boolean,
    lessonCount: Int,
) {
    val locale = currentLocale(LocalContext.current)
    val label = remember(date, locale) {
        date.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "·",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = androidx.compose.ui.res.pluralStringResource(
                R.plurals.timetable_lesson_count,
                lessonCount,
                lessonCount,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (isToday) {
            Spacer(Modifier.width(8.dp))
            AccentBadge(
                text = stringResource(R.string.today),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun DayTimeline(
    blocks: List<TimelineBlock>,
    range: TimelineRange,
    periodStarts: List<LocalTime>,
    isToday: Boolean,
    now: LocalDateTime,
    onLessonClick: (UntisLesson) -> Unit,
) {
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
                blocks = blocks,
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
            blocks.forEach { block ->
                val top = pxPerMinute * minutesBetween(range.start, block.start)
                val blockHeight = max(
                    34f,
                    pxPerMinute.value * minutesBetween(block.start, block.end) - 3f,
                ).dp
                val columnWidth = maxWidth / block.columnCount
                val current = block == currentBlock
                DayLessonBlock(
                    block = block,
                    current = current,
                    progress = if (current) {
                        block.progressAt(nowTime)
                    } else {
                        0f
                    },
                    onClick = { onLessonClick(block.lesson) },
                    modifier = Modifier
                        .width(columnWidth - 4.dp)
                        .height(blockHeight)
                        .offset(
                            x = columnWidth * block.column + 2.dp,
                            y = top,
                        ),
                )
            }
            if (isToday && nowTime >= range.start && nowTime < range.end) {
                val y = pxPerMinute * minutesBetween(range.start, nowTime)
                Row(
                    modifier = Modifier
                        .offset(y = y - 9.dp)
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
                            text = formatTime(nowTime),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
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
        Text(
            text = formatTime(range.end),
            modifier = Modifier.align(Alignment.BottomEnd),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayLessonBlock(
    block: TimelineBlock,
    current: Boolean,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lesson = block.lesson
    val subject = lesson.subject.ifBlank {
        stringResource(R.string.lesson_unknown_subject)
    }
    val tone = rememberSubjectTone(subject)
    val cancelled = block.isCancelled
    val border = when {
        current -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        cancelled -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
        block.hasSubstitution -> BorderStroke(1.5.dp, warningAccent())
        else -> null
    }
    val badge = when {
        cancelled -> stringResource(R.string.lesson_cancelled)
        block.hasRoomChange -> stringResource(R.string.lesson_room_short)
        block.hasSubstitution -> stringResource(R.string.lesson_substitution_short)
        else -> null
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(if (block.lessons.size > 1) 18.dp else 14.dp),
        color = tone.container,
        contentColor = tone.onContainer,
        border = border,
        tonalElevation = if (current) 3.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = subject,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textDecoration = if (cancelled) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Spacer(Modifier.width(4.dp))
                    AccentBadge(
                        text = badge,
                        containerColor = if (cancelled) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            warningContainer()
                        },
                        contentColor = if (cancelled) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            warningAccent()
                        },
                    )
                }
            }
            if (block.durationMinutes >= 35) {
                val details = buildList {
                    if (lesson.room.isNotBlank()) {
                        add(context.getString(R.string.room_value, lesson.room))
                    }
                    if (block.durationMinutes >= 75 && lesson.teacher.isNotBlank()) {
                        add(lesson.teacher)
                    }
                }.joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.labelSmall,
                        color = tone.onContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (current) {
                Spacer(Modifier.weight(1f))
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                )
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
) {
    val context = LocalContext.current
    val week = cache.weeks[weekStart]
    val allLessons = remember(week) { week?.lessons.orEmpty() }
    val days = remember(weekStart) { (0L..4L).map { weekStart.plusDays(it) } }
    val range = remember(allLessons) { timelineRange(allLessons) }
    val periodStarts = remember(allLessons) {
        periodStartsFor(allLessons)
    }
    val longEventsByDay = remember(allLessons, days) {
        days.associateWith { day ->
            allLessons.filter { it.date == day && it.isLongEvent() }
        }
    }
    val gridLessonsByDay = remember(allLessons, days) {
        days.associateWith { day ->
            allLessons.filter { it.date == day && !it.isLongEvent() }
        }
    }
    val pxPerMinute = 1.05.dp
    val contentHeight = (pxPerMinute.value * range.minutes).dp
    var selectedLesson by remember { mutableStateOf<UntisLesson?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            week == null && status.isLoading -> LoadingPane()
            week == null && status.lastError != null -> ErrorPane(
                message = status.lastError!!.toUserMessage(context),
                onRetry = onRetry,
            )
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    WeekHeaderLine(
                        weekStart = weekStart,
                        lessonCount = allLessons.size,
                    )
                    if (status.lastError != null) {
                        OfflineBanner(
                            status = status,
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (allLessons.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            EmptyMessage(
                                title = stringResource(R.string.timetable_empty_week_title),
                                body = stringResource(R.string.timetable_empty_body),
                            )
                        }
                    } else {
                        WeekDayHeaderRow(
                            weekStart = weekStart,
                            today = today,
                        )
                        if (longEventsByDay.values.any { it.isNotEmpty() }) {
                            WeekAllDayRow(
                                days = days,
                                longEventsByDay = longEventsByDay,
                                onLessonClick = { selectedLesson = it },
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
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
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(contentHeight),
                                ) {
                                    days.forEach { day ->
                                        WeekDayColumn(
                                            date = day,
                                            today = today,
                                            now = now,
                                            lessons = gridLessonsByDay[day].orEmpty(),
                                            range = range,
                                            periodStarts = periodStarts,
                                            pxPerMinute = pxPerMinute,
                                            onLessonClick = { selectedLesson = it },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        selectedLesson?.let { lesson ->
            LessonDetailsSheet(
                lesson = lesson,
                onDismiss = { selectedLesson = null },
            )
        }
    }
}

@Composable
private fun WeekHeaderLine(
    weekStart: LocalDate,
    lessonCount: Int,
) {
    val locale = currentLocale(LocalContext.current)
    val weekNumber = remember(weekStart, locale) {
        weekStart.get(java.time.temporal.WeekFields.of(locale).weekOfWeekBasedYear())
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.timetable_calendar_week, weekNumber),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "·",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = androidx.compose.ui.res.pluralStringResource(
                R.plurals.timetable_lesson_count,
                lessonCount,
                lessonCount,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
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
private fun WeekAllDayRow(
    days: List<LocalDate>,
    longEventsByDay: Map<LocalDate, List<UntisLesson>>,
    onLessonClick: (UntisLesson) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 14.dp, bottom = 4.dp),
    ) {
        Spacer(Modifier.width(52.dp))
        Spacer(Modifier.width(8.dp))
        Row(modifier = Modifier.weight(1f)) {
            days.forEach { day ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    longEventsByDay[day].orEmpty().take(2).forEach { lesson ->
                        val subject = lesson.subject.ifBlank {
                            stringResource(R.string.lesson_unknown_subject)
                        }
                        val tone = rememberSubjectTone(subject)
                        Surface(
                            onClick = { onLessonClick(lesson) },
                            shape = RoundedCornerShape(8.dp),
                            color = tone.container,
                            contentColor = tone.onContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = subject,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
    onLessonClick: (UntisLesson) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isToday = date == today
    val blocks = remember(lessons) { layoutTimeline(mergeLessons(lessons)) }
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
            blocks.forEach { block ->
                val top = pxPerMinute * minutesBetween(range.start, block.start)
                val blockHeight = max(
                    24f,
                    pxPerMinute.value * minutesBetween(block.start, block.end) - 2f,
                ).dp
                val columnWidth = maxWidth / block.columnCount
                val current = block == currentBlock
                WeekLessonBlock(
                    block = block,
                    current = current,
                    onClick = { onLessonClick(block.lesson) },
                    modifier = Modifier
                        .width(maxOf(columnWidth - 2.dp, 14.dp))
                        .height(blockHeight)
                        .offset(
                            x = columnWidth * block.column + 1.dp,
                            y = top,
                        ),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun WeekLessonBlock(
    block: TimelineBlock,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lesson = block.lesson
    val subject = lesson.subject.ifBlank {
        stringResource(R.string.lesson_unknown_subject)
    }
    val tone = rememberSubjectTone(subject)
    val cancelled = block.isCancelled
    val accent = when {
        cancelled -> MaterialTheme.colorScheme.error
        block.hasRoomChange || block.hasSubstitution -> warningAccent()
        else -> null
    }
    val border = when {
        current -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        cancelled -> BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        block.hasRoomChange || block.hasSubstitution -> BorderStroke(1.dp, warningAccent())
        else -> null
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = tone.container,
        contentColor = tone.onContainer,
        border = border,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 3.dp),
        ) {
            Column {
                Text(
                    text = subject,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
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
                    val detail = lesson.room.ifBlank { lesson.teacher }
                    if (detail.isNotBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                lineHeight = 10.sp,
                            ),
                            color = tone.onContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (accent != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(5.dp)
                        .background(accent, CircleShape),
                )
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
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
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingPane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
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
        )
        Spacer(Modifier.height(14.dp))
        androidx.compose.material3.Button(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
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

private data class TimelineBlock(
    val lessons: List<UntisLesson>,
    val start: LocalTime,
    val end: LocalTime,
    val column: Int = 0,
    val columnCount: Int = 1,
) {
    val lesson: UntisLesson get() = lessons.first()
    val isCancelled: Boolean get() = lessons.any { it.isCancelled }
    val hasRoomChange: Boolean get() = lessons.any { it.hasRoomChange }
    val hasSubstitution: Boolean get() = lessons.any { it.hasSubstitution }
    val durationMinutes: Int get() = minutesBetween(start, end)

    fun progressAt(time: LocalTime): Float {
        val total = Duration.between(start, end).seconds.toFloat()
        if (total <= 0f) return 0f
        return (Duration.between(start, time).seconds.toFloat() / total).coerceIn(0f, 1f)
    }
}

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
 * Lays overlapping blocks out in side-by-side columns. Each cluster is processed
 * independently so an all-morning event can sit next to the single lessons that
 * run underneath it.
 */
private fun layoutTimeline(blocks: List<TimelineBlock>): List<TimelineBlock> {
    if (blocks.size <= 1) return blocks
    val sorted = blocks.sortedWith(compareBy({ it.start }, { it.end }))
    val result = mutableListOf<TimelineBlock>()
    var cluster = mutableListOf<TimelineBlock>()
    var clusterEnd = LocalTime.MIN

    fun flush() {
        if (cluster.isEmpty()) return
        val columnEnds = mutableListOf<LocalTime>()
        val placed = cluster.map { block ->
            val freeColumn = columnEnds.indexOfFirst { !it.isAfter(block.start) }
            val column = if (freeColumn == -1) {
                columnEnds += block.end
                columnEnds.lastIndex
            } else {
                columnEnds[freeColumn] = block.end
                freeColumn
            }
            block.copy(column = column)
        }
        val count = columnEnds.size.coerceAtLeast(1)
        result += placed.map { it.copy(columnCount = count) }
        cluster = mutableListOf()
        clusterEnd = LocalTime.MIN
    }

    for (block in sorted) {
        if (cluster.isNotEmpty() && !block.start.isBefore(clusterEnd)) {
            flush()
        }
        cluster += block
        if (block.end > clusterEnd) clusterEnd = block.end
    }
    flush()
    return result.sortedWith(compareBy({ it.start }, { it.column }))
}

private fun freeGaps(blocks: List<TimelineBlock>, range: TimelineRange): List<FreeGap> {
    var cursor = range.start
    val gaps = mutableListOf<FreeGap>()
    for (block in blocks.sortedBy { it.start }) {
        if (block.start > cursor) {
            val end = minOf(block.start, range.end)
            if (minutesBetween(cursor, end) >= 10) gaps += FreeGap(cursor, end)
        }
        if (block.end > cursor) cursor = block.end
    }
    if (cursor < range.end && minutesBetween(cursor, range.end) >= 10) {
        gaps += FreeGap(cursor, range.end)
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

private fun UntisLesson.isLongEvent(): Boolean =
    Duration.between(start, end).toMinutes() >= 180

private fun formatTime(time: LocalTime): String =
    time.format(DateTimeFormatter.ofPattern("HH:mm"))

private fun currentLocale(context: android.content.Context): Locale =
    context.resources.configuration.locales[0] ?: Locale.GERMAN

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonDetailsSheet(
    lesson: UntisLesson,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
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
    val statusContainer = when {
        cancelled -> MaterialTheme.colorScheme.errorContainer
        warning -> warningContainer()
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val statusContent = when {
        cancelled -> MaterialTheme.colorScheme.onErrorContainer
        warning -> warningAccent()
        else -> MaterialTheme.colorScheme.onSurfaceVariant
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = tone.container,
                contentColor = tone.onContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = subject,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            textDecoration = if (cancelled) {
                                TextDecoration.LineThrough
                            } else {
                                TextDecoration.None
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        AccentBadge(
                            text = statusText,
                            containerColor = statusContainer,
                            contentColor = statusContent,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "$dateLabel · ${formatTime(lesson.start)} – ${formatTime(lesson.end)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = tone.onContainer.copy(alpha = 0.82f),
                    )
                }
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
            DetailRow(
                icon = Icons.Outlined.Room,
                label = stringResource(R.string.label_room),
                value = lesson.room.ifBlank { "–" },
            )
            DetailRow(
                icon = Icons.Outlined.Person,
                label = stringResource(R.string.label_teacher),
                value = lesson.teacher.ifBlank { "–" },
            )
            if (lesson.hasTeacherChange) {
                DetailRow(
                    icon = Icons.Outlined.Person,
                    label = stringResource(R.string.label_previous_teacher),
                    value = lesson.originalTeacher,
                )
            }
            if (lesson.className.isNotBlank()) {
                DetailRow(
                    icon = Icons.Outlined.CalendarMonth,
                    label = stringResource(R.string.label_class),
                    value = lesson.className,
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
                DetailRow(
                    icon = Icons.Outlined.Info,
                    label = stringResource(R.string.label_info),
                    value = lesson.info,
                )
            }

            Spacer(Modifier.height(18.dp))
            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape,
            ) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

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
