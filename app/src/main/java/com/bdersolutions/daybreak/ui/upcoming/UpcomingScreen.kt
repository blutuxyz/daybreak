package com.bdersolutions.daybreak.ui.upcoming

import com.bdersolutions.daybreak.ui.components.SegmentedButton
import com.bdersolutions.daybreak.ui.components.Button
import com.bdersolutions.daybreak.ui.components.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.ui.components.rememberSubjectTone
import com.bdersolutions.daybreak.ui.settings.SettingsPageScaffold
import com.bdersolutions.daybreak.ui.toUserMessage
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisHomework
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Homework and exams that are still ahead, on one page with a switch between the two. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingScreen(
    viewModel: UpcomingViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val today = remember { LocalDate.now() }

    SettingsPageScaffold(
        title = stringResource(R.string.upcoming_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        item(key = "tabs") {
            val labels = listOf(
                stringResource(R.string.upcoming_homework) to state.homework.size,
                stringResource(R.string.upcoming_exams) to state.exams.size,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                labels.forEachIndexed { index, (label, count) ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                    ) {
                        Text(if (state.loading || count == 0) label else "$label · $count", maxLines = 1)
                    }
                }
            }
        }

        val error = if (tab == 0) state.homeworkError else state.examsError
        when {
            state.loading -> item(key = "loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error != null -> item(key = "error") {
                ErrorBlock(message = error.toUserMessage(context), onRetry = viewModel::load)
            }

            tab == 0 -> homeworkList(state.homework, today, locale)
            else -> examList(state.exams, today, locale)
        }
    }
}

private fun LazyListScope.homeworkList(items: List<UntisHomework>, today: LocalDate, locale: Locale) {
    if (items.isEmpty()) {
        item(key = "empty") { EmptyText(R.string.upcoming_homework_empty) }
        return
    }
    items.groupBy { it.due }.forEach { (date, entries) ->
        item(key = "hw-day-$date") { DayHeader(date, today, locale) }
        items(entries, key = { "hw-${it.id}" }) { HomeworkCard(it) }
    }
}

private fun LazyListScope.examList(items: List<UntisExam>, today: LocalDate, locale: Locale) {
    if (items.isEmpty()) {
        item(key = "empty") { EmptyText(R.string.upcoming_exams_empty) }
        return
    }
    items.groupBy { it.date }.forEach { (date, entries) ->
        item(key = "ex-day-$date") { DayHeader(date, today, locale) }
        items(entries, key = { "ex-${it.id}" }) { ExamCard(it) }
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, locale: Locale) {
    val label = when (date) {
        today -> stringResource(R.string.upcoming_today)
        today.plusDays(1) -> stringResource(R.string.upcoming_tomorrow)
        else -> DateTimeFormatter.ofPattern("EEEE, dd.MM.", locale).format(date)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun HomeworkCard(homework: UntisHomework) {
    ItemCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubjectChip(homework.subject)
            Spacer(Modifier.weight(1f))
            if (homework.completed) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.upcoming_done),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (homework.text.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(homework.text, style = MaterialTheme.typography.bodyLarge)
        }
        if (homework.remark.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = homework.remark,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExamCard(exam: UntisExam) {
    ItemCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${exam.start} – ${exam.end}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            SubjectChip(exam.subject)
        }
        val title = exam.name.ifBlank { exam.text }
        if (title.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        if (exam.text.isNotBlank() && exam.text != title) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = exam.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ItemCard(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/** The subject in its timetable colour, so it reads the same as in the timetable. */
@Composable
private fun SubjectChip(subject: String) {
    val name = subject.ifBlank { stringResource(R.string.upcoming_no_subject) }
    val tone = rememberSubjectTone(subject)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = tone.accent.copy(alpha = 0.2f),
        contentColor = tone.accent,
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun EmptyText(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
    )
}

@Composable
internal fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}
