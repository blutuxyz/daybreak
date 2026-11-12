package com.bdersolutions.daybreak.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.data.account.Access
import com.bdersolutions.daybreak.data.account.Entitlement
import com.bdersolutions.daybreak.data.branding.AppNamePreset
import com.bdersolutions.daybreak.data.branding.AppNaming
import com.bdersolutions.daybreak.data.schedule.IcsExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The name the app shows in its own title (the timetable header and the window title). Changing it is
 * part of Plus (and stays for old Ko-fi supporters), as on Android: without it the app is always
 * "Daybreak". Windows has no launcher name to change, so this is the only place the name appears.
 * [allowed] is `access.allows(Entitlement.APP_NAME)`.
 */
fun appTitle(allowed: Boolean, custom: String): String = AppNaming.resolve(allowed, AppNamePreset.DAYBREAK, custom)

/** The card of extras on the More page: starred and tinted, dimmed with a lock until the plan unlocks them. */
@Composable
fun SupporterExtras(
    access: Access,
    currentTitle: String,
    onOpenAppName: () -> Unit,
    onOpenCalendarExport: () -> Unit,
    onLocked: (Entitlement) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val nameAllowed = access.allows(Entitlement.APP_NAME)
    val calendarAllowed = access.allows(Entitlement.CALENDAR_EXPORT)
    val paid = nameAllowed || calendarAllowed
    val lockedNote = tr("Teil von Plus", "Part of Plus")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.padding(start = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Star, contentDescription = null, tint = scheme.tertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(
                tr("Extras mit Plus", "Extras with Plus"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.tertiary,
            )
        }
        Surface(
            shape = CardShape,
            color = scheme.tertiaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, scheme.tertiary.copy(alpha = if (paid) 0.7f else 0.35f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Box(modifier = Modifier.alpha(if (nameAllowed) 1f else 0.6f)) {
                    SettingsRow(
                        icon = Icons.Outlined.DriveFileRenameOutline,
                        title = tr("App-Name", "App name"),
                        subtitle = if (nameAllowed) currentTitle else lockedNote,
                        onClick = if (nameAllowed) onOpenAppName else ({ onLocked(Entitlement.APP_NAME) }),
                        trailing = extraTrailing(nameAllowed),
                    )
                }
                HorizontalDivider(color = scheme.tertiary.copy(alpha = 0.25f))
                Box(modifier = Modifier.alpha(if (calendarAllowed) 1f else 0.6f)) {
                    SettingsRow(
                        icon = Icons.Outlined.Event,
                        title = tr("Kalender-Export", "Calendar export"),
                        subtitle = if (calendarAllowed) {
                            tr("Stundenplan als Kalenderdatei (.ics)", "Timetable as a calendar file (.ics)")
                        } else {
                            lockedNote
                        },
                        onClick = if (calendarAllowed) onOpenCalendarExport else ({ onLocked(Entitlement.CALENDAR_EXPORT) }),
                        trailing = extraTrailing(calendarAllowed),
                    )
                }
            }
        }
    }
}

/** A lock before the unlock, a small "New" pill and the chevron after it. */
@Composable
private fun extraTrailing(paid: Boolean): @Composable () -> Unit = {
    val scheme = MaterialTheme.colorScheme
    if (paid) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = scheme.tertiary, contentColor = scheme.onTertiary, modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    tr("NEU", "NEW"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
            )
        }
    } else {
        Icon(Icons.Outlined.Lock, contentDescription = tr("Gesperrt", "Locked"), tint = scheme.onSurfaceVariant)
    }
}

@Composable
private fun ExtraPage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/** Supporter extra: the title shown in the app. */
@Composable
fun AppNamePage(container: DesktopContainer) {
    val settings by container.settings.settings.collectAsState()
    val access by container.access.collectAsState()
    val paid = access.allows(Entitlement.APP_NAME)
    var text by remember { mutableStateOf(settings.customAppName) }
    ExtraPage {
        Text(
            tr(
                "Der Name erscheint als Titel im Stundenplan und in der Titelleiste des Fensters. Unter Windows gibt es keinen Startmenü-Namen, den die App ändern könnte.",
                "The name appears as the title of the timetable and in the window's title bar. Windows has no launcher name the app could change.",
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                text = typed.take(AppNaming.MAX_CUSTOM_LENGTH)
                if (paid) container.settings.setCustomAppName(text)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr("Eigener Name", "Your own name")) },
            placeholder = { Text(AppNaming.DEFAULT) },
            supportingText = { Text("${text.length}/${AppNaming.MAX_CUSTOM_LENGTH}") },
            singleLine = true,
            shape = CircleShape,
            enabled = paid,
        )
        Text(
            tr("Leer lassen, um wieder „Daybreak“ zu zeigen.", "Leave it empty to show “Daybreak” again."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class ExportRange(val weeks: Int) { THIS_WEEK(1), FOUR_WEEKS(4), EIGHT_WEEKS(8) }

/** Supporter extra: the timetable as an iCalendar (.ics) file to save and open in any calendar app. */
@Composable
fun CalendarExportPage(container: DesktopContainer) {
    val language = LocalLanguage.current
    val scope = rememberCoroutineScope()
    var range by remember { mutableStateOf(ExportRange.FOUR_WEEKS) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun export() {
        if (working) return
        working = true
        message = null
        scope.launch {
            try {
                val text = buildCalendar(container, range.weeks, language)
                val saved = saveTextFile(text, "daybreak-stundenplan.ics")
                failed = false
                message = if (saved) trFor(language, "Kalenderdatei gespeichert.", "Calendar file saved.") else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                message = e.userMessage(language, trFor(language, "Der Export ist fehlgeschlagen.", "The export failed."))
            } finally {
                working = false
            }
        }
    }

    ExtraPage {
        Text(
            tr(
                "Dein Stundenplan als Kalenderdatei: Stunden, Räume, Lehrkräfte und Prüfungen. Entfallene Stunden werden nicht eingetragen, Doppelstunden sind ein Termin. Die Datei entsteht auf deinem Computer und wird nirgends hingeschickt.",
                "Your timetable as a calendar file: lessons, rooms, teachers and exams. Cancelled lessons are left out and a double lesson is one entry. The file is made on your computer and is not sent anywhere.",
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(tr("Zeitraum", "Period"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SegmentedRow(
            options = ExportRange.entries,
            selected = range,
            label = {
                when (it) {
                    ExportRange.THIS_WEEK -> trFor(language, "Diese Woche", "This week")
                    ExportRange.FOUR_WEEKS -> trFor(language, "4 Wochen", "4 weeks")
                    ExportRange.EIGHT_WEEKS -> trFor(language, "8 Wochen", "8 weeks")
                }
            },
            onSelect = { range = it },
        )
        Button(
            onClick = { export() },
            enabled = !working,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(52.dp).handCursor(),
        ) {
            if (working) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Text(tr("Als .ics-Datei speichern", "Save as .ics file"))
            }
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            tr(
                "Ändert sich dein Stundenplan später, exportiere ihn einfach noch einmal. Bereits importierte Termine werden ersetzt, wenn deine Kalender-App das unterstützt.",
                "If your timetable changes later, just export it again. Entries you imported before are replaced when your calendar app supports that.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Loads the missing weeks, then builds the file. Fails only when no lesson could be loaded at all. */
private suspend fun buildCalendar(container: DesktopContainer, weekCount: Int, language: com.bdersolutions.daybreak.data.settings.AppLanguage): String {
    check(container.access.value.allows(Entitlement.CALENDAR_EXPORT)) { "Calendar export is part of Plus." }
    val repository = container.timetable
    val first = repository.weekStartFor(LocalDate.now())
    val weeks = (0 until weekCount).map { first.plusWeeks(it.toLong()) }
    var failure: Throwable? = null
    for (week in weeks) failure = repository.ensureWeek(week).error ?: failure
    val cache = repository.cache.value
    val lessons = weeks.flatMap { cache.weeks[it]?.lessons.orEmpty() }
    if (lessons.isEmpty()) throw failure ?: IllegalStateException("No timetable loaded.")
    val labels = IcsExport.Labels(
        calendarName = AppNaming.DEFAULT,
        examPrefix = trFor(language, "Prüfung", "Exam"),
        teacher = trFor(language, "Lehrkraft", "Teacher"),
        className = trFor(language, "Klasse", "Class"),
        substituted = trFor(language, "Vertretung oder Änderung", "Substitution or change"),
    )
    return IcsExport.build(lessons, cache.exams, labels, first, first.plusWeeks(weekCount.toLong()).minusDays(1))
}
