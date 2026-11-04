package com.bdersolutions.daybreak.data.cache

import android.content.Context
import android.util.AtomicFile
import com.bdersolutions.daybreak.untis.LessonStatus
import com.bdersolutions.daybreak.untis.UntisLesson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Finished weeks of the student's own lessons, kept only so absences can be matched to subjects. */
data class AbsenceLessonData(
    val studentId: Long = 0L,
    val weeks: Map<LocalDate, List<UntisLesson>> = emptyMap(),
)

/**
 * Persists the lessons of weeks that are over. Unlike [TimetableCacheStore] it is not limited to a
 * few weeks, because absences reach back to the start of the school year. Only the fields needed
 * for matching are written: no teachers, rooms or credentials.
 */
class AbsenceLessonStore(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val atomicFile = AtomicFile(file)

    suspend fun load(): AbsenceLessonData = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext AbsenceLessonData()
        runCatching { decode(atomicFile.readFully().toString(Charsets.UTF_8)) }
            .getOrDefault(AbsenceLessonData())
    }

    suspend fun save(data: AbsenceLessonData): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val stream = atomicFile.startWrite()
            try {
                stream.write(encode(data).toByteArray(Charsets.UTF_8))
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

    private fun encode(data: AbsenceLessonData): String {
        val root = JsonObject().apply {
            addProperty("version", VERSION)
            addProperty("studentId", data.studentId)
            add("weeks", JsonArray().apply {
                data.weeks.toSortedMap().forEach { (weekStart, lessons) ->
                    add(JsonObject().apply {
                        addProperty("weekStart", weekStart.toString())
                        add("lessons", JsonArray().apply {
                            lessons.forEach { lesson ->
                                add(JsonObject().apply {
                                    addProperty("id", lesson.id)
                                    addProperty("date", lesson.date.toString())
                                    addProperty("start", lesson.start.toString())
                                    addProperty("end", lesson.end.toString())
                                    addProperty("subject", lesson.subject)
                                    addProperty("status", lesson.status.name)
                                })
                            }
                        })
                    })
                }
            })
        }
        return root.toString()
    }

    private fun decode(text: String): AbsenceLessonData {
        val root = JsonParser.parseString(text) as? JsonObject ?: return AbsenceLessonData()
        val studentId = root.get("studentId")?.asLong ?: 0L
        val weeks = linkedMapOf<LocalDate, List<UntisLesson>>()
        root.getAsJsonArray("weeks")?.forEach { weekElement ->
            val week = weekElement.asJsonObject
            val weekStart = week.get("weekStart")?.asString
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@forEach
            val lessons = week.getAsJsonArray("lessons")?.mapNotNull { lessonElement ->
                runCatching { decodeLesson(lessonElement.asJsonObject) }.getOrNull()
            }.orEmpty()
            weeks[weekStart] = lessons
        }
        return AbsenceLessonData(studentId, weeks)
    }

    private fun decodeLesson(obj: JsonObject): UntisLesson {
        val status = runCatching {
            LessonStatus.valueOf(obj.get("status")?.asString ?: LessonStatus.REGULAR.name)
        }.getOrDefault(LessonStatus.REGULAR)
        return UntisLesson(
            id = obj.get("id")?.asLong ?: 0L,
            date = LocalDate.parse(obj.get("date").asString),
            start = LocalTime.parse(obj.get("start").asString),
            end = LocalTime.parse(obj.get("end").asString),
            subject = obj.get("subject")?.asString.orEmpty(),
            status = status,
        )
    }

    private companion object {
        const val FILE_NAME = "absence_lessons.json"
        const val VERSION = 1
    }
}
