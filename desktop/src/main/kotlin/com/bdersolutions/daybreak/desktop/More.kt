package com.bdersolutions.daybreak.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.data.settings.AbsenceUnit
import com.bdersolutions.daybreak.data.settings.AccentColor
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.data.settings.MotionStyle
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.data.settings.StartView
import com.bdersolutions.daybreak.data.settings.SubjectColorMode
import com.bdersolutions.daybreak.data.settings.TextScale
import com.bdersolutions.daybreak.data.settings.ThemeMode
import com.bdersolutions.daybreak.desktop.update.UpdateState
import com.bdersolutions.daybreak.untis.UntisSession
import kotlin.system.exitProcess

/** The palette the Android app offers for subject colours, as ARGB values. */
internal val lessonPalette: List<Long> = listOf(
    0xFF3B6EA8L, 0xFF3F7D4E, 0xFF7457A8, 0xFFC0622E,
    0xFFB04A6E, 0xFF2E8B8B, 0xFFB8860B, 0xFF5C5C5C,
)

/** The Mehr page: the profile, then the lists the Android More tab shows. */
@Composable
fun MoreScreen(
    container: DesktopContainer,
    session: UntisSession,
    onOpen: (SubRoute) -> Unit,
    onLogout: () -> Unit,
) {
    val settings by container.settings.settings.collectAsState()
    val language = LocalLanguage.current
    val debugUnlocked by container.debugUnlock.unlocked.collectAsState()
    val paid by container.supportStore.paid.collectAsState()
    // The dialog that unlocks the extras or hides the Ko-fi button; the text says what paying does.
    var supportNote by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    ) {
        PageHeader(title = tr("Mehr", "More"), subtitle = session.studentName.ifBlank { session.username })
        Column(
            modifier = Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileHeader(session)
            // The Ko-fi button hides once a payment is confirmed. Hiding it is paid, as on Android, and
            // needs the update server to confirm; builds without one keep the button.
            if (!paid) {
                KofiButton(onClick = { openSupportPage() })
                if (container.supportApi != null) {
                    TextButton(
                        onClick = { supportNote = trFor(language, "Dieser Button verschwindet, sobald du auf Ko-fi zahlst.", "This button hides when you pay on Ko-fi.") },
                        modifier = Modifier.align(Alignment.CenterHorizontally).handCursor(),
                    ) {
                        Text(str("settings_support_hide"), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            // The extras need the update server to confirm a payment, so builds without one hide the card.
            if (container.supportApi != null || paid) {
                SupporterExtras(
                    paid = paid,
                    currentTitle = appTitle(paid, settings.customAppName),
                    onOpenAppName = { onOpen(SubRoute.APP_NAME) },
                    onOpenCalendarExport = { onOpen(SubRoute.CALENDAR_EXPORT) },
                    onLocked = {
                        supportNote = trFor(language, "Dieses Extra wird freigeschaltet, sobald du auf Ko-fi zahlst.", "This extra unlocks when you pay on Ko-fi.")
                    },
                )
            }
            SectionLabel(tr("Mein Profil", "My profile"))
            ListCard {
                SettingsRow(
                    icon = Icons.Outlined.AccountCircle,
                    title = tr("Mein Konto", "My account"),
                    subtitle = tr("Mitteilungen deiner Schule", "Notices from your school"),
                    onClick = { onOpen(SubRoute.NOTICES) },
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Outlined.Mail,
                    title = tr("Nachrichten", "Messages"),
                    subtitle = tr("Eingang, Gesendet und Entwürfe", "Inbox, sent and drafts"),
                    onClick = { onOpen(SubRoute.MESSAGES) },
                )
            }

            SectionLabel(tr("Einstellungen", "Settings"))
            ListCard {
                SettingsRow(
                    icon = Icons.Outlined.DarkMode,
                    title = tr("Darstellung", "Appearance"),
                    subtitle = trFor(language, "${themeName(settings.themeMode, language)} · ${accentName(settings.accent, language)}", "${themeName(settings.themeMode, language)} · ${accentName(settings.accent, language)}"),
                    onClick = { onOpen(SubRoute.APPEARANCE) },
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Outlined.CalendarMonth,
                    title = tr("Stundenplan", "Timetable"),
                    subtitle = trFor(language, "Startansicht: ${startName(settings.startView, language)}", "Start view: ${startName(settings.startView, language)}"),
                    onClick = { onOpen(SubRoute.TIMETABLE_SETTINGS) },
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Outlined.Brush,
                    title = tr("Stundenfarben", "Lesson colours"),
                    subtitle = tr("Fächer und Status einzeln einfärben.", "Colour subjects and statuses one by one."),
                    onClick = { onOpen(SubRoute.LESSON_COLORS) },
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Outlined.Language,
                    title = tr("Sprache", "Language"),
                    subtitle = languageName(settings.language),
                    onClick = { onOpen(SubRoute.LANGUAGE) },
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Outlined.Notifications,
                    title = tr("Benachrichtigungen", "Notifications"),
                    subtitle = if (settings.notificationsEnabled) {
                        tr("Bei Änderungen benachrichtigen", "Notify about changes")
                    } else {
                        tr("Aus", "Off")
                    },
                    onClick = { onOpen(SubRoute.NOTIFICATIONS) },
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Outlined.Sync,
                    title = tr("Synchronisierung & Updates", "Sync & updates"),
                    subtitle = if (settings.syncEnabled) {
                        trFor(language, "Alle ${settings.syncIntervalMinutes} Minuten", "Every ${settings.syncIntervalMinutes} minutes")
                    } else {
                        tr("Aus", "Off")
                    },
                    onClick = { onOpen(SubRoute.SYNC_UPDATES) },
                )
            }

            SectionLabel(tr("Sonstiges", "Other"))
            ListCard {
                if (debugUnlocked) {
                    SettingsRow(
                        icon = Icons.Outlined.BugReport,
                        title = str("settings_debug_title"),
                        subtitle = str("settings_debug_desc"),
                        onClick = { onOpen(SubRoute.DEBUG) },
                    )
                    HorizontalDivider()
                }
                SettingsRow(
                    icon = Icons.Outlined.Favorite,
                    title = str("settings_support"),
                    subtitle = str("settings_support_desc"),
                    onClick = { openSupportPage() },
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Outlined.PrivacyTip,
                    title = str("settings_legal"),
                    subtitle = null,
                    onClick = { onOpen(SubRoute.LEGAL) },
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = str("settings_about"),
                    subtitle = null,
                    onClick = { onOpen(SubRoute.ABOUT) },
                )
            }
            // Log out asks first, inside the card, as on Android.
            LogoutCard(onConfirm = onLogout)
        }
    }
    supportNote?.let { note ->
        HideSupportButtonDialog(
            unlockCode = container.supportStore.unlockCode(),
            note = note,
            onDismiss = { supportNote = null },
            onCheckPayment = { checkSupportPayment(container) },
        )
    }
}

@Composable
private fun ProfileHeader(session: UntisSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    session.studentName.ifBlank { session.username }.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    session.studentName.ifBlank { session.username },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    session.school,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 2.dp),
    )
}

/** A rounded card that groups a list of rows, like the Android More tab. */
@Composable
private fun ListCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

/** One row of a list: a tinted icon, a title and a subtitle, and a chevron when it opens something. */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickableRow(onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedShape16).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing() else Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val RoundedShape16 = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)

/** A segmented control in the app's style: one pill split into equal parts, the chosen one filled. */
@Composable
fun <T> SegmentedRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(PillShape)
            .background(Color.Transparent)
            .padding(0.dp),
    ) {
        options.forEachIndexed { index, option ->
            val chosen = option == selected
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = if (index == 0) 50.dp else 0.dp,
                bottomStart = if (index == 0) 50.dp else 0.dp,
                topEnd = if (index == options.lastIndex) 50.dp else 0.dp,
                bottomEnd = if (index == options.lastIndex) 50.dp else 0.dp,
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(shape)
                    .background(if (chosen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickableRow { onSelect(option) }
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chosen) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (chosen) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index < options.lastIndex) {
                Box(
                    modifier = Modifier.width(1.dp).height(48.dp).background(MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

/** A settings block: a title row with an icon, then the controls. */
@Composable
private fun SettingBlock(icon: ImageVector, title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        content()
    }
}

@Composable
private fun SettingSwitch(label: String, description: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** A page body: a card with the rows, centred at a readable width. */
@Composable
private fun SettingsPage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ListCard(content)
        }
    }
}

@Composable
fun AppearanceSettings(container: DesktopContainer) {
    val settings by container.settings.settings.collectAsState()
    val language = LocalLanguage.current
    SettingsPage {
        SettingBlock(Icons.Outlined.DarkMode, tr("Design", "Theme")) {
            SegmentedRow(ThemeMode.entries, settings.themeMode, { themeName(it, language) }) {
                // Ten taps on "System" unlock the debug page, as on Android.
                if (it == ThemeMode.SYSTEM) container.debugUnlock.tapSystemTheme()
                container.settings.setThemeMode(it)
            }
        }
        HorizontalDivider()
        SettingSwitch(
            label = tr("Farben aus Hintergrundbild", "Colours from wallpaper"),
            description = tr("Nur auf dem Android-Gerät verfügbar. Auf dem Desktop wählst du die Akzentfarbe unten.",
                "Only on Android. On desktop, pick the accent colour below."),
            checked = false,
            enabled = false,
        ) {}
        HorizontalDivider()
        SettingBlock(Icons.Outlined.ColorLens, tr("Akzentfarbe", "Accent colour")) {
            SegmentedRow(AccentColor.entries, settings.accent, { accentName(it, language) }) { container.settings.setAccent(it) }
        }
        HorizontalDivider()
        SettingBlock(Icons.Outlined.FormatSize, tr("Textgröße", "Text size")) {
            SegmentedRow(TextScale.entries, settings.textScale, { textScaleName(it, language) }) { container.settings.setTextScale(it) }
        }
        HorizontalDivider()
        SettingBlock(Icons.Outlined.Speed, tr("Bewegung", "Motion")) {
            SegmentedRow(MotionStyle.entries, settings.motionStyle, { motionName(it, language) }) { container.settings.setMotionStyle(it) }
        }
    }
}

@Composable
fun TimetableSettings(container: DesktopContainer) {
    val settings by container.settings.settings.collectAsState()
    val language = LocalLanguage.current
    SettingsPage {
        SettingBlock(Icons.Outlined.CalendarMonth, tr("Startansicht", "Start view")) {
            SegmentedRow(StartView.entries, settings.startView, { startName(it, language) }) { container.settings.setStartView(it) }
        }
        HorizontalDivider()
        SettingBlock(Icons.Outlined.Speed, tr("Fehlstunden zeigen", "Show absences as")) {
            SegmentedRow(AbsenceUnit.entries, settings.absenceUnit, { absenceUnitName(it, language) }) { container.settings.setAbsenceUnit(it) }
        }
        HorizontalDivider()
        SettingBlock(Icons.Outlined.Brush, tr("Fachfarben", "Subject colours")) {
            SegmentedRow(SubjectColorMode.entries, settings.subjectColorMode, { colorModeName(it, language) }) {
                container.settings.setSubjectColorMode(it)
            }
        }
        HorizontalDivider()
        SettingSwitch(
            label = tr("Jetzt & als Nächstes", "Now and next"),
            description = tr("Die Karte oben zeigt die aktuelle und die nächste Stunde.", "The card at the top shows the current and next lesson."),
            checked = settings.showNowNext,
        ) { container.settings.setShowNowNext(it) }
    }
}

@Composable
fun LanguageSettings(container: DesktopContainer) {
    val settings by container.settings.settings.collectAsState()
    SettingsPage {
        SettingBlock(Icons.Outlined.Language, tr("Sprache", "Language")) {
            SegmentedRow(AppLanguage.entries, settings.language, { languageName(it) }) { container.settings.setLanguage(it) }
        }
    }
}

@Composable
fun NotificationSettings(container: DesktopContainer) {
    val settings by container.settings.settings.collectAsState()
    val language = LocalLanguage.current
    SettingsPage {
        SettingSwitch(
            label = tr("Bei Änderungen benachrichtigen", "Notify about changes"),
            description = tr("Wenn sich eine Stunde, ein Raum oder eine Lehrkraft ändert.", "When a lesson, room or teacher changes."),
            checked = settings.notificationsEnabled,
        ) { container.settings.setNotificationsEnabled(it) }
        HorizontalDivider()
        SettingSwitch(
            label = tr("Tageszusammenfassung", "Morning summary"),
            description = tr("Eine Nachricht vor der ersten Stunde.", "A message before the first lesson."),
            checked = settings.morningSummaryEnabled,
        ) { container.settings.setMorningSummaryEnabled(it) }
        HorizontalDivider()
        SettingBlock(Icons.Outlined.Notifications, tr("Vorlauf", "Lead time")) {
            SegmentedRow(listOf(30, 60, 90), settings.morningLeadMinutes, { "$it min" }) {
                container.settings.setMorningLeadMinutes(it)
            }
        }
        HorizontalDivider()
        SettingSwitch(
            label = tr("Prüfungen und Hausaufgaben", "Exams and homework"),
            description = tr("Am Abend vorher an Prüfungen und fällige Hausaufgaben erinnern.", "The evening before, a reminder for exams and homework due."),
            checked = settings.examRemindersEnabled,
        ) { container.settings.setExamRemindersEnabled(it) }
    }
}

/** Automatic sync, then the update check, which the Android app also has under More. */
@Composable
fun SyncSettings(container: DesktopContainer) {
    val settings by container.settings.settings.collectAsState()
    val language = LocalLanguage.current
    val scope = rememberCoroutineScope()
    val updater = container.updater
    val state by updater.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ListCard {
                SettingSwitch(
                    label = tr("Automatisch synchronisieren", "Sync automatically"),
                    description = tr("Hält den Stundenplan und die Benachrichtigungen aktuell.", "Keeps the timetable and notifications up to date."),
                    checked = settings.syncEnabled,
                ) { container.settings.setSyncEnabled(it) }
                HorizontalDivider()
                SettingBlock(Icons.Outlined.Sync, tr("Intervall", "Interval")) {
                    SegmentedRow(listOf(15, 30, 60), settings.syncIntervalMinutes, { "$it min" }) {
                        container.settings.setSyncInterval(it)
                    }
                }
                HorizontalDivider()
                ManualSync(container)
            }
            ListCard {
                SettingBlock(
                    Icons.Outlined.Sync,
                    tr("Updates", "Updates"),
                    trFor(language, "Installierte Version ${updater.build.versionName}", "Installed version ${updater.build.versionName}"),
                ) {
                    if (!updater.build.updatesEnabled) {
                        Text(tr("Diese Version sucht nicht nach Updates.", "This build does not check for updates."),
                            style = MaterialTheme.typography.bodyMedium)
                    } else {
                        UpdateControls(container, state, scope, settings.autoCheckUpdates, settings.updateCheckHours)
                    }
                }
            }
        }
    }
}

/** "Sync now" with the time of the last sync, as the Android sync page has it. */
@Composable
private fun ManualSync(container: DesktopContainer) {
    val language = LocalLanguage.current
    val scope = rememberCoroutineScope()
    val status by container.timetable.status.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    val last = status.lastSyncAt?.let {
        java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    }
    SettingBlock(
        Icons.Outlined.Sync,
        tr("Jetzt synchronisieren", "Sync now"),
        if (last != null) {
            trFor(language, "Zuletzt: $last", "Last: $last")
        } else {
            trFor(language, "Noch nicht synchronisiert", "Not synced yet")
        },
    ) {
        OutlinedButton(
            onClick = {
                if (status.isLoading) return@OutlinedButton
                message = null
                scope.launch {
                    try {
                        syncTimetableOnce(container, container.settings.current())
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message = e.userMessage(language, trFor(language, "Synchronisierung fehlgeschlagen.", "Sync failed."))
                    }
                }
            },
            enabled = !status.isLoading,
            modifier = Modifier.handCursor(),
        ) {
            Text(if (status.isLoading) tr("Synchronisiere …", "Syncing …") else tr("Jetzt synchronisieren", "Sync now"))
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun UpdateControls(
    container: DesktopContainer,
    state: UpdateState,
    scope: CoroutineScope,
    autoCheck: Boolean,
    checkHours: Int,
) {
    val language = LocalLanguage.current
    val updater = container.updater
    SettingSwitch(
        label = tr("Automatisch suchen", "Check automatically"),
        description = null,
        checked = autoCheck,
    ) { container.settings.setAutoCheckUpdates(it) }
    SegmentedRow(SettingsRepository.UPDATE_CHECK_HOURS, checkHours, { "$it h" }) { container.settings.setUpdateCheckHours(it) }
    when (state) {
        UpdateState.Idle, UpdateState.UpToDate, is UpdateState.Failed -> {
            if (state == UpdateState.UpToDate) {
                Text(tr("Du hast die neueste Version.", "You have the newest version."), style = MaterialTheme.typography.bodyLarge)
            }
            if (state is UpdateState.Failed) {
                Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = { scope.launch { updater.check(autoDownload = autoCheck) } }) {
                Text(tr("Nach Updates suchen", "Check for updates"))
            }
        }
        UpdateState.Checking -> Text(tr("Suche…", "Checking…"))
        is UpdateState.Available -> {
            Text(
                trFor(language, "Version ${state.release.versionName} ist verfügbar.", "Version ${state.release.versionName} is available."),
                style = MaterialTheme.typography.titleSmall,
            )
            if (state.release.changelog.isNotBlank()) {
                Text(state.release.changelog.take(600), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { scope.launch { updater.download(state.release) } }) { Text(tr("Herunterladen", "Download")) }
        }
        is UpdateState.Downloading -> {
            Text(
                trFor(language, "Lädt herunter: ${state.done / 1024 / 1024} von ${state.total / 1024 / 1024} MB",
                    "Downloading: ${state.done / 1024 / 1024} of ${state.total / 1024 / 1024} MB"),
            )
        }
        is UpdateState.ReadyToInstall -> {
            Text(
                tr("Der Download ist geprüft. Daybreak schließt sich für die Installation.",
                    "The download is checked. Daybreak closes for the installation."),
            )
            Button(onClick = {
                updater.install(state.file)
                exitProcess(0)
            }) { Text(tr("Installieren", "Install")) }
        }
    }
}


private fun themeName(mode: ThemeMode, language: AppLanguage): String = when (mode) {
    ThemeMode.SYSTEM -> trFor(language, "System", "System")
    ThemeMode.LIGHT -> trFor(language, "Hell", "Light")
    ThemeMode.DARK -> trFor(language, "Dunkel", "Dark")
}

private fun accentName(accent: AccentColor, language: AppLanguage): String = when (accent) {
    AccentColor.OCEAN -> trFor(language, "Ozean", "Ocean")
    AccentColor.FOREST -> trFor(language, "Wald", "Forest")
    AccentColor.LAVENDER -> trFor(language, "Lavendel", "Lavender")
    AccentColor.SUNSET -> trFor(language, "Abend", "Sunset")
    AccentColor.ROSE -> trFor(language, "Rose", "Rose")
}

private fun textScaleName(scale: TextScale, language: AppLanguage): String = when (scale) {
    TextScale.STANDARD -> trFor(language, "Normal", "Standard")
    TextScale.LARGE -> trFor(language, "Groß", "Large")
    TextScale.EXTRA_LARGE -> trFor(language, "Sehr groß", "Extra large")
}

private fun motionName(style: MotionStyle, language: AppLanguage): String = when (style) {
    MotionStyle.EXPRESSIVE -> trFor(language, "Lebendig", "Lively")
    MotionStyle.CALM -> trFor(language, "Ruhig", "Calm")
}

private fun startName(view: StartView, language: AppLanguage): String = when (view) {
    StartView.DAY -> trFor(language, "Tag", "Day")
    StartView.WEEK -> trFor(language, "Woche", "Week")
}

private fun absenceUnitName(unit: AbsenceUnit, language: AppLanguage): String = when (unit) {
    AbsenceUnit.MINUTES -> trFor(language, "Zeit", "Time")
    AbsenceUnit.LESSONS -> trFor(language, "Schulstunden", "Lessons")
}

private fun colorModeName(mode: SubjectColorMode, language: AppLanguage): String = when (mode) {
    SubjectColorMode.ACCENT_HUES -> trFor(language, "Akzentfarben", "Accent hues")
    SubjectColorMode.ROLES -> trFor(language, "Nach Rolle", "By role")
}

private fun languageName(language: AppLanguage): String = when (language) {
    AppLanguage.GERMAN -> "Deutsch"
    AppLanguage.ENGLISH -> "English"
}

/** A row or segment that reacts to a click without the ripple of a button. */
internal fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
