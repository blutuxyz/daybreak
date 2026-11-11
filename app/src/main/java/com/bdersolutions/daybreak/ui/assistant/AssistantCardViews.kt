package com.bdersolutions.daybreak.ui.assistant

import com.bdersolutions.daybreak.ui.components.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import com.bdersolutions.daybreak.ui.timetable.LessonFocus
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.assistant.AbsenceCard
import com.bdersolutions.daybreak.data.assistant.AssistantCard
import com.bdersolutions.daybreak.data.assistant.DayCard
import com.bdersolutions.daybreak.data.assistant.ExamCard
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.ui.absences.formatAbsenceDuration
import com.bdersolutions.daybreak.ui.components.AccentBadge
import com.bdersolutions.daybreak.ui.components.examContainer
import com.bdersolutions.daybreak.ui.components.examContent
import com.bdersolutions.daybreak.ui.components.motionAnimationsEnabled
import com.bdersolutions.daybreak.ui.components.rememberSubjectTone
import com.bdersolutions.daybreak.ui.settings.currentAppLanguage
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val CARD_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** How many rows a card shows before "N more". */
private const val COLLAPSED_ROWS = 4

/** Any card the assistant showed, by kind. */
@Composable
internal fun AssistantCardView(card: AssistantCard, onOpenLesson: ((LessonFocus) -> Unit)? = null) {
    when (card) {
        is DayCard -> DayCardView(card, onOpenLesson = onOpenLesson)
        is ExamCard -> ExamCardView(card, onOpenLesson)
        is AbsenceCard -> AbsenceCardView(card)
    }
}

@Composable
private fun appLocale(): Locale = if (currentAppLanguage() == AppLanguage.GERMAN) Locale.GERMAN else Locale.ENGLISH

/** The frame every card shares with the day card: bordered, an icon, a title, a quiet line under it. */
@Composable
private fun CardFrame(icon: ImageVector, title: String, subtitle: String?, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = colors.primary)
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = colors.outlineVariant)
            content()
        }
    }
}

/** "4 more" / "Show less" under a list that is longer than [COLLAPSED_ROWS]. */
@Composable
private fun MoreToggle(hidden: Int, expanded: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(expanded) { rotation.animateTo(if (expanded) 180f else 0f, spring(dampingRatio = 0.8f, stiffness = 400f)) }
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (expanded) stringResource(R.string.assistant_card_less) else pluralStringResource(R.plurals.assistant_card_more, hidden, hidden),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
        )
        Icon(
            Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = rotation.value },
        )
    }
}

/** A list that shows its first rows and slides the rest open on "N more". */
@Composable
private fun <T> CollapsibleRows(items: List<T>, row: @Composable (T) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.padding(top = 6.dp)) {
        items.take(COLLAPSED_ROWS).forEach { row(it) }
        if (items.size > COLLAPSED_ROWS) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) + fadeIn(tween(200, delayMillis = 60)),
                exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 450f)) + fadeOut(tween(120)),
            ) {
                Column { items.drop(COLLAPSED_ROWS).forEach { row(it) } }
            }
            MoreToggle(hidden = items.size - COLLAPSED_ROWS, expanded = expanded, onToggle = { expanded = !expanded })
        }
    }
}

/** "Today", "Tomorrow", "in 3 days", "Yesterday", "4 days ago" — always counted from today, so it stays true in old chats. */
@Composable
private fun relativeDay(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date).toInt()
    return when {
        days == 0 -> stringResource(R.string.assistant_card_today)
        days == 1 -> stringResource(R.string.assistant_card_tomorrow)
        days == -1 -> stringResource(R.string.assistant_card_yesterday)
        days > 1 -> pluralStringResource(R.plurals.assistant_card_in_days, days, days)
        else -> pluralStringResource(R.plurals.assistant_card_days_ago, -days, -days)
    }
}

// ---------------------------------------------------------------------------------------------------
// Exams
// ---------------------------------------------------------------------------------------------------

/**
 * Exams as a card: a date tile in the subject's colour, the subject, time, teacher and room, what the
 * school called it, and its topic text (tap to read all of it). Past exams are dimmed; a badge says how
 * far away each one is.
 */
@Composable
internal fun ExamCardView(card: ExamCard, onOpenLesson: ((LessonFocus) -> Unit)? = null) {
    val locale = appLocale()
    val range = remember(card.from, card.to, locale) {
        val pattern = DateTimeFormatter.ofPattern(if (locale == Locale.GERMAN) "d. MMM" else "MMM d", locale)
        "${card.from.format(pattern)} – ${card.to.format(pattern)}"
    }
    CardFrame(Icons.Outlined.School, stringResource(R.string.assistant_card_exams_title), range) {
        if (card.exams.isEmpty()) {
            Text(
                stringResource(R.string.assistant_card_exams_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        } else {
            val today = remember { LocalDate.now() }
            CollapsibleRows(card.exams) { exam -> ExamRow(exam, today, locale, onOpenLesson) }
        }
    }
}

@Composable
private fun ExamRow(exam: ExamCard.Exam, today: LocalDate, locale: Locale, onOpenLesson: ((LessonFocus) -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val tone = rememberSubjectTone(exam.subject)
    val past = exam.date.isBefore(today)
    val soon = !past && ChronoUnit.DAYS.between(today, exam.date) <= 1
    var open by rememberSaveable(exam.date, exam.start, exam.subject) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            // The row opens the exam's lesson in the timetable; its topic text expands on its own.
            .clickable(enabled = onOpenLesson != null) {
                onOpenLesson?.invoke(LessonFocus(exam.date, exam.start, exam.end, exam.subject))
            }
            .alpha(if (past) 0.6f else 1f)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 400f)),
    ) {
        // The date as a small calendar leaf in the subject's colour.
        Column(
            modifier = Modifier
                .width(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tone.container)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                exam.date.format(DateTimeFormatter.ofPattern("EEE", locale)).trimEnd('.'),
                style = MaterialTheme.typography.labelSmall,
                color = tone.onContainer,
            )
            Text(exam.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tone.accent)
            Text(
                exam.date.format(DateTimeFormatter.ofPattern("MMM", locale)).trimEnd('.'),
                style = MaterialTheme.typography.labelSmall,
                color = tone.onContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    exam.subjectName.ifBlank { exam.subject }.ifBlank { stringResource(R.string.lesson_unknown_subject) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                AccentBadge(
                    text = relativeDay(exam.date, today),
                    containerColor = when {
                        soon -> examContainer()
                        past -> colors.surfaceContainerHighest
                        else -> colors.primaryContainer
                    },
                    contentColor = when {
                        soon -> examContent()
                        past -> colors.onSurfaceVariant
                        else -> colors.onPrimaryContainer
                    },
                )
            }
            val meta = listOfNotNull(
                "${exam.start.format(CARD_CLOCK)}–${exam.end.format(CARD_CLOCK)}",
                exam.teacher.ifBlank { null },
                exam.room.ifBlank { null },
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (exam.name.isNotBlank()) {
                Text(exam.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (exam.text.isNotBlank()) {
                Text(
                    exam.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = if (open) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { open = !open },
                )
            }
        }
        if (onOpenLesson != null) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.outline,
                modifier = Modifier.align(Alignment.CenterVertically).size(20.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// Absences
// ---------------------------------------------------------------------------------------------------

/**
 * The school year's absences as a card: the time that counts (as on the Absences tab, in the unit picked
 * there), how much of it is excused, a bar that fills to the excused share, and the entries, newest first.
 */
@Composable
internal fun AbsenceCardView(card: AbsenceCard) {
    val count = card.excusedCount + card.openCount
    val subtitle = listOf(
        stringResource(R.string.assistant_card_absences_year),
        stringResource(R.string.absences_count, count),
    ).joinToString(" · ")
    CardFrame(Icons.Outlined.EventBusy, stringResource(R.string.assistant_card_absences_title), subtitle) {
        if (count == 0) {
            Text(
                stringResource(R.string.assistant_card_absences_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            return@CardFrame
        }
        AbsenceTotals(card)
        val locale = appLocale()
        CollapsibleRows(card.entries) { entry -> AbsenceRow(entry, locale) }
        if (card.recordedMinutes != card.countedMinutes) {
            Text(
                stringResource(R.string.assistant_card_absences_clipped),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun AbsenceTotals(card: AbsenceCard) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Equal tiles: each value stays on one line (shrinking a little if it must), all as tall as the tallest.
        Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.absences_total), formatAbsenceDuration(Duration.ofMinutes(card.countedMinutes)), colors.surfaceContainerHighest, colors.onSurface, Modifier.weight(1f))
            StatTile(stringResource(R.string.absences_excused), formatAbsenceDuration(Duration.ofMinutes(card.excusedMinutes)), colors.secondaryContainer, colors.onSecondaryContainer, Modifier.weight(1f))
            StatTile(
                stringResource(R.string.absences_unexcused),
                formatAbsenceDuration(Duration.ofMinutes(card.openMinutes)),
                if (card.openMinutes > 0) colors.errorContainer else colors.surfaceContainerHighest,
                if (card.openMinutes > 0) colors.onErrorContainer else colors.onSurfaceVariant,
                Modifier.weight(1f),
            )
        }
        // The excused share, filling in once when the card appears.
        val share = if (card.countedMinutes > 0) card.excusedMinutes.toFloat() / card.countedMinutes else 0f
        val animations = motionAnimationsEnabled()
        val fill = remember { Animatable(if (animations) 0f else share) }
        LaunchedEffect(share) { if (animations) fill.animateTo(share, spring(dampingRatio = 0.9f, stiffness = 120f)) else fill.snapTo(share) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(if (card.openMinutes > 0) colors.error.copy(alpha = 0.35f) else colors.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill.value.coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(colors.secondary),
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AbsenceRow(entry: AbsenceCard.Entry, locale: Locale) {
    val colors = MaterialTheme.colorScheme
    val day = DateTimeFormatter.ofPattern(if (locale == Locale.GERMAN) "EEE, d.M." else "EEE, MMM d", locale)
    val sameDay = entry.start.toLocalDate() == entry.end.toLocalDate()
    val when_ = if (sameDay) {
        "${entry.start.toLocalDate().format(day)} · ${entry.start.toLocalTime().format(CARD_CLOCK)}–${entry.end.toLocalTime().format(CARD_CLOCK)}"
    } else {
        "${entry.start.toLocalDate().format(day)} – ${entry.end.toLocalDate().format(day)}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(if (entry.excused) colors.secondary else colors.error),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(when_, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val detail = listOf(entry.reason, entry.text).filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(formatAbsenceDuration(Duration.ofMinutes(entry.countedMinutes)), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            AccentBadge(
                text = stringResource(if (entry.excused) R.string.absences_excused_short else R.string.absences_unexcused_short),
                containerColor = if (entry.excused) colors.secondaryContainer else colors.errorContainer,
                contentColor = if (entry.excused) colors.onSecondaryContainer else colors.onErrorContainer,
            )
        }
    }
}
