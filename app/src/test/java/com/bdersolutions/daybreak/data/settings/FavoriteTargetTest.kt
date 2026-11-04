package com.bdersolutions.daybreak.data.settings

import com.bdersolutions.daybreak.untis.ElementType
import com.bdersolutions.daybreak.untis.UntisElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteTargetTest {
    private val klass = UntisElement(1, ElementType.CLASS, "3AHMBA")
    private val teacher = UntisElement(1, ElementType.TEACHER, "KOGL", "Kogler Hans")
    private val room = UntisElement(7, ElementType.ROOM, "OG-1001")

    @Test
    fun sameIdInDifferentKindsAreDifferentFavourites() {
        assertEquals(false, klass.favoriteKey == teacher.favoriteKey)
    }

    @Test
    fun toggledAddsThenRemoves() {
        val added = emptyList<FavoriteTarget>().withToggled(klass)
        assertEquals(listOf(klass.favoriteKey), added.map { it.key })

        val removed = added.withToggled(klass)
        assertEquals(emptyList<FavoriteTarget>(), removed)
    }

    @Test
    fun toggledKeepsTheOthersAndTheOrder() {
        val list = emptyList<FavoriteTarget>().withToggled(klass).withToggled(teacher).withToggled(room)

        assertEquals(listOf(klass, teacher, room).map { it.favoriteKey }, list.map { it.key })
        assertEquals(
            listOf(klass, room).map { it.favoriteKey },
            list.withToggled(teacher).map { it.key },
        )
    }

    @Test
    fun roundTripsToTheElement() {
        assertEquals(teacher, FavoriteTarget.from(teacher).toElement())
        assertEquals("Kogler Hans", FavoriteTarget.from(teacher).displayName)
        assertEquals("OG-1001", FavoriteTarget.from(room).displayName)
    }

    @Test
    fun unknownKindHasNoElement() {
        assertNull(FavoriteTarget(type = "GALAXY", id = 1, name = "X").toElement())
    }

    @Test
    fun reorderedFollowsKeysAndKeepsLeftOutOnes() {
        val list = emptyList<FavoriteTarget>().withToggled(klass).withToggled(teacher).withToggled(room)

        val reordered = list.reorderedByKeys(listOf(room.favoriteKey, "gone", klass.favoriteKey, room.favoriteKey))

        assertEquals(
            listOf(room, klass, teacher).map { it.favoriteKey },
            reordered.map { it.key },
        )
        assertEquals(list, list.reorderedByKeys(emptyList()))
    }
}
