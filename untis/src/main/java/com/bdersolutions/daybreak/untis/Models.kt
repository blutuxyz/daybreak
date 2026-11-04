package com.bdersolutions.daybreak.untis

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

/** One fetched range of lessons together with parser metadata. */
data class UntisTimetable(
    val lessons: List<UntisLesson>,
    val masterDataTimestamp: Long = 0L,
)

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
