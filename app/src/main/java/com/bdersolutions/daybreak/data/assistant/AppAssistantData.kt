package com.bdersolutions.daybreak.data.assistant

import android.content.Context
import com.bdersolutions.daybreak.data.notices.RemoteFeatures
import com.bdersolutions.daybreak.data.notices.ServerNotices
import com.bdersolutions.daybreak.data.repository.AbsenceLessonHistory
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.data.repository.TimetableRepository
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisHomework
import com.bdersolutions.daybreak.untis.UntisLesson
import com.bdersolutions.daybreak.untis.UntisRecipient
import java.time.LocalDate
import java.util.UUID

/**
 * The student's data for the assistant's tools. Lessons outside the cache come through
 * [AbsenceLessonHistory], which never touches the timetable cache, sync or widget.
 */
class AppAssistantData(
    private val timetable: TimetableRepository,
    private val history: AbsenceLessonHistory,
    private val schoolData: SchoolDataRepository,
    private val serverNotices: ServerNotices,
) : AssistantData {
    override suspend fun lessons(from: LocalDate, to: LocalDate): List<UntisLesson>? {
        val loaded = history.lessons(from, to)
        val lessons = loaded.lessons.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        return if (lessons.isEmpty() && loaded.failedWeeks > 0) null else lessons
    }

    override suspend fun exams(from: LocalDate, to: LocalDate): List<UntisExam> =
        runCatching { schoolData.exams(from, to) }.getOrElse {
            // Offline: what the cache has for that range.
            timetable.cache.value.exams.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        }

    override suspend fun homework(from: LocalDate, to: LocalDate): List<UntisHomework> = schoolData.homework(from, to)

    override fun absences(): List<UntisAbsence> = timetable.cache.value.absences

    override suspend fun teachers(): List<UntisRecipient> = schoolData.recipients()

    override val canSendMessages: Boolean
        get() = serverNotices.featureOn(RemoteFeatures.MESSAGES_SEND)
}

/**
 * The random id the assistant's daily budget is counted under on the server. It names no one: it is made
 * on this phone, is not linked to the WebUntis account, and the server keeps only a hash of it.
 */
class AssistantIdentity(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val installId: String
        get() = preferences.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(KEY_ID, it).apply()
        }

    /** The model the student picked, if the server shows the picker. */
    var model: String?
        get() = preferences.getString(KEY_MODEL, null)
        set(value) = preferences.edit().putString(KEY_MODEL, value).apply()

    /** The reasoning effort the student picked, if the server shows the picker. */
    var effort: String?
        get() = preferences.getString(KEY_EFFORT, null)
        set(value) = preferences.edit().putString(KEY_EFFORT, value).apply()

    private companion object {
        const val KEY_EFFORT = "effort"
        const val PREFS_NAME = "daybreak_assistant"
        const val KEY_ID = "install_id"
        const val KEY_MODEL = "model"
    }
}
