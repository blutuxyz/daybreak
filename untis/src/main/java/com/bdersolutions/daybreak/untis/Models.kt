package com.bdersolutions.daybreak.untis

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Identity and secret data required for a mobile API session. */
data class UntisSession(
    val server: String,
    val school: String,
    val username: String,
    val secret: String,
    val studentId: Long,
    val studentName: String,
    val studentType: String = "STUDENT",
)

/** A child (or the account's own student) whose timetable can be loaded. */
data class UntisChild(
    val id: Long,
    val firstName: String,
    val lastName: String = "",
    val type: String = "STUDENT",
) {
    val displayName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Student $id" }
}

/** Result of a successful login, including the derived TOTP secret. */
data class UntisLoginResult(
    val server: String,
    val school: String,
    val username: String,
    val secret: String,
    val displayName: String,
    val schoolName: String,
    val children: List<UntisChild>,
    val usedPasswordLogin: Boolean,
)

/** How a lesson differs from the regular timetable. */
enum class LessonStatus {
    REGULAR,
    CANCELLED,
    IRREGULAR,
}

/** Normalized timetable period, independent of the wire format. */
data class UntisLesson(
    val id: Long,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val subject: String,
    val subjectLong: String = "",
    val teacher: String = "",
    val room: String = "",
    val className: String = "",
    val status: LessonStatus = LessonStatus.REGULAR,
    val substitutionText: String = "",
    val lessonText: String = "",
    val info: String = "",
    val group: String = "",
    val originalRoom: String = "",
    val originalTeacher: String = "",
) {
    val isCancelled: Boolean get() = status == LessonStatus.CANCELLED
    val isIrregular: Boolean get() = status == LessonStatus.IRREGULAR

    val hasRoomChange: Boolean
        get() = originalRoom.isNotBlank() && originalRoom != room

    val hasTeacherChange: Boolean
        get() = originalTeacher.isNotBlank() && originalTeacher != teacher

    val hasSubstitution: Boolean
        get() = hasRoomChange || hasTeacherChange || isIrregular || substitutionText.isNotBlank()

    val startsAt: LocalDateTime get() = LocalDateTime.of(date, start)
    val endsAt: LocalDateTime get() = LocalDateTime.of(date, end)
}

/**
 * A scheduled test or exam (Prüfung) for the student. [subject] is the short subject
 * name used to match it to a lesson; [name] and [text] are whatever the school wrote.
 */
data class UntisExam(
    val id: Long,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val subject: String,
    val name: String = "",
    val text: String = "",
)

/** One fetched range of lessons together with parser metadata. */
data class UntisTimetable(
    val lessons: List<UntisLesson>,
    val masterDataTimestamp: Long = 0L,
    val directory: UntisDirectory = UntisDirectory(emptyList(), emptyList(), emptyList(), emptyMap()),
)

/** The kinds of school element whose timetable can be viewed. */
enum class ElementType { CLASS, TEACHER, ROOM }

/** A class, teacher or room that can be selected in the timetable picker. */
data class UntisElement(
    val id: Long,
    val type: ElementType,
    val name: String,
    val longName: String = "",
) {
    val displayName: String
        get() = longName.ifBlank { name }
}

/** Master data delivered with every timetable response. */
data class UntisDirectory(
    val classes: List<UntisElement>,
    val teachers: List<UntisElement>,
    val rooms: List<UntisElement>,
    val absenceReasons: Map<Long, String>,
    /** Short subject name by subject id, the names lessons carry. Exams refer to subjects by id. */
    val subjectNamesById: Map<Long, String> = emptyMap(),
)

/** One absence (Fehlstunde) of the student. Times are local school time. */
data class UntisAbsence(
    val id: Long,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val excused: Boolean,
    val reasonId: Long?,
    val reasonName: String,
    val text: String,
) {
    val duration: Duration
        get() = Duration.between(start, end)
}

/** Outcome of one diagnostic call. The raw response itself goes to the debug log. */
data class ProbeResult(
    val label: String,
    val method: String,
    val ok: Boolean,
    val message: String,
)

/** Callback for the debug screen. It must never receive request bodies or secrets. */
fun interface RawResponseSink {
    fun onResponse(method: String, receivedAt: Long, body: String)
}

/** One homework entry (Hausaufgabe). [subject] is the short name of the lesson it belongs to. */
data class UntisHomework(
    val id: Long,
    val subject: String,
    val assigned: LocalDate?,
    val due: LocalDate,
    val text: String,
    val remark: String = "",
    val completed: Boolean = false,
)

/** A notice from the school's "messages of the day" (Mitteilungen). */
data class UntisMessage(
    val id: Long,
    val subject: String,
    val text: String,
)

/** The folders of the WebUntis message box (Nachrichten). */
enum class MailFolder { INBOX, SENT, DRAFTS }

/** A person on a message, named as WebUntis shows them (for teachers the short name). */
data class UntisMailPerson(
    val userId: Long,
    val name: String,
)

/**
 * One message of the message box. Lists only carry a [preview]; [content] is filled when a single
 * message is loaded.
 */
data class UntisMail(
    val id: Long,
    val subject: String,
    val preview: String = "",
    val content: String = "",
    val sender: UntisMailPerson? = null,
    val recipients: List<UntisMailPerson> = emptyList(),
    val sentAt: LocalDateTime? = null,
    val read: Boolean = true,
    val hasAttachments: Boolean = false,
)
