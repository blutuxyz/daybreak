package com.bdersolutions.daybreak.untis

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * Reads the student's exams (Prüfungen).
 *
 * The exam response has not been checked against every school's server, so the field
 * names are read tolerantly. An exam that cannot be read is skipped instead of failing
 * the whole list, and the timetable keeps working without exams.
 */
internal object ExamParser {

    /**
     * [subjectNames] maps a subject id to its short name. Exams carry only the id, and the id
     * is what lets an exam match the right lesson.
     */
    fun parse(result: JsonElement, subjectNames: Map<Long, String> = emptyMap()): List<UntisExam> {
        val items: JsonArray = when {
            result.isJsonArray -> result.asJsonArray
            result.isJsonObject -> result.asJsonObject.arrayOrNull("exams", "examList", "items")
                ?: return emptyList()

            else -> return emptyList()
        }
        return items.mapNotNull { element ->
            element.asJsonObjectOrNull()?.let { parseExam(it, subjectNames) }
        }
    }

    /**
     * Shape seen on a live server: `startDateTime` / `endDateTime` as "2026-11-06T07:55Z",
     * `examType` ("Test", "Diktat"), `name` and `text` (often identical, with a leading
     * space), and `subjectId` only, with no subject short name. The older `examDate` /
     * `startTime` shape is still accepted.
     */
    private fun parseExam(obj: JsonObject, subjectNames: Map<Long, String>): UntisExam? {
        val startPair = TimetableParser.parseCombinedDateTime(obj.takeFirstElement("startDateTime", "start"))
        val date: LocalDate = startPair?.first
            ?: WireDate.parseDate(obj.takeFirstElement("examDate", "date", "startDate"))
            ?: return null
        val start: LocalTime = startPair?.second
            ?: WireDate.parseTime(obj.takeFirstElement("startTime"))
            ?: return null
        val endPair = TimetableParser.parseCombinedDateTime(obj.takeFirstElement("endDateTime", "end"))
        val rawEnd = endPair?.second ?: WireDate.parseTime(obj.takeFirstElement("endTime"))
        val end = if (rawEnd != null && rawEnd.isAfter(start)) rawEnd else start.plusMinutes(45)
        val subject = obj.stringOrNull("subject", "subjectName", "su", "subjectShort")
            ?: obj.objectOrNull("subject")?.stringOrNull("name", "shortName", "short")
            ?: obj.longOrNull("subjectId")?.let { subjectNames[it] }
            ?: ""
        val name = obj.stringOrNull("name", "title").orEmpty().trim()
            .ifBlank { obj.stringOrNull("examType").orEmpty().trim() }
        return UntisExam(
            id = obj.longOrNull("id", "examId") ?: stableExamId(date, start, subject),
            date = date,
            start = start,
            end = end,
            subject = subject.trim(),
            name = name,
            text = obj.stringOrNull("text", "description", "info").orEmpty().trim(),
        )
    }

    private fun stableExamId(date: LocalDate, start: LocalTime, subject: String): Long =
        (date.toEpochDay() * 31L + start.toSecondOfDay()) * 31L + subject.hashCode()
}
