package com.bdersolutions.daybreak.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bdersolutions.daybreak.MainActivity
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.data.repository.TimetableRepository
import com.bdersolutions.daybreak.data.schedule.LessonRun
import com.bdersolutions.daybreak.data.settings.SettingsRepository
import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisHomework
import com.bdersolutions.daybreak.untis.UntisLesson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds and posts the two reminder notifications: the summary before the first lesson and the
 * evening note about exams and homework that are due the next day. Each has its own channel, so
 * either can be muted in Android's settings without touching the other.
 */
class ReminderNotificationManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val timetable: TimetableRepository,
    private val schoolData: SchoolDataRepository,
    private val store: ReminderStore,
) {
    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val localized = localizedContext()
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MORNING,
                localized.getString(R.string.reminder_channel_morning),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = localized.getString(R.string.reminder_channel_morning_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EVENING,
                localized.getString(R.string.reminder_channel_evening),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = localized.getString(R.string.reminder_channel_evening_description) },
        )
    }

    // ---- The scheduled reminders ----

    /**
     * Sends the summary for [date] unless it was handled already or school is over. The day is
     * marked as handled even if nothing was sent, so a refusal (no permission) cannot loop.
     */
    suspend fun sendMorningSummary(date: LocalDate) {
        if (store.lastMorning() == date) return
        store.markMorning(date)
        val settings = settingsRepository.current()
        if (!settings.notificationsEnabled || !settings.morningSummaryEnabled || !canNotify()) return
        if (date != LocalDate.now()) return
        // Fresh data first: a substitution entered at 06:30 should be in a 06:55 summary. Offline is fine.
        runCatching { timetable.refreshWeeks(listOf(timetable.weekStartFor(date))) }
        timetable.awaitLoaded()
        val summary = DaySummaries.build(date, timetable.lessonsFor(date), timetable.cache.value.exams) ?: return
        val lastEnd = summary.lastEnd
        if (lastEnd != null && LocalTime.now().isAfter(lastEnd)) return
        postMorning(summary)
    }

    /** Sends the reminder for what is due on [today] plus one day, with the same rules as the summary. */
    suspend fun sendEveningReminder(today: LocalDate) {
        if (store.lastEvening() == today) return
        store.markEvening(today)
        val settings = settingsRepository.current()
        if (!settings.notificationsEnabled || !settings.examRemindersEnabled || !canNotify()) return
        val reminder = loadEvening(today.plusDays(1)) ?: return
        postEvening(reminder)
    }

    // ---- Previews from the settings page ----

    /** A summary of today, or of a made-up day if there is no school today, so the look can be checked. */
    suspend fun sendMorningPreview() {
        if (!canNotify(ignoreFeatureSwitch = true)) return
        timetable.awaitLoaded()
        val today = LocalDate.now()
        val summary = DaySummaries.build(today, timetable.lessonsFor(today), timetable.cache.value.exams)
            ?: sampleSummary(today)
        postMorning(summary)
    }

    suspend fun sendEveningPreview() {
        if (!canNotify(ignoreFeatureSwitch = true)) return
        val reminder = loadEvening(LocalDate.now().plusDays(1)) ?: sampleEvening(LocalDate.now().plusDays(1))
        postEvening(reminder)
    }

    // ---- Building and posting ----

    private suspend fun loadEvening(date: LocalDate): EveningReminder? {
        runCatching { timetable.ensureWeek(date) }
        timetable.awaitLoaded()
        val homework = runCatching { schoolData.homework(date, date) }.getOrDefault(emptyList())
        return EveningReminders.build(date, timetable.cache.value.exams, homework)
    }

    private fun postMorning(summary: DaySummary) {
        val localized = localizedContext()
        val time = DateTimeFormatter.ofPattern("HH:mm")
        val title: String
        val text: String
        if (summary.periods == 0) {
            title = localized.getString(R.string.reminder_morning_all_cancelled)
            text = localized.getString(R.string.reminder_morning_all_cancelled_text)
        } else {
            val lessons = localized.resources.getQuantityString(R.plurals.reminder_lessons, summary.periods, summary.periods)
            title = localized.getString(R.string.reminder_morning_title, lessons, summary.firstStart!!.format(time))
            text = listOfNotNull(
                if (summary.changeCount > 0) {
                    localized.resources.getQuantityString(R.plurals.reminder_changes, summary.changeCount, summary.changeCount)
                } else {
                    localized.getString(R.string.reminder_no_changes)
                },
                summary.exams.takeIf { it.isNotEmpty() }?.let {
                    localized.resources.getQuantityString(R.plurals.reminder_exams, it.size, it.size)
                },
                localized.getString(R.string.reminder_school_ends, summary.lastEnd!!.format(time)),
            ).joinToString(" · ")
        }
        val lines = buildList {
            summary.cancelled.forEach { add("• " + describeCancelled(it, localized)) }
            summary.changed.forEach { add("• " + describeChanged(it, localized)) }
            summary.exams.forEach { exam ->
                add("• " + localized.getString(R.string.reminder_exam_line, examName(exam, localized), exam.start.format(time)))
            }
        }
        post(CHANNEL_MORNING, NOTIFICATION_MORNING, title, text, lines)
    }

    private fun postEvening(reminder: EveningReminder) {
        val localized = localizedContext()
        val time = DateTimeFormatter.ofPattern("HH:mm")
        val title = localized.getString(R.string.reminder_evening_title)
        val text = listOfNotNull(
            reminder.exams.takeIf { it.isNotEmpty() }?.let {
                localized.resources.getQuantityString(R.plurals.reminder_exams, it.size, it.size)
            },
            reminder.homework.takeIf { it.isNotEmpty() }?.let {
                localized.resources.getQuantityString(R.plurals.reminder_homework, it.size, it.size)
            },
        ).joinToString(" · ")
        val lines = buildList {
            reminder.exams.forEach { exam ->
                add("• " + localized.getString(R.string.reminder_evening_exam_line, examName(exam, localized), exam.start.format(time)))
            }
            reminder.homework.forEach { work ->
                val subject = work.subject.ifBlank { localized.getString(R.string.lesson_unknown_subject) }
                add("• " + localized.getString(R.string.reminder_evening_homework_line, subject, work.text.trim().take(MAX_TEXT)))
            }
        }
        post(CHANNEL_EVENING, NOTIFICATION_EVENING, title, text, lines)
    }

    private fun post(channel: String, id: Int, title: String, text: String, lines: List<String>) {
        ensureChannels()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (lines.isNotEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n" + lines.joinToString("\n")))
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    private fun describeCancelled(run: LessonRun, localized: Context): String =
        localized.getString(R.string.notification_cancelled, subjectOf(run.lesson, localized), run.start.format(TIME))

    private fun describeChanged(run: LessonRun, localized: Context): String {
        val lesson = run.lesson
        val subject = subjectOf(lesson, localized)
        return when {
            lesson.hasRoomChange ->
                localized.getString(R.string.notification_room_changed, subject, lesson.originalRoom.ifBlank { "–" }, lesson.room.ifBlank { "–" })
            lesson.hasTeacherChange ->
                localized.getString(R.string.notification_teacher_changed, subject, lesson.teacher.ifBlank { "–" }, lesson.originalTeacher.ifBlank { "–" })
            else -> localized.getString(R.string.notification_substitution, subject, run.start.format(TIME))
        }
    }

    private fun subjectOf(lesson: UntisLesson, localized: Context): String =
        lesson.subject.ifBlank { localized.getString(R.string.lesson_unknown_subject) }

    private fun examName(exam: UntisExam, localized: Context): String =
        listOf(exam.subject, exam.name).filter { it.isNotBlank() }.distinct().joinToString(" ")
            .ifBlank { localized.getString(R.string.lesson_unknown_subject) }

    private fun canNotify(ignoreFeatureSwitch: Boolean = false): Boolean {
        if (!ignoreFeatureSwitch && !settingsRepository.current().notificationsEnabled) return false
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun localizedContext(): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(settingsRepository.current().language.tag))
        return context.createConfigurationContext(config)
    }

    private fun sampleSummary(date: LocalDate): DaySummary {
        fun lesson(start: String, end: String, subject: String, status: LessonStatus = LessonStatus.REGULAR, room: String = "", original: String = "") =
            UntisLesson(
                id = (start + subject).hashCode().toLong(), date = date, start = LocalTime.parse(start),
                end = LocalTime.parse(end), subject = subject, room = room, status = status, originalRoom = original,
            )
        return DaySummaries.build(
            date,
            listOf(
                lesson("07:55", "08:45", "M"),
                lesson("08:45", "09:35", "E", room = "105", original = "204"),
                lesson("09:50", "10:40", "PH", status = LessonStatus.CANCELLED),
                lesson("10:40", "11:30", "D"),
            ),
            emptyList(),
        )!!
    }

    private fun sampleEvening(date: LocalDate) = EveningReminder(
        date = date,
        exams = listOf(UntisExam(0, date, LocalTime.of(8, 0), LocalTime.of(9, 0), "M", name = "")),
        homework = listOf(UntisHomework(0, "E", null, date, "Read chapter 4")),
    )

    companion object {
        const val CHANNEL_MORNING = "daily_summary"
        const val CHANNEL_EVENING = "due_reminders"
        private const val NOTIFICATION_MORNING = 4102
        private const val NOTIFICATION_EVENING = 4103
        private const val MAX_TEXT = 80
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
