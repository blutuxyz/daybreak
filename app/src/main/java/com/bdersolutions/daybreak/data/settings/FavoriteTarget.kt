package com.bdersolutions.daybreak.data.settings

import com.bdersolutions.daybreak.untis.ElementType
import com.bdersolutions.daybreak.untis.UntisElement

/**
 * A class, teacher or room the user pinned in the timetable picker. It keeps the names, so the
 * favourites show even before the school's master data has loaded. Only primitives, so Gson can
 * store it without adapters.
 */
data class FavoriteTarget(
    val type: String,
    val id: Long,
    val name: String,
    val longName: String = "",
) {
    /** Identifies the element across the app; also the list key. */
    val key: String get() = keyOf(type, id)

    val displayName: String get() = longName.ifBlank { name }

    /** The element this favourite stands for, or null when [type] is not a known kind. */
    fun toElement(): UntisElement? = ElementType.entries
        .firstOrNull { it.name == type }
        ?.let { UntisElement(id = id, type = it, name = name, longName = longName) }

    companion object {
        fun keyOf(type: String, id: Long): String = "$type-$id"

        fun from(element: UntisElement): FavoriteTarget = FavoriteTarget(
            type = element.type.name,
            id = element.id,
            name = element.name,
            longName = element.longName,
        )
    }
}

val UntisElement.favoriteKey: String get() = FavoriteTarget.keyOf(type.name, id)

/** Adds [element] as a favourite, or removes it when it already is one. */
fun List<FavoriteTarget>.withToggled(element: UntisElement): List<FavoriteTarget> =
    if (any { it.key == element.favoriteKey }) {
        filterNot { it.key == element.favoriteKey }
    } else {
        this + FavoriteTarget.from(element)
    }

/**
 * The favourites in the order of [keys]. Unknown keys are ignored, and favourites that [keys] leaves
 * out keep their relative order at the end, so a stale order never loses one.
 */
fun List<FavoriteTarget>.reorderedByKeys(keys: List<String>): List<FavoriteTarget> {
    val byKey = associateBy { it.key }
    val wanted = keys.distinct().mapNotNull(byKey::get)
    val taken = wanted.map { it.key }.toSet()
    return wanted + filter { it.key !in taken }
}
