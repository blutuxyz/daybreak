package com.bdersolutions.daybreak.ui.absences

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.IconButton
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.settings.AbsenceUnit
import com.bdersolutions.daybreak.ui.components.LocalAbsenceUnit
import com.bdersolutions.daybreak.ui.toUserMessage
import com.bdersolutions.daybreak.untis.UntisAbsence
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private enum class AbsenceFilter {
    ALL,
    UNEXCUSED,
    EXCUSED;

    fun matches(absence: UntisAbsence): Boolean = when (this) {
        ALL -> true
        UNEXCUSED -> !absence.excused
        EXCUSED -> absence.excused
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AbsencesScreen(
    viewModel: AbsencesViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val windows by viewModel.windows.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val sinceText = remember(viewModel.schoolYearStart) {
        viewModel.schoolYearStart.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault()))
    }
    var sheet by remember { mutableStateOf<CategorySheetRequest?>(null) }
    // Owned here: the list shows the selected card's absences, and deleting the visible category
    // first moves the pager off its card.
    val pagerState = rememberPagerState(pageCount = { 1 + categories.categories.size })
    val haptics = LocalHaptics.current
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { haptics.perform(HapticEvent.PAGE_SNAP) }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.absences_title)) },
                subtitle = { Text(stringResource(R.string.absences_since, sinceText)) },
                actions = {
                    if (state is AbsencesUiState.Loaded) {
                        if (categories.categories.isNotEmpty()) {
                            IconButton(onClick = { sheet = CategorySheetRequest.Manage }) {
                                Icon(
                                    Icons.Outlined.Tune,
                                    contentDescription = stringResource(R.string.absences_manage),
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                viewModel.loadSubjects()
                                sheet = CategorySheetRequest.New
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.absences_add_category),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        when (val current = state) {
            AbsencesUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is AbsencesUiState.Failed -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
                Spacer(Modifier.height(12.dp))
                Text(
                    text = current.error.toUserMessage(context),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = viewModel::load) {
                    Text(stringResource(R.string.action_retry))
                }
            }

            is AbsencesUiState.Loaded -> AbsenceList(
                absences = current.absences,
                reasons = current.reasons,
                categories = categories,
                pagerState = pagerState,
                contentPadding = innerPadding,
                windows = windows,
            )
        }
    }

    AbsenceCategorySheets(
        request = sheet,
        onRequest = { sheet = it },
        categories = categories,
        onSave = viewModel::saveCategory,
        onPrepareDelete = { id ->
            val page = categories.categories.indexOfFirst { it.id == id } + 1
            if (page > 0 && pagerState.currentPage == page) pagerState.animateScrollToPage(page - 1)
        },
        onDelete = viewModel::deleteCategory,
        onReorder = viewModel::reorderCategories,
    )
}

@Composable
private fun AbsenceList(
    absences: List<UntisAbsence>,
    reasons: Map<Long, String>,
    categories: CategoriesUiState,
    pagerState: PagerState,
    contentPadding: PaddingValues,
    windows: SchoolDayWindows?,
) {
    var filter by rememberSaveable { mutableStateOf(AbsenceFilter.ALL) }
    val motion = MaterialTheme.motionScheme

    // The card the pager is heading for decides what the list shows: every absence on "Total", and
    // only the absences that missed a lesson of the subject on a category card. The list items
    // animate, so the change flows instead of cutting.
    val selected = categories.categories.getOrNull(pagerState.targetPage - 1)
    val lessonsByAbsence: Map<Long, Int>? = selected?.let { categories.lessonsByAbsence[it.id] }
    val base = remember(absences, selected, lessonsByAbsence) {
        when {
            selected == null -> absences
            lessonsByAbsence == null -> emptyList()
            else -> absences.filter { (lessonsByAbsence[it.id] ?: 0) > 0 }
        }
    }
    val shown = remember(base, filter) { base.filter { filter.matches(it) } }
    val groups = remember(shown) { shown.groupBy { it.start.toLocalDate() } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "summary") {
            AbsenceSummaryPager(
                absences = absences,
                categories = categories,
                pagerState = pagerState,
                windows = windows,
            )
        }
        item(key = "filters") {
            val totals = remember(base, windows) { absenceTotals(base, windows) }
            val totalTime = totals.total
            val excusedTime = totals.excused
            val unexcusedTime = totals.unexcused
            // The chips carry their counts, so they can be wider than a phone. They scroll rather
            // than squeezing the labels.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AbsenceFilter.entries.forEach { option ->
                    val (name, time) = when (option) {
                        AbsenceFilter.ALL -> stringResource(R.string.absences_filter_all) to totalTime
                        AbsenceFilter.UNEXCUSED -> stringResource(R.string.absences_unexcused) to unexcusedTime
                        AbsenceFilter.EXCUSED -> stringResource(R.string.absences_excused) to excusedTime
                    }
                    // On a category card the chips count lessons of the subject, not clock time.
                    val chipValue = if (lessonsByAbsence != null) {
                        stringResource(
                            R.string.absences_lessons_count,
                            base.filter { option.matches(it) }.sumOf { lessonsByAbsence[it.id] ?: 0 },
                        )
                    } else {
                        formatDuration(time)
                    }
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = name, maxLines = 1)
                                Spacer(Modifier.width(6.dp))
                                // The value rolls to its new number instead of swapping.
                                AnimatedContent(
                                    targetState = chipValue,
                                    transitionSpec = {
                                        (
                                            fadeIn(motion.defaultEffectsSpec<Float>()) +
                                                slideInVertically(motion.defaultSpatialSpec<IntOffset>()) { it / 2 }
                                            ).togetherWith(
                                                fadeOut(motion.fastEffectsSpec<Float>()) +
                                                    slideOutVertically(motion.fastSpatialSpec<IntOffset>()) { -it / 2 },
                                            ).using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec<IntSize>() })
                                    },
                                    label = "chipValue",
                                ) { value ->
                                    // Only the unexcused value carries the accent colour.
                                    Text(
                                        text = value,
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

        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(
                        if (selected != null && lessonsByAbsence == null) {
                            R.string.category_loading
                        } else {
                            R.string.absences_empty
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        groups.forEach { (date, dayAbsences) ->
            item(key = "date-$date") {
                DateHeader(date, modifier = Modifier.animateItem())
            }
            items(dayAbsences, key = { it.id }) { absence ->
                AbsenceCard(
                    absence = absence,
                    reasons = reasons,
                    lessons = lessonsByAbsence?.get(absence.id),
                    windows = windows,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * The overview card: the total absence time, how much of it is excused, the two durations,
 * and the most recent absence.
 */
@Composable
internal fun AbsenceSummary(
    absences: List<UntisAbsence>,
    modifier: Modifier = Modifier,
    windows: SchoolDayWindows? = null,
) {
    val totals = remember(absences, windows) { absenceTotals(absences, windows) }
    val total = totals.total
    val excusedTime = totals.excused
    val unexcusedTime = totals.unexcused
    val excusedShare = if (total.isZero) {
        0f
    } else {
        excusedTime.toMinutes().toFloat() / total.toMinutes().toFloat()
    }
    val latest = absences.maxByOrNull { it.start }
    val locale = Locale.getDefault()
    val latestText = remember(latest, locale) {
        latest?.start?.format(DateTimeFormatter.ofPattern("EEE dd.MM. HH:mm", locale))
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.absences_total),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDuration(total),
                        style = MaterialTheme.typography.displaySmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.absences_count, absences.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { excusedShare },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.absences_excused_share,
                    (excusedShare * 100).roundToInt(),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.absences_excused),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDuration(excusedTime),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.absences_unexcused),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDuration(unexcusedTime),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        // The unexcused time carries the accent, as in the filter chips.
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (latestText != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.absences_latest, latestText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate, modifier: Modifier = Modifier) {
    val text = remember(date) {
        date.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.", Locale.getDefault()))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp),
    )
}

/**
 * "07:55 – 13:15" for one day. An absence over several days shows both dates, like the
 * school's own list: "Wed 23.09. 07:55 – Thu 24.09. 17:30".
 */
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
    /** Lessons of the selected category's subject this absence covers; null on the Total card. */
    lessons: Int? = null,
    windows: SchoolDayWindows? = null,
    modifier: Modifier = Modifier,
) {
    val range = remember(absence) { absenceRange(absence) }
    val reason = absence.reasonName
        .ifBlank { absence.reasonId?.let { reasons[it] }.orEmpty() }
        .ifBlank { stringResource(R.string.absences_reason_unknown) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = range,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                ExcuseBadge(excused = absence.excused)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (absence.text.isNotBlank()) {
                Text(
                    text = absence.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDuration(absence.countedDuration(windows)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lessons != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.absences_lessons_missed, lessons),
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
    val container = if (excused) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val content = if (excused) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = stringResource(if (excused) R.string.absences_excused_short else R.string.absences_unexcused_short),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun formatDuration(duration: Duration): String {
    val minutes = duration.toMinutes()
    if (LocalAbsenceUnit.current == AbsenceUnit.LESSONS) {
        val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
        return stringResource(R.string.absences_duration_lessons, formatSchoolLessons(minutes, locale))
    }
    return if (minutes >= 60) {
        stringResource(R.string.absences_duration_hm, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.absences_duration_m, minutes)
    }
}
