package com.bdersolutions.daybreak.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.data.repository.LessonHistory
import com.bdersolutions.daybreak.data.settings.AbsenceUnit
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.data.settings.LessonColorSettings
import com.bdersolutions.daybreak.ui.absences.AbsenceCategory
import com.bdersolutions.daybreak.ui.absences.CategoryUsage
import com.bdersolutions.daybreak.ui.absences.absenceTotals
import com.bdersolutions.daybreak.ui.absences.countMissedLessons
import com.bdersolutions.daybreak.ui.absences.formatSchoolLessons
import com.bdersolutions.daybreak.ui.absences.missedLessonsByAbsence
import com.bdersolutions.daybreak.ui.absences.percentOf
import com.bdersolutions.daybreak.ui.absences.reorderedBy
import com.bdersolutions.daybreak.ui.absences.studentSubjects
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisLesson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/** The filters above the list, as on the Android page. */
private enum class AbsenceFilter {
    ALL, UNEXCUSED, EXCUSED;

    fun matches(absence: UntisAbsence): Boolean = when (this) {
        ALL -> true
        UNEXCUSED -> !absence.excused
        EXCUSED -> absence.excused
    }
}

/** What the category dialog shows: the list of categories, or the form for one category. */
private sealed interface CategorySheet {
    data object Manage : CategorySheet
    data class Form(val id: String?) : CategorySheet
}

/** 1 August of the school year that contains [today] (German school years start in summer). */
fun schoolYearStartFor(today: LocalDate): LocalDate =
    if (today.monthValue >= 8) LocalDate.of(today.year, 8, 1) else LocalDate.of(today.year - 1, 8, 1)

/**
 * The Fehlstunden page of the Android app: a summary card per category (and one for the total),
 * the filters with their values, then the absences of the selected card by day. A category counts
 * the lessons of its subject that an absence covers, so excused and unexcused are lessons there.
 */
@Composable
fun AbsencesScreen(container: DesktopContainer) {
    val language = LocalLanguage.current
    val today = remember { LocalDate.now() }
    val schoolYearStart = remember(today) { schoolYearStartFor(today) }
    val since = schoolYearStart.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY))
    val settings by container.settings.settings.collectAsState()
    val cache by container.timetable.cache.collectAsState()
    val categories = settings.absenceCategories
    val unit = settings.absenceUnit

    var absences by remember { mutableStateOf<List<UntisAbsence>?>(null) }
    var reasons by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var history by remember { mutableStateOf<LessonHistory?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var subjectsRequested by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(AbsenceFilter.ALL) }
    var sheet by remember { mutableStateOf<CategorySheet?>(null) }
    val pagerState = rememberPagerState(pageCount = { 1 + categories.size })

    // The lessons of the school year are only needed to count for a category, or to list the subjects
    // in the form. Without either, the page makes no extra requests.
    val needsHistory = categories.isNotEmpty() || subjectsRequested

    LaunchedEffect(reloadKey) {
        error = null
        try {
            reasons = container.schoolData.loadDirectory().absenceReasons
            absences = container.schoolData.absences(schoolYearStart, today).sortedByDescending { it.start }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: trFor(language, "Fehlstunden konnten nicht geladen werden.", "Could not load absences.")
        }
    }
    LaunchedEffect(reloadKey, needsHistory) {
        if (!needsHistory) return@LaunchedEffect
        history = try {
            container.absenceHistory.lessons(schoolYearStart, today)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fall back to the cached weeks; the counts may then be too low, which the card says.
            LessonHistory(
                lessons = container.timetable.cache.value.weeks.values.flatMap { it.lessons },
                failedWeeks = 1,
            )
        }
    }

    val loaded = absences
    val lessonSet = history
    val ready = loaded != null && lessonSet != null
    val allLessons = remember(lessonSet, cache) {
        lessonSet?.lessons.orEmpty() + cache.weeks.values.flatMap { it.lessons }
    }
    val subjects = remember(allLessons) { studentSubjects(allLessons) }
    val usage: Map<String, CategoryUsage> = remember(loaded, lessonSet, categories) {
        if (loaded == null || lessonSet == null) {
            emptyMap()
        } else {
            categories.associate { it.id to countMissedLessons(loaded, lessonSet.lessons, it.subject) }
        }
    }
    val lessonsByAbsence: Map<String, Map<Long, Int>> = remember(loaded, lessonSet, categories) {
        if (loaded == null || lessonSet == null) {
            emptyMap()
        } else {
            categories.associate { it.id to missedLessonsByAbsence(loaded, lessonSet.lessons, it.subject) }
        }
    }
    val missingSubject: Set<String> = remember(subjects, categories, ready) {
        if (!ready) {
            emptySet()
        } else {
            categories.filter { c -> subjects.none { it.equals(c.subject, ignoreCase = true) } }.map { it.id }.toSet()
        }
    }

    // The card the pager is heading for decides what the list shows: every absence on "Total", and
    // only the absences that missed a lesson of the subject on a category card.
    val selected = categories.getOrNull(pagerState.targetPage - 1)
    val selectedLessons: Map<Long, Int>? = selected?.let { lessonsByAbsence[it.id] }
    val base = remember(loaded, selected, selectedLessons) {
        when {
            loaded == null -> emptyList()
            selected == null -> loaded
            selectedLessons == null -> emptyList()
            else -> loaded.filter { (selectedLessons[it.id] ?: 0) > 0 }
        }
    }
    val shown = remember(base, filter) { base.filter { filter.matches(it) } }
    val groups = remember(shown) {
        shown.groupBy { it.start.toLocalDate() }.toSortedMap(Comparator.reverseOrder())
    }
    val totals = remember(base) { absenceTotals(base) }

    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = tr("Fehlstunden", "Absences"),
            subtitle = trFor(language, "Seit $since", "Since $since"),
            actions = {
                if (loaded != null && categories.isNotEmpty()) {
                    HeaderIconButton(Icons.Outlined.Tune, tr("Kategorien verwalten", "Manage categories")) {
                        sheet = CategorySheet.Manage
                    }
                }
                if (loaded != null) {
                    HeaderIconButton(Icons.Outlined.Add, tr("Kategorie hinzufügen", "Add category")) {
                        subjectsRequested = true
                        sheet = CategorySheet.Form(null)
                    }
                }
            },
        )

        val errorText = error
        when {
            errorText != null -> Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(errorText, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { reloadKey++ }) { Text(tr("Erneut versuchen", "Try again")) }
            }

            loaded == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= 880.dp
                val emptyText = when {
                    selected != null && selectedLessons == null -> trFor(language, "Fehlstunden werden gezählt …", "Counting absences …")
                    else -> trFor(language, "Keine Fehlstunden in diesem Zeitraum", "No absences in this period")
                }
                val summary: @Composable () -> Unit = {
                    SummaryPager(
                        absences = loaded,
                        categories = categories,
                        usage = usage,
                        loading = categories.isNotEmpty() && !ready,
                        missingSubject = missingSubject,
                        partial = (lessonSet?.failedWeeks ?: 0) > 0,
                        unit = unit,
                        language = language,
                        pagerState = pagerState,
                        onAdd = {
                            subjectsRequested = true
                            sheet = CategorySheet.Form(null)
                        },
                    )
                }
                val filters: @Composable () -> Unit = {
                    FilterRow(
                        filter = filter,
                        onFilter = { filter = it },
                        totals = totals,
                        base = base,
                        lessonsByAbsence = selectedLessons,
                        unit = unit,
                        language = language,
                    )
                }

                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.width(400.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            summary()
                            filters()
                        }
                        val listState = rememberLazyListState()
                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                absenceGroups(groups, reasons, selectedLessons, unit, shown.isEmpty(), emptyText)
                            }
                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(listState),
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            )
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item(key = "summary") { summary() }
                            item(key = "filters") { filters() }
                            absenceGroups(groups, reasons, selectedLessons, unit, shown.isEmpty(), emptyText)
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(listState),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }

    sheet?.let { current ->
        CategoryDialog(
            request = current,
            categories = categories,
            subjects = subjects,
            onRequest = { sheet = it },
            onSave = { id, name, subject, maxLessons, maxPercent ->
                saveCategory(container, id, name, subject, maxLessons, maxPercent)
            },
            onPrepareDelete = { id ->
                val page = categories.indexOfFirst { it.id == id } + 1
                if (page > 0 && pagerState.currentPage == page) pagerState.animateScrollToPage(page - 1)
            },
            onDelete = { id -> container.settings.updateAbsenceCategories { list -> list.filterNot { it.id == id } } },
            onReorder = { ids -> container.settings.updateAbsenceCategories { list -> list.reorderedBy(ids) } },
        )
    }
    // Keep the pager on the first card when there are no categories left.
    LaunchedEffect(categories.size) {
        if (pagerState.currentPage > categories.size) scope.launch { pagerState.animateScrollToPage(0) }
    }
}

/** Creates a category, or changes the one with [id]. Same rules as the Android app. */
private fun saveCategory(
    container: DesktopContainer,
    id: String?,
    name: String,
    subject: String,
    maxLessons: Int,
    maxPercent: Int,
) {
    val percent = maxPercent.takeIf { it in 1..100 } ?: 0
    if (subject.isBlank() || (percent == 0 && maxLessons <= 0)) return
    // A percent limit leaves the lesson count unused, and the other way round.
    val lessons = if (percent > 0) 0 else maxLessons
    val cleanName = name.trim().ifEmpty { subject.trim() }
    container.settings.updateAbsenceCategories { list ->
        if (id == null) {
            list + AbsenceCategory(UUID.randomUUID().toString(), cleanName, subject.trim(), lessons, percent)
        } else {
            list.map {
                if (it.id == id) {
                    it.copy(name = cleanName, subject = subject.trim(), maxLessons = lessons, maxPercent = percent)
                } else {
                    it
                }
            }
        }
    }
}

/** The summary cards: the total first, then one card per category, swiped or clicked through. */
@Composable
private fun SummaryPager(
    absences: List<UntisAbsence>,
    categories: List<AbsenceCategory>,
    usage: Map<String, CategoryUsage>,
    loading: Boolean,
    missingSubject: Set<String>,
    partial: Boolean,
    unit: AbsenceUnit,
    language: AppLanguage,
    pagerState: PagerState,
    onAdd: () -> Unit,
) {
    val pageCount = 1 + categories.size
    val scope = rememberCoroutineScope()

    // A new category becomes the visible page, so it is clear where it went.
    var knownCount by remember { mutableIntStateOf(pageCount) }
    LaunchedEffect(pageCount) {
        if (pageCount > knownCount) pagerState.animateScrollToPage(pageCount - 1)
        knownCount = pageCount
    }

    // All cards share the height of the tallest one, so the page does not jump between cards.
    val density = LocalDensity.current
    var cardHeightPx by remember(density.density, density.fontScale) { mutableIntStateOf(0) }
    val cardModifier = Modifier
        .heightIn(min = with(density) { cardHeightPx.toDp() })
        .onSizeChanged { size -> if (size.height > cardHeightPx) cardHeightPx = size.height }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ArrowButton(
                forward = false,
                enabled = pagerState.currentPage > 0,
                description = trFor(language, "Vorherige Karte", "Previous card"),
            ) { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                pageSpacing = 12.dp,
                beyondViewportPageCount = 1,
                key = { page -> if (page == 0) "total" else categories.getOrNull(page - 1)?.id ?: page },
            ) { page ->
                if (page == 0) {
                    AbsenceSummary(absences = absences, unit = unit, language = language, modifier = cardModifier)
                } else {
                    val category = categories.getOrNull(page - 1)
                    if (category != null) {
                        CategoryCard(
                            category = category,
                            usage = usage[category.id],
                            loading = loading,
                            missingSubject = category.id in missingSubject,
                            partial = partial,
                            language = language,
                            modifier = cardModifier,
                        )
                    }
                }
            }
            ArrowButton(
                forward = true,
                enabled = pagerState.currentPage < pageCount - 1,
                description = trFor(language, "Nächste Karte", "Next card"),
            ) { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }
        }
        if (pageCount > 1) {
            Spacer(Modifier.height(10.dp))
            PageDots(count = pageCount, current = pagerState.currentPage, onSelect = { page ->
                scope.launch { pagerState.animateScrollToPage(page) }
            })
        }
        if (categories.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().handCursor()) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(trFor(language, "Kategorie hinzufügen", "Add category"))
            }
        }
    }
}

@Composable
private fun ArrowButton(forward: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.handCursor()) {
        Icon(
            if (forward) Icons.AutoMirrored.Outlined.ArrowForward else Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = description,
        )
    }
}

/** Launcher-style page dots: the current page is the accent colour and slightly larger. */
@Composable
private fun PageDots(count: Int, current: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val selected = index == current
            val color by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                animationSpec = DesktopMotion.effects(),
                label = "pageDotColor",
            )
            val size by animateDpAsState(
                targetValue = if (selected) 9.dp else 7.dp,
                animationSpec = DesktopMotion.spatial(),
                label = "pageDotSize",
            )
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onSelect(index) }.handCursor(),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(size).background(color, CircleShape))
            }
        }
    }
}

/** The total card: total absence time, how much of it is excused, the two durations, the latest absence. */
@Composable
private fun AbsenceSummary(
    absences: List<UntisAbsence>,
    unit: AbsenceUnit,
    language: AppLanguage,
    modifier: Modifier = Modifier,
) {
    val totals = remember(absences) { absenceTotals(absences) }
    val total = totals.total
    val excusedTime = totals.excused
    val unexcusedTime = totals.unexcused
    // Time share, the same basis as the two durations below it. It used to count absences instead.
    val excusedShare = if (total.isZero) 0f else excusedTime.toMinutes().toFloat() / total.toMinutes().toFloat()
    val latest = absences.maxByOrNull { it.start }
    val locale = localeOf(language)
    val latestText = remember(latest, locale) {
        latest?.start?.format(DateTimeFormatter.ofPattern("EEE dd.MM. HH:mm", locale))
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        trFor(language, "Gesamt", "Total"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatDuration(total, unit, language),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    trFor(language, "${absences.size} Einträge", "${absences.size} entries"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            val animatedShare by animateFloatAsState(
                targetValue = excusedShare,
                animationSpec = DesktopMotion.spatial(),
                label = "excusedShare",
            )
            LinearProgressIndicator(
                progress = { animatedShare },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                trFor(language, "${(excusedShare * 100).roundToInt()}% entschuldigt", "${(excusedShare * 100).roundToInt()}% excused"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                DurationColumn(trFor(language, "Entschuldigt", "Excused"), formatDuration(excusedTime, unit, language), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                DurationColumn(trFor(language, "Unentschuldigt", "Unexcused"), formatDuration(unexcusedTime, unit, language), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
            if (latestText != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    trFor(language, "Zuletzt: $latestText", "Latest: $latestText"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DurationColumn(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

/**
 * One category card: the used amount against its limit (lessons, or percent of the held lessons),
 * how much is left, and the excused and unexcused counts.
 */
@Composable
private fun CategoryCard(
    category: AbsenceCategory,
    usage: CategoryUsage?,
    loading: Boolean,
    missingSubject: Boolean,
    partial: Boolean,
    language: AppLanguage,
    modifier: Modifier = Modifier,
) {
    val locale = localeOf(language)
    val percentMode = category.isPercent
    val used = usage?.total ?: 0
    val usedPercent = usage?.percent ?: 0.0
    // Both modes reduce to "how much of the limit is used", in lessons or in percent.
    val max = if (percentMode) category.maxPercent else category.maxLessons
    val amount = if (percentMode) usedPercent else used.toDouble()
    val atLimit = usage != null && amount >= max
    val accent = when {
        atLimit -> MaterialTheme.colorScheme.error
        usage != null && amount * 5 >= max * 4 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val progress by animateFloatAsState(
        targetValue = if (usage == null) 0f else (amount / max).toFloat().coerceIn(0f, 1f),
        animationSpec = DesktopMotion.spatial(),
        label = "categoryProgress",
    )
    val status = when {
        missingSubject -> trFor(language, "Dieses Fach kommt nicht mehr im Stundenplan vor.", "This subject is no longer in the timetable.")
        usage == null -> trFor(language, "Fehlstunden werden gezählt …", "Counting absences …")
        percentMode && amount - max >= 0.05 -> trFor(language, "${formatPercent(amount - max, locale)} % über dem Limit", "${formatPercent(amount - max, locale)} % over the limit")
        percentMode && amount >= max -> trFor(language, "Limit erreicht", "Limit reached")
        percentMode -> trFor(language, "Noch ${formatPercent(max - amount, locale)} % bis zum Limit", "${formatPercent(max - amount, locale)} % left until the limit")
        used > max -> trFor(language, "${used - max} Stunden über dem Limit", "${used - max} lessons over the limit")
        used == max -> trFor(language, "Limit erreicht", "Limit reached")
        else -> trFor(language, "Noch ${max - used} Stunden", "${max - used} lessons left")
    }
    val dash = "–"

    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedContent(
                            targetState = when {
                                usage == null -> dash
                                percentMode -> formatPercent(usedPercent, locale) + " %"
                                else -> used.toString()
                            },
                            transitionSpec = { rollTransition() },
                            label = "categoryValue",
                        ) { value ->
                            Text(
                                text = value,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = if (atLimit) accent else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = if (percentMode) " / $max %" else " / $max",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                }
                // A name that is just the subject needs no second label.
                if (!category.name.equals(category.subject, ignoreCase = true)) {
                    Spacer(Modifier.width(12.dp))
                    SubjectBadge(subject = category.subject, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { if (loading && usage == null) 0f else progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = accent,
                trackColor = accent.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(6.dp))
            Text(status, style = MaterialTheme.typography.labelMedium, color = if (atLimit) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            if (percentMode && usage != null && !missingSubject) {
                Text(
                    trFor(language, "${used} von ${usage.held} Stunden gefehlt", "$used of ${usage.held} lessons missed"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                DurationColumn(
                    trFor(language, "Entschuldigt", "Excused"),
                    usage?.excused?.toString() ?: dash,
                    MaterialTheme.colorScheme.onSurface,
                    Modifier.weight(1f),
                )
                DurationColumn(
                    trFor(language, "Unentschuldigt", "Unexcused"),
                    usage?.unexcused?.toString() ?: dash,
                    MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f),
                )
            }
            if (partial && usage != null && !missingSubject) {
                Spacer(Modifier.height(14.dp))
                Text(
                    trFor(language, "Einige Wochen konnten nicht geladen werden, die Zahl kann zu niedrig sein.", "Some weeks could not be loaded, so the count may be too low."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The number rolls up or down when it changes, instead of swapping. */
private fun rollTransition(): ContentTransform =
    (
        fadeIn(DesktopMotion.effects<Float>()) +
            slideInVertically(DesktopMotion.spatial<IntOffset>()) { it / 2 }
        ).togetherWith(
        fadeOut(DesktopMotion.fastEffects<Float>()) +
            slideOutVertically(DesktopMotion.fastSpatial<IntOffset>()) { -it / 2 },
    )

/** "7.5" or "10": one decimal at most, in the app's language. */
fun formatPercent(value: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }.format(value)

/** The subject in its timetable colour, so it reads the same here as in the timetable. */
@Composable
private fun SubjectBadge(subject: String, modifier: Modifier = Modifier) {
    val accent = subjectAccentFor(subject)
    Surface(shape = MaterialTheme.shapes.small, color = accent.copy(alpha = 0.2f), contentColor = accent, modifier = modifier) {
        Text(
            text = subject,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    filter: AbsenceFilter,
    onFilter: (AbsenceFilter) -> Unit,
    totals: com.bdersolutions.daybreak.ui.absences.AbsenceTotals,
    base: List<UntisAbsence>,
    lessonsByAbsence: Map<Long, Int>?,
    unit: AbsenceUnit,
    language: AppLanguage,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AbsenceFilter.entries.forEach { option ->
            val (name, time) = when (option) {
                AbsenceFilter.ALL -> trFor(language, "Alle", "All") to totals.total
                AbsenceFilter.UNEXCUSED -> trFor(language, "Unentschuldigt", "Unexcused") to totals.unexcused
                AbsenceFilter.EXCUSED -> trFor(language, "Entschuldigt", "Excused") to totals.excused
            }
            // On a category card the chips count lessons of the subject, not clock time.
            val value = if (lessonsByAbsence != null) {
                trFor(language, "${base.filter { option.matches(it) }.sumOf { lessonsByAbsence[it.id] ?: 0 }} Std.", "${base.filter { option.matches(it) }.sumOf { lessonsByAbsence[it.id] ?: 0 }} lessons")
            } else {
                formatDuration(time, unit, language)
            }
            FilterChip(
                selected = filter == option,
                onClick = { onFilter(option) },
                modifier = Modifier.handCursor(),
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                        AnimatedContent(
                            targetState = value,
                            transitionSpec = { rollTransition() },
                            label = "chipValue",
                        ) { current ->
                            // Only the unexcused value carries the accent colour.
                            Text(
                                text = current,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (option == AbsenceFilter.UNEXCUSED) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                            )
                        }
                    }
                },
            )
        }
    }
}

/** The day headers and the absences of each day, newest day first. Items animate in and out. */
private fun LazyListScope.absenceGroups(
    groups: Map<LocalDate, List<UntisAbsence>>,
    reasons: Map<Long, String>,
    lessonsByAbsence: Map<Long, Int>?,
    unit: AbsenceUnit,
    empty: Boolean,
    emptyText: String,
) {
    if (empty) {
        item(key = "empty") {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
    groups.forEach { (date, dayAbsences) ->
        item(key = "date-$date") {
            Text(
                date.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.", Locale.getDefault())),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
        items(dayAbsences, key = { it.id }) { absence ->
            AbsenceCard(
                absence = absence,
                reasons = reasons,
                lessons = lessonsByAbsence?.get(absence.id),
                unit = unit,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** "07:55 – 13:15" for one day; over several days both dates show, as the school's own list does. */
private fun absenceRange(absence: UntisAbsence): String {
    val time = DateTimeFormatter.ofPattern("HH:mm")
    if (absence.start.toLocalDate() == absence.end.toLocalDate()) {
        return "${absence.start.format(time)} – ${absence.end.format(time)}"
    }
    val day = DateTimeFormatter.ofPattern("EEE dd.MM.", Locale.getDefault())
    return "${absence.start.format(day)} ${absence.start.format(time)} – " +
        "${absence.end.format(day)} ${absence.end.format(time)}"
}

@Composable
private fun AbsenceCard(
    absence: UntisAbsence,
    reasons: Map<Long, String>,
    lessons: Int?,
    unit: AbsenceUnit,
    modifier: Modifier = Modifier,
) {
    val language = LocalLanguage.current
    val reason = absence.reasonName
        .ifBlank { absence.reasonId?.let { reasons[it] }.orEmpty() }
        .ifBlank { trFor(language, "Ohne Angabe", "No reason given") }
    Surface(
        modifier = modifier.fillMaxWidth().handCursor(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = absenceRange(absence),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                ExcuseBadge(excused = absence.excused)
            }
            Spacer(Modifier.height(6.dp))
            Text(reason, style = MaterialTheme.typography.bodyLarge)
            if (absence.text.isNotBlank()) {
                Text(absence.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(absence.duration, unit, language),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lessons != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        trFor(language, "$lessons Stunden verpasst", "$lessons lessons missed"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExcuseBadge(excused: Boolean) {
    val language = LocalLanguage.current
    val container = if (excused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (excused) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(shape = RoundedCornerShape(9.dp), color = container, contentColor = content) {
        Text(
            text = if (excused) trFor(language, "Entsch.", "Excused") else trFor(language, "Unentsch.", "Unexcused"),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Hours, minutes or school lessons, as the Android app's absence unit setting shows them. */
fun formatDuration(duration: Duration, unit: AbsenceUnit, language: AppLanguage): String {
    val minutes = duration.toMinutes()
    if (unit == AbsenceUnit.LESSONS) {
        return trFor(language, "${formatSchoolLessons(minutes, localeOf(language))} Std.", "${formatSchoolLessons(minutes, localeOf(language))} lessons")
    }
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

/** The colour of a subject, as the timetable draws it, for use outside the timetable. */
@Composable
private fun subjectAccentFor(subject: String): Color = subjectAccent(
    subject = subject,
    colors = LocalLessonColorSettings.current,
    primary = MaterialTheme.colorScheme.primary,
    dark = LocalStatusColors.current.dark,
)

/** The category dialog: the list (Manage) and the form (new or edit), switching inside one dialog. */
@Composable
private fun CategoryDialog(
    request: CategorySheet,
    categories: List<AbsenceCategory>,
    subjects: List<String>,
    onRequest: (CategorySheet?) -> Unit,
    onSave: (id: String?, name: String, subject: String, maxLessons: Int, maxPercent: Int) -> Unit,
    onPrepareDelete: suspend (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onReorder: (ids: List<String>) -> Unit,
) {
    val language = LocalLanguage.current
    // Opened from the list: the form has a back arrow and saving returns to the list, as on Android.
    val fromManage = request == CategorySheet.Manage
    var page by remember(request) { mutableStateOf(request) }
    val scope = rememberCoroutineScope()

    DesktopDialog(onDismiss = { onRequest(null) }) { close ->
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState is CategorySheet.Form
                (
                    slideInVertically(DesktopMotion.spatial<IntOffset>()) { h -> if (forward) h / 12 else -h / 12 } +
                        fadeIn(DesktopMotion.effects<Float>())
                    ).togetherWith(
                    slideOutVertically(DesktopMotion.fastSpatial<IntOffset>()) { h -> if (forward) -h / 12 else h / 12 } +
                        fadeOut(DesktopMotion.fastEffects<Float>()),
                ).using(SizeTransform(clip = false) { _, _ -> DesktopMotion.spatial<IntSize>() })
            },
            label = "categoryDialogPage",
        ) { current ->
            when (current) {
                CategorySheet.Manage -> ManageCategories(
                    categories = categories,
                    onAdd = { page = CategorySheet.Form(null) },
                    onEdit = { category -> page = CategorySheet.Form(category.id) },
                    onPrepareDelete = onPrepareDelete,
                    onDelete = onDelete,
                    onReorder = onReorder,
                    onClose = close,
                )

                is CategorySheet.Form -> {
                    val editing = current.id?.let { id -> categories.firstOrNull { it.id == id } }
                    // The category was deleted while its form was open: go back to the list.
                    LaunchedEffect(current, editing) {
                        if (current.id != null && editing == null) {
                            if (fromManage) page = CategorySheet.Manage else close()
                        }
                    }
                    CategoryForm(
                        initial = editing,
                        subjects = subjects,
                        takenSubjects = categories.filter { it.id != editing?.id }.map { it.subject.lowercase() }.toSet(),
                        showBack = fromManage,
                        language = language,
                        onBack = { page = CategorySheet.Manage },
                        onSubmit = { name, subject, max, percent ->
                            if (fromManage) {
                                onSave(current.id, name, subject, max, percent)
                                page = CategorySheet.Manage
                            } else {
                                onSave(current.id, name, subject, max, percent)
                                close()
                            }
                        },
                    )
                }
            }
        }
    }
}

/** The categories as rows: edit, move up or down, delete with a confirmation in the row. */
@Composable
private fun ManageCategories(
    categories: List<AbsenceCategory>,
    onAdd: () -> Unit,
    onEdit: (AbsenceCategory) -> Unit,
    onPrepareDelete: suspend (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onReorder: (ids: List<String>) -> Unit,
    onClose: () -> Unit,
) {
    val language = LocalLanguage.current
    var order by remember(categories) { mutableStateOf(categories) }
    var confirmingId by remember { mutableStateOf<String?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }

    // A removed row first collapses, then the category is really deleted.
    LaunchedEffect(removingId) {
        val id = removingId ?: return@LaunchedEffect
        onPrepareDelete(id)
        delay(260)
        onDelete(id)
        removingId = null
        confirmingId = null
    }

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            trFor(language, "Kategorien verwalten", "Manage categories"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = order.isEmpty(),
            transitionSpec = { fadeIn(DesktopMotion.effects<Float>()).togetherWith(fadeOut(DesktopMotion.fastEffects<Float>())).using(SizeTransform(clip = false) { _, _ -> DesktopMotion.spatial<IntSize>() }) },
            label = "manageEmpty",
        ) { empty ->
            if (empty) {
                Text(
                    trFor(language, "Noch keine Kategorien. Lege eine an, um das Limit für ein Fach zu verfolgen.", "No categories yet. Add one to track the limit for a subject."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    order.forEachIndexed { index, category ->
                        key(category.id) {
                            AnimatedVisibility(
                                visible = category.id != removingId,
                                enter = fadeIn(DesktopMotion.effects()),
                                exit = fadeOut(DesktopMotion.fastEffects()) + scaleOut(DesktopMotion.fastSpatial(), targetScale = 0.96f),
                            ) {
                                ManageRow(
                                    category = category,
                                    confirming = confirmingId == category.id,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < order.size - 1,
                                    onEdit = { onEdit(category) },
                                    onAskDelete = { confirmingId = category.id },
                                    onCancelDelete = { confirmingId = null },
                                    onConfirmDelete = { removingId = category.id },
                                    onMove = { delta ->
                                        val target = index + delta
                                        if (target in order.indices) {
                                            val moved = order.toMutableList().apply {
                                                val item = removeAt(index)
                                                add(target, item)
                                            }
                                            order = moved
                                            onReorder(moved.map { it.id })
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = onAdd, modifier = Modifier.weight(1f).height(48.dp).handCursor()) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(trFor(language, "Kategorie hinzufügen", "Add category"))
            }
            TextButton(onClick = onClose, modifier = Modifier.height(48.dp).handCursor()) {
                Text(trFor(language, "Fertig", "Done"))
            }
        }
    }
}

@Composable
private fun ManageRow(
    category: AbsenceCategory,
    confirming: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onAskDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val language = LocalLanguage.current
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (confirming) lerp(scheme.surfaceContainerHigh, scheme.errorContainer, 0.55f) else scheme.surfaceContainerHigh,
        animationSpec = DesktopMotion.effects(),
        label = "manageRowContainer",
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedContent(
            targetState = confirming,
            transitionSpec = {
                (fadeIn(DesktopMotion.effects()) + scaleIn(DesktopMotion.spatial(), initialScale = 0.96f))
                    .togetherWith(fadeOut(DesktopMotion.fastEffects()))
                    .using(SizeTransform(clip = false) { _, _ -> DesktopMotion.spatial<IntSize>() })
            },
            label = "manageRowContent",
        ) { isConfirming ->
            if (isConfirming) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        trFor(language, "„${category.name}“ löschen?", "Delete “${category.name}”?"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onErrorContainer,
                    )
                    Text(
                        trFor(language, "Deine Fehlstunden bleiben unverändert.", "Your absences stay unchanged."),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onErrorContainer.copy(alpha = 0.8f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onCancelDelete, modifier = Modifier.weight(1f).height(44.dp).handCursor()) {
                            Text(trFor(language, "Abbrechen", "Cancel"))
                        }
                        Button(
                            onClick = onConfirmDelete,
                            modifier = Modifier.weight(1f).height(44.dp).handCursor(),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                        ) {
                            Text(trFor(language, "Löschen", "Delete"))
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val accent = subjectAccentFor(category.subject)
                    Box(modifier = Modifier.size(44.dp).background(accent.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(
                            text = category.subject.take(2).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            maxLines = 1,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when {
                                category.isPercent && category.name.equals(category.subject, ignoreCase = true) ->
                                    trFor(language, "max. ${category.maxPercent} %", "max. ${category.maxPercent} %")
                                category.isPercent ->
                                    trFor(language, "${category.subject} · max. ${category.maxPercent} %", "${category.subject} · max. ${category.maxPercent} %")
                                category.name.equals(category.subject, ignoreCase = true) ->
                                    trFor(language, "max. ${category.maxLessons} Stunden", "max. ${category.maxLessons} lessons")
                                else ->
                                    trFor(language, "${category.subject} · max. ${category.maxLessons} Stunden", "${category.subject} · max. ${category.maxLessons} lessons")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onMove(-1) }, enabled = canMoveUp, modifier = Modifier.handCursor()) {
                        Icon(Icons.Outlined.ArrowUpward, contentDescription = trFor(language, "Nach oben", "Move up"))
                    }
                    IconButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.handCursor()) {
                        Icon(Icons.Outlined.ArrowDownward, contentDescription = trFor(language, "Nach unten", "Move down"))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.handCursor()) {
                        Icon(Icons.Outlined.Edit, contentDescription = trFor(language, "Bearbeiten", "Edit"), tint = scheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = onAskDelete,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = scheme.error),
                        modifier = Modifier.handCursor(),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = trFor(language, "Löschen", "Delete"))
                    }
                }
            }
        }
    }
}

/** Create or edit a category: the subject, the limit in lessons or percent, and an optional name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryForm(
    initial: AbsenceCategory?,
    subjects: List<String>,
    takenSubjects: Set<String>,
    showBack: Boolean,
    language: AppLanguage,
    onBack: () -> Unit,
    onSubmit: (name: String, subject: String, maxLessons: Int, maxPercent: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var subject by remember { mutableStateOf(initial?.subject.orEmpty()) }
    var maxText by remember { mutableStateOf(initial?.maxLessons?.takeIf { it > 0 }?.toString().orEmpty()) }
    var percentMode by remember { mutableStateOf(initial?.isPercent == true) }
    var percent by remember { mutableIntStateOf(initial?.maxPercent?.takeIf { it > 0 }?.coerceIn(1, 100) ?: 10) }

    // A category whose subject left the timetable keeps it visible in the list.
    val options = remember(subjects, initial) {
        val own = initial?.subject?.takeIf { s -> subjects.none { it.equals(s, ignoreCase = true) } }
        listOfNotNull(own) + subjects
    }
    val maxLessons = maxText.toIntOrNull()
    val canSave = subject.isNotBlank() && (percentMode || (maxLessons != null && maxLessons > 0))
    val anyTaken = options.any { it.lowercase() in takenSubjects }

    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp).handCursor()) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = trFor(language, "Zurück", "Back"))
                }
            }
            Text(
                trFor(language, if (initial == null) "Neue Kategorie" else "Kategorie bearbeiten", if (initial == null) "New category" else "Edit category"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.height(20.dp))

        SectionLabel(trFor(language, "Fach", "Subject"))
        if (options.isEmpty()) {
            Text(
                trFor(language, "Noch keine Fächer im Stundenplan geladen.", "No subjects loaded yet."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                options.forEach { option ->
                    SubjectTile(
                        subject = option,
                        selected = subject.equals(option, ignoreCase = true),
                        taken = option.lowercase() in takenSubjects,
                        onClick = { subject = option },
                    )
                }
            }
            if (anyTaken) {
                Text(
                    trFor(language, "Ausgegraute Fächer sind schon in einer Kategorie.", "Greyed-out subjects already belong to a category."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel(trFor(language, if (percentMode) "Maximale Fehlzeit" else "Maximale Fehlstunden", if (percentMode) "Maximum absence" else "Maximum absences"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            listOf(false to trFor(language, "Stunden", "Lessons"), true to trFor(language, "Prozent", "Percent")).forEach { (isPercent, label) ->
                val selected = percentMode == isPercent
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable { percentMode = isPercent }.handCursor(),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
        // The lesson count field and the percent slider swap places inside one animated slot.
        AnimatedContent(
            targetState = percentMode,
            transitionSpec = {
                fadeIn(DesktopMotion.effects<Float>()).togetherWith(fadeOut(DesktopMotion.fastEffects<Float>()))
                    .using(SizeTransform(clip = false) { _, _ -> DesktopMotion.spatial<IntSize>() })
            },
            label = "categoryLimitInput",
        ) { isPercent ->
            if (isPercent) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text(
                        trFor(language, "$percent %", "$percent %"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Slider(
                        value = percent.toFloat(),
                        onValueChange = { percent = it.roundToInt().coerceIn(1, 100) },
                        valueRange = 1f..100f,
                        steps = 98,
                        modifier = Modifier.fillMaxWidth().handCursor(),
                    )
                    Text(
                        trFor(language, "Stundenlimit", "Lesson limit"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { value -> if (value.length <= 3 && value.all { it.isDigit() }) maxText = value },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = CircleShape,
                    suffix = { Text(trFor(language, "Stunden", "lessons")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions.Default,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
            Text(
                trFor(language, "Name", "Name"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    trFor(language, "Optional", "Optional"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = CircleShape,
            placeholder = {
                Text(
                    subject.ifBlank { trFor(language, "Name des Fachs", "Subject name") },
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
            supportingText = { Text(trFor(language, "Leer gelassen wird der Name des Fachs verwendet.", "If left empty, the subject name is used.")) },
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val max = if (percentMode) 0 else maxLessons ?: return@Button
                onSubmit(name, subject, max, if (percentMode) percent else 0)
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().height(52.dp).handCursor(),
            shape = CircleShape,
        ) {
            Text(trFor(language, "Speichern", "Save"))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/**
 * A subject as a tile in its timetable colour. The corners morph from a rounded square to a pill
 * when chosen, the colour fills, and a check badge pops in. A taken subject is dimmed.
 */
@Composable
private fun SubjectTile(subject: String, selected: Boolean, taken: Boolean, onClick: () -> Unit) {
    val accent = subjectAccentFor(subject)
    val scheme = MaterialTheme.colorScheme
    val corner by animateDpAsState(
        targetValue = if (selected) 26.dp else 14.dp,
        animationSpec = DesktopMotion.spatial(),
        label = "tileCorner",
    )
    val container by animateColorAsState(
        targetValue = if (selected) accent else lerp(scheme.surfaceContainerHigh, accent, 0.22f),
        animationSpec = DesktopMotion.fastEffects(),
        label = "tileContainer",
    )
    val readable = if (accent.luminance() > 0.3f) Color(0xDE000000) else Color.White
    val content by animateColorAsState(
        targetValue = if (selected) readable else scheme.onSurface,
        animationSpec = DesktopMotion.fastEffects(),
        label = "tileContent",
    )
    Box(modifier = Modifier.alpha(if (taken) 0.38f else 1f)) {
        Surface(
            onClick = onClick,
            enabled = !taken,
            shape = RoundedCornerShape(corner),
            color = container,
            contentColor = content,
            modifier = Modifier.height(48.dp).widthIn(min = 72.dp).handCursor(),
        ) {
            Box(modifier = Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                Text(subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        AnimatedVisibility(
            visible = selected,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
            enter = scaleIn(DesktopMotion.fastSpatial()) + fadeIn(DesktopMotion.fastEffects()),
            exit = scaleOut(DesktopMotion.fastSpatial()) + fadeOut(DesktopMotion.fastEffects()),
        ) {
            Box(
                modifier = Modifier.size(22.dp).background(scheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}
