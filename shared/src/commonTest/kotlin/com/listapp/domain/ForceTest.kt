package com.listapp.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForceTest {
    private fun list(id: Long, name: String, n: Int) = MagicList(id, name, List(n) { "x${it + 1}" })

    @Test fun numberFromDigits() {
        assertEquals(16, Force.numberOf(1, 6))
        assertEquals(5, Force.numberOf(0, 5))
        assertEquals(50, Force.numberOf(5, 0))
        assertNull(Force.numberOf(null, 3))
        assertNull(Force.numberOf(0, 0))
    }

    @Test fun digitMapping() {
        assertEquals(1, digitOfIndex(0))
        assertEquals(0, digitOfIndex(9))
        assertEquals(9, indexOfDigit(0))
        assertEquals(0, indexOfDigit(1))
    }

    @Test fun insertsWordAtNumber() {
        val lists = listOf(list(1, "Juegos", 50), list(2, "Música", 50), list(3, "Otra", 50))
        val pairs = listOf(ForcePair(1, "juegos", "Splendor"), ForcePair(2, " Música ", "Extremoduro"))
        val (res, undo) = Force.apply(lists, pairs, 16)
        assertEquals("Splendor", res[0].items[15])
        assertEquals("Extremoduro", res[1].items[15])
        assertEquals("x16", res[2].items[15])
        assertEquals(2, undo.size)
        // sustituye: el número de elementos no cambia
        assertEquals(50, res[0].items.size)
        assertEquals(50, res[1].items.size)
    }

    @Test fun undoRestoresOriginal() {
        val lists = listOf(list(1, "Juegos", 50))
        val (res, undo) = Force.apply(lists, listOf(ForcePair(1, "Juegos", "Splendor")), 16)
        assertEquals(lists, Force.undo(res, undo))
    }

    @Test fun shortListIsNotTouched() {
        val lists = listOf(list(1, "Corta", 3), list(2, "Larga", 20))
        val pairs = listOf(ForcePair(1, "Corta", "Francia"), ForcePair(2, "Larga", "Italia"))
        val (res, undo) = Force.apply(lists, pairs, 6)
        assertEquals(lists[0], res[0])
        assertEquals("Italia", res[1].items[5])
        assertEquals(1, undo.size)
    }

    @Test fun unknownListAndBlankWordAreIgnored() {
        val lists = listOf(list(1, "A", 10))
        val (res, undo) = Force.apply(lists, listOf(ForcePair(1, "Nada", "x"), ForcePair(2, "A", " ")), 3)
        assertEquals(lists, res)
        assertEquals(0, undo.size)
    }

    @Test fun existingWordIsMovedAndRestShifts() {
        val lists = listOf(MagicList(1, "L", listOf("a", "b", "c", "d", "e")))
        // «E» (otra capitalización) está en el puesto 5 → pasa al 2; el resto se reordena
        val (res, undo) = Force.apply(lists, listOf(ForcePair(1, "L", " E ")), 2)
        assertEquals(listOf("a", "e", "b", "c", "d"), res[0].items)
        assertEquals(lists, Force.undo(res, undo))
        // mover hacia atrás
        val (res2, _) = Force.apply(lists, listOf(ForcePair(1, "L", "a")), 4)
        assertEquals(listOf("b", "c", "d", "a", "e"), res2[0].items)
    }

    @Test fun existingWordAlreadyInPlaceChangesNothing() {
        val lists = listOf(MagicList(1, "L", listOf("a", "b", "c")))
        val (res, undo) = Force.apply(lists, listOf(ForcePair(1, "L", "B")), 2)
        assertEquals(lists, res)
        assertEquals(0, undo.size)
    }

    @Test fun configRoundTripsThroughJson() {
        val cfg = MagicConfig(
            pairs = listOf(ForcePair(1, "A", "b")),
            masks = listOf(Mask(1, NRect(0f, 0f, 0.2f, 0.05f), showClock = true)),
            iconBox = NRect(0.1f, 0.2f, 0.2f, 0.1f),
        )
        assertEquals(cfg, AppJson.decodeFromString(MagicConfig.serializer(), AppJson.encodeToString(MagicConfig.serializer(), cfg)))
    }
}
