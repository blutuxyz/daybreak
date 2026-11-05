package com.bdersolutions.daybreak.data.cache

import android.content.Context
import android.util.AtomicFile
import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisAbsence
import com.bdersolutions.daybreak.untis.UntisExam
import com.bdersolutions.daybreak.untis.UntisLesson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class CachedWeek(
    val weekStart: LocalDate,
    val lessons: List<UntisLesson>,
    val updatedAt: Long,
)

data class TimetableCache(
    val studentId: Long = 0L,
    val weeks: Map<LocalDate, CachedWeek> = emptyMap(),
    val lastSyncAt: Long = 0L,
    /** The student's exams for the cached weeks. Empty for class, teacher and room views. */
    val exams: List<UntisExam> = emptyList(),
    /** Subject short name by subject id, so exams can be matched to lessons by subject. */
    val subjectNames: Map<Long, String> = emptyMap(),
    /** The student's absences for the cached weeks, so lessons can be greyed out while the student was away. */
    val absences: List<UntisAbsence> = emptyList(),
)

/**
 * Persists normalized weeks as plain JSON. No credentials or secrets are stored here.
 * AtomicFile prevents a crash from leaving half a cache file behind.
 */
class TimetableCacheStore(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val atomicFile = AtomicFile(file)

    suspend fun load(): TimetableCache = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext TimetableCache()
        runCatching {
            val text = atomicFile.readFully().toString(Charsets.UTF_8)
            decode(text)
        }.getOrDefault(TimetableCache())
    }

    suspend fun save(cache: TimetableCache): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            File(file.parentFile, "$FILE_NAME.tmp").delete()
            val stream = atomicFile.startWrite()
            try {
                stream.write(encode(cache).toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(stream)
            } catch (error: Exception) {
                atomicFile.failWrite(stream)
                throw error
            }
            true
        }.getOrDefault(false)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { atomicFile.delete() }
        Unit
    }

    private fun encode(cache: TimetableCache): String {
        val root = JsonObject().apply {
            addProperty("version", VERSION)
            addProperty("studentId", cache.studentId)
            addProperty("lastSyncAt", cache.lastSyncAt)
            add("weeks", JsonArray().apply {
                cache.weeks.values
                    .sortedByDescending { it.weekStart }
                    .take(MAX_WEEKS)
                    .forEach { week -> add(encodeWeek(week)) }
            })
            add("exams", JsonArray().apply {
                cache.exams.forEach { exam -> add(encodeExam(exam)) }
            })
            add("absences", JsonArray().apply {
                cache.absences.forEach { absence -> add(encodeAbsence(absence)) }
            })
            add("subjectNames", JsonObject().apply {
                cache.subjectNames.forEach { (id, name) -> addProperty(id.toString(), name) }
            })
        }
        return root.toString()
    }

    private fun encodeAbsence(absence: UntisAbsence): JsonObject = JsonObject().apply {
        addProperty("id", absence.id)
        addProperty("start", absence.start.toString())
        addProperty("end", absence.end.toString())
        addProperty("excused", absence.excused)
        absence.reasonId?.let { addProperty("reasonId", it) }
        addProperty("reasonName", absence.reasonName)
        addProperty("text", absence.text)
    }

    private fun decodeAbsence(obj: JsonObject): UntisAbsence? = runCatching {
        UntisAbsence(
            id = obj.get("id")?.asLong ?: 0L,
            start = LocalDateTime.parse(obj.get("start").asString),
            end = LocalDateTime.parse(obj.get("end").asString),
            excused = obj.get("excused")?.asBoolean ?: false,
            reasonId = obj.get("reasonId")?.takeIf { !it.isJsonNull }?.asLong,
            reasonName = obj.get("reasonName")?.asString.orEmpty(),
            text = obj.get("text")?.asString.orEmpty(),
        )
    }.getOrNull()

    private fun encodeExam(exam: UntisExam): JsonObject = JsonObject().apply {
        addProperty("id", exam.id)
        addProperty("date", exam.date.toString())
        addProperty("start", exam.start.toString())
        addProperty("end", exam.end.toString())
        addProperty("subject", exam.subject)
        addProperty("name", exam.name)
        addProperty("text", exam.text)
    }

    private fun decodeExam(obj: JsonObject): UntisExam? = runCatching {
        UntisExam(
            id = obj.get("id")?.asLong ?: 0L,
            date = LocalDate.parse(obj.get("date").asString),
            start = LocalTime.parse(obj.get("start").asString),
            end = LocalTime.parse(obj.get("end").asString),
            subject = obj.get("subject")?.asString.orEmpty(),
            name = obj.get("name")?.asString.orEmpty(),
            text = obj.get("text")?.asString.orEmpty(),
        )
    }.getOrNull()

    private fun encodeWeek(week: CachedWeek): JsonObject = JsonObject().apply {
        addProperty("weekStart", week.weekStart.toString())
        addProperty("updatedAt", week.updatedAt)
        add("lessons", JsonArray().apply {
            week.lessons.forEach { lesson -> add(encodeLesson(lesson)) }
        })
    }

    private fun encodeLesson(lesson: UntisLesson): JsonObject = JsonObject().apply {
        addProperty("id", lesson.id)
        addProperty("date", lesson.date.toString())
        addProperty("start", lesson.start.toString())
        addProperty("end", lesson.end.toString())
        addProperty("subject", lesson.subject)
        addProperty("subjectLong", lesson.subjectLong)
        addProperty("teacher", lesson.teacher)
        addProperty("room", lesson.room)
        addProperty("className", lesson.className)
        addProperty("status", lesson.status.name)
        addProperty("substitutionText", lesson.substitutionText)
        addProperty("lessonText", lesson.lessonText)
        addProperty("info", lesson.info)
        addProperty("group", lesson.group)
        addProperty("originalRoom", lesson.originalRoom)
        addProperty("originalTeacher", lesson.originalTeacher)
    }

    private fun decode(text: String): TimetableCache {
        val root = JsonParser.parseString(text) as? JsonObject ?: return TimetableCache()
        val studentId = root.get("studentId")?.asLong ?: 0L
        val lastSyncAt = root.get("lastSyncAt")?.asLong ?: 0L
        val weeks = linkedMapOf<LocalDate, CachedWeek>()
        root.getAsJsonArray("weeks")?.forEach { element ->
            val obj = element.asJsonObject
            val weekStart = obj.get("weekStart")?.asString?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@forEach
            val updatedAt = obj.get("updatedAt")?.asLong ?: 0L
            val lessons = mutableListOf<UntisLesson>()
            obj.getAsJsonArray("lessons")?.forEach { lessonElement ->
                decodeLesson(lessonElement.asJsonObject)?.let(lessons::add)
            }
            weeks[weekStart] = CachedWeek(weekStart, lessons, updatedAt)
        }
        val exams = root.getAsJsonArray("exams")?.mapNotNull { element ->
            runCatching { decodeExam(element.asJsonObject) }.getOrNull()
        }.orEmpty()
        val subjectNames = root.getAsJsonObject("subjectNames")?.entrySet()?.mapNotNull { (key, value) ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            runCatching { value.asString }.getOrNull()?.let { id to it }
        }?.toMap().orEmpty()
        val absences = root.getAsJsonArray("absences")?.mapNotNull { element ->
            runCatching { decodeAbsence(element.asJsonObject) }.getOrNull()
        }.orEmpty()
        return TimetableCache(studentId, weeks, lastSyncAt, exams, subjectNames, absences)
    }

    private fun decodeLesson(obj: JsonObject): UntisLesson? = runCatching {
        val date = LocalDate.parse(obj.get("date").asString)
        val start = LocalTime.parse(obj.get("start").asString)
        val end = LocalTime.parse(obj.get("end").asString)
        val status = runCatching {
            LessonStatus.valueOf(obj.get("status")?.asString ?: LessonStatus.REGULAR.name)
        }.getOrDefault(LessonStatus.REGULAR)
        UntisLesson(
            id = obj.get("id")?.asLong ?: 0L,
            date = date,
            start = start,
            end = end,
            subject = obj.get("subject")?.asString.orEmpty(),
            subjectLong = obj.get("subjectLong")?.asString.orEmpty(),
            teacher = obj.get("teacher")?.asString.orEmpty(),
            room = obj.get("room")?.asString.orEmpty(),
            className = obj.get("className")?.asString.orEmpty(),
            status = status,
            substitutionText = obj.get("substitutionText")?.asString.orEmpty(),
            lessonText = obj.get("lessonText")?.asString.orEmpty(),
            info = obj.get("info")?.asString.orEmpty(),
            group = obj.get("group")?.asString.orEmpty(),
            originalRoom = obj.get("originalRoom")?.asString.orEmpty(),
            originalTeacher = obj.get("originalTeacher")?.asString.orEmpty(),
        )
    }.getOrNull()

    private companion object {
        const val FILE_NAME = "timetable_cache.json"
        const val MAX_WEEKS = 8

        /**
         * 2 added the exams list, 3 added the subject names exams are matched with, 4 added the
         * absences. Older caches load fine and fill in the new data on the next sync.
         */
        const val VERSION = 4
    }
}
