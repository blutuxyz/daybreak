package com.bdersolutions.daybreak.ui.absences

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.ui.toUserMessage
import com.bdersolutions.daybreak.untis.UntisAbsence
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbsencesScreen(
    viewModel: AbsencesViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val sinceText = remember(viewModel.schoolYearStart) {
        viewModel.schoolYearStart.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault()))
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.absences_title)) },
                subtitle = { Text(stringResource(R.string.absences_since, sinceText)) },
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
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun AbsenceList(
    absences: List<UntisAbsence>,
    reasons: Map<Long, String>,
    contentPadding: PaddingValues,
) {
    var filter by rememberSaveable { mutableStateOf(AbsenceFilter.ALL) }
    val shown = remember(absences, filter) { absences.filter { filter.matches(it) } }
    val groups = remember(shown) { shown.groupBy { it.start.toLocalDate() } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "summary") {
            AbsenceSummary(absences = absences)
        }
        item(key = "filters") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AbsenceFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = {
                            Text(
                                text = when (option) {
                                    AbsenceFilter.ALL -> stringResource(R.string.absences_filter_all)
                                    AbsenceFilter.UNEXCUSED -> stringResource(R.string.absences_unexcused)
                                    AbsenceFilter.EXCUSED -> stringResource(R.string.absences_excused)
                                },
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
        }

        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.absences_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        groups.forEach { (date, dayAbsences) ->
            item(key = "date-$date") {
                DateHeader(date)
            }
            items(dayAbsences, key = { it.id }) { absence ->
                AbsenceCard(absence = absence, reasons = reasons)
            }
        }
    }
}

@Composable
private fun AbsenceSummary(absences: List<UntisAbsence>) {
    val total = absences.fold(Duration.ZERO) { sum, absence -> sum.plus(absence.duration) }
    val excused = absences.filter { it.excused }
    val excusedTime = excused.fold(Duration.ZERO) { sum, absence -> sum.plus(absence.duration) }
    val unexcusedTime = total.minus(excusedTime)
    val excusedShare = if (total.isZero) 0f else excusedTime.toMinutes().toFloat() / total.toMinutes().toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.absences_total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = formatDuration(total),
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.absences_count, absences.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { excusedShare },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f),
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.absences_excused),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = formatDuration(excusedTime),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.absences_unexcused),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = formatDuration(unexcusedTime),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    val text = remember(date) {
        date.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.", Locale.getDefault()))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun AbsenceCard(
    absence: UntisAbsence,
    reasons: Map<Long, String>,
) {
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val reason = absence.reasonName
        .ifBlank { absence.reasonId?.let { reasons[it] }.orEmpty() }
        .ifBlank { stringResource(R.string.absences_reason_unknown) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${absence.start.format(timeFormat)} – ${absence.end.format(timeFormat)}",
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
            Text(
                text = formatDuration(absence.duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    return if (minutes >= 60) {
        stringResource(R.string.absences_duration_hm, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.absences_duration_m, minutes)
    }
}
