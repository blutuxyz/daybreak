package com.bdersolutions.daybreak.untis

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Reads `getHomeWork2017`: a `homeWorks` list plus `lessonsById`, which names the subject of each
 * entry's lesson. The response is read tolerantly. An entry that cannot be read is skipped.
 */
internal object HomeworkParser {

    fun parse(result: JsonElement): List<UntisHomework> {
        val root = result.asJsonObjectOrNull() ?: return emptyList()
        val items = root.arrayOrNull("homeWorks", "homeworks", "homework") ?: return emptyList()
        val lessons = root.objectOrNull("lessonsById", "lessons")
        return items.mapNotNull { element ->
            element.asJsonObjectOrNull()?.let { parseEntry(it, lessons) }
        }
    }

    private fun parseEntry(obj: JsonObject, lessons: JsonObject?): UntisHomework? {
        val due = WireDate.parseDate(obj.takeFirstElement("dueDate", "due", "date")) ?: return null
        val text = obj.stringOrNull("text", "description", "name").orEmpty().trim()
        val remark = obj.stringOrNull("remark", "note").orEmpty().trim()
        if (text.isBlank() && remark.isBlank()) return null
        val lessonId = obj.longOrNull("lessonId", "lessonID")
        val lesson = lessonId?.let { lessons?.get(it.toString()).asJsonObjectOrNull() }
        val subject = obj.stringOrNull("subject", "subjectName")
            ?: lesson?.stringOrNull("subject", "subjectName", "su")
            ?: lesson?.objectOrNull("subject")?.stringOrNull("name", "shortName", "short")
            ?: ""
        val assigned = WireDate.parseDate(obj.takeFirstElement("date", "startDate"))
            ?.takeIf { it != due }
        return UntisHomework(
            id = obj.longOrNull("id", "homeWorkId") ?: (due.toEpochDay() * 31L + text.hashCode()),
            subject = subject.trim(),
            assigned = assigned,
            due = due,
            text = text,
            remark = remark,
            completed = obj.booleanOrNull("completed", "done", "isDone") ?: false,
        )
    }
}

/** Reads `getMessagesOfDay2017`: a `messages` list with a subject and a text. */
internal object MessageParser {

    fun parse(result: JsonElement): List<UntisMessage> {
        val root = result.asJsonObjectOrNull()
        val items = (root?.arrayOrNull("messages", "messagesOfDay", "items") ?: result.asJsonArrayOrNull())
            ?: return emptyList()
        return items.mapNotNull { element ->
            val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val subject = obj.stringOrNull("subject", "title", "header").orEmpty().trim()
            val text = obj.stringOrNull("text", "body", "message", "content").orEmpty().trim()
            if (subject.isBlank() && text.isBlank()) return@mapNotNull null
            UntisMessage(
                id = obj.longOrNull("id", "messageId") ?: (subject + text).hashCode().toLong(),
                subject = subject,
                text = text,
            )
        }
    }
}
