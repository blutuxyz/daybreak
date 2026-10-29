package com.bdersolutions.daybreak.untis

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

internal fun JsonElement?.asJsonObjectOrNull(): JsonObject? =
    (this as? JsonObject)?.takeUnless { it.isJsonNull }

internal fun JsonElement?.asJsonArrayOrNull(): JsonArray? =
    (this as? JsonArray)?.takeUnless { it.isJsonNull }

internal fun JsonElement?.asPlainStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive.isJsonNull) return null
    return primitive.asString.takeIf { it.isNotBlank() }
}

internal fun JsonObject.stringOrNull(vararg keys: String): String? {
    for (key in keys) {
        val value = get(key)?.asPlainStringOrNull()
        if (value != null) return value
    }
    return null
}

internal fun JsonObject.longOrNull(vararg keys: String): Long? {
    for (key in keys) {
        val primitive = get(key) as? JsonPrimitive ?: continue
        if (primitive.isJsonNull) continue
        primitive.asString.toLongOrNull()?.let { return it }
    }
    return null
}

internal fun JsonObject.intOrNull(vararg keys: String): Int? = longOrNull(*keys)?.toInt()

internal fun JsonObject.booleanOrNull(vararg keys: String): Boolean? {
    for (key in keys) {
        val primitive = get(key) as? JsonPrimitive ?: continue
        if (primitive.isJsonNull) continue
        if (primitive.isBoolean) return primitive.asBoolean
        when (primitive.asString.lowercase()) {
            "true", "1", "yes" -> return true
            "false", "0", "no" -> return false
        }
    }
    return null
}

internal fun JsonObject.objectOrNull(vararg keys: String): JsonObject? {
    for (key in keys) {
        (get(key) as? JsonObject)?.let { if (!it.isJsonNull) return it }
    }
    return null
}

internal fun JsonObject.arrayOrNull(vararg keys: String): JsonArray? {
    for (key in keys) {
        (get(key) as? JsonArray)?.let { if (!it.isJsonNull) return it }
    }
    return null
}

internal fun JsonObject.takeFirstElement(vararg keys: String): JsonElement? {
    for (key in keys) {
        val value = get(key)
        if (value != null && value !is JsonNull) return value
    }
    return null
}
