package com.listapp.domain

/** Lógica pura del truco: insertar las palabras en el número elegido y deshacerlo. */
object Force {
    /** Decenas y unidades → número. Devuelve null si falta algún dígito o si es 0. */
    fun numberOf(tens: Int?, units: Int?): Int? {
        if (tens == null || units == null) return null
        val n = tens * 10 + units
        return if (n == 0) null else n
    }

    /**
     * Coloca cada palabra en la posición `number` (1-based) de su lista:
     * - Si la lista no existe o es más corta que `number`, no se toca.
     * - Si la palabra ya está en la lista (literal, sin distinguir mayúsculas), se mueve a esa posición y el resto se reordena.
     * - Si no está, sustituye al elemento de esa posición.
     * Devuelve listas nuevas y el registro para deshacer.
     */
    fun apply(lists: List<MagicList>, pairs: List<ForcePair>, number: Int): Pair<List<MagicList>, List<UndoEntry>> {
        if (number <= 0) return lists to emptyList()
        val result = lists.toMutableList()
        val undo = ArrayList<UndoEntry>()
        val index = number - 1
        for (pair in pairs) {
            val word = pair.word.trim()
            if (word.isEmpty()) continue
            val li = result.indexOfFirst { it.name.trim().equals(pair.listName.trim(), ignoreCase = true) }
            if (li < 0) continue
            val list = result[li]
            if (index >= list.items.size) continue
            val items = list.items.toMutableList()
            val existing = items.indexOfFirst { it.trim().equals(word, ignoreCase = true) }
            if (existing == index) continue
            if (existing >= 0) {
                val moved = items.removeAt(existing)
                items.add(index, moved)
            } else {
                items[index] = word
            }
            undo.add(UndoEntry(list.id, list.items))
            result[li] = list.copy(items = items)
        }
        return result to undo
    }

    /** Restaura las listas a como estaban antes de `apply`. */
    fun undo(lists: List<MagicList>, undo: List<UndoEntry>): List<MagicList> {
        val result = lists.toMutableList()
        for (e in undo.asReversed()) {
            val li = result.indexOfFirst { it.id == e.listId }
            if (li >= 0) result[li] = result[li].copy(items = e.previousItems)
        }
        return result
    }
}
