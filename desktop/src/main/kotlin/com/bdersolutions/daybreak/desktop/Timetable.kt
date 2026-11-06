package com.bdersolutions.daybreak.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdersolutions.daybreak.data.schedule.DayPhase
import com.bdersolutions.daybreak.data.schedule.DayStatus
import com.bdersolutions.daybreak.data.schedule.NowAndNext
import com.bdersolutions.daybreak.data.schedule.ShareLabels
import com.bdersolutions.daybreak.data.schedule.ShareText
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.data.settings.LessonColorSettings
import com.bdersolutions.daybreak.data.settings.StartView
import com.bdersolutions.daybreak.ui.components.OkLch
import com.bdersolutions.daybreak.ui.components.oklchToSrgb
import com.bdersolutions.daybreak.ui.components.srgbToOkLch
import com.bdersolutions.daybreak.ui.components.subjectHueDegrees
import com.bdersolutions.daybreak.untis.ElementType
import com.bdersolutions.daybreak.untis.UntisElement
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisHoliday
import com.bdersolutions.daybreak.untis.UntisLesson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** How far the day and week pagers reach from today, as on the Android timetable. */
private const val DAY_PAGE_COUNT = 731
private const val DAY_START_OFFSET = 365
private const val WEEK_PAGE_COUNT = 105
private const val WEEK_START_OFFSET = 52

/** One lesson as the timetable draws it: its colours and labels worked out once. */
private class LessonLook(val stripe: Color?, val fill: Color, val accent: Color)

/**
 * The timetable, in the Android app's layout: a header with the actions, the Tag and Woche switch,
 * the day strip, then the day or the week as a pager. Days and weeks swipe, drag, or move with the
 * arrow buttons and keys, and the Heute button brings back today.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TimetableScreen(container: DesktopContainer, onOpenSearch: () -> Unit, onOpenUpcoming: () -> Unit) {
    val repository = container.timetable
    val cache by repository.cache.collectAsState()
    val status by repository.status.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val language = LocalLanguage.current
    val locale = localeOf(language)
    val today = remember { LocalDate.now() }
    val todayWeek = remember(today) { today.with(DayOfWeek.MONDAY) }
    val dayOrigin = remember(today) { today.minusDays(DAY_START_OFFSET.toLong()) }
    val weekOrigin = remember(today) { todayWeek.minusWeeks(WEEK_START_OFFSET.toLong()) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    var week by remember { mutableStateOf(settings.startView == StartView.WEEK) }
    val dayPager = rememberPagerState(initialPage = DAY_START_OFFSET, pageCount = { DAY_PAGE_COUNT })
    val weekPager = rememberPagerState(initialPage = WEEK_START_OFFSET, pageCount = { WEEK_PAGE_COUNT })
    var target by remember { mutableStateOf<UntisElement?>(null) }
    var elementWeeks by remember(target) { mutableStateOf<Map<LocalDate, List<UntisLesson>>>(emptyMap()) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var showChooser by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<UntisLesson?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    // The date and week the pager shows. The day pager moves by days, the week pager by weeks.
    val selectedDate = if (week) weekOrigin.plusWeeks(weekPager.currentPage.toLong()) else dayOrigin.plusDays(dayPager.currentPage.toLong())
    val activeWeek = repository.weekStartFor(selectedDate)
    val isOnToday = if (week) activeWeek == todayWeek else selectedDate == today
    // The calendar week of the page, counted like the Android app does (locale rules).
    val weekNumber = selectedDate.get(WeekFields.of(locale).weekOfWeekBasedYear())

    // Own timetable: the shared repository caches each week. The visible week first, then its neighbours.
    LaunchedEffect(activeWeek, reloadKey) {
        if (target != null) return@LaunchedEffect
        // Flipping through weeks (holding an arrow key) must not start a request for every week it
        // passes: wait a moment, and only for weeks that are not cached yet. The next change of the
        // week cancels this effect, which is normal and never shown as an error.
        if (repository.cache.value.weeks[activeWeek] == null) delay(250)
        try {
            error = repository.ensureWeek(activeWeek, force = reloadKey > 0).error?.let {
                it.userMessage(language, trFor(language, "Stundenplan konnte nicht geladen werden.", "Could not load the timetable."))
            }
            repository.ensureWeek(activeWeek.plusWeeks(1))
            repository.ensureWeek(activeWeek.minusWeeks(1))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.userMessage(language, trFor(language, "Stundenplan konnte nicht geladen werden.", "Could not load the timetable."))
        }
    }

    // A class, teacher or room: loaded per week on demand, not cached by the shared repository.
    LaunchedEffect(target, activeWeek, reloadKey) {
        val element = target ?: return@LaunchedEffect
        for (weekStart in listOf(activeWeek, activeWeek.plusWeeks(1), activeWeek.minusWeeks(1))) {
            if (elementWeeks.containsKey(weekStart) && reloadKey == 0) continue
            try {
                val lessons = container.schoolData.elementWeek(element, weekStart).filter { !it.isHoliday }
                elementWeeks = elementWeeks + (weekStart to lessons)
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (weekStart == activeWeek) {
                    error = e.userMessage(language, trFor(language, "Stundenplan konnte nicht geladen werden.", "Could not load the timetable."))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Keyboard arrows need focus on the page. Not fatal if the node is not ready yet.
        runCatching { focus.requestFocus() }
        while (isActive) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }

    // Every lesson the page can show, by date. A class, teacher or room shows its own weeks instead.
    val lessonsByDate: Map<LocalDate, List<UntisLesson>> = remember(cache, elementWeeks, target) {
        if (target == null) {
            cache.weeks.values.flatMap { it.lessons }.filter { !it.isHoliday }.groupBy { it.date }
        } else {
            elementWeeks.values.flatten().groupBy { it.date }
        }
    }
    val exams = if (target == null) cache.exams else emptyList()
    val dayStatus: DayStatus = remember(lessonsByDate, now) {
        NowAndNext.compute(lessonsByDate[today].orEmpty(), now)
    }
    val paid by container.supportStore.paid.collectAsState()
    val title = appTitle(paid, settings.customAppName)
    val targetName = target?.displayName ?: session(container)
    val subtitle = trFor(language, "KW $weekNumber · $targetName", "CW $weekNumber · $targetName")

    // Arrow keys move through days or weeks, unless a dialog is open.
    fun move(delta: Int) {
        if (showChooser || opened != null) return
        scope.launch {
            if (week) {
                weekPager.animateScrollToPage((weekPager.targetPage + delta).coerceIn(0, WEEK_PAGE_COUNT - 1))
            } else {
                dayPager.animateScrollToPage((dayPager.targetPage + delta).coerceIn(0, DAY_PAGE_COUNT - 1))
            }
        }
    }
    fun goToToday() {
        scope.launch {
            if (week) weekPager.animateScrollToPage(WEEK_START_OFFSET) else dayPager.animateScrollToPage(DAY_START_OFFSET)
        }
    }

    // What is shared: the selected day, or the visible week without the empty weekend.
    fun sharedDates(): List<LocalDate> = if (week) {
        (0..6).map { activeWeek.plusDays(it.toLong()) }
            .filter { date -> date.dayOfWeek.value <= 5 || lessonsByDate[date].orEmpty().isNotEmpty() }
    } else {
        listOf(selectedDate)
    }
    fun copyText() {
        copyToClipboard(shareText(sharedDates(), lessonsByDate.values.flatten(), language))
        notice = trFor(language, "In die Zwischenablage kopiert.", "Copied to the clipboard.")
    }
    fun saveImage() {
        val labels = ShareLabels(
            noLessons = AppStrings.text("share_no_lessons", language),
            cancelled = AppStrings.text("share_cancelled", language),
            room = { room -> AppStrings.format("share_room", language, room) },
            dateHeading = { date -> date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", localeOf(language))) },
        )
        val days = sharedDates().map { date -> ShareText.day(date, lessonsByDate.values.flatten(), labels) }
        val image = renderTimetableImage(title = targetName, appTitle = title, days = days, labels = labels)
        val saved = saveTimetableImage(image, "Stundenplan-${selectedDate}.png")
        notice = if (saved) trFor(language, "Bild gespeichert.", "Picture saved.") else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { move(-1); true }
                    Key.DirectionRight -> { move(1); true }
                    Key.Home -> { goToToday(); true }
                    else -> false
                }
            },
    ) {
        PageHeader(
            title = title,
            subtitle = subtitle,
            actions = {
                HeaderIconButton(SearchIcon, tr("Suche", "Search"), onOpenSearch)
                ShareMenu(
                    onCopyText = { copyText() },
                    onSaveImage = { saveImage() },
                )
                HeaderIconButton(UpcomingIcon, tr("Hausaufgaben & Prüfungen", "Homework & exams"), onOpenUpcoming)
                HeaderIconButton(PickerIcon, tr("Stundenplan wählen", "Choose a timetable")) { showChooser = true }
            },
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (status.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            // Tag and Woche, and the Heute button when the pager is away from today.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModePill(tr("Tag", "Day"), selected = !week, modifier = Modifier.widthIn(min = 120.dp, max = 180.dp).weight(1f, fill = false)) { week = false }
                ModePill(tr("Woche", "Week"), selected = week, modifier = Modifier.widthIn(min = 120.dp, max = 180.dp).weight(1f, fill = false)) { week = true }
                Spacer(Modifier.weight(1f))
                // Previous and next day or week, for people who do not use the arrow keys.
                StepButton(
                    forward = false,
                    description = if (week) tr("Vorherige Woche", "Previous week") else tr("Vorheriger Tag", "Previous day"),
                    onClick = { move(-1) },
                )
                StepButton(
                    forward = true,
                    description = if (week) tr("Nächste Woche", "Next week") else tr("Nächster Tag", "Next day"),
                    onClick = { move(1) },
                )
                AnimatedVisibility(
                    visible = !isOnToday,
                    enter = fadeIn(DesktopMotion.effects()) + scaleIn(DesktopMotion.spatial(), initialScale = 0.8f),
                    exit = fadeOut(DesktopMotion.fastEffects()) + scaleOut(DesktopMotion.fastSpatial(), targetScale = 0.8f),
                ) {
                    FilledTonalButton(onClick = { goToToday() }, modifier = Modifier.handCursor()) {
                        Icon(Icons.Outlined.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Heute", "Today"))
                    }
                }
            }

            DayStrip(
                weekStart = activeWeek,
                selected = if (week) null else selectedDate,
                today = today,
                language = language,
                onSelect = { day ->
                    week = false
                    scope.launch {
                        dayPager.animateScrollToPage((DAY_START_OFFSET + ChronoUnit.DAYS.between(today, day).toInt()).coerceIn(0, DAY_PAGE_COUNT - 1))
                    }
                },
            )

            // Day and week slide sideways when switched, like the Android app.
            AnimatedContent(
                targetState = week,
                modifier = Modifier.fillMaxWidth().weight(1f),
                transitionSpec = {
                    val direction = if (targetState) 1 else -1
                    (
                        slideInHorizontally(DesktopMotion.spatial<IntOffset>()) { width -> direction * width / 6 } +
                            fadeIn(DesktopMotion.effects<Float>())
                        ).togetherWith(
                        slideOutHorizontally(DesktopMotion.spatial<IntOffset>()) { width -> -direction * width / 6 } +
                            fadeOut(DesktopMotion.fastEffects<Float>()),
                    ).using(SizeTransform(clip = false))
                },
                label = "timetableMode",
            ) { isWeek ->
                val pagerState = if (isWeek) weekPager else dayPager
                Box(
                    modifier = Modifier.fillMaxSize().onPointerEvent(PointerEventType.Scroll) { event ->
                        // Shift and the wheel turn the page, as in a browser's horizontal scrolling.
                        val change = event.changes.firstOrNull() ?: return@onPointerEvent
                        val dy = change.scrollDelta.y
                        if (event.keyboardModifiers.isShiftPressed && dy != 0f && !pagerState.isScrollInProgress) {
                            move(if (dy > 0) 1 else -1)
                            change.consume()
                        }
                    },
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 24.dp,
                        beyondViewportPageCount = 1,
                    ) { page ->
                        if (isWeek) {
                            val weekStart = weekOrigin.plusWeeks(page.toLong())
                            WeekPage(
                                weekStart = weekStart,
                                today = today,
                                lessonsByDate = lessonsByDate,
                                exams = exams,
                                holidays = cache.holidays,
                                colors = LocalLessonColorSettings.current,
                                onOpen = { opened = it },
                            )
                        } else {
                            val date = dayOrigin.plusDays(page.toLong())
                            DayPage(
                                date = date,
                                today = today,
                                lessons = lessonsByDate[date].orEmpty().sortedBy { it.start },
                                exams = exams,
                                holiday = cache.holidays.firstOrNull { date >= it.start && date <= it.end },
                                status = dayStatus,
                                showNowNext = settings.showNowNext,
                                colors = LocalLessonColorSettings.current,
                                primary = MaterialTheme.colorScheme.primary,
                                onOpen = { opened = it },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showChooser) {
        ElementChooser(
            container = container,
            current = target,
            // The chooser closes itself (with its exit animation) and then calls onDismiss.
            onPick = { element ->
                target = element
                error = null
                if (element == null) reloadKey++
            },
            onDismiss = { showChooser = false },
        )
    }

    opened?.let { lesson ->
        DesktopDialog(onDismiss = { opened = null }) { close ->
            LessonSheet(
                container = container,
                lesson = lesson,
                exam = examFor(lesson, exams),
                colors = LocalLessonColorSettings.current,
                language = language,
                onOpenElement = { element ->
                    target = element
                    close()
                },
                onClose = close,
            )
        }
    }
}

/** A round arrow button that moves the day or week pager one step. */
@Composable
private fun StepButton(forward: Boolean, description: String, onClick: () -> Unit) {
    androidx.compose.material3.FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(44.dp).handCursor()) {
        Icon(
            if (forward) Icons.AutoMirrored.Outlined.ArrowForward else Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = description,
        )
    }
}

private fun session(container: DesktopContainer): String =
    container.sessionStore.timetableSession()?.studentName?.ifBlank { null } ?: ""

@Composable
private fun ModePill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = DesktopMotion.effects(),
        label = "modePillColor",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = DesktopMotion.effects(),
        label = "modePillContent",
    )
    Surface(
        modifier = modifier.height(48.dp).clickable(onClick = onClick).handCursor(),
        shape = PillShape,
        color = container,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = content)
        }
    }
}

/** Mo to So with the date, today marked by a dot and the chosen day by a circle. */
@Composable
private fun DayStrip(
    weekStart: LocalDate,
    selected: LocalDate?,
    today: LocalDate,
    language: AppLanguage,
    onSelect: (LocalDate) -> Unit,
) {
    val short = DateTimeFormatter.ofPattern("EEE", localeOf(language))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (0L..6L).map { weekStart.plusDays(it) }.forEach { day ->
            val isSelected = day == selected
            val circle by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = DesktopMotion.effects(),
                label = "dayCircle",
            )
            val bar by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = DesktopMotion.effects(),
                label = "dayBar",
            )
            Column(
                modifier = Modifier.weight(1f).clickable { onSelect(day) }.handCursor(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        day.format(short),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (day == today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (day == today) {
                        Spacer(Modifier.width(4.dp))
                        Box(modifier = Modifier.size(6.dp).background(LocalStatusColors.current.exam, CircleShape))
                    }
                }
                Box(
                    modifier = Modifier.size(44.dp).background(circle, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box(modifier = Modifier.width(28.dp).height(4.dp).background(bar, PillShape))
            }
        }
    }
}

/** One day page: now and next (today only), then the lessons of the day. */
@Composable
private fun DayPage(
    date: LocalDate,
    today: LocalDate,
    lessons: List<UntisLesson>,
    exams: List<UntisExam>,
    holiday: UntisHoliday?,
    status: DayStatus,
    showNowNext: Boolean,
    colors: LessonColorSettings,
    primary: Color,
    onOpen: (UntisLesson) -> Unit,
) {
    val language = LocalLanguage.current
    val scroll = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 880.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (date == today && showNowNext) NowNextPill(status, language)
                DayList(
                    lessons = lessons,
                    exams = exams,
                    holiday = holiday,
                    colors = colors,
                    primary = primary,
                    onOpen = onOpen,
                )
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scroll),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

/** Now and next, as two columns in one pill (JETZT / DANACH). */
@Composable
private fun NowNextPill(status: DayStatus, language: AppLanguage) {
    val onColor = MaterialTheme.colorScheme.onSecondaryContainer
    val now = when (status.phase) {
        DayPhase.NO_LESSONS -> trFor(language, "Kein Unterricht", "No lessons")
        DayPhase.BEFORE_SCHOOL -> trFor(language, "Vor dem Unterricht", "Before school")
        DayPhase.IN_LESSON -> status.current?.lesson?.let { it.subjectLong.ifBlank { it.subject } } ?: ""
        DayPhase.BREAK -> trFor(language, "Pause", "Break")
        DayPhase.AFTER_SCHOOL -> trFor(language, "Für heute geschafft", "Done for today")
    }
    val next = status.next?.let { it.lesson.subjectLong.ifBlank { it.lesson.subject } }
        ?: trFor(language, "Keine weitere Stunde", "No more lessons")
    Surface(modifier = Modifier.fillMaxWidth(), shape = PillShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(trFor(language, "JETZT", "NOW"), style = MaterialTheme.typography.labelMedium, color = onColor.copy(alpha = 0.7f))
                Text(now, style = MaterialTheme.typography.titleLarge, color = onColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(trFor(language, "DANACH", "NEXT"), style = MaterialTheme.typography.labelMedium, color = onColor.copy(alpha = 0.7f))
                Text(next, style = MaterialTheme.typography.titleLarge, color = onColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The lessons of one day: number and time on the left, the block on the right. */
@Composable
private fun DayList(
    lessons: List<UntisLesson>,
    exams: List<UntisExam>,
    holiday: UntisHoliday?,
    colors: LessonColorSettings,
    primary: Color,
    onOpen: (UntisLesson) -> Unit,
) {
    val language = LocalLanguage.current
    if (holiday != null) {
        Text(holiday.longName.ifBlank { holiday.name }, color = LocalStatusColors.current.holiday, style = MaterialTheme.typography.titleMedium)
    }
    if (lessons.isEmpty()) {
        if (holiday == null) {
            Text(tr("Heute kein Unterricht.", "No lessons today."), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    val freeGaps = freeGaps(lessons)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lessons.forEachIndexed { index, lesson ->
            freeGaps.firstOrNull { it.first == index }?.let { FreeGapCard(language) }
            LessonRow(
                number = index + 1,
                lesson = lesson,
                exam = examFor(lesson, exams),
                colors = colors,
                primary = primary,
                onOpen = { onOpen(lesson) },
            )
        }
        exams.filter { exam -> exam.date == lessons.first().date && lessons.none { examFor(it, listOf(exam)) != null } }
            .forEach { exam ->
                Text(
                    "${tr("Prüfung", "Exam")} ${exam.start.format(timeFmt)} ${exam.subject.ifBlank { exam.name }}",
                    color = LocalStatusColors.current.exam,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
    }
}

/** A gap of 30 minutes or more between lessons, shown as "Frei" like the Android app. */
private fun freeGaps(lessons: List<UntisLesson>): List<Pair<Int, Long>> {
    val gaps = mutableListOf<Pair<Int, Long>>()
    for (index in 1 until lessons.size) {
        val minutes = ChronoUnit.MINUTES.between(lessons[index - 1].end, lessons[index].start)
        if (minutes >= 30) gaps += index to minutes
    }
    return gaps
}

@Composable
private fun FreeGapCard(language: AppLanguage) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 72.dp),
        shape = BlockShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
            Text(trFor(language, "Frei", "Free"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LessonRow(
    number: Int,
    lesson: UntisLesson,
    exam: UntisExam?,
    colors: LessonColorSettings,
    primary: Color,
    onOpen: () -> Unit,
) {
    val look = lessonLook(lesson, exam, colors)
    val hovered = hoverState()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(64.dp), horizontalAlignment = Alignment.End) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = primary)
            Text(lesson.start.format(timeFmt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            modifier = Modifier.weight(1f).hoverable(hovered.source).clickable(interactionSource = hovered.source, indication = null, onClick = onOpen).handCursor(),
            shape = BlockShape,
            color = hoverFill(look.fill, hovered.hovered),
            border = when {
                exam != null -> BorderStroke(4.5.dp, LocalStatusColors.current.exam)
                else -> null
            },
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(look.stripe ?: Color.Transparent))
                Column(modifier = Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            lesson.subjectLong.ifBlank { lesson.subject },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        StatusChip(lesson, exam)
                    }
                    val details = listOfNotNull(
                        lesson.room.takeIf { it.isNotBlank() }?.let { trFor(LocalLanguage.current, "Raum $it", "Room $it") },
                        lesson.teacher.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    if (details.isNotBlank()) {
                        Text(details, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** The status label of a lesson: Entfällt, Geändert or Prüfung, in one line, never broken. */
@Composable
private fun StatusChip(lesson: UntisLesson, exam: UntisExam?) {
    val language = LocalLanguage.current
    val status = LocalStatusColors.current
    val label: String
    val background: Color
    val text: Color
    when {
        lesson.isCancelled -> {
            label = trFor(language, "Entfällt", "Cancelled")
            background = status.cancelled.copy(alpha = 0.22f)
            text = status.cancelled
        }
        exam != null -> {
            label = trFor(language, "Prüfung", "Exam")
            background = status.exam
            text = Color(0xFF1B1B1F)
        }
        lesson.isIrregular || lesson.hasSubstitution -> {
            label = trFor(language, "Geändert", "Changed")
            background = status.warning.copy(alpha = 0.22f)
            text = status.warning
        }
        else -> return
    }
    Surface(shape = PillShape, color = background, modifier = Modifier.padding(start = 8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = text,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** One week page: a column per day (Saturday and Sunday only when they have lessons). */
@Composable
private fun WeekPage(
    weekStart: LocalDate,
    today: LocalDate,
    lessonsByDate: Map<LocalDate, List<UntisLesson>>,
    exams: List<UntisExam>,
    holidays: List<UntisHoliday>,
    colors: LessonColorSettings,
    onOpen: (UntisLesson) -> Unit,
) {
    val language = LocalLanguage.current
    val scroll = rememberScrollState()
    val days = (0L..6L).map { weekStart.plusDays(it) }.filter { day ->
        day.dayOfWeek.value <= 5 || lessonsByDate[day].orEmpty().isNotEmpty() || day == today
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            days.forEach { day ->
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isToday = day == today
                    Surface(
                        shape = PillShape,
                        color = if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            day.format(DateTimeFormatter.ofPattern("EE d.", localeOf(language))),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                    }
                    val holiday = holidays.firstOrNull { day >= it.start && day <= it.end }
                    holiday?.let {
                        Text(
                            it.longName.ifBlank { it.name },
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalStatusColors.current.holiday,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    lessonsByDate[day].orEmpty().sortedBy { it.start }.forEach { lesson ->
                        WeekBlock(lesson, examFor(lesson, exams), colors, onOpen = { onOpen(lesson) })
                    }
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scroll),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun WeekBlock(lesson: UntisLesson, exam: UntisExam?, colors: LessonColorSettings, onOpen: () -> Unit) {
    val look = lessonLook(lesson, exam, colors)
    val hovered = hoverState()
    Surface(
        modifier = Modifier.fillMaxWidth().hoverable(hovered.source).clickable(interactionSource = hovered.source, indication = null, onClick = onOpen).handCursor(),
        shape = BlockShape,
        color = hoverFill(look.fill, hovered.hovered),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(look.stripe ?: Color.Transparent))
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                Text(lesson.start.format(timeFmt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    lesson.subject.ifBlank { lesson.subjectLong },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    lesson.room,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lesson.isCancelled || exam != null || lesson.isIrregular || lesson.hasSubstitution) {
                    StatusChip(lesson, exam)
                }
            }
        }
    }
}

/**
 * The lesson details, as the Android app's sheet: a tinted header with the subject and date, the
 * substitution and exam text, and the room, teacher and class. A name that the directory knows
 * opens that class, teacher or room timetable.
 */
@Composable
private fun LessonSheet(
    container: DesktopContainer,
    lesson: UntisLesson,
    exam: UntisExam?,
    colors: LessonColorSettings,
    language: AppLanguage,
    onOpenElement: (UntisElement) -> Unit,
    onClose: () -> Unit,
) {
    val look = lessonLook(lesson, exam, colors)
    val date = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", localeOf(language))
    var directory by remember { mutableStateOf<com.bdersolutions.daybreak.untis.UntisDirectory?>(null) }
    LaunchedEffect(lesson) {
        directory = try {
            container.schoolData.loadDirectory()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    fun find(type: ElementType, name: String): UntisElement? = directory?.let { master ->
        when (type) {
            ElementType.CLASS -> master.classes
            ElementType.TEACHER -> master.teachers
            ElementType.ROOM -> master.rooms
        }.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = CardShape, color = look.fill) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        lesson.subjectLong.ifBlank { lesson.subject },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(lesson, exam)
                }
                Text(
                    "${lesson.date.format(date)} · ${lesson.start.format(timeFmt)} – ${lesson.end.format(timeFmt)}",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        if (lesson.substitutionText.isNotBlank()) {
            Text(lesson.substitutionText, style = MaterialTheme.typography.bodyLarge)
        }
        exam?.let {
            Text("${tr("Prüfung", "Exam")}: ${it.name.ifBlank { it.subject }}", style = MaterialTheme.typography.titleMedium)
        }
        SheetRow(Icons.Outlined.LocationOn, tr("Raum", "Room"), lesson.room, find(ElementType.ROOM, lesson.room), onOpenElement)
        SheetRow(Icons.Outlined.Person, tr("Lehrkraft", "Teacher"), lesson.teacher, find(ElementType.TEACHER, lesson.teacher), onOpenElement)
        SheetRow(Icons.Outlined.CalendarMonth, tr("Klasse", "Class"), lesson.className, find(ElementType.CLASS, lesson.className), onOpenElement)
        OutlinedButton(onClick = onClose, modifier = Modifier.handCursor()) { Text(tr("Schließen", "Close")) }
    }
}

/**
 * One detail row: a label and the value as a pill. When the directory knows the name, the pill opens
 * that class, teacher or room timetable.
 */
@Composable
private fun SheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    element: UntisElement?,
    onOpen: (UntisElement) -> Unit,
) {
    if (value.isBlank()) return
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = if (element != null) Modifier.clickable { onOpen(element) }.handCursor() else Modifier,
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * The colours of a lesson, as the Android app works them out: a custom status colour first, then
 * the subject's colour. The stripe takes the status; the fill is the surface tinted with it.
 */
@Composable
private fun lessonLook(lesson: UntisLesson, exam: UntisExam?, colors: LessonColorSettings): LessonLook {
    val scheme = MaterialTheme.colorScheme
    val statuses = LocalStatusColors.current
    val dark = statuses.dark
    val surface = scheme.surfaceContainerLow
    val accent = subjectAccent(lesson.subject, colors, scheme.primary, dark)
    val stripe = when {
        lesson.isCancelled -> colors.cancelledColor?.let { Color(it.toInt()) } ?: statuses.cancelled
        exam != null -> colors.examColor?.let { Color(it.toInt()) } ?: statuses.exam
        lesson.isIrregular || lesson.hasSubstitution -> colors.substitutionColor?.let { Color(it.toInt()) } ?: statuses.warning
        else -> accent
    }
    val fill = when {
        colors.fillWholeCard -> lerp(surface, stripe, 0.24f)
        exam != null -> lerp(surface, stripe, 0.4f)
        else -> lerp(surface, stripe, if (dark) 0.2f else 0.14f)
    }
    return LessonLook(stripe = if (colors.fillWholeCard) null else stripe, fill = fill, accent = stripe)
}

/**
 * The colour of a subject: the user's own colour when one is set, otherwise a hue from the subject's
 * name on the primary's hue family. Shared by the timetable and the absence categories.
 */
fun subjectAccent(subject: String, colors: LessonColorSettings, primary: Color, dark: Boolean): Color {
    colors.subjectColors[subject]?.let { return Color(it.toInt()) }
    val primaryHue = srgbToOkLch(primary.red.toDouble(), primary.green.toDouble(), primary.blue.toDouble()).hueDegrees
    val rgb = oklchToSrgb(
        OkLch(
            lightness = if (dark) 0.8 else 0.56,
            chroma = if (dark) 0.12 else 0.14,
            hueDegrees = subjectHueDegrees(subject, primaryHue),
        ),
    )
    return Color(rgb[0].toFloat(), rgb[1].toFloat(), rgb[2].toFloat())
}

/** The lesson that falls on [lesson]: same day, overlapping time, same subject when the exam names one. */
fun examFor(lesson: UntisLesson, exams: List<UntisExam>): UntisExam? {
    if (exams.isEmpty() || lesson.isHoliday) return null
    return exams.firstOrNull { exam ->
        exam.date == lesson.date &&
            exam.start < lesson.end &&
            exam.end > lesson.start &&
            (exam.subject.isBlank() || exam.subject.equals(lesson.subject, ignoreCase = true))
    }
}

private fun shareText(days: List<LocalDate>, lessons: List<UntisLesson>, language: AppLanguage): String {
    val labels = ShareLabels(
        noLessons = trFor(language, "Kein Unterricht", "No lessons"),
        cancelled = trFor(language, "entfällt", "cancelled"),
        room = { room -> trFor(language, "Raum $room", "Room $room") },
        dateHeading = { date -> date.format(DateTimeFormatter.ofPattern("EEEE d MMM", localeOf(language))) },
    )
    return ShareText.format(days.map { ShareText.day(it, lessons, labels) }, labels)
}

/** The share button of the header: copy the shown days as text, or save them as a picture. */
@Composable
private fun ShareMenu(onCopyText: () -> Unit, onSaveImage: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        HeaderIconButton(ShareIcon, tr("Teilen", "Share")) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(tr("Als Text kopieren", "Copy as text")) },
                onClick = {
                    open = false
                    onCopyText()
                },
            )
            DropdownMenuItem(
                text = { Text(tr("Als Bild speichern …", "Save as picture …")) },
                onClick = {
                    open = false
                    onSaveImage()
                },
            )
        }
    }
}

/** An icon button in the page header, drawn in the variant colour as on Android. */
@Composable
fun HeaderIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.handCursor()) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The hover state of a block: a source for [hoverable] and whether the pointer is over it now. */
private class HoverState(val source: MutableInteractionSource, val hovered: Boolean)

@Composable
private fun hoverState(): HoverState {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    return HoverState(source, hovered)
}

/** A block lifts a little under the pointer, so it reads as clickable with a mouse. */
@Composable
private fun hoverFill(fill: Color, hovered: Boolean): Color {
    val target = if (hovered) lerp(fill, MaterialTheme.colorScheme.onSurface, 0.06f) else fill
    val animated by animateColorAsState(targetValue = target, animationSpec = DesktopMotion.fastEffects(), label = "blockHover")
    return animated
}

internal val SearchIcon: androidx.compose.ui.graphics.vector.ImageVector get() = Icons.Outlined.Search
internal val ShareIcon: androidx.compose.ui.graphics.vector.ImageVector get() = Icons.Outlined.Share
internal val UpcomingIcon: androidx.compose.ui.graphics.vector.ImageVector get() = Icons.Outlined.Checklist
internal val PickerIcon: androidx.compose.ui.graphics.vector.ImageVector get() = Icons.Outlined.SwapHoriz
