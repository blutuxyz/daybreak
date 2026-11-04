package com.bdersolutions.daybreak.untis

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Tolerant parsers for master data and absences. The absence layout is inferred from the
 * public Untis client libraries, so every field is read through aliases and unknown
 * shapes are skipped instead of failing the whole list.
 */
internal object SchoolDataParser {
    fun directory(root: JsonElement): UntisDirectory {
        val master = root.asJsonObjectOrNull()?.objectOrNull("masterData") ?: JsonObject()
        return UntisDirectory(
            classes = elements(master, ElementType.CLASS, "klassen", "classes"),
            teachers = elements(master, ElementType.TEACHER, "teachers"),
            rooms = elements(master, ElementType.ROOM, "rooms"),
            absenceReasons = absenceReasons(master),
        )
    }

    fun absences(result: JsonElement): List<UntisAbsence> {
        val array = result.asJsonObjectOrNull()?.arrayOrNull("absences")
            ?: result.asJsonArrayOrNull()
            ?: JsonArray()
        return array
            .mapNotNull { raw -> raw.asJsonObjectOrNull()?.let(::absence) }
            .sortedByDescending { it.start }
    }

    private fun elements(master: JsonObject, type: ElementType, vararg keys: String): List<UntisElement> =
        (master.arrayOrNull(*keys) ?: JsonArray())
            .mapNotNull { raw ->
                val obj = raw.asJsonObjectOrNull() ?: return@mapNotNull null
                val id = obj.longOrNull("id") ?: return@mapNotNull null
                val name = obj.stringOrNull("name", "shortName", "displayName").orEmpty()
                val longName = obj.stringOrNull("longName", "longname").orEmpty()
                if (name.isBlank() && longName.isBlank()) {
                    null
                } else {
                    UntisElement(id = id, type = type, name = name.ifBlank { longName }, longName = longName)
                }
            }
            .sortedBy { it.displayName.lowercase() }

    private fun absenceReasons(master: JsonObject): Map<Long, String> {
        val array = master.arrayOrNull("absenceReasons") ?: return emptyMap()
        return buildMap {
            for (raw in array) {
                val obj = raw.asJsonObjectOrNull() ?: continue
                val id = obj.longOrNull("id") ?: continue
                val name = obj.stringOrNull("longName", "name").orEmpty()
                if (name.isNotBlank()) put(id, name)
            }
        }
    }

    private fun absence(obj: JsonObject): UntisAbsence? {
        val startPair = TimetableParser.parseCombinedDateTime(obj.takeFirstElement("startDateTime", "start"))
        val startDate = startPair?.first
            ?: WireDate.parseDate(obj.takeFirstElement("startDate", "date"))
            ?: return null
        val startTime = startPair?.second
            ?: WireDate.parseTime(obj.takeFirstElement("startTime"))
            ?: LocalTime.MIDNIGHT
        val start = LocalDateTime.of(startDate, startTime)

        val endPair = TimetableParser.parseCombinedDateTime(obj.takeFirstElement("endDateTime", "end"))
        val endDate = endPair?.first ?: WireDate.parseDate(obj.takeFirstElement("endDate")) ?: startDate
        val endTime = endPair?.second
            ?: WireDate.parseTime(obj.takeFirstElement("endTime"))
            ?: start.toLocalTime().plusMinutes(45)
        val endCandidate = LocalDateTime.of(endDate, endTime)
        val end = if (endCandidate.isAfter(start)) endCandidate else start.plusMinutes(45)

        val reason = obj.get("reason")
        val reasonObject = reason?.asJsonObjectOrNull()
        val reasonId = reasonObject?.longOrNull("id")
            ?: (reason as? JsonPrimitive)?.takeIf { it.isNumber }?.asLong
            ?: obj.longOrNull("absenceReasonId", "reasonId")
        val reasonName = reasonObject?.stringOrNull("longName", "name").orEmpty()

        val excused = obj.booleanOrNull("excused", "isExcused")
            ?: obj.objectOrNull("excuse")?.let { true }
            ?: false

        return UntisAbsence(
            id = obj.longOrNull("id") ?: (start.hashCode().toLong() * 31 + end.hashCode()),
            start = start,
            end = end,
            excused = excused,
            reasonId = reasonId,
            reasonName = reasonName,
            text = obj.stringOrNull("text", "reasonText").orEmpty(),
        )
    }
}
