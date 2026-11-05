package com.bdersolutions.daybreak.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.schedule.DayPhase
import com.bdersolutions.daybreak.data.schedule.DayStatus
import com.bdersolutions.daybreak.data.schedule.LessonRun
import java.time.format.DateTimeFormatter

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * "Now and next": the lesson that is on with its time left, the one after it, when school ends and the
 * next free period. It sits at the top of today's day view and moves on with the clock.
 */
@Composable
internal fun NowNextCard(status: DayStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(stringResource(R.string.now_next_now))
                    NowContent(status)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(stringResource(R.string.now_next_next))
                    NextContent(status)
                }
            }
            footer(status)?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
    )
}

@Composable
private fun NowContent(status: DayStatus) {
    when (status.phase) {
        DayPhase.IN_LESSON -> status.current?.let { run ->
            LessonText(run)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { status.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.now_next_left, durationText(status.minutesLeft)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        DayPhase.BEFORE_SCHOOL -> BigText(
            stringResource(R.string.now_next_before, status.schoolStart?.format(CLOCK).orEmpty()),
        )
        DayPhase.BREAK -> {
            val free = status.freePeriod
            if (status.inFreePeriod && free != null) {
                BigText(stringResource(R.string.now_next_free))
                Text(
                    text = stringResource(R.string.now_next_free_until, free.end.format(CLOCK)),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                BigText(stringResource(R.string.now_next_break))
            }
        }
        DayPhase.AFTER_SCHOOL -> BigText(stringResource(R.string.now_next_done))
        DayPhase.NO_LESSONS -> Unit
    }
}

@Composable
private fun NextContent(status: DayStatus) {
    val next = status.next
    if (next == null) {
        BigText(stringResource(R.string.now_next_no_more))
        return
    }
    LessonText(next)
    Text(
        text = stringResource(R.string.now_next_in, durationText(status.minutesUntilNext)),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun LessonText(run: LessonRun) {
    val lesson = run.lesson
    BigText(lesson.subject.ifBlank { lesson.subjectLong }.ifBlank { "?" })
    val details = listOfNotNull(
        lesson.room.takeIf { it.isNotBlank() }?.let { stringResource(R.string.now_next_room, it) },
        lesson.teacher.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    if (details.isNotEmpty()) {
        Text(
            text = details,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BigText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The line under the card: when school ends and the next free period, as far as they add something. */
@Composable
private fun footer(status: DayStatus): String? {
    val parts = mutableListOf<String>()
    if (status.phase != DayPhase.AFTER_SCHOOL) {
        status.schoolEnd?.let { parts += stringResource(R.string.now_next_school_ends, it.format(CLOCK)) }
    }
    val free = status.freePeriod
    // While a free period is on, the left half already says so.
    if (free != null && !status.inFreePeriod && status.phase != DayPhase.AFTER_SCHOOL) {
        parts += stringResource(R.string.now_next_free_period, free.start.format(CLOCK), free.end.format(CLOCK))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun durationText(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.duration_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.duration_hours, minutes / 60)
    else -> stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
}
