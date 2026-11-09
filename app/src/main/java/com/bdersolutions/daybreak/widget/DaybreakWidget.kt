package com.bdersolutions.daybreak.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.currentState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bdersolutions.daybreak.DaybreakApplication
import com.bdersolutions.daybreak.MainActivity
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.cache.CachedWeek
import com.bdersolutions.daybreak.data.cache.TimetableCache
import com.bdersolutions.daybreak.data.schedule.LessonRun
import com.bdersolutions.daybreak.data.schedule.WidgetPlan
import com.bdersolutions.daybreak.data.schedule.WidgetPlanner
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.data.settings.AppSettings
import com.bdersolutions.daybreak.data.settings.ThemeMode
import com.bdersolutions.daybreak.data.settings.WidgetDetail
import com.bdersolutions.daybreak.data.settings.WidgetSettings
import com.bdersolutions.daybreak.data.settings.WidgetTheme
import com.bdersolutions.daybreak.ui.components.automaticSubjectColor
import com.bdersolutions.daybreak.ui.theme.daybreakColorScheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.glance.color.ColorProvider as DayNightColor

/**
 * The home screen widget: the day's lessons with the one that is on now, in the app's colours.
 *
 * It is drawn for its exact size, so it lists as many lessons as fit. Times are written as clock
 * times ("until 10:40"), never as a countdown: the widget is only redrawn at lesson boundaries and
 * a countdown would go stale in between.
 */
class DaybreakWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as? DaybreakApplication)?.container
        container?.timetableRepository?.awaitLoaded()
        val cornerRadius = widgetCornerRadius(context)

        provideContent {
            // Everything is read inside the composition. Glance keeps a session alive for a while
            // and an update that reaches a live session only recomposes it, so data read before
            // provideContent would stay stale. The repositories' flows recompose on their own; the
            // refresh stamp (set by WidgetUpdater at lesson boundaries) brings in the new time.
            val refresh = currentState(REFRESH_KEY) ?: 0L
            val settings = container?.settingsRepository?.settings?.collectAsState()?.value ?: AppSettings()
            val cache = container?.timetableRepository?.cache?.collectAsState()?.value ?: TimetableCache()
            val loggedIn = container?.sessionStore?.isLoggedIn() == true
            val today = LocalDate.now()
            val now = LocalTime.now()
            val widget = settings.widget
            val data = remember(refresh, settings, cache, loggedIn, today, now.hour, now.minute) {
                WidgetData(
                    loggedIn = loggedIn,
                    hasData = cache.weeks.isNotEmpty(),
                    today = today,
                    now = now,
                    plan = WidgetPlanner.plan(
                        lessons = cache.weeks.values.flatMap(CachedWeek::lessons),
                        today = today,
                        now = now,
                        showCancelled = widget.showCancelled,
                        nextDayAfterSchool = widget.nextDayAfterSchool,
                    ),
                    lastSyncAt = cache.lastSyncAt,
                    settings = widget,
                )
            }
            val localized = remember(settings.language) { localizedContext(context, settings.language) }
            val palette = remember(settings) { WidgetPalette.build(context, settings) }
            WidgetContent(
                data = data,
                localized = localized,
                palette = palette,
                cornerRadius = cornerRadius,
            )
        }
    }

    @Composable
    private fun WidgetContent(
        data: WidgetData,
        localized: Context,
        palette: WidgetPalette,
        cornerRadius: Dp,
    ) {
        val size = LocalSize.current
        val action = actionStartActivity<MainActivity>(
            actionParametersOf(
                ActionParameters.Key<String>(MainActivity.EXTRA_WIDGET_DATE) to
                    (data.plan.day ?: data.today).toString(),
            ),
        )
        val compact = size.height < 130.dp
        val padding = if (compact) 12.dp else 16.dp

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(palette.background)
                .appWidgetBackground()
                .cornerRadius(cornerRadius)
                .padding(padding)
                .clickable(action),
            verticalAlignment = Alignment.Top,
        ) {
            WidgetHeader(data = data, localized = localized, palette = palette)
            Spacer(GlanceModifier.height(if (compact) 4.dp else 8.dp))
            val plan = data.plan
            when {
                !data.loggedIn -> EmptyState(
                    title = localized.getString(R.string.widget_login_prompt),
                    subtitle = localized.getString(R.string.widget_open_app),
                    palette = palette,
                )

                plan.day == null -> EmptyState(
                    title = localized.getString(if (data.hasData) R.string.widget_no_lessons else R.string.widget_no_data),
                    subtitle = localized.getString(R.string.widget_open_app),
                    palette = palette,
                )

                else -> {
                    val headline = headlineText(data, localized)
                    if (headline != null) {
                        Text(
                            text = headline,
                            style = TextStyle(
                                color = palette.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                        Spacer(GlanceModifier.height(4.dp))
                    }
                    val rows = plan.remainingRows(data.now)
                    if (rows.isEmpty()) {
                        Text(
                            text = localized.getString(R.string.widget_no_lessons),
                            style = TextStyle(color = palette.onSurfaceVariant, fontSize = 12.sp),
                            maxLines = 1,
                        )
                    } else {
                        // Header, headline and padding take the top; the rest is filled with rows.
                        val used = padding * 2 + HEADER_HEIGHT + (if (headline != null) HEADLINE_HEIGHT else 0.dp)
                        val fit = ((size.height - used).value / ROW_HEIGHT.value).toInt().coerceIn(1, MAX_ROWS)
                        LessonList(
                            rows = rows,
                            fit = fit,
                            current = plan.current,
                            localized = localized,
                            palette = palette,
                            detail = data.settings.detail,
                        )
                    }
                }
            }
        }
    }

    /** The line above the list: what is on now, what comes next, or why another day is shown. */
    private fun headlineText(data: WidgetData, localized: Context): String? {
        if (!data.settings.showHeadline) return null
        val plan = data.plan
        val current = plan.current
        val next = plan.next
        return when {
            !plan.isToday && plan.freeToday -> localized.getString(R.string.widget_free_today)
            !plan.isToday -> localized.getString(R.string.widget_school_over)
            current != null -> localized.getString(
                R.string.widget_now_until,
                subjectOf(current, localized),
                formatTime(current.end),
            )
            next != null && next.lesson.date == data.today -> localized.getString(
                R.string.widget_next_at,
                subjectOf(next, localized),
                formatTime(next.start),
            )
            plan.schoolOverToday -> localized.getString(R.string.widget_school_over)
            else -> null
        }
    }

    @Composable
    private fun WidgetHeader(data: WidgetData, localized: Context, palette: WidgetPalette) {
        val locale = languageLocale(localized)
        val day = data.plan.day
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = localized.getString(R.string.widget_app_name),
                    style = TextStyle(color = palette.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    text = when {
                        day == null || day == data.today -> fullDayDate(data.today, locale)
                        else -> localized.getString(R.string.widget_next_school_day, fullDayDate(day, locale))
                    },
                    style = TextStyle(color = palette.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
            if (data.settings.showSyncTime && data.lastSyncAt > 0L) {
                Text(
                    text = localized.getString(R.string.widget_last_sync_short, formatSyncTime(data.lastSyncAt)),
                    style = TextStyle(color = palette.onSurfaceVariant, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun LessonList(
        rows: List<LessonRun>,
        fit: Int,
        current: LessonRun?,
        localized: Context,
        palette: WidgetPalette,
        detail: WidgetDetail,
    ) {
        // When not everything fits, the last slot says how many are left out.
        val shown = if (rows.size > fit) rows.take((fit - 1).coerceAtLeast(1)) else rows
        val hidden = rows.size - shown.size
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            shown.forEach { row ->
                LessonRow(
                    row = row,
                    current = row == current,
                    localized = localized,
                    palette = palette,
                    detail = detail,
                )
            }
            if (hidden > 0) {
                Text(
                    text = localized.getString(R.string.widget_more, hidden),
                    modifier = GlanceModifier.padding(start = 10.dp, top = 2.dp),
                    style = TextStyle(color = palette.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun LessonRow(
        row: LessonRun,
        current: Boolean,
        localized: Context,
        palette: WidgetPalette,
        detail: WidgetDetail,
    ) {
        val lesson = row.lesson
        val cancelled = lesson.isCancelled
        val changed = !cancelled && lesson.hasSubstitution
        val base = GlanceModifier.fillMaxWidth().height(ROW_HEIGHT)
        val rowModifier = if (current) {
            base.background(palette.primaryContainer).cornerRadius(10.dp).padding(horizontal = 6.dp)
        } else {
            base.padding(horizontal = 2.dp)
        }
        val mainColor = when {
            current -> palette.onPrimaryContainer
            cancelled -> palette.error
            else -> palette.onSurface
        }
        val subColor = if (current) palette.onPrimaryContainer else palette.onSurfaceVariant
        Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                GlanceModifier
                    .width(4.dp)
                    .height(26.dp)
                    .cornerRadius(2.dp)
                    .background(if (cancelled) palette.error else palette.subject(lesson.subject)),
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.width(40.dp)) {
                Text(
                    text = formatTime(row.start),
                    style = TextStyle(color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Text(
                    text = formatTime(row.end),
                    style = TextStyle(color = subColor, fontSize = 10.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = subjectOf(row, localized),
                    style = TextStyle(
                        color = mainColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (cancelled) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    maxLines = 1,
                )
                val second = detailText(row, detail, localized)
                if (second.isNotBlank()) {
                    Text(
                        text = second,
                        style = TextStyle(color = subColor, fontSize = 11.sp),
                        maxLines = 1,
                    )
                }
            }
            when {
                cancelled -> Badge(
                    text = localized.getString(R.string.lesson_cancelled),
                    content = palette.onErrorContainer,
                    container = palette.errorContainer,
                )

                changed -> Badge(
                    text = localized.getString(
                        if (lesson.hasRoomChange) R.string.lesson_room_short else R.string.lesson_substitution_short,
                    ),
                    content = palette.warning,
                    container = palette.warningContainer,
                )
            }
        }
    }

    @Composable
    private fun Badge(text: String, content: ColorProvider, container: ColorProvider) {
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = text,
            modifier = GlanceModifier
                .background(container)
                .cornerRadius(6.dp)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            style = TextStyle(color = content, fontSize = 10.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }

    @Composable
    private fun EmptyState(title: String, subtitle: String, palette: WidgetPalette) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = title,
                style = TextStyle(color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = subtitle,
                style = TextStyle(color = palette.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 2,
            )
        }
    }

    private data class WidgetData(
        val loggedIn: Boolean,
        val hasData: Boolean,
        val today: LocalDate,
        val now: LocalTime,
        val plan: WidgetPlan,
        val lastSyncAt: Long,
        val settings: WidgetSettings,
    )

    private fun subjectOf(row: LessonRun, localized: Context): String =
        row.lesson.subject.ifBlank { localized.getString(R.string.lesson_unknown_subject) }

    private fun detailText(row: LessonRun, detail: WidgetDetail, localized: Context): String {
        val lesson = row.lesson
        val room = lesson.room.takeIf { it.isNotBlank() }?.let { localized.getString(R.string.widget_room, it) }
        val teacher = lesson.teacher.takeIf { it.isNotBlank() }
        return when (detail) {
            // A lesson without a room still says something useful.
            WidgetDetail.ROOM -> room ?: teacher
            WidgetDetail.TEACHER -> teacher ?: room
            WidgetDetail.ROOM_AND_TEACHER -> listOfNotNull(room, teacher).joinToString(" · ")
        }.orEmpty()
    }

    private fun localizedContext(context: Context, language: AppLanguage): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language.tag))
        return context.createConfigurationContext(configuration)
    }

    private fun languageLocale(context: Context): Locale =
        context.resources.configuration.locales[0] ?: Locale.GERMAN

    private fun formatTime(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern("HH:mm"))

    private fun fullDayDate(date: LocalDate, locale: Locale): String {
        val pattern = if (locale.language == "de") "EEEE, d. MMM" else "EEEE, MMM d"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
            .replaceFirstChar { it.titlecase(locale) }
    }

    private fun formatSyncTime(lastSyncAt: Long): String =
        Instant.ofEpochMilli(lastSyncAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))

    private fun widgetCornerRadius(context: Context): Dp {
        val resources = context.resources
        val id = resources.getIdentifier("system_app_widget_background_radius", "dimen", "android")
        if (id == 0) return 24.dp
        return (resources.getDimension(id) / resources.displayMetrics.density).dp
    }

    companion object {
        /** Bumped by [WidgetUpdater] so a live widget session recomposes with the current time. */
        val REFRESH_KEY = longPreferencesKey("refresh")

        private val HEADER_HEIGHT = 44.dp
        private val HEADLINE_HEIGHT = 24.dp
        private val ROW_HEIGHT = 36.dp

        /** Glance containers hold at most ten children; one slot is kept for the "+N more" line. */
        private const val MAX_ROWS = 9
    }
}

/**
 * The widget's colours, taken from the app's own scheme (Material You or the accent palette) and
 * its lesson colours. Each colour has a light and a dark value; whether the widget follows the
 * phone or stays in one mode is decided by [WidgetSettings.theme].
 */
private class WidgetPalette(
    private val light: ColorScheme,
    private val dark: ColorScheme,
    /** Null follows the phone's dark mode; true or false fixes it. */
    private val forceDark: Boolean?,
    private val opacity: Float,
    private val subjectColor: (String, ColorScheme, Boolean) -> Color,
) {
    private fun pick(role: (ColorScheme) -> Color): ColorProvider = when (forceDark) {
        null -> DayNightColor(day = role(light), night = role(dark))
        true -> DayNightColor(day = role(dark), night = role(dark))
        false -> DayNightColor(day = role(light), night = role(light))
    }

    private fun pickPair(lightColor: Color, darkColor: Color): ColorProvider = when (forceDark) {
        null -> DayNightColor(day = lightColor, night = darkColor)
        true -> DayNightColor(day = darkColor, night = darkColor)
        false -> DayNightColor(day = lightColor, night = lightColor)
    }

    val background = pick { it.surfaceContainer.copy(alpha = opacity) }
    val onSurface = pick { it.onSurface }
    val onSurfaceVariant = pick { it.onSurfaceVariant }
    val primary = pick { it.primary }
    val primaryContainer = pick { it.primaryContainer }
    val onPrimaryContainer = pick { it.onPrimaryContainer }
    val error = pick { it.error }
    val errorContainer = pick { it.errorContainer }
    val onErrorContainer = pick { it.onErrorContainer }
    val warning = pickPair(Color(0xFFC45A00), Color(0xFFFFB35C))
    val warningContainer = pickPair(Color(0xFFFFE0C2), Color(0xFF5A3200))

    private val subjects = mutableMapOf<String, ColorProvider>()

    /** The stripe colour of [subject], the same as in the timetable. */
    fun subject(subject: String): ColorProvider = subjects.getOrPut(subject) {
        pickPair(subjectColor(subject, light, false), subjectColor(subject, dark, true))
    }

    companion object {
        fun build(context: Context, settings: AppSettings): WidgetPalette {
            val widget = settings.widget
            val forceDark = when (widget.theme) {
                WidgetTheme.SYSTEM -> null
                WidgetTheme.LIGHT -> false
                WidgetTheme.DARK -> true
                WidgetTheme.APP -> when (settings.themeMode) {
                    ThemeMode.SYSTEM -> null
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            }
            val custom = settings.lessonColors.subjectColors
            return WidgetPalette(
                light = daybreakColorScheme(context, settings, dark = false),
                dark = daybreakColorScheme(context, settings, dark = true),
                forceDark = forceDark,
                opacity = widget.backgroundOpacity / 100f,
                subjectColor = { subject, scheme, dark ->
                    custom[subject]?.let { Color(it.toInt()) }
                        ?: automaticSubjectColor(subject, scheme, dark, settings.subjectColorMode)
                },
            )
        }
    }
}
