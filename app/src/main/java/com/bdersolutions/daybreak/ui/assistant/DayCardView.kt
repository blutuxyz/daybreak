package com.bdersolutions.daybreak.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.assistant.DayCard
import com.bdersolutions.daybreak.data.schedule.NowAndNext
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.ui.components.AccentBadge
import com.bdersolutions.daybreak.ui.components.cancelledAccent
import com.bdersolutions.daybreak.ui.components.examContainer
import com.bdersolutions.daybreak.ui.components.examContent
import com.bdersolutions.daybreak.ui.components.rememberSubjectTone
import com.bdersolutions.daybreak.ui.components.substitutionAccent
import com.bdersolutions.daybreak.ui.components.warningContainer
import com.bdersolutions.daybreak.ui.settings.currentAppLanguage
import com.bdersolutions.daybreak.ui.timetable.LessonFocus
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Above this many characters of full subject names, the "instead of" line uses the short names. */
private const val MAX_INSTEAD_CHARS = 48

/**
 * A day of the timetable inside the chat, drawn like the drafted message card: the date, then one row per
 * lesson (double lessons as one) with its colour, teacher and room, and badges for cancellations,
 * substitutions, room changes and exams. On today's card the running lesson is marked and finished ones
 * are dimmed. Everything on it comes from the student's own data (show_timetable), not from the model.
 */
@Composable
internal fun DayCardView(card: DayCard, modifier: Modifier = Modifier, onOpenLesson: ((LessonFocus) -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val locale = if (currentAppLanguage() == AppLanguage.GERMAN) Locale.GERMAN else Locale.ENGLISH
    val now = remember { LocalDateTime.now() }
    val today = now.toLocalDate()
    val dateText = remember(card.date, locale) {
        val pattern = if (locale == Locale.GERMAN) "EEEE, d. MMMM" else "EEEE, MMMM d"
        card.date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
    val held = card.entries.filterNot { it.cancelled }
    // Every cancelled lesson, whether something replaces it or not.
    val cancelledCount = card.entries.sumOf { entry -> entry.replaces.size + if (entry.cancelled) 1 else 0 }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 36.dp),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = colors.primary)
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(dateText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    // When school is, and how much is cancelled: "07:55–17:30 · 4 cancelled".
                    val summary = listOfNotNull(
                        held.takeIf { it.isNotEmpty() }?.let { "${it.minOf { e -> e.start }.format(CLOCK)}–${it.maxOf { e -> e.end }.format(CLOCK)}" },
                        cancelledCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.assistant_card_cancelled, it, it) },
                    ).joinToString(" · ")
                    if (summary.isNotEmpty()) {
                        Text(summary, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                }
                dayLabel(card.date, today)?.let { label ->
                    AccentBadge(text = label, containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = colors.outlineVariant)
            when {
                card.entries.isEmpty() -> EmptyDay(card.holiday)
                else -> Column(modifier = Modifier.padding(top = 6.dp)) {
                    // The latest end so far: an entry can end before the one above it (a short lesson beside a long event).
                    var busyUntil: LocalTime? = null
                    card.entries.forEach { entry ->
                        val until = busyUntil
                        if (until != null && Duration.between(until, entry.start).toMinutes() >= NowAndNext.MIN_FREE_PERIOD_MINUTES) {
                            FreeRow(until, entry.start)
                        }
                        busyUntil = if (until == null || entry.end > until) entry.end else until
                        val isToday = card.date == today
                        LessonRow(
                            onClick = onOpenLesson?.let { open -> { open(LessonFocus(card.date, entry.start, entry.end, entry.subject)) } },
                            entry = entry,
                            current = isToday && !entry.cancelled && entry.start <= now.toLocalTime() && now.toLocalTime() < entry.end,
                            past = isToday && entry.end <= now.toLocalTime(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String? = when (date) {
    today -> stringResource(R.string.assistant_card_today)
    today.plusDays(1) -> stringResource(R.string.assistant_card_tomorrow)
    else -> null
}

@Composable
private fun EmptyDay(holiday: String?) {
    Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (holiday != null) {
            Icon(Icons.Outlined.BeachAccess, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(
            text = holiday ?: stringResource(R.string.assistant_card_no_lessons),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonRow(entry: DayCard.Entry, current: Boolean, past: Boolean, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val tone = rememberSubjectTone(entry.subject)
    val cancelled = cancelledAccent()
    val changed = substitutionAccent()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) colors.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (past && !current) 0.55f else 1f)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.width(48.dp)) {
            Text(entry.start.format(CLOCK), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(entry.end.format(CLOCK), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        val stripe = if (entry.cancelled) cancelled.copy(alpha = 0.5f) else tone.accent
        Column(
            modifier = Modifier
                .weight(1f)
                // The subject's colour as a stripe down the left edge, as tall as the row's text.
                .drawBehind {
                    drawRoundRect(stripe, size = Size(4.dp.toPx(), size.height), cornerRadius = CornerRadius(2.dp.toPx()))
                }
                .padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.subjectName.ifBlank { stringResource(R.string.lesson_unknown_subject) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (entry.cancelled) TextDecoration.LineThrough else null,
                color = if (entry.cancelled) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val teacherChange = if (entry.originalTeacher.isNotBlank() && entry.teacher.isNotBlank()) {
                stringResource(R.string.assistant_card_teacher_change, entry.teacher, entry.originalTeacher)
            } else {
                null
            }
            val meta = buildAnnotatedString {
                val teacher = teacherChange ?: entry.teacher
                if (teacher.isNotBlank()) {
                    if (teacherChange != null) withStyle(SpanStyle(color = changed, fontWeight = FontWeight.Medium)) { append(teacher) } else append(teacher)
                }
                if (entry.room.isNotBlank()) {
                    if (length > 0) append(" · ")
                    if (entry.originalRoom.isNotBlank()) {
                        withStyle(SpanStyle(color = changed, fontWeight = FontWeight.Medium)) { append("${entry.room} ") }
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(entry.originalRoom) }
                    } else {
                        append(entry.room)
                    }
                }
            }
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (entry.note.isNotBlank()) {
                Text(entry.note, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (entry.replaces.isNotEmpty()) {
                // What this block stands in for: "Instead of ~~Technische Mechanik~~, ~~Deutsch~~".
                // Full names read best, but a long list of them filled five lines: then the short names.
                val long = entry.replaces.map { it.subjectName.ifBlank { it.subject } }.filter { it.isNotBlank() }.distinct()
                val names = if (long.sumOf { it.length } > MAX_INSTEAD_CHARS) {
                    entry.replaces.map { it.subject.ifBlank { it.subjectName } }.filter { it.isNotBlank() }.distinct()
                } else {
                    long
                }
                val instead = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(stringResource(R.string.assistant_card_instead)) }
                    append(" ")
                    names.forEachIndexed { index, name ->
                        if (index > 0) append(", ")
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = cancelled)) { append(name) }
                    }
                }
                Text(instead, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            val badges = entry.cancelled || entry.originalTeacher.isNotBlank() || entry.irregular || entry.originalRoom.isNotBlank() ||
                entry.exam != null || current || entry.periods > 1
            if (badges) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                    if (current) AccentBadge(stringResource(R.string.now), colors.primary, colors.onPrimary)
                    if (entry.exam != null) AccentBadge("${stringResource(R.string.assistant_card_exam)} · ${entry.exam}", examContainer(), examContent())
                    if (entry.cancelled) AccentBadge(stringResource(R.string.lesson_cancelled), cancelled.copy(alpha = 0.16f), cancelled)
                    // A substitute teacher is a substitution; an extra lesson or a school event is just changed.
                    if (!entry.cancelled && entry.originalTeacher.isNotBlank()) {
                        AccentBadge(stringResource(R.string.lesson_substitution), warningContainer(), changed)
                    } else if (!entry.cancelled && entry.irregular) {
                        AccentBadge(stringResource(R.string.lesson_changed), warningContainer(), changed)
                    }
                    if (!entry.cancelled && entry.originalRoom.isNotBlank()) AccentBadge(stringResource(R.string.lesson_room_changed), warningContainer(), changed)
                    if (entry.periods > 1 && !entry.cancelled) AccentBadge(stringResource(R.string.assistant_card_double), colors.secondaryContainer, colors.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun FreeRow(start: LocalTime, end: LocalTime) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${stringResource(R.string.assistant_card_free)} · ${start.format(CLOCK)}–${end.format(CLOCK)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 64.dp),
        )
    }
}
