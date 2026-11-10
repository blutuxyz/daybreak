package com.bdersolutions.daybreak.untis

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Reads `getPeriodData2017`: `dataByTTId` maps each period id to its class register data. What students
 * may see there differs per school; the part used is the lesson topic ("Lehrstoff"), which most teachers
 * fill in and which is not in the timetable answer. It comes as `topic.text`, on some servers as plain
 * text. Read tolerantly: an unknown shape gives an empty map.
 */
internal object PeriodDataParser {
    fun topics(result: JsonElement): Map<Long, String> {
        val root = result.asJsonObjectOrNull() ?: return emptyMap()
        val byId = root.objectOrNull("dataByTTId", "dataByTtId", "dataById")
        val entries: List<Pair<Long?, JsonObject>> = if (byId != null) {
            byId.entrySet().mapNotNull { (key, value) ->
                value.asJsonObjectOrNull()?.let { data -> (data.longOrNull("ttId", "id") ?: key.toLongOrNull()) to data }
            }
        } else {
            (root.arrayOrNull("periods", "data") ?: return emptyMap()).mapNotNull { element ->
                element.asJsonObjectOrNull()?.let { data -> data.longOrNull("ttId", "id") to data }
            }
        }
        return entries.mapNotNull { (id, data) ->
            val topic = topicOf(data) ?: return@mapNotNull null
            id?.let { it to topic }
        }.toMap()
    }

    private fun topicOf(data: JsonObject): String? {
        val raw = data.takeFirstElement("topic", "lessonTopic", "teachingContent") ?: return null
        val text = when {
            raw.isJsonPrimitive -> raw.asPlainStringOrNull()
            raw.isJsonObject -> raw.asJsonObject.stringOrNull("text", "topic", "name")
            else -> null
        }
        return text?.trim()?.takeIf { it.isNotEmpty() }
    }
}
