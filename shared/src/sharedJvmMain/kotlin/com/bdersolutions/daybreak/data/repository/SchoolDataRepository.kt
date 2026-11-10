package com.bdersolutions.daybreak.data.repository

import com.bdersolutions.daybreak.data.session.SessionStore
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisApi
import com.bdersolutions.daybreak.untis.UntisAuthException
import com.bdersolutions.daybreak.untis.UntisDirectory
import com.bdersolutions.daybreak.untis.UntisElement
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisHomework
import com.bdersolutions.daybreak.untis.UntisDownloadLink
import com.bdersolutions.daybreak.untis.UntisMail
import com.bdersolutions.daybreak.untis.UntisRecipient
import com.bdersolutions.daybreak.untis.UntisMessage
import com.bdersolutions.daybreak.untis.MailFolder
import com.bdersolutions.daybreak.untis.UntisLesson
import com.bdersolutions.daybreak.untis.UntisSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Whose timetable the timetable screen shows. */
sealed interface TimetableTarget {
    /** The signed-in student's own timetable, which is cached and synced. */
    data object Own : TimetableTarget

    /** A class, teacher or room. These are loaded on demand and kept in memory only. */
    data class Element(val element: UntisElement) : TimetableTarget {
        val cachePrefix: String
            get() = "${element.type.name}:${element.id}:"

        fun cacheKey(weekStart: LocalDate): String = cachePrefix + weekStart
    }
}

/**
 * Reads school data that is not part of the student's cached timetable: master data for
 * the picker, absences, and other classes', teachers' or rooms' timetables.
 */
class SchoolDataRepository(
    private val api: UntisApi,
    private val sessionStore: SessionStore,
) {
    private val _directory = MutableStateFlow<UntisDirectory?>(null)
    val directory: StateFlow<UntisDirectory?> = _directory.asStateFlow()

    /** Loads the master data once; [force] fetches it again. */
    suspend fun loadDirectory(force: Boolean = false): UntisDirectory {
        if (!force) _directory.value?.let { return it }
        val fresh = api.fetchDirectory(requireSession(), LocalDate.now())
        _directory.value = fresh
        return fresh
    }

    suspend fun absences(startDate: LocalDate, endDate: LocalDate): List<UntisAbsence> =
        api.fetchAbsences(requireSession(), startDate, endDate)

    /** The class register topics ("Lehrstoff") of these lessons, by lesson id. */
    suspend fun lessonTopics(lessonIds: List<Long>): Map<Long, String> =
        api.fetchLessonTopics(requireSession(), lessonIds)

    suspend fun homework(startDate: LocalDate, endDate: LocalDate): List<UntisHomework> =
        api.fetchHomework(requireSession(), startDate, endDate)

    suspend fun messages(date: LocalDate): List<UntisMessage> =
        api.fetchMessages(requireSession(), date)

    /** The messages (Nachrichten) of one folder, newest first. */
    suspend fun mails(folder: MailFolder): List<UntisMail> = api.fetchMailList(requireSession(), folder)

    /** The people the student may write to. */
    suspend fun recipients(): List<UntisRecipient> = api.fetchRecipients(requireSession())

    /** Sends a message to one teacher. Returns how many recipients the server counted. */
    suspend fun sendMail(subject: String, content: String, recipientUserId: Long): Int =
        api.sendMail(requireSession(), subject, content, recipientUserId)

    /** The signed address of one attached file. */
    suspend fun attachmentLink(attachmentId: String): UntisDownloadLink =
        api.fetchAttachmentLink(requireSession(), attachmentId)

    /** Streams the file behind [link] into [out] and returns its size. */
    suspend fun downloadAttachment(link: UntisDownloadLink, out: java.io.OutputStream, maxBytes: Long): Long =
        api.downloadAttachment(link, out, maxBytes)

    /** One message with its full text. */
    suspend fun mail(id: Long): UntisMail = api.fetchMail(requireSession(), id)

    /** Exams in a range. They carry only a subject id, so the master data names them. */
    suspend fun exams(startDate: LocalDate, endDate: LocalDate): List<UntisExam> {
        val names = loadDirectory().subjectNamesById
        return api.fetchExams(requireSession(), startDate, endDate, names)
    }

    suspend fun elementWeek(element: UntisElement, weekStart: LocalDate): List<UntisLesson> =
        api.fetchElementTimetable(
            session = requireSession(),
            element = element,
            startDate = weekStart,
            endDate = weekStart.plusDays(6),
        ).lessons

    fun clear() {
        _directory.value = null
    }

    private fun requireSession(): UntisSession =
        sessionStore.timetableSession() ?: throw UntisAuthException("Not signed in.")
}
