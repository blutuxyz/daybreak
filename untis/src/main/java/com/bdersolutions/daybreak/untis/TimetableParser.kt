package com.bdersolutions.daybreak.untis

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Tolerant parser for the 2017 mobile API timetable. WebUntis installations differ
 * in small ways, so every field is looked up through aliases and malformed periods
 * are skipped instead of breaking the whole response.
 */
internal object TimetableParser {
    fun parse(result: JsonElement): UntisTimetable {
        val root = result.asJsonObjectOrNull()
            ?: throw UntisParseException("The timetable response is not an object.")
        val master = root.objectOrNull("masterData") ?: JsonObject()
        val lookup = MasterLookup.from(master)
        val periods = root.objectOrNull("timetable")?.arrayOrNull("periods")
            ?: root.arrayOrNull("timetable", "periods")
            ?: JsonArray()

        val lessons = buildList {
            for (element in periods) {
                val period = element.asJsonObjectOrNull() ?: continue
                parsePeriod(period, lookup)?.let(::add)
            }
        }.sortedWith(compareBy<UntisLesson> { it.date }.thenBy { it.start }.thenBy { it.end })

        return UntisTimetable(
            lessons = lessons,
            masterDataTimestamp = master.longOrNull("timeStamp", "timestamp") ?: 0L,
        )
    }

    private fun parsePeriod(period: JsonObject, lookup: MasterLookup): UntisLesson? {
        val start = parseCombinedDateTime(period.takeFirstElement("startDateTime", "start"))
        val end = parseCombinedDateTime(period.takeFirstElement("endDateTime", "end"))

        val date = start?.first
            ?: WireDate.parseDate(period.takeFirstElement("date", "startDate"))
            ?: return null
        val startTime = start?.second
            ?: WireDate.parseTime(period.takeFirstElement("startTime"))
            ?: return null
        val endTime = end?.second
            ?: WireDate.parseTime(period.takeFirstElement("endTime"))
            ?: startTime.plusMinutes(45)
        val safeEnd = if (endTime <= startTime) startTime.plusMinutes(45) else endTime

        val subjectNames = mutableListOf<String>()
        val subjectLongNames = mutableListOf<String>()
        val teacherNames = mutableListOf<String>()
        val roomNames = mutableListOf<String>()
        val classNames = mutableListOf<String>()
        var originalRoom = ""
        var originalTeacher = ""

        val elements = period.arrayOrNull("elements")
        if (elements != null) {
            for (raw in elements) {
                val element = raw.asJsonObjectOrNull() ?: continue
                val typeElement = element.get("type")
                val type = when {
                    typeElement is JsonPrimitive && typeElement.isNumber ->
                        WireDate.typeFromNumber(typeElement.asInt)
                    else -> element.stringOrNull("type")
                        ?.uppercase(Locale.ROOT)
                        ?.let { WireDate.typeFromNumber(it.toIntOrNull()) ?: it }
                }
                val id = element.longOrNull("id") ?: continue
                val orgId = element.longOrNull("orgId", "orgID") ?: 0L
                when (type) {
                    "SUBJECT" -> {
                        val named = lookup.subject(id).ifBlank { element.nameOrEmpty() }
                        if (named.isNotBlank()) subjectNames += named
                        lookup.subjectLong(id).ifBlank { element.stringOrNull("longName", "longname").orEmpty() }
                            .takeIf { it.isNotBlank() }?.let(subjectLongNames::add)
                    }

                    "TEACHER" -> {
                        val named = lookup.teacher(id).ifBlank { element.nameOrEmpty() }
                        if (named.isNotBlank()) teacherNames += named
                        if (orgId != 0L && orgId != id) {
                            originalTeacher = lookup.teacher(orgId)
                        }
                    }

                    "ROOM" -> {
                        val named = lookup.room(id).ifBlank { element.nameOrEmpty() }
                        if (named.isNotBlank()) roomNames += named
                        if (orgId != 0L && orgId != id) {
                            originalRoom = lookup.room(orgId)
                        }
                    }

                    "CLASS" -> {
                        val named = lookup.klass(id).ifBlank { element.nameOrEmpty() }
                        if (named.isNotBlank()) classNames += named
                    }
                }
            }
        }

        // Some servers only expose the short arrays (su/te/ro/kl).
        appendShortNames(period, "su", lookup.subjects, subjectNames)
        appendShortNames(period, "te", lookup.teachers, teacherNames)
        appendShortNames(period, "ro", lookup.rooms, roomNames)
        appendShortNames(period, "kl", lookup.klassen, classNames)

        val text = period.objectOrNull("text") ?: JsonObject()
        val lessonText = period.stringOrNull("lstext", "lessonText")
            ?: text.stringOrNull("lesson", "text")
            ?: ""
        val substitution = period.stringOrNull("substText", "substitutionText")
            ?: text.stringOrNull("substitution")
            ?: ""
        val info = period.stringOrNull("info")
            ?: text.stringOrNull("info")
            ?: ""

        val cancelled = period.booleanOrNull("isCancelled", "cancelled") == true ||
            containsFlag(period, "CANCELLED") ||
            period.stringOrNull("code", "status").orEmpty().contains("cancel", ignoreCase = true)
        val irregular = period.booleanOrNull("isIrregular", "irregular") == true ||
            containsFlag(period, "IRREGULAR") ||
            period.stringOrNull("code", "status").orEmpty().contains("irregular", ignoreCase = true)

        val status = when {
            cancelled -> LessonStatus.CANCELLED
            irregular -> LessonStatus.IRREGULAR
            else -> LessonStatus.REGULAR
        }

        if (originalRoom.isBlank()) {
            originalRoom = period.stringOrNull("originalRoom", "orgRoom").orEmpty()
        }
        if (originalTeacher.isBlank()) {
            originalTeacher = period.stringOrNull("originalTeacher", "orgTeacher").orEmpty()
        }

        val subject = subjectNames.distinct().joinToString(", ")
            .ifBlank { lessonText }
        val lessonId = period.longOrNull("id", "lessonId", "periodId")
            ?: stableId(date, startTime, safeEnd, subject)
        val group = period.stringOrNull("sg", "studentGroup", "group").orEmpty()

        return UntisLesson(
            id = lessonId,
            date = date,
            start = startTime,
            end = safeEnd,
            subject = subject,
            subjectLong = subjectLongNames.distinct().joinToString(", "),
            teacher = teacherNames.distinct().joinToString(", "),
            room = roomNames.distinct().joinToString(", "),
            className = classNames.distinct().joinToString(", "),
            status = status,
            substitutionText = substitution,
            lessonText = lessonText,
            info = info,
            group = group,
            originalRoom = originalRoom,
            originalTeacher = originalTeacher,
        )
    }

    private fun appendShortNames(
        period: JsonObject,
        key: String,
        lookup: Map<Long, NamedElement>,
        target: MutableList<String>,
    ) {
        val array = period.arrayOrNull(key) ?: return
        for (raw in array) {
            val obj = raw.asJsonObjectOrNull()
            if (obj != null) {
                val id = obj.longOrNull("id")
                val name = id?.let { lookup[it]?.name }.orEmpty()
                    .ifBlank { obj.stringOrNull("name", "longName", "longname").orEmpty() }
                if (name.isNotBlank()) target += name
            } else {
                raw.asPlainStringOrNull()?.takeIf { it.isNotBlank() }?.let(target::add)
            }
        }
    }

    private fun containsFlag(period: JsonObject, flag: String): Boolean {
        val element = period.takeFirstElement("is", "flags", "state") ?: return false
        val values = when {
            element is JsonArray -> element.mapNotNull { it.asPlainStringOrNull() }
            else -> listOfNotNull(element.asPlainStringOrNull())
        }
        return values.any { it.equals(flag, ignoreCase = true) || it.contains(flag, ignoreCase = true) }
    }

    private fun stableId(date: LocalDate, start: LocalTime, end: LocalTime, subject: String): Long {
        var result = date.hashCode()
        result = 31 * result + start.hashCode()
        result = 31 * result + end.hashCode()
        result = 31 * result + subject.hashCode()
        return result.toLong()
    }

    private fun parseCombinedDateTime(element: JsonElement?): Pair<LocalDate, LocalTime>? {
        val value = element?.asPlainStringOrNull() ?: return null
        val match = DATE_TIME.find(value.trim()) ?: return null
        return try {
            val (year, month, day, hour, minute) = match.destructured
            LocalDate.of(year.toInt(), month.toInt(), day.toInt()) to
                LocalTime.of(hour.toInt(), minute.toInt())
        } catch (_: Exception) {
            null
        }
    }

    private data class NamedElement(val name: String, val longName: String)

    private class MasterLookup(
        val subjects: Map<Long, NamedElement>,
        val teachers: Map<Long, NamedElement>,
        val rooms: Map<Long, NamedElement>,
        val klassen: Map<Long, NamedElement>,
    ) {
        fun subject(id: Long) = subjects[id]?.name.orEmpty()
        fun subjectLong(id: Long) = subjects[id]?.longName.orEmpty()
        fun teacher(id: Long) = teachers[id]?.name.orEmpty()
        fun room(id: Long) = rooms[id]?.name.orEmpty()
        fun klass(id: Long) = klassen[id]?.name.orEmpty()

        companion object {
            fun from(master: JsonObject): MasterLookup = MasterLookup(
                subjects = parse(master.arrayOrNull("subjects")),
                teachers = parse(master.arrayOrNull("teachers")),
                rooms = parse(master.arrayOrNull("rooms")),
                klassen = parse(master.arrayOrNull("klassen", "classes")),
            )

            private fun parse(array: JsonArray?): Map<Long, NamedElement> {
                if (array == null) return emptyMap()
                return buildMap {
                    for (raw in array) {
                        val obj = raw.asJsonObjectOrNull() ?: continue
                        val id = obj.longOrNull("id") ?: continue
                        put(
                            id,
                            NamedElement(
                                name = obj.stringOrNull("name", "shortName", "displayName").orEmpty(),
                                longName = obj.stringOrNull("longName", "longname").orEmpty(),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun JsonObject.nameOrEmpty(): String =
        stringOrNull("name", "longName", "longname", "displayName", "displayname").orEmpty()

    private val DATE_TIME = Regex(
        """(\d{4})-?(\d{2})-?(\d{2})[T ](\d{1,2}):?(\d{2})(?::?\d{2})?""",
    )
}

internal object WireDate {
    fun parseDate(element: JsonElement?): LocalDate? {
        val value = element?.asPlainStringOrNull() ?: return null
        val digits = value.take(10).filter(Char::isDigit)
        if (digits.length < 8) return null
        return try {
            LocalDate.of(
                digits.substring(0, 4).toInt(),
                digits.substring(4, 6).toInt(),
                digits.substring(6, 8).toInt(),
            )
        } catch (_: Exception) {
            try {
                LocalDate.parse(value)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    fun parseTime(element: JsonElement?): LocalTime? {
        val value = element?.asPlainStringOrNull() ?: return null
        val trimmed = value.trim().removePrefix("T")
        val digits = trimmed.filter(Char::isDigit)
        if (digits.isEmpty()) return null
        val four = digits.padStart(4, '0').take(4)
        return try {
            LocalTime.of(four.substring(0, 2).toInt(), four.substring(2, 4).toInt())
        } catch (_: Exception) {
            null
        }
    }

    fun typeFromNumber(value: Int?): String? = when (value) {
        1 -> "CLASS"
        2 -> "TEACHER"
        3 -> "SUBJECT"
        4 -> "ROOM"
        5 -> "STUDENT"
        else -> null
    }
}
