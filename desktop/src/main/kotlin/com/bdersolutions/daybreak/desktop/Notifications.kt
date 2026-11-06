package com.bdersolutions.daybreak.desktop

import com.bdersolutions.daybreak.data.reminders.DaySummaries
import com.bdersolutions.daybreak.data.reminders.EveningReminders
import com.bdersolutions.daybreak.data.reminders.ReminderPlanner
import com.bdersolutions.daybreak.data.settings.AppSettings
import com.bdersolutions.daybreak.data.sync.LessonChangeDetector
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Shows a balloon from the system tray icon. On Windows this appears as a notification in the
 * action centre. If the tray is not available, nothing is shown and the app keeps running.
 */
object TrayNotifier {
    private val icon: TrayIcon? by lazy { createIcon() }

    private fun createIcon(): TrayIcon? = try {
        if (!SystemTray.isSupported()) {
            null
        } else {
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            graphics.color = java.awt.Color(0x3B6EA8)
            graphics.fillOval(0, 0, 16, 16)
            graphics.dispose()
            TrayIcon(image, "Daybreak").also { SystemTray.getSystemTray().add(it) }
        }
    } catch (_: Exception) {
        null
    }

    fun show(title: String, text: String) {
        runCatching { icon?.displayMessage(title, text, TrayIcon.MessageType.INFO) }
    }
}

private const val KEY_MORNING = "morning_last"
private const val KEY_EVENING = "evening_last"

/**
 * Sends the morning summary and the evening exam/homework reminder when they are due. Each one is
 * sent once per day; the last day is kept in the reminder store.
 */
suspend fun runReminders(container: DesktopContainer, settings: AppSettings) {
    val language = settings.language
    val now = LocalDateTime.now()
    val cache = container.timetable.cache.value
    val lessons = cache.weeks.values.flatMap { it.lessons }
    val exams = cache.exams
    val store = container.reminderStore

    if (settings.morningSummaryEnabled) {
        val last = store.getString(KEY_MORNING)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val plan = ReminderPlanner.nextMorning(
            now = now,
            firstLessonStart = { date ->
                lessons.filter { it.date == date && !it.isHoliday && !it.isCancelled }.minOfOrNull { it.start }
            },
            leadMinutes = settings.morningLeadMinutes,
            lastSent = last,
        )
        if (plan != null && !now.isBefore(plan.at)) {
            DaySummaries.build(plan.date, lessons, exams)?.let { summary ->
                val body = listOf(
                    trFor(language, "${summary.periods} Stunden", "${summary.periods} lessons"),
                    summary.changeCount.takeIf { it > 0 }?.let { trFor(language, "$it Änderungen", "$it changes") },
                    summary.exams.takeIf { it.isNotEmpty() }?.let { trFor(language, "${it.size} Prüfungen", "${it.size} exams") },
                ).filterNotNull().joinToString(" · ")
                TrayNotifier.show(trFor(language, "Heute im Stundenplan", "Today's timetable"), body)
            }
            store.edit().putString(KEY_MORNING, plan.date.toString()).apply()
        }
    }

    if (settings.examRemindersEnabled) {
        val last = store.getString(KEY_EVENING)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val at = ReminderPlanner.nextEvening(now, last)
        if (!now.isBefore(at)) {
            val tomorrow = at.toLocalDate().plusDays(1)
            val homework = runCatching {
                container.schoolData.homework(at.toLocalDate(), tomorrow)
            }.getOrDefault(emptyList())
            EveningReminders.build(tomorrow, exams, homework)?.let { reminder ->
                val parts = listOfNotNull(
                    reminder.exams.takeIf { it.isNotEmpty() }?.let {
                        trFor(language, "Prüfungen: ", "Exams: ") + it.joinToString { exam -> exam.subject.ifBlank { exam.name } }
                    },
                    reminder.homework.takeIf { it.isNotEmpty() }?.let {
                        trFor(language, "Hausaufgaben: ", "Homework: ") + it.joinToString { hw -> hw.subject }
                    },
                )
                TrayNotifier.show(trFor(language, "Morgen fällig", "Due tomorrow"), parts.joinToString("\n"))
            }
            store.edit().putString(KEY_EVENING, at.toLocalDate().toString()).apply()
        }
    }
}

/**
 * Refreshes this week and the next from WebUntis and, when notifications are on, reports lessons
 * that changed since the last refresh.
 */
suspend fun syncTimetableOnce(container: DesktopContainer, settings: AppSettings) {
    val monday = LocalDate.now().with(DayOfWeek.MONDAY)
    val result = container.timetable.refreshWeeks(setOf(monday, monday.plusWeeks(1)))
    if (!settings.notificationsEnabled) return
    val changes = LessonChangeDetector.detect(result.previousWeeks, result.currentWeeks)
    if (changes.isEmpty()) return
    val date = DateTimeFormatter.ofPattern("EEE d MMM", localeOf(settings.language))
    val body = changes.take(5).joinToString("\n") { change ->
        "${change.lesson.date.format(date)} ${change.lesson.start.format(DateTimeFormatter.ofPattern("HH:mm"))} " +
            change.lesson.subject
    }
    TrayNotifier.show(
        trFor(settings.language, "Stundenplan geändert", "Timetable changed"),
        body + if (changes.size > 5) "\n…" else "",
    )
}

