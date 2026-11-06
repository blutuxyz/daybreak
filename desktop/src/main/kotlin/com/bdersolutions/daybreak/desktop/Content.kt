package com.bdersolutions.daybreak.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.data.schedule.LessonSearch
import com.bdersolutions.daybreak.untis.MailFolder
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisHomework
import com.bdersolutions.daybreak.untis.UntisMail
import com.bdersolutions.daybreak.untis.UntisMessage
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Title row with a refresh button, shared by the list pages. */
@Composable
private fun PageTitle(title: String, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        OutlinedButton(onClick = onRefresh) { Text(tr("Aktualisieren", "Refresh")) }
    }
}

@Composable
internal fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = BlockShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
    }
}

/**
 * A page that loads one list from a repository and shows it as cards. Errors are shown in place
 * of the list; the refresh button loads it again.
 */
@Composable
private fun <T> LoadedList(
    title: String,
    emptyText: String,
    key: (T) -> Any,
    load: suspend () -> List<T>,
    row: @Composable (T) -> Unit,
) {
    val language = LocalLanguage.current
    var rows by remember { mutableStateOf<List<T>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        error = null
        try {
            rows = load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.userMessage(language, trFor(language, "Konnte nicht geladen werden.", "Could not load."))
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageTitle(title) { reloadKey++ }
        if (loading) CircularProgressIndicator(modifier = Modifier.height(24.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!loading && error == null && rows.isEmpty()) Text(emptyText)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = key) { row(it) }
        }
    }
}

@Composable
fun HomeworkScreen(container: DesktopContainer) {
    val language = LocalLanguage.current
    val date = DateTimeFormatter.ofPattern("d MMM yyyy", localeOf(language))
    LoadedList<UntisHomework>(
        title = tr("Hausaufgaben", "Homework"),
        emptyText = tr("Keine Hausaufgaben in den nächsten 60 Tagen.", "No homework in the next 60 days."),
        key = { it.id },
        load = {
            val today = LocalDate.now()
            container.schoolData.homework(today.minusDays(7), today.plusDays(60)).sortedBy { it.due }
        },
    ) { homework ->
        InfoCard {
            Text(
                "${homework.subject} · ${tr("fällig", "due")} ${homework.due.format(date)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (homework.text.isNotBlank()) Text(homework.text, style = MaterialTheme.typography.bodyMedium)
            if (homework.remark.isNotBlank()) Text(homework.remark, style = MaterialTheme.typography.labelMedium)
            if (homework.completed) Text(tr("Erledigt", "Done"), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ExamsScreen(container: DesktopContainer) {
    val language = LocalLanguage.current
    val date = DateTimeFormatter.ofPattern("EEE d MMM yyyy", localeOf(language))
    LoadedList<UntisExam>(
        title = tr("Prüfungen", "Exams"),
        emptyText = tr("Keine Prüfungen in den nächsten 120 Tagen.", "No exams in the next 120 days."),
        key = { it.id },
        load = {
            val today = LocalDate.now()
            container.schoolData.exams(today.minusDays(30), today.plusDays(120)).sortedBy { it.date }
        },
    ) { exam ->
        InfoCard {
            Text(
                "${exam.date.format(date)} · ${exam.start.format(timeFormat)}–${exam.end.format(timeFormat)}",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                exam.subject.ifBlank { exam.name },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (exam.name.isNotBlank() && exam.name != exam.subject) Text(exam.name, style = MaterialTheme.typography.bodyMedium)
            if (exam.text.isNotBlank()) Text(exam.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** School notices of today: the same messages of the day the Android Account page shows. */
@Composable
fun NoticesScreen(container: DesktopContainer) {
    LoadedList<UntisMessage>(
        title = tr("Schulmitteilungen", "School notices"),
        emptyText = tr("Heute keine Schulmitteilungen.", "No school notices today."),
        key = { it.id },
        load = { container.schoolData.messages(LocalDate.now()) },
    ) { notice ->
        InfoCard {
            Text(notice.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (notice.text.isNotBlank()) Text(notice.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Searches the cached lessons (the same search as the Android app). */
@Composable
fun SearchScreen(container: DesktopContainer) {
    val language = LocalLanguage.current
    val cache by container.timetable.cache.collectAsState()
    var query by remember { mutableStateOf("") }
    val hits = remember(cache, query) {
        LessonSearch.search(
            lessons = cache.weeks.values.flatMap { it.lessons },
            exams = cache.exams,
            query = query,
            now = LocalDateTime.now(),
        )
    }
    val date = DateTimeFormatter.ofPattern("EEE d MMM", localeOf(language))

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("Suche", "Search"), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(tr("Fach, Lehrer, Raum oder Klasse", "Subject, teacher, room or class")) },
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            query.isBlank() -> Text(tr("Sucht in den geladenen Wochen.", "Searches the weeks that are loaded."))
            hits.isEmpty() -> Text(tr("Keine Treffer.", "No matches."))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hits, key = { it.lesson.id.toString() + it.lesson.date + it.lesson.start }) { hit ->
                InfoCard {
                    Text(
                        "${hit.lesson.date.format(date)} · ${hit.lesson.start.format(timeFormat)}–${hit.lesson.end.format(timeFormat)}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        hit.lesson.subjectLong.ifBlank { hit.lesson.subject },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val details = listOfNotNull(
                        hit.lesson.teacher.takeIf { it.isNotBlank() },
                        hit.lesson.room.takeIf { it.isNotBlank() },
                        hit.exam?.let { tr("Prüfung", "Exam") + ": " + it.subject.ifBlank { it.name } },
                    ).joinToString(" · ")
                    if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Homework and exams side by side, the Android app's Hausaufgaben & Prüfungen page. */
@Composable
fun UpcomingScreen(container: DesktopContainer) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            HomeworkScreen(container)
        }
        Column(modifier = Modifier.weight(1f)) {
            ExamsScreen(container)
        }
    }
}
