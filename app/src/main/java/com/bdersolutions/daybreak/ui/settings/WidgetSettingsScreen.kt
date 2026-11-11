package com.bdersolutions.daybreak.ui.settings

import com.bdersolutions.daybreak.ui.components.FilledTonalButton
import com.bdersolutions.daybreak.ui.components.Surface
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.data.schedule.LessonRun
import com.bdersolutions.daybreak.data.schedule.WidgetPlanner
import com.bdersolutions.daybreak.data.settings.AppSettings
import com.bdersolutions.daybreak.data.settings.WidgetDetail
import com.bdersolutions.daybreak.data.settings.WidgetSettings
import com.bdersolutions.daybreak.data.settings.WidgetTheme
import com.bdersolutions.daybreak.ui.components.automaticSubjectColor
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import com.bdersolutions.daybreak.ui.theme.daybreakColorScheme
import com.bdersolutions.daybreak.ui.theme.resolveDarkTheme
import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisLesson
import com.bdersolutions.daybreak.widget.DaybreakWidgetReceiver
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * How the home screen widget looks and what it shows. A preview at the top draws the widget with the
 * current choices (and the real lessons when there are some), over a backdrop that stands in for the
 * wallpaper, so the background opacity can be judged. Every change also redraws the real widget.
 */
@Composable
fun WidgetSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cache by viewModel.timetableCache.collectAsStateWithLifecycle()
    val widget = settings.widget
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    val provider = remember(context) { ComponentName(context, DaybreakWidgetReceiver::class.java) }
    val canPin = remember(manager) { manager.isRequestPinAppWidgetSupported }
    // Counted again whenever the screen comes back, e.g. after placing a widget from the launcher.
    var placed by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            placed = runCatching { manager.getAppWidgetIds(provider).size }.getOrDefault(0)
        }
    }
    val lessons = remember(cache) { cache.weeks.values.flatMap { it.lessons } }

    SettingsPageScaffold(title = stringResource(R.string.settings_widget), onBack = onBack) {
        item(key = "preview") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WidgetPreview(settings = settings, lessons = lessons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (placed > 0) {
                            pluralStringResource(R.plurals.widget_settings_placed, placed, placed)
                        } else {
                            stringResource(R.string.widget_settings_not_placed)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (canPin) {
                        Spacer(Modifier.width(12.dp))
                        FilledTonalButton(
                            onClick = {
                                haptics.perform(HapticEvent.TAP)
                                manager.requestPinAppWidget(provider, null, null)
                            },
                        ) {
                            Icon(Icons.Outlined.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.widget_settings_add_short))
                        }
                    }
                }
            }
        }

        item(key = "look_label") { SectionLabel(stringResource(R.string.widget_settings_look)) }
        item(key = "look") {
            SettingsGroup {
                AppearanceOption(
                    icon = Icons.Outlined.DarkMode,
                    title = stringResource(R.string.widget_settings_theme),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetTheme.entries.forEach { theme ->
                            ThemeTile(
                                icon = widgetThemeIcon(theme),
                                label = widgetThemeLabel(theme),
                                selected = widget.theme == theme,
                                onClick = {
                                    if (widget.theme != theme) haptics.perform(HapticEvent.SELECT)
                                    viewModel.updateWidget { it.copy(theme = theme) }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                AppearanceDivider()
                AppearanceOption(
                    icon = Icons.Outlined.Opacity,
                    title = stringResource(R.string.widget_settings_background),
                ) {
                    PercentSlider(
                        value = widget.backgroundOpacity,
                        range = 0..100,
                        step = WidgetSettings.OPACITY_STEP,
                        onValueChange = { value -> viewModel.updateWidget { it.copy(backgroundOpacity = value) } },
                    )
                    Text(
                        text = stringResource(R.string.widget_settings_background_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "content_label") { SectionLabel(stringResource(R.string.widget_settings_content)) }
        item(key = "content") {
            SettingsGroup {
                WidgetSwitchRow(
                    icon = Icons.Outlined.Schedule,
                    title = stringResource(R.string.widget_settings_headline),
                    subtitle = stringResource(R.string.widget_settings_headline_desc),
                    checked = widget.showHeadline,
                    onCheckedChange = { value -> viewModel.updateWidget { it.copy(showHeadline = value) } },
                )
                AppearanceDivider()
                AppearanceOption(
                    icon = Icons.Outlined.ViewAgenda,
                    title = stringResource(R.string.widget_settings_detail),
                ) {
                    SegmentChoice(
                        options = WidgetDetail.entries.map { it to widgetDetailLabel(it) },
                        selected = widget.detail,
                        onSelect = { detail -> viewModel.updateWidget { it.copy(detail = detail) } },
                    )
                }
                AppearanceDivider()
                WidgetSwitchRow(
                    icon = Icons.Outlined.EventBusy,
                    title = stringResource(R.string.widget_settings_cancelled),
                    subtitle = stringResource(R.string.widget_settings_cancelled_desc),
                    checked = widget.showCancelled,
                    onCheckedChange = { value -> viewModel.updateWidget { it.copy(showCancelled = value) } },
                )
                AppearanceDivider()
                WidgetSwitchRow(
                    icon = Icons.Outlined.CalendarMonth,
                    title = stringResource(R.string.widget_settings_next_day),
                    subtitle = stringResource(R.string.widget_settings_next_day_desc),
                    checked = widget.nextDayAfterSchool,
                    onCheckedChange = { value -> viewModel.updateWidget { it.copy(nextDayAfterSchool = value) } },
                )
                AppearanceDivider()
                WidgetSwitchRow(
                    icon = Icons.Outlined.Sync,
                    title = stringResource(R.string.widget_settings_sync_time),
                    subtitle = stringResource(R.string.widget_settings_sync_time_desc),
                    checked = widget.showSyncTime,
                    onCheckedChange = { value -> viewModel.updateWidget { it.copy(showSyncTime = value) } },
                )
            }
        }
    }
}

/** One choice of the widget theme: an icon over a label in a tile that fills when chosen. */
@Composable
private fun ThemeTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "themeTile",
    )
    val content = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The widget as it will look, drawn with Compose: the same colours, layout and texts as
 * [com.bdersolutions.daybreak.widget.DaybreakWidget], filled with the next lessons (or examples).
 */
@Composable
private fun WidgetPreview(settings: AppSettings, lessons: List<UntisLesson>) {
    val context = LocalContext.current
    val widget = settings.widget
    val dark = when (widget.theme) {
        WidgetTheme.APP -> resolveDarkTheme(settings.themeMode)
        WidgetTheme.SYSTEM -> isSystemInDarkTheme()
        WidgetTheme.LIGHT -> false
        WidgetTheme.DARK -> true
    }
    val scheme = remember(settings, dark) { daybreakColorScheme(context, settings, dark) }
    val today = remember { LocalDate.now() }
    val now = remember { LocalTime.now() }
    val plan = remember(lessons, widget) {
        WidgetPlanner.plan(lessons, today, now, widget.showCancelled, widget.nextDayAfterSchool)
    }
    val realRows = plan.remainingRows(now)
    val rows = (if (realRows.isEmpty()) sampleRows(today, widget.showCancelled) else realRows).take(3)
    val current = plan.current.takeIf { realRows.isNotEmpty() }
    val subjectColor = { subject: String ->
        settings.lessonColors.subjectColors[subject]?.let { Color(it.toInt()) }
            ?: automaticSubjectColor(subject, scheme, dark, settings.subjectColorMode)
    }
    // Header and headline follow the same rules as the real widget (DaybreakWidget.headlineText).
    val samples = realRows.isEmpty()
    val next = plan.next
    val headline = when {
        !widget.showHeadline -> null
        samples -> rows.firstOrNull()?.let { stringResource(R.string.widget_next_at, it.lesson.subject, time(it.start)) }
        !plan.isToday && plan.freeToday -> stringResource(R.string.widget_free_today)
        !plan.isToday -> stringResource(R.string.widget_school_over)
        current != null -> stringResource(R.string.widget_now_until, current.lesson.subject, time(current.end))
        next != null && next.lesson.date == today ->
            stringResource(R.string.widget_next_at, next.lesson.subject, time(next.start))
        plan.schoolOverToday -> stringResource(R.string.widget_school_over)
        else -> null
    }
    val shownDay = plan.day.takeUnless { samples } ?: today
    val dateText = remember(shownDay) { shownDay.format(DateTimeFormatter.ofPattern("EEEE, d. MMM")) }
    val dayText = if (shownDay == today) dateText else stringResource(R.string.widget_next_school_day, dateText)

    // A soft gradient in the app's colours stands in for the wallpaper behind the widget.
    val backdrop = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
    )
    val opacity = widget.backgroundOpacity / 100f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(backdrop)
            .padding(18.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = scheme.surfaceContainer.copy(alpha = opacity),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, scheme.outlineVariant.copy(alpha = 0.3f * opacity), RoundedCornerShape(24.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.widget_app_name),
                            color = scheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(text = dayText, color = scheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    if (widget.showSyncTime) {
                        Text(
                            text = stringResource(R.string.widget_last_sync_short, time(now)),
                            color = scheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (headline != null) {
                    Text(text = headline, color = scheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                }
                rows.forEach { row ->
                    PreviewRow(
                        row = row,
                        current = row == current,
                        scheme = scheme,
                        stripe = if (row.lesson.isCancelled) scheme.error else subjectColor(row.lesson.subject),
                        detail = previewDetail(row.lesson, widget.detail),
                        dark = dark,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(
    row: LessonRun,
    current: Boolean,
    scheme: ColorScheme,
    stripe: Color,
    detail: String,
    dark: Boolean,
) {
    val cancelled = row.lesson.isCancelled
    val main = when {
        current -> scheme.onPrimaryContainer
        cancelled -> scheme.error
        else -> scheme.onSurface
    }
    val sub = if (current) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (current) scheme.primaryContainer else Color.Transparent)
            .padding(horizontal = if (current) 6.dp else 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(stripe),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.width(40.dp)) {
            Text(time(row.start), color = sub, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
            Text(time(row.end), color = sub, fontSize = 10.sp, lineHeight = 12.sp)
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.lesson.subject,
                color = main,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                textDecoration = if (cancelled) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
            )
            if (detail.isNotBlank()) Text(detail, color = sub, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1)
        }
        if (cancelled) {
            Text(
                text = stringResource(R.string.lesson_cancelled),
                color = scheme.onErrorContainer,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(scheme.errorContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        } else if (row.lesson.hasSubstitution) {
            Text(
                text = stringResource(if (row.lesson.hasRoomChange) R.string.lesson_room_short else R.string.lesson_substitution_short),
                color = if (dark) Color(0xFFFFB35C) else Color(0xFFC45A00),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (dark) Color(0xFF5A3200) else Color(0xFFFFE0C2))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun previewDetail(lesson: UntisLesson, detail: WidgetDetail): String {
    val room = lesson.room.takeIf { it.isNotBlank() }?.let { stringResource(R.string.widget_room, it) }
    val teacher = lesson.teacher.takeIf { it.isNotBlank() }
    return when (detail) {
        WidgetDetail.ROOM -> room ?: teacher
        WidgetDetail.TEACHER -> teacher ?: room
        WidgetDetail.ROOM_AND_TEACHER -> listOfNotNull(room, teacher).joinToString(" · ")
    }.orEmpty()
}

/** Example lessons for the preview when the timetable has nothing coming up. */
private fun sampleRows(day: LocalDate, withCancelled: Boolean): List<LessonRun> {
    fun run(
        id: Long,
        start: String,
        end: String,
        subject: String,
        room: String,
        teacher: String,
        status: LessonStatus = LessonStatus.REGULAR,
    ) = LessonRun(
        UntisLesson(id, day, LocalTime.parse(start), LocalTime.parse(end), subject, room = room, teacher = teacher, status = status),
        LocalTime.parse(start),
        LocalTime.parse(end),
    )
    return listOfNotNull(
        run(-1, "07:55", "09:35", "M", "204", "HUBE"),
        run(-2, "09:50", "10:40", "E", "105", "MAIE"),
        if (withCancelled) run(-3, "10:40", "11:30", "PH", "301", "GRUB", LessonStatus.CANCELLED) else null,
        run(-4, "11:35", "12:25", "D", "110", "WINK"),
    )
}

private fun time(value: LocalTime): String = value.format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
private fun WidgetSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        trailing = { SpringSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

private fun widgetThemeIcon(theme: WidgetTheme): ImageVector = when (theme) {
    WidgetTheme.APP -> Icons.Outlined.Palette
    WidgetTheme.SYSTEM -> Icons.Outlined.PhoneAndroid
    WidgetTheme.LIGHT -> Icons.Outlined.LightMode
    WidgetTheme.DARK -> Icons.Outlined.DarkMode
}

@Composable
internal fun widgetThemeLabel(theme: WidgetTheme): String = when (theme) {
    WidgetTheme.APP -> stringResource(R.string.widget_theme_app)
    WidgetTheme.SYSTEM -> stringResource(R.string.theme_system)
    WidgetTheme.LIGHT -> stringResource(R.string.theme_light)
    WidgetTheme.DARK -> stringResource(R.string.theme_dark)
}

@Composable
private fun widgetDetailLabel(detail: WidgetDetail): String = when (detail) {
    WidgetDetail.ROOM -> stringResource(R.string.widget_detail_room)
    WidgetDetail.TEACHER -> stringResource(R.string.widget_detail_teacher)
    WidgetDetail.ROOM_AND_TEACHER -> stringResource(R.string.widget_detail_both)
}
