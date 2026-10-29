package com.bdersolutions.daybreak.ui.timetable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Room
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.cache.CachedWeek
import com.bdersolutions.daybreak.data.cache.TimetableCache
import com.bdersolutions.daybreak.data.repository.TimetableSyncStatus
import com.bdersolutions.daybreak.data.session.DaybreakSession
import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisLesson
import com.bdersolutions.daybreak.ui.toUserMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private const val DAY_PAGE_COUNT = 731
private const val DAY_START_OFFSET = 365
private const val WEEK_PAGE_COUNT = 105
private const val WEEK_START_OFFSET = 52

@OptIn(ExperimentalMaterial3Api::class)
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
    var mode by remember { mutableIntStateOf(0) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { error ->
            snackbarHostState.showSnackbar(error.toUserMessage(context))
        }
    }

    key(mode) {
        val today = remember { LocalDate.now() }
        val locale = remember { currentLocale(context) }
        val dayOrigin = remember { today.minusDays(DAY_START_OFFSET.toLong()) }
        val weekOrigin = remember {
            today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(WEEK_START_OFFSET.toLong())
        }
        val pagerState = rememberPagerState(
            initialPage = if (mode == 0) DAY_START_OFFSET else WEEK_START_OFFSET,
            pageCount = { if (mode == 0) DAY_PAGE_COUNT else WEEK_PAGE_COUNT },
        )
        val scope = rememberCoroutineScope()
        val selectedDate = if (mode == 0) {
            dayOrigin.plusDays(pagerState.currentPage.toLong())
        } else {
            weekOrigin.plusWeeks(pagerState.currentPage.toLong())
        }
        val now = rememberNow()

        LaunchedEffect(pagerState.currentPage, mode) {
            viewModel.ensureWeek(
                if (mode == 0) dayOrigin.plusDays(pagerState.currentPage.toLong()) else selectedDate,
            )
        }

        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    title = {
                        Column {
                            Text(
                                text = context.getString(R.string.timetable_title),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = if (mode == 0) {
                                    formatDaySubtitle(selectedDate, today, context, locale)
                                } else {
                                    formatWeekSubtitle(selectedDate, locale, context)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        if (mode == 0) DAY_START_OFFSET else WEEK_START_OFFSET,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Today,
                                contentDescription = context.getString(R.string.action_today),
                            )
                        }
                        IconButton(
                            onClick = { viewModel.refresh(selectedDate) },
                            enabled = !status.isLoading,
                        ) {
                            if (status.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = context.getString(R.string.action_refresh),
                                )
                            }
                        }
                    },
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                if (status.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
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
                            session = currentSession,
                            onRetry = { viewModel.refresh(pageDate) },
                        )
                    } else {
                        WeekPage(
                            weekStart = pageDate,
                            today = today,
                            now = now,
                            cache = cache,
                            status = status,
                            session = currentSession,
                            onRetry = { viewModel.refresh(pageDate) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeSwitcher(
    mode: Int,
    onModeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        SegmentedButton(
            selected = mode == 0,
            onClick = { onModeChange(0) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text(context.getString(R.string.timetable_day)) },
        )
        SegmentedButton(
            selected = mode == 1,
            onClick = { onModeChange(1) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text(context.getString(R.string.timetable_week)) },
        )
    }
}

@Composable
private fun DayPage(
    date: LocalDate,
    today: LocalDate,
    now: LocalDateTime,
    cache: TimetableCache,
    status: TimetableSyncStatus,
    session: DaybreakSession,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    var selectedLesson by remember { mutableStateOf<UntisLesson?>(null) }
    val weekStart = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = cache.weeks[weekStart]
    val lessons = week?.lessons?.filter { it.date == date }.orEmpty().sortedBy { it.start }
    val isToday = date == today

    Box(modifier = Modifier.fillMaxSize()) {
        if (week == null && status.isLoading) {
            LoadingPane()
        } else if (week == null && status.lastError != null) {
            ErrorPane(
                message = status.lastError!!.toUserMessage(context),
                onRetry = onRetry,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    DayHeader(
                        date = date,
                        isToday = isToday,
                        lessonCount = lessons.size,
                        session = session,
                        status = status,
                    )
                }
                item {
                    OfflineBanner(status)
                }
                if (lessons.isEmpty()) {
                    item { EmptyDay(isToday = isToday) }
                } else {
                    items(lessons, key = { "${it.id}-${it.date}-${it.start}" }) { lesson ->
                        val current = isToday &&
                            !now.toLocalTime().isBefore(lesson.start) &&
                            now.toLocalTime().isBefore(lesson.end)
                        LessonCard(
                            lesson = lesson,
                            current = current,
                            past = isToday && now.toLocalTime().isAfter(lesson.end),
                            onClick = {
                                selectedLesson = lesson
                            },
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = context.getString(R.string.timetable_swipe_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        selectedLesson?.let { lesson ->
            LessonDetailsSheet(lesson = lesson, onDismiss = { selectedLesson = null })
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
    session: DaybreakSession,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    var selectedLesson by remember { mutableStateOf<UntisLesson?>(null) }
    val week = cache.weeks[weekStart]
    val allLessons = week?.lessons.orEmpty()
    val visibleDays = remember(weekStart, allLessons) {
        val weekdays = (0L..4L).map { weekStart.plusDays(it) }
        val weekend = (5L..6L).map { weekStart.plusDays(it) }
            .filter { day -> allLessons.any { it.date == day } }
        weekdays + weekend
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (week == null && status.isLoading) {
            LoadingPane()
        } else if (week == null && status.lastError != null) {
            ErrorPane(
                message = status.lastError!!.toUserMessage(context),
                onRetry = onRetry,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    WeekHeader(weekStart = weekStart, session = session, status = status)
                }
                item { OfflineBanner(status) }
                if (allLessons.isEmpty()) {
                    item { EmptyWeek() }
                } else {
                    items(visibleDays, key = { it.toString() }) { day ->
                        val lessons = allLessons.filter { it.date == day }.sortedBy { it.start }
                        DaySection(
                            date = day,
                            isToday = day == today,
                            lessons = lessons,
                            now = now,
                            onLessonClick = { selectedLesson = it },
                        )
                    }
                }
            }
        }
        selectedLesson?.let { lesson ->
            LessonDetailsSheet(lesson = lesson, onDismiss = { selectedLesson = null })
        }
    }
}

@Composable
private fun DayHeader(
    date: LocalDate,
    isToday: Boolean,
    lessonCount: Int,
    session: DaybreakSession,
    status: TimetableSyncStatus,
) {
    val context = LocalContext.current
    val locale = currentLocale(context)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("EEEE", locale))
                            .replaceFirstChar { it.titlecase(locale) },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("d. MMMM yyyy", locale)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isToday) {
                    StatusBadge(
                        text = context.getString(R.string.today),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = session.studentName.ifBlank { session.displayName },
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = context.resources.getQuantityString(
                        R.plurals.timetable_lesson_count,
                        lessonCount,
                        lessonCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SyncCaption(status)
        }
    }
}

@Composable
private fun WeekHeader(
    weekStart: LocalDate,
    session: DaybreakSession,
    status: TimetableSyncStatus,
) {
    val context = LocalContext.current
    val locale = currentLocale(context)
    val weekEnd = weekStart.plusDays(4)
    val weekNumber = weekStart.get(WeekFields.of(locale).weekOfWeekBasedYear())
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = context.getString(R.string.timetable_calendar_week, weekNumber),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "${weekStart.format(DateTimeFormatter.ofPattern("d. MMM", locale))} – " +
                    weekEnd.format(DateTimeFormatter.ofPattern("d. MMM yyyy", locale)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = session.studentName.ifBlank { session.displayName },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            SyncCaption(status)
        }
    }
}

@Composable
private fun SyncCaption(status: TimetableSyncStatus) {
    val context = LocalContext.current
    if (status.lastSyncAt > 0L) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = context.getString(
                R.string.timetable_last_sync,
                java.time.Instant.ofEpochMilli(status.lastSyncAt)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalTime()
                    .format(DateTimeFormatter.ofPattern("HH:mm")),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OfflineBanner(status: TimetableSyncStatus) {
    val context = LocalContext.current
    val error = status.lastError ?: return
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth(),
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
                text = context.getString(R.string.timetable_offline, error.toUserMessage(context)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun DaySection(
    date: LocalDate,
    isToday: Boolean,
    lessons: List<UntisLesson>,
    now: LocalDateTime,
    onLessonClick: (UntisLesson) -> Unit,
) {
    val context = LocalContext.current
    val locale = currentLocale(context)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        ) {
            Text(
                text = date.format(DateTimeFormatter.ofPattern("EEEE", locale))
                    .replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.titleMedium,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = date.format(DateTimeFormatter.ofPattern("d. MMM", locale)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isToday) {
                Spacer(Modifier.width(8.dp))
                StatusBadge(
                    text = context.getString(R.string.today),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        }
        if (lessons.isEmpty()) {
            Text(
                text = context.getString(R.string.timetable_no_lessons),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            lessons.forEach { lesson ->
                val current = isToday &&
                    !now.toLocalTime().isBefore(lesson.start) &&
                    now.toLocalTime().isBefore(lesson.end)
                CompactLessonRow(
                    lesson = lesson,
                    current = current,
                    onClick = { onLessonClick(lesson) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun LessonCard(
    lesson: UntisLesson,
    current: Boolean,
    past: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val container = when {
        lesson.isCancelled -> colors.errorContainer.copy(alpha = 0.55f)
        current -> colors.primaryContainer
        else -> colors.surfaceContainer
    }
    val border = if (current) BorderStroke(2.dp, colors.primary) else null
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = if (lesson.isCancelled) colors.onErrorContainer else colors.onSurface,
        ),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = if (current) 2.dp else 0.dp),
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(54.dp),
            ) {
                Text(
                    text = formatTime(lesson.start),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatTime(lesson.end),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(28.dp)
                        .background(
                            color = when {
                                lesson.isCancelled -> colors.error
                                current -> colors.primary
                                lesson.hasSubstitution -> colors.tertiary
                                else -> colors.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lesson.subject.ifBlank { context.getString(R.string.lesson_unknown_subject) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (lesson.isCancelled) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (current) {
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(
                            text = context.getString(R.string.now),
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (lesson.teacher.isNotBlank() || lesson.room.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildList {
                            if (lesson.teacher.isNotBlank()) add(lesson.teacher)
                            if (lesson.room.isNotBlank()) add(context.getString(R.string.room_value, lesson.room))
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val badges = buildList {
                    if (lesson.isCancelled) add(context.getString(R.string.lesson_cancelled))
                    if (lesson.hasRoomChange) add(context.getString(R.string.lesson_room_changed))
                    if (lesson.hasTeacherChange) add(context.getString(R.string.lesson_teacher_changed))
                    if (lesson.isIrregular && !lesson.hasSubstitution) add(context.getString(R.string.lesson_substitution))
                    if (lesson.substitutionText.isNotBlank() && !lesson.isCancelled) {
                        add(lesson.substitutionText)
                    }
                }
                if (badges.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        badges.take(3).forEach { badge ->
                            StatusBadge(
                                text = badge,
                                contentColor = if (lesson.isCancelled) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                },
                                containerColor = if (lesson.isCancelled) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                },
                            )
                        }
                    }
                }
                if (lesson.hasRoomChange) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = context.getString(
                            R.string.lesson_room_change_detail,
                            lesson.originalRoom,
                            lesson.room,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (past && !lesson.isCancelled) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = context.getString(R.string.lesson_past),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactLessonRow(
    lesson: UntisLesson,
    current: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = when {
            lesson.isCancelled -> colors.errorContainer.copy(alpha = 0.45f)
            current -> colors.primaryContainer
            else -> colors.surfaceContainerLow
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatTime(lesson.start),
                style = MaterialTheme.typography.labelLarge,
                color = if (current) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.width(48.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lesson.subject.ifBlank { context.getString(R.string.lesson_unknown_subject) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (lesson.isCancelled) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildList {
                        if (lesson.room.isNotBlank()) add(context.getString(R.string.room_value, lesson.room))
                        if (lesson.teacher.isNotBlank()) add(lesson.teacher)
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (current) {
                StatusBadge(
                    text = context.getString(R.string.now),
                    contentColor = colors.onPrimary,
                    containerColor = colors.primary,
                )
            } else if (lesson.isCancelled) {
                StatusBadge(
                    text = context.getString(R.string.lesson_cancelled),
                    contentColor = colors.onErrorContainer,
                    containerColor = colors.errorContainer,
                )
            } else if (lesson.hasSubstitution) {
                StatusBadge(
                    text = context.getString(R.string.lesson_substitution),
                    contentColor = colors.onTertiaryContainer,
                    containerColor = colors.tertiaryContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonDetailsSheet(
    lesson: UntisLesson,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val locale = currentLocale(context)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lesson.subject.ifBlank { context.getString(R.string.lesson_unknown_subject) },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = lesson.subjectLong.takeIf { it.isNotBlank() && it != lesson.subject }.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(
                    text = when (lesson.status) {
                        LessonStatus.CANCELLED -> context.getString(R.string.lesson_cancelled)
                        LessonStatus.IRREGULAR -> context.getString(R.string.lesson_substitution)
                        LessonStatus.REGULAR -> if (lesson.hasSubstitution) {
                            context.getString(R.string.lesson_changed)
                        } else {
                            context.getString(R.string.lesson_regular)
                        }
                    },
                    contentColor = if (lesson.isCancelled) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                    containerColor = if (lesson.isCancelled) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${lesson.date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", locale))} · " +
                    "${formatTime(lesson.start)} – ${formatTime(lesson.end)}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (lesson.hasRoomChange) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Room, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = context.getString(
                                R.string.lesson_room_change_detail,
                                lesson.originalRoom,
                                lesson.room,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            DetailRow(
                icon = { Icon(Icons.Outlined.Room, null, modifier = Modifier.size(18.dp)) },
                label = context.getString(R.string.label_room),
                value = lesson.room.ifBlank { "–" },
            )
            DetailRow(
                icon = { Icon(Icons.Outlined.Person, null, modifier = Modifier.size(18.dp)) },
                label = context.getString(R.string.label_teacher),
                value = lesson.teacher.ifBlank { "–" },
            )
            if (lesson.originalTeacher.isNotBlank() && lesson.hasTeacherChange) {
                DetailRow(
                    icon = { Icon(Icons.Outlined.Person, null, modifier = Modifier.size(18.dp)) },
                    label = context.getString(R.string.label_previous_teacher),
                    value = lesson.originalTeacher,
                )
            }
            if (lesson.className.isNotBlank()) {
                DetailRow(
                    icon = { Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(18.dp)) },
                    label = context.getString(R.string.label_class),
                    value = lesson.className,
                )
            }
            if (lesson.group.isNotBlank()) {
                DetailRow(
                    icon = { Icon(Icons.Outlined.Info, null, modifier = Modifier.size(18.dp)) },
                    label = context.getString(R.string.label_group),
                    value = lesson.group,
                )
            }
            if (lesson.lessonText.isNotBlank()) {
                DetailRow(
                    icon = { Icon(Icons.Outlined.Info, null, modifier = Modifier.size(18.dp)) },
                    label = context.getString(R.string.label_topic),
                    value = lesson.lessonText,
                )
            }
            if (lesson.substitutionText.isNotBlank()) {
                DetailRow(
                    icon = { Icon(Icons.Outlined.Info, null, modifier = Modifier.size(18.dp)) },
                    label = context.getString(R.string.label_substitution),
                    value = lesson.substitutionText,
                )
            }
            if (lesson.info.isNotBlank()) {
                DetailRow(
                    icon = { Icon(Icons.Outlined.Info, null, modifier = Modifier.size(18.dp)) },
                    label = context.getString(R.string.label_info),
                    value = lesson.info,
                )
            }
            Spacer(Modifier.height(20.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(context.getString(R.string.action_close))
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    contentColor: Color,
    containerColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyDay(isToday: Boolean) {
    val context = LocalContext.current
    EmptyMessage(
        title = context.getString(if (isToday) R.string.timetable_empty_today_title else R.string.timetable_empty_title),
        body = context.getString(R.string.timetable_empty_body),
    )
}

@Composable
private fun EmptyWeek() {
    val context = LocalContext.current
    EmptyMessage(
        title = context.getString(R.string.timetable_empty_week_title),
        body = context.getString(R.string.timetable_empty_body),
    )
}

@Composable
private fun EmptyMessage(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingPane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit) {
    val context = LocalContext.current
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
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = context.getString(R.string.timetable_load_error),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(context.getString(R.string.action_retry))
        }
    }
}

private fun formatTime(time: LocalTime): String =
    time.format(DateTimeFormatter.ofPattern("HH:mm"))

private fun currentLocale(context: android.content.Context): Locale =
    context.resources.configuration.locales[0] ?: Locale.GERMAN

private fun formatDaySubtitle(
    date: LocalDate,
    today: LocalDate,
    context: android.content.Context,
    locale: Locale,
): String {
    val prefix = when (date) {
        today -> context.getString(R.string.today)
        today.plusDays(1) -> context.getString(R.string.tomorrow)
        today.minusDays(1) -> context.getString(R.string.yesterday)
        else -> null
    }
    val formatted = date.format(DateTimeFormatter.ofPattern("d. MMM", locale))
    return if (prefix != null) "$prefix · $formatted" else formatted
}

private fun formatWeekSubtitle(date: LocalDate, locale: Locale, context: android.content.Context): String {
    val weekStart = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekEnd = weekStart.plusDays(4)
    val number = weekStart.get(WeekFields.of(locale).weekOfWeekBasedYear())
    return context.getString(
        R.string.timetable_week_subtitle,
        number,
        weekStart.format(DateTimeFormatter.ofPattern("d. MMM", locale)),
        weekEnd.format(DateTimeFormatter.ofPattern("d. MMM", locale)),
    )
}

@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    return now
}
