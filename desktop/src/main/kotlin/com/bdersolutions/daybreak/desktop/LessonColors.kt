package com.bdersolutions.daybreak.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.untis.UntisLesson

/** The colours the Android app shows when a status has no custom colour. */
private val cancelledDefault = Color(0xFFB3261E)
private val examDefault = Color(0xFF7D5260)
private val substitutionDefault = Color(0xFF7B5800)

/** Which status colour a row changes. */
private enum class StatusColor { CANCELLED, EXAM, SUBSTITUTION }

/**
 * Lesson colours: one colour per subject, the colours for cancelled lessons, exams and
 * substitutions, and whether the whole card takes the colour. Each choice is saved at once.
 */
@Composable
fun LessonColorsScreen(container: DesktopContainer) {
    val settings by container.settings.settings.collectAsState()
    val language = LocalLanguage.current
    val colors = settings.lessonColors
    val subjects = remember(settings) {
        container.timetable.cache.value.weeks.values
            .flatMap { week -> week.lessons.map(UntisLesson::subject) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    fun setStatus(target: StatusColor, argb: Long?) {
        container.settings.updateLessonColors {
            when (target) {
                StatusColor.CANCELLED -> it.copy(cancelledColor = argb)
                StatusColor.EXAM -> it.copy(examColor = argb)
                StatusColor.SUBSTITUTION -> it.copy(substitutionColor = argb)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Fächer", "Subjects"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (subjects.isEmpty()) {
                    Text(tr("Lade zuerst den Stundenplan; die Fächer erscheinen hier.", "Load the timetable first; the subjects appear here."))
                }
                subjects.forEach { subject ->
                    ColorChoiceRow(
                        label = subject,
                        current = colors.subjectColors[subject],
                        onPick = { argb ->
                            container.settings.updateLessonColors {
                                it.copy(subjectColors = if (argb == null) it.subjectColors - subject else it.subjectColors + (subject to argb))
                            }
                        },
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Status", "Statuses"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ColorChoiceRow(
                    label = tr("Entfällt", "Cancelled"),
                    current = colors.cancelledColor,
                    fallback = cancelledDefault,
                    onPick = { setStatus(StatusColor.CANCELLED, it) },
                )
                ColorChoiceRow(
                    label = tr("Prüfung", "Exam"),
                    current = colors.examColor,
                    fallback = examDefault,
                    onPick = { setStatus(StatusColor.EXAM, it) },
                )
                ColorChoiceRow(
                    label = tr("Vertretung", "Substitution"),
                    current = colors.substitutionColor,
                    fallback = substitutionDefault,
                    onPick = { setStatus(StatusColor.SUBSTITUTION, it) },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(
                        checked = colors.fillWholeCard,
                        onCheckedChange = { checked ->
                            container.settings.updateLessonColors { it.copy(fillWholeCard = checked) }
                        },
                    )
                    Text(tr("Ganze Karte in der Farbe füllen", "Fill the whole card with the colour"))
                }
            }
        }
    }
}

/**
 * One row of colour dots. Picking the dot that is already chosen, or the "Automatic" button,
 * removes the custom colour. [fallback] is only shown as a hint when nothing is chosen.
 */
@Composable
private fun ColorChoiceRow(
    label: String,
    current: Long?,
    fallback: Color? = null,
    onPick: (Long?) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyLarge)
        if (current == null && fallback != null) {
            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(fallback))
        }
        lessonPalette.forEach { argb ->
            Box(
                modifier = Modifier
                    .size(if (current == argb) 30.dp else 24.dp)
                    .clip(CircleShape)
                    .background(Color(argb.toInt()))
                    .clickable { onPick(argb) },
            )
        }
        OutlinedButton(onClick = { onPick(null) }) {
            Text(tr("Automatisch", "Automatic"))
        }
    }
}

