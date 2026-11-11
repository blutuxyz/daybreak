package com.bdersolutions.daybreak.ui.timetable

import com.bdersolutions.daybreak.ui.components.IconButton
import com.bdersolutions.daybreak.ui.components.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.cache.TimetableCache
import com.bdersolutions.daybreak.data.schedule.LessonSearch
import com.bdersolutions.daybreak.data.schedule.SearchHit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Searches the cached weeks for a subject, teacher or room ("When is my next Maths lesson?"). The
 * first upcoming hit is marked; tapping a hit opens that day in the timetable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LessonSearchSheet(
    cache: TimetableCache,
    now: LocalDateTime,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("EEE, d. MMM", locale) }

    val lessons = remember(cache.weeks) { cache.weeks.values.flatMap { it.lessons } }
    val hits = remember(lessons, cache.exams, query, now.toLocalDate()) {
        LessonSearch.search(lessons, cache.exams, query, now)
    }
    val upcoming = hits.filter { it.upcoming }
    val past = hits.filter { !it.upcoming }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                shape = CircleShape,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Clear, contentDescription = null)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            Spacer(Modifier.height(12.dp))
            when {
                query.isBlank() -> Hint(stringResource(R.string.search_prompt), null)
                hits.isEmpty() -> Hint(stringResource(R.string.search_empty), stringResource(R.string.search_empty_body))
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (upcoming.isNotEmpty()) {
                        item(key = "h-up") { SectionTitle(stringResource(R.string.search_upcoming)) }
                        items(upcoming, key = { "u${it.lesson.id}-${it.lesson.date}-${it.lesson.start}" }) { hit ->
                            HitRow(hit, dateFormat, isNext = hit === upcoming.first(), onClick = { onSelect(hit.lesson.date) })
                        }
                    }
                    if (past.isNotEmpty()) {
                        item(key = "h-past") { SectionTitle(stringResource(R.string.search_past)) }
                        items(past, key = { "p${it.lesson.id}-${it.lesson.date}-${it.lesson.start}" }) { hit ->
                            HitRow(hit, dateFormat, isNext = false, onClick = { onSelect(hit.lesson.date) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Hint(title: String, body: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
    )
}

@Composable
private fun HitRow(hit: SearchHit, dateFormat: DateTimeFormatter, isNext: Boolean, onClick: () -> Unit) {
    val lesson = hit.lesson
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (isNext) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (isNext) scheme.onPrimaryContainer else scheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = lesson.subject.ifBlank { lesson.subjectLong }.ifBlank { "?" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isNext) Badge(stringResource(R.string.search_next_badge), scheme.primary, scheme.onPrimary)
                    if (hit.exam != null) Badge(stringResource(R.string.search_exam_badge), scheme.tertiary, scheme.onTertiary)
                }
                val details = listOfNotNull(
                    "${lesson.start.format(CLOCK)}–${lesson.end.format(CLOCK)}",
                    lesson.room.takeIf { it.isNotBlank() }?.let { stringResource(R.string.now_next_room, it) },
                    lesson.teacher.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = lesson.date.format(dateFormat),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun Badge(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
