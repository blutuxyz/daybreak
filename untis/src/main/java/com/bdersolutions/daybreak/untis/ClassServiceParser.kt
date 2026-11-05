package com.bdersolutions.daybreak.untis

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.LocalDate

/** One class service (Klassendienst), e.g. "Klassenordner" held by one student for a period. */
data class UntisClassService(
    val id: Long,
    val service: String,
    val student: String,
    val className: String,
    val start: LocalDate?,
    val end: LocalDate?,
    val text: String = "",
)

/**
 * Reads the answer of `/WebUntis/api/classreg/classservices`.
 *
 * This response has not been checked against a live server, so it is read tolerantly: the list may
 * sit under `data.classServices` or directly in `data`, and the service, student and class may be
 * nested objects, plain strings, or ids that a lookup list next to the entries explains. An entry
 * that cannot be read is skipped. The raw response is in the debug log, so the shape can be
 * checked there.
 */
internal object ClassServiceParser {

    fun parse(result: JsonElement): List<UntisClassService> {
        val root = result.asJsonObjectOrNull()
        val data = root?.objectOrNull("data") ?: root
        val items: JsonArray = result.asJsonArrayOrNull()
            ?: data?.arrayOrNull("classServices", "classservices", "services", "items")
            ?: root?.arrayOrNull("classServices", "classservices", "services", "items", "data")
            ?: return emptyList()
        val services = lookup(data, "classServiceTypes", "serviceTypes", "services", "classServices")
        val students = lookup(data, "students")
        val classes = lookup(data, "klassen", "classes")

        return items.mapNotNull { element ->
            val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val service = nameOf(obj, "service", "classService", "classServiceType", "type", "dienst")
                ?: obj.stringOrNull("serviceName", "name", "title")
                ?: obj.longOrNull("classServiceId", "serviceId", "typeId")?.let { services[it] }
                ?: return@mapNotNull null
            val student = studentNames(obj, students)
            UntisClassService(
                id = obj.longOrNull("id") ?: (service + student + obj.toString()).hashCode().toLong(),
                service = service,
                student = student,
                className = nameOf(obj, "klasse", "class", "schoolClass")
                    ?: obj.stringOrNull("className", "klasseName")
                    ?: obj.longOrNull("klasseId", "classId")?.let { classes[it] }
                    ?: "",
                start = WireDate.parseDate(obj.takeFirstElement("startDate", "start", "from", "von")),
                end = WireDate.parseDate(obj.takeFirstElement("endDate", "end", "to", "bis")),
                text = obj.stringOrNull("text", "remark", "note").orEmpty().trim(),
            )
        }
    }

    private fun studentNames(obj: JsonObject, lookup: Map<Long, String>): String {
        val list = obj.arrayOrNull("students", "persons")
        if (list != null) {
            return list.mapNotNull { entry ->
                entry.asPlainStringOrNull()
                    ?: entry.asJsonObjectOrNull()?.let { displayName(it) }
                    ?: entry.asPlainStringOrNull()?.toLongOrNull()?.let { lookup[it] }
            }.joinToString(", ")
        }
        return nameOf(obj, "student", "person", "schueler")
            ?: obj.stringOrNull("studentName", "personName")
            ?: obj.longOrNull("studentId", "personId")?.let { lookup[it] }
            ?: ""
    }

    /** The name of a nested object or plain string stored under one of [keys]. */
    private fun nameOf(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val value = obj.get(key) ?: continue
            value.asPlainStringOrNull()?.let { return it.trim() }
            value.asJsonObjectOrNull()?.let { nested -> displayName(nested)?.let { return it } }
        }
        return null
    }

    private fun displayName(obj: JsonObject): String? {
        obj.stringOrNull("displayName", "longName", "name", "shortName", "title")
            ?.let { primary ->
                val fore = obj.stringOrNull("foreName", "firstName")
                return if (fore != null && primary != fore && obj.stringOrNull("displayName") == null) {
                    "$fore $primary"
                } else {
                    primary
                }.trim()
            }
        return null
    }

    private fun lookup(data: JsonObject?, vararg keys: String): Map<Long, String> {
        val array = data?.arrayOrNull(*keys) ?: return emptyMap()
        return array.mapNotNull { entry ->
            val obj = entry.asJsonObjectOrNull() ?: return@mapNotNull null
            val id = obj.longOrNull("id") ?: return@mapNotNull null
            val name = displayName(obj) ?: return@mapNotNull null
            id to name
        }.toMap()
    }
}
