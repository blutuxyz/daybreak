package com.bdersolutions.daybreak.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
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
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.untis.UntisLesson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.graphics.Color as AndroidColor

private val SMALL_SIZE = DpSize(180.dp, 110.dp)
private val MEDIUM_SIZE = DpSize(250.dp, 160.dp)
private val LARGE_SIZE = DpSize(320.dp, 280.dp)

class DaybreakWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_SIZE, MEDIUM_SIZE, LARGE_SIZE),
    )

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val app = context.applicationContext as? DaybreakApplication
        val settings = app?.container?.settingsRepository?.current()
        val loggedIn = app?.container?.sessionStore?.isLoggedIn() == true
        val cache = withContext(Dispatchers.IO) {
            app?.container?.timetableCacheStore?.load() ?: TimetableCache()
        }
        val language = settings?.language ?: AppLanguage.GERMAN
        val localized = localizedContext(context, language)
        val data = buildWidgetData(cache, loggedIn, LocalDate.now(), LocalTime.now())
        val cornerRadius = widgetCornerRadius(context)

        provideContent {
            WidgetTheme(context) { seedColor, dark ->
                WidgetContent(
                    data = data,
                    localized = localized,
                    cornerRadius = cornerRadius,
                    seedColor = seedColor,
                    dark = dark,
                )
            }
        }
    }

    private fun buildWidgetData(
        cache: TimetableCache,
        loggedIn: Boolean,
        today: LocalDate,
        now: LocalTime,
    ): WidgetData {
        val all = cache.weeks.values
            .flatMap(CachedWeek::lessons)
            .filter { it.date >= today }
            .sortedWith(compareBy<UntisLesson> { it.date }.thenBy { it.start })
        val todayLessons = all.filter { it.date == today }
        val nextSchoolDay = all.asSequence().map { it.date }.firstOrNull { it > today }
        val nextSchoolDayLessons = nextSchoolDay?.let { day ->
            all.filter { it.date == day }
        }.orEmpty()

        val current = todayLessons.firstOrNull {
            !it.start.isAfter(now) && it.end.isAfter(now)
        }
        val nextToday = todayLessons.firstOrNull { it.start.isAfter(now) }
        val nextAfterCurrent = current?.let { active ->
            todayLessons.firstOrNull { it.date == active.date && it.start >= active.end }
                ?: nextSchoolDayLessons.firstOrNull()
        } ?: nextToday ?: nextSchoolDayLessons.firstOrNull()

        val isFree = todayLessons.isEmpty()
        val displayDay = if (isFree) nextSchoolDay ?: today else today
        val displayLessons = if (isFree) nextSchoolDayLessons else todayLessons

        return WidgetData(
            loggedIn = loggedIn,
            today = today,
            now = now,
            todayLessons = todayLessons,
            current = current,
            next = nextToday ?: nextSchoolDayLessons.firstOrNull(),
            nextAfterCurrent = nextAfterCurrent,
            nextSchoolDay = nextSchoolDay,
            nextSchoolDayLessons = nextSchoolDayLessons,
            displayDay = displayDay,
            displayLessons = displayLessons,
            lastSyncAt = cache.lastSyncAt,
        )
    }

    @Composable
    private fun WidgetTheme(
        context: Context,
        content: @Composable (seedColor: Color, dark: Boolean) -> Unit,
    ) {
        val dark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val light = if (dynamic) dynamicLightColorScheme(context) else lightColorScheme()
        val darkScheme = if (dynamic) dynamicDarkColorScheme(context) else darkColorScheme()
        GlanceTheme(colors = ColorProviders(light = light, dark = darkScheme)) {
            content(if (dark) darkScheme.primary else light.primary, dark)
        }
    }

    @Composable
    private fun WidgetContent(
        data: WidgetData,
        localized: Context,
        cornerRadius: Dp,
        seedColor: Color,
        dark: Boolean,
    ) {
        val colors = GlanceTheme.colors
        val size = LocalSize.current
        val layout = when {
            size.width < 220.dp || size.height < 140.dp -> WidgetLayout.Small
            size.height < 240.dp -> WidgetLayout.Medium
            else -> WidgetLayout.Large
        }
        val action = actionStartActivity<MainActivity>(
            actionParametersOf(
                ActionParameters.Key<String>(MainActivity.EXTRA_WIDGET_DATE) to
                    data.displayDay.toString(),
            ),
        )

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(colors.widgetBackground)
                .appWidgetBackground()
                .cornerRadius(cornerRadius)
                .padding(16.dp)
                .clickable(action),
            verticalAlignment = Alignment.Top,
        ) {
            WidgetHeader(data = data, localized = localized)
            Spacer(GlanceModifier.height(8.dp))
            when {
                !data.loggedIn -> EmptyWidgetState(
                    title = localized.getString(R.string.widget_login_prompt),
                    subtitle = localized.getString(R.string.widget_open_app),
                    titleColor = colors.onSurface,
                    subtitleColor = colors.onSurfaceVariant,
                )

                data.displayLessons.isEmpty() -> EmptyWidgetState(
                    title = localized.getString(
                        if (data.todayLessons.isEmpty()) {
                            R.string.widget_free_today
                        } else {
                            R.string.widget_no_lessons
                        },
                    ),
                    subtitle = localized.getString(R.string.widget_open_app),
                    titleColor = colors.onSurface,
                    subtitleColor = colors.onSurfaceVariant,
                )

                layout == WidgetLayout.Small -> SmallWidget(
                    data = data,
                    localized = localized,
                    seedColor = seedColor,
                    dark = dark,
                )

                layout == WidgetLayout.Medium -> MediumWidget(
                    data = data,
                    localized = localized,
                    seedColor = seedColor,
                    dark = dark,
                )

                else -> LargeWidget(
                    data = data,
                    localized = localized,
                    seedColor = seedColor,
                    dark = dark,
                )
            }
        }
    }

    @Composable
    private fun WidgetHeader(data: WidgetData, localized: Context) {
        val colors = GlanceTheme.colors
        val locale = languageLocale(localized)
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = localized.getString(R.string.widget_app_name),
                    style = TextStyle(
                        color = colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = if (data.todayLessons.isEmpty()) {
                        localized.getString(R.string.widget_free_today)
                    } else {
                        fullDayDate(data.today, locale)
                    },
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                )
            }
            if (data.lastSyncAt > 0L) {
                Text(
                    text = localized.getString(
                        R.string.widget_last_sync_short,
                        formatSyncTime(data.lastSyncAt),
                    ),
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = 10.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun SmallWidget(
        data: WidgetData,
        localized: Context,
        seedColor: Color,
        dark: Boolean,
    ) {
        val colors = GlanceTheme.colors
        val locale = languageLocale(localized)
        when {
            data.todayLessons.isEmpty() -> {
                Text(
                    text = nextSchoolDayLabel(localized, data.displayDay, locale),
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 2,
                )
                Spacer(GlanceModifier.height(3.dp))
                data.displayLessons.firstOrNull()?.let { lesson ->
                    SmallLessonRow(
                        lesson = lesson,
                        localized = localized,
                        seedColor = seedColor,
                        dark = dark,
                    )
                }
            }

            data.current != null -> {
                val current = data.current
                val cancelled = current.isCancelled
                Text(
                    text = localized.getString(
                        R.string.widget_now_remaining,
                        current.subject.ifBlank {
                            localized.getString(R.string.lesson_unknown_subject)
                        },
                        remainingMinutes(current, data.now),
                    ),
                    style = TextStyle(
                        color = if (cancelled) colors.error else colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (cancelled) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                    ),
                    maxLines = 1,
                )
                data.nextAfterCurrent?.let { next ->
                    Text(
                        text = localized.getString(
                            R.string.widget_after,
                            next.subject.ifBlank {
                                localized.getString(R.string.lesson_unknown_subject)
                            },
                            formatTime(next.start),
                        ),
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = 10.sp,
                        ),
                        maxLines = 1,
                    )
                }
                Text(
                    text = "${formatTime(current.start)} – ${formatTime(current.end)}" +
                        roomSuffix(current, localized),
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = 9.sp,
                    ),
                    maxLines = 1,
                )
            }

            else -> {
                val next = data.next
                if (next != null) {
                    Text(
                        text = if (next.date == data.today) {
                            localized.getString(R.string.widget_next_lesson)
                        } else {
                            nextSchoolDayLabel(localized, next.date, locale)
                        },
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 2,
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    SmallLessonRow(
                        lesson = next,
                        localized = localized,
                        seedColor = seedColor,
                        dark = dark,
                    )
                }
            }
        }
    }

    @Composable
    private fun MediumWidget(
        data: WidgetData,
        localized: Context,
        seedColor: Color,
        dark: Boolean,
    ) {
        val colors = GlanceTheme.colors
        val locale = languageLocale(localized)
        when {
            data.todayLessons.isEmpty() -> {
                Text(
                    text = nextSchoolDayLabel(localized, data.displayDay, locale),
                    style = TextStyle(
                        color = colors.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 2,
                )
                Spacer(GlanceModifier.height(3.dp))
                data.displayLessons.take(3).forEach { lesson ->
                    LessonRow(
                        lesson = lesson,
                        localized = localized,
                        seedColor = seedColor,
                        dark = dark,
                        dense = false,
                        showBadges = true,
                        current = false,
                    )
                }
            }

            data.current != null -> {
                val current = data.current
                Text(
                    text = localized.getString(
                        R.string.widget_now_remaining,
                        current.subject.ifBlank {
                            localized.getString(R.string.lesson_unknown_subject)
                        },
                        remainingMinutes(current, data.now),
                    ),
                    style = TextStyle(
                        color = if (current.isCancelled) colors.error else colors.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                data.nextAfterCurrent?.let { next ->
                    Text(
                        text = localized.getString(
                            R.string.widget_after,
                            next.subject.ifBlank {
                                localized.getString(R.string.lesson_unknown_subject)
                            },
                            formatTime(next.start),
                        ),
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = 10.sp,
                        ),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height(3.dp))
                data.todayLessons
                    .filter { it.date == current.date && it.start >= current.end }
                    .take(2)
                    .forEach { lesson ->
                        LessonRow(
                            lesson = lesson,
                            localized = localized,
                            seedColor = seedColor,
                            dark = dark,
                            dense = true,
                            showBadges = true,
                            current = false,
                        )
                    }
            }

            else -> {
                val upcoming = data.todayLessons
                    .filter { it.end > data.now }
                    .take(4)
                if (upcoming.isEmpty()) {
                    Text(
                        text = localized.getString(R.string.widget_no_lessons),
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                    data.nextSchoolDay?.let { day ->
                        Spacer(GlanceModifier.height(3.dp))
                        Text(
                            text = nextSchoolDayLabel(localized, day, locale),
                            style = TextStyle(
                                color = colors.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 2,
                        )
                        data.nextSchoolDayLessons.take(2).forEach { lesson ->
                            LessonRow(
                                lesson = lesson,
                                localized = localized,
                                seedColor = seedColor,
                                dark = dark,
                                dense = true,
                                showBadges = false,
                                current = false,
                            )
                        }
                    }
                } else {
                    upcoming.forEach { lesson ->
                        LessonRow(
                            lesson = lesson,
                            localized = localized,
                            seedColor = seedColor,
                            dark = dark,
                            dense = true,
                            showBadges = true,
                            current = false,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun LargeWidget(
        data: WidgetData,
        localized: Context,
        seedColor: Color,
        dark: Boolean,
    ) {
        val colors = GlanceTheme.colors
        val locale = languageLocale(localized)
        if (data.todayLessons.isEmpty()) {
            Text(
                text = nextSchoolDayLabel(localized, data.displayDay, locale),
                style = TextStyle(
                    color = colors.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
            )
            Spacer(GlanceModifier.height(3.dp))
        }
        val dense = data.displayLessons.size > 8
        data.displayLessons.forEach { lesson ->
            LessonRow(
                lesson = lesson,
                localized = localized,
                seedColor = seedColor,
                dark = dark,
                dense = dense,
                showBadges = true,
                current = lesson == data.current,
            )
        }
    }

    @Composable
    private fun SmallLessonRow(
        lesson: UntisLesson,
        localized: Context,
        seedColor: Color,
        dark: Boolean,
    ) {
        val colors = GlanceTheme.colors
        val warning = !lesson.isCancelled && lesson.hasSubstitution
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SubjectStripe(lesson.subject, seedColor, dark)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = formatTime(lesson.start),
                modifier = GlanceModifier.width(34.dp),
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 9.sp),
            )
            Spacer(GlanceModifier.width(5.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = lesson.subject.ifBlank {
                        localized.getString(R.string.lesson_unknown_subject)
                    },
                    style = TextStyle(
                        color = if (lesson.isCancelled) colors.error else colors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (lesson.isCancelled) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                    ),
                    maxLines = 1,
                )
                Text(
                    text = roomText(lesson, localized),
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = 9.sp,
                    ),
                    maxLines = 1,
                )
            }
            if (warning) {
                Spacer(
                    GlanceModifier
                        .size(5.dp)
                        .cornerRadius(3.dp)
                        .background(ColorProvider(warningColor(dark))),
                )
            }
        }
    }

    @Composable
    private fun LessonRow(
        lesson: UntisLesson,
        localized: Context,
        seedColor: Color,
        dark: Boolean,
        dense: Boolean,
        showBadges: Boolean,
        current: Boolean,
    ) {
        val colors = GlanceTheme.colors
        val cancelled = lesson.isCancelled
        val warning = !cancelled && lesson.hasSubstitution
        val rowModifier = if (current) {
            GlanceModifier
                .fillMaxWidth()
                .background(colors.primaryContainer)
                .cornerRadius(7.dp)
                .padding(horizontal = 4.dp, vertical = 1.dp)
        } else {
            GlanceModifier
                .fillMaxWidth()
                .padding(vertical = if (dense) 1.dp else 2.dp)
        }
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SubjectStripe(lesson.subject, seedColor, dark, if (dense) 18.dp else 22.dp)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = formatTime(lesson.start),
                modifier = GlanceModifier.width(36.dp),
                style = TextStyle(
                    color = if (current) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    fontSize = if (dense) 9.sp else 10.sp,
                ),
            )
            Spacer(GlanceModifier.width(5.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = lesson.subject.ifBlank {
                        localized.getString(R.string.lesson_unknown_subject)
                    },
                    style = TextStyle(
                        color = when {
                            cancelled -> colors.error
                            current -> colors.onPrimaryContainer
                            else -> colors.onSurface
                        },
                        fontSize = if (dense) 10.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (cancelled) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                    ),
                    maxLines = 1,
                )
                Text(
                    text = roomText(lesson, localized),
                    style = TextStyle(
                        color = if (current) {
                            colors.onPrimaryContainer
                        } else {
                            colors.onSurfaceVariant
                        },
                        fontSize = if (dense) 8.sp else 9.sp,
                    ),
                    maxLines = 1,
                )
            }
            if (showBadges) {
                when {
                    cancelled -> {
                        Spacer(GlanceModifier.width(4.dp))
                        Badge(
                            text = localized.getString(R.string.lesson_cancelled),
                            content = colors.onErrorContainer,
                            container = colors.errorContainer,
                        )
                    }

                    warning -> {
                        Spacer(GlanceModifier.width(4.dp))
                        Badge(
                            text = localized.getString(
                                if (lesson.hasRoomChange) {
                                    R.string.lesson_room_short
                                } else {
                                    R.string.lesson_substitution_short
                                },
                            ),
                            content = ColorProvider(warningColor(dark)),
                            container = ColorProvider(warningContainerColor(dark)),
                        )
                    }
                }
            } else if (warning) {
                Spacer(
                    GlanceModifier
                        .size(5.dp)
                        .cornerRadius(3.dp)
                        .background(ColorProvider(warningColor(dark))),
                )
            }
        }
    }

    @Composable
    private fun SubjectStripe(
        subject: String,
        seedColor: Color,
        dark: Boolean,
        height: Dp = 22.dp,
    ) {
        Spacer(
            GlanceModifier
                .width(4.dp)
                .height(height)
                .cornerRadius(2.dp)
                .background(ColorProvider(subjectColor(subject, seedColor, dark))),
        )
    }

    @Composable
    private fun Badge(
        text: String,
        content: ColorProvider,
        container: ColorProvider,
    ) {
        Text(
            text = text,
            modifier = GlanceModifier
                .background(container)
                .cornerRadius(6.dp)
                .padding(horizontal = 5.dp, vertical = 1.dp),
            style = TextStyle(
                color = content,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }

    @Composable
    private fun EmptyWidgetState(
        title: String,
        subtitle: String,
        titleColor: ColorProvider,
        subtitleColor: ColorProvider,
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = title,
                style = TextStyle(
                    color = titleColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
            )
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = subtitle,
                style = TextStyle(color = subtitleColor, fontSize = 11.sp),
                maxLines = 2,
            )
        }
    }

    private data class WidgetData(
        val loggedIn: Boolean,
        val today: LocalDate,
        val now: LocalTime,
        val todayLessons: List<UntisLesson>,
        val current: UntisLesson?,
        val next: UntisLesson?,
        val nextAfterCurrent: UntisLesson?,
        val nextSchoolDay: LocalDate?,
        val nextSchoolDayLessons: List<UntisLesson>,
        val displayDay: LocalDate,
        val displayLessons: List<UntisLesson>,
        val lastSyncAt: Long,
    )

    private enum class WidgetLayout {
        Small,
        Medium,
        Large,
    }

    private fun localizedContext(context: Context, language: AppLanguage): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language.tag))
        return context.createConfigurationContext(configuration)
    }

    private fun languageLocale(context: Context): Locale =
        context.resources.configuration.locales[0] ?: Locale.GERMAN

    private fun formatTime(time: LocalTime): String =
        time.format(DateTimeFormatter.ofPattern("HH:mm"))

    private fun fullDayDate(date: LocalDate, locale: Locale): String {
        val pattern = if (locale.language == "de") "EEEE, d. MMM" else "EEEE, MMM d"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
            .replaceFirstChar { it.titlecase(locale) }
    }

    private fun nextSchoolDayLabel(context: Context, date: LocalDate, locale: Locale): String =
        context.getString(R.string.widget_next_school_day, fullDayDate(date, locale))

    private fun roomText(lesson: UntisLesson, localized: Context): String {
        val room = if (lesson.room.isNotBlank()) {
            localized.getString(R.string.widget_room, lesson.room)
        } else {
            lesson.teacher
        }
        return room
    }

    private fun roomSuffix(lesson: UntisLesson, localized: Context): String {
        val room = roomText(lesson, localized)
        return if (room.isBlank()) "" else " · $room"
    }

    private fun remainingMinutes(lesson: UntisLesson, now: LocalTime): Int =
        Duration.between(now, lesson.end).toMinutes().toInt().coerceAtLeast(1)

    private fun formatSyncTime(lastSyncAt: Long): String =
        Instant.ofEpochMilli(lastSyncAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))

    private fun warningColor(dark: Boolean): Color =
        if (dark) Color(0xFFFFB35C) else Color(0xFFC45A00)

    private fun warningContainerColor(dark: Boolean): Color =
        if (dark) Color(0xFF5A3200) else Color(0xFFFFE0C2)

    /**
     * Mirrors the app's stable subject hue: seed from the current Material You
     * primary, rotated by a fixed per-subject hash.
     */
    private fun subjectColor(subject: String, seedColor: Color, dark: Boolean): Color {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(seedColor.toArgb(), hsv)
        val seed = (stableSubjectHash(subject) % 12) * 30f
        val hue = (hsv[0] + seed) % 360f
        val saturation = if (dark) 0.52f else 0.7f
        val value = if (dark) 0.92f else 0.52f
        return Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
    }

    private fun stableSubjectHash(value: String): Int {
        var hash = 7
        for (char in value.lowercase(Locale.ROOT)) {
            hash = hash * 31 + char.code
        }
        return hash and 0x7fffffff
    }

    private fun widgetCornerRadius(context: Context): Dp {
        val resources = context.resources
        val id = resources.getIdentifier(
            "system_app_widget_background_radius",
            "dimen",
            "android",
        )
        if (id == 0) return 24.dp
        return (resources.getDimension(id) / resources.displayMetrics.density).dp
    }
}
