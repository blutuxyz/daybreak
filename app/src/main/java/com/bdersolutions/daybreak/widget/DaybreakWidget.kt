package com.bdersolutions.daybreak.widget

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
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
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.bdersolutions.daybreak.DaybreakApplication
import com.bdersolutions.daybreak.MainActivity
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.cache.CachedWeek
import com.bdersolutions.daybreak.data.cache.TimetableCache
import com.bdersolutions.daybreak.data.settings.AppLanguage
import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisLesson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class DaybreakWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 110.dp),
            DpSize(250.dp, 160.dp),
            DpSize(320.dp, 220.dp),
        ),
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

        provideContent {
            WidgetTheme(context) {
                WidgetContent(data, localized)
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
            .filter { it.date >= today.minusDays(1) }
            .sortedWith(compareBy<UntisLesson> { it.date }.thenBy { it.start })
        val todayLessons = all.filter { it.date == today }
        val current = todayLessons.firstOrNull { !it.start.isAfter(now) && it.end.isAfter(now) }
        val nextToday = todayLessons.firstOrNull { it.start.isAfter(now) }
        val nextFuture = all.firstOrNull { it.date > today }
        val focus = current ?: nextToday ?: nextFuture
        return WidgetData(
            loggedIn = loggedIn,
            todayLessons = todayLessons,
            focus = focus,
            isNow = current != null && focus?.id == current.id,
            today = today,
            lastSyncAt = cache.lastSyncAt,
        )
    }

    @Composable
    private fun WidgetTheme(context: Context, content: @Composable () -> Unit) {
        val light = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(context)
        } else {
            fallbackLight()
        }
        val dark = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            fallbackDark()
        }
        GlanceTheme(colors = ColorProviders(light = light, dark = dark)) {
            content()
        }
    }

    @Composable
    private fun WidgetContent(data: WidgetData, localized: Context) {
        val compact = LocalSize.current.height < 150.dp
        val colors = GlanceTheme.colors
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(colors.widgetBackground)
                .appWidgetBackground()
                .cornerRadius(24.dp)
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Top,
        ) {
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
                        text = data.today.format(
                            DateTimeFormatter.ofPattern("EEEE, d. MMMM", languageLocale(localized)),
                        ),
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp,
                        ),
                        maxLines = 1,
                    )
                }
                if (!compact) {
                    Text(
                        text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = TextStyle(color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }

            Spacer(GlanceModifier.height(10.dp))

            when {
                !data.loggedIn -> EmptyWidgetState(
                    localized.getString(R.string.widget_login_prompt),
                    localized.getString(R.string.widget_open_app),
                    colors.onSurface,
                    colors.onSurfaceVariant,
                )
                data.focus == null -> EmptyWidgetState(
                    localized.getString(R.string.widget_no_lessons),
                    localized.getString(R.string.widget_open_app),
                    colors.onSurface,
                    colors.onSurfaceVariant,
                )
                else -> {
                    val lesson = data.focus ?: return@Column
                    FocusedLesson(
                        lesson = lesson,
                        isNow = data.isNow,
                        isToday = lesson.date == data.today,
                        localized = localized,
                        compact = compact,
                    )
                    if (!compact) {
                        val additional = data.todayLessons
                            .filter { it.id != lesson.id || it.date != lesson.date }
                            .filter { it.start.isAfter(LocalTime.now()) }
                            .take(2)
                        if (additional.isNotEmpty()) {
                            Spacer(GlanceModifier.height(10.dp))
                            additional.forEach { extra ->
                                CompactLesson(extra, localized)
                                Spacer(GlanceModifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FocusedLesson(
        lesson: UntisLesson,
        isNow: Boolean,
        isToday: Boolean,
        localized: Context,
        compact: Boolean,
    ) {
        val colors = GlanceTheme.colors
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = when {
                    isNow -> localized.getString(R.string.widget_now)
                    isToday -> localized.getString(R.string.widget_next_lesson)
                    else -> localized.getString(
                        R.string.widget_next_on_day,
                        lesson.date.format(DateTimeFormatter.ofPattern("EEE", languageLocale(localized))),
                    )
                },
                style = TextStyle(
                    color = if (isNow) colors.primary else colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.height(2.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${lesson.start} – ${lesson.end}",
                    style = TextStyle(
                        color = colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.width(10.dp))
                Text(
                    text = lesson.subject.ifBlank { localized.getString(R.string.lesson_unknown_subject) },
                    style = TextStyle(
                        color = if (lesson.isCancelled) colors.error else colors.onSurface,
                        fontSize = if (compact) 15.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (lesson.isCancelled) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    maxLines = 1,
                )
            }
            val pieces = buildList {
                if (lesson.room.isNotBlank()) add(localized.getString(R.string.widget_room, lesson.room))
                if (lesson.isCancelled) add(localized.getString(R.string.lesson_cancelled))
                else if (lesson.hasRoomChange) add(localized.getString(R.string.lesson_room_changed))
                else if (lesson.hasSubstitution) add(localized.getString(R.string.lesson_substitution))
            }
            if (pieces.isNotEmpty()) {
                Text(
                    text = pieces.joinToString(" · "),
                    style = TextStyle(
                        color = if (lesson.isCancelled) colors.error
                        else if (lesson.hasSubstitution) colors.tertiary
                        else colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun CompactLesson(lesson: UntisLesson, localized: Context) {
        val colors = GlanceTheme.colors
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${lesson.start}",
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = lesson.subject.ifBlank { localized.getString(R.string.lesson_unknown_subject) },
                style = TextStyle(color = colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = lesson.room,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun EmptyWidgetState(
        title: String,
        subtitle: String,
        titleColor: androidx.glance.unit.ColorProvider,
        subtitleColor: androidx.glance.unit.ColorProvider,
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = title,
                style = TextStyle(color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = subtitle,
                style = TextStyle(color = subtitleColor, fontSize = 12.sp),
                maxLines = 2,
            )
        }
    }

    private data class WidgetData(
        val loggedIn: Boolean,
        val todayLessons: List<UntisLesson>,
        val focus: UntisLesson?,
        val isNow: Boolean,
        val today: LocalDate,
        val lastSyncAt: Long,
    )

    private fun localizedContext(context: Context, language: AppLanguage): Context {
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language.tag))
        return context.createConfigurationContext(configuration)
    }

    private fun languageLocale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.GERMAN
}

private fun fallbackLight(): ColorScheme = lightColorScheme(
    primary = Color(0xFF3F5F90),
    secondary = Color(0xFF53606F),
    tertiary = Color(0xFF6B5778),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFF8F9FF),
)

private fun fallbackDark(): ColorScheme = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    secondary = Color(0xFFBBC7DB),
    tertiary = Color(0xFFD7BDE5),
    background = Color(0xFF111418),
    surface = Color(0xFF111418),
)
