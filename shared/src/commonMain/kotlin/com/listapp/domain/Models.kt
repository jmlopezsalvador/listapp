package com.listapp.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Rectángulo en fracciones (0..1) del tamaño de la pantalla: vale para cualquier resolución. */
@Serializable
data class NRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    fun contains(px: Float, py: Float) = px >= x && px <= x + w && py >= y && py <= y + h

    /** Lo mantiene dentro de la pantalla y con un tamaño mínimo usable. */
    fun clamped(): NRect {
        val nw = w.coerceIn(MIN, 1f)
        val nh = h.coerceIn(MIN, 1f)
        return NRect(x.coerceIn(0f, 1f - nw), y.coerceIn(0f, 1f - nh), nw, nh)
    }

    companion object {
        const val MIN = 0.03f
    }
}

/** Una lista real de la app. Los elementos se muestran numerados por su posición (1, 2, 3…). */
@Serializable
data class MagicList(val id: Long, val name: String, val items: List<String> = emptyList())

/** Par secreto: en la lista `listName` se escribirá `word` en la posición del número elegido. */
@Serializable
data class ForcePair(val id: Long, val listName: String, val word: String)

/** Cuadro que tapa algo de la captura (p. ej. la hora) y puede mostrar la hora real del sistema. */
@Serializable
data class Mask(
    val id: Long,
    val rect: NRect,
    /** true: se rellena con la «imagen limpia»; false: con `color`. */
    val useClean: Boolean = false,
    val color: Long = 0xFF000000,
    val showClock: Boolean = false,
    val clockColor: Long = 0xFFFFFFFF,
    /** Alto del texto respecto al alto de la caja. */
    val clockScale: Float = 0.6f,
    /** true: «09:41»; false: «9:41». */
    val clockPadHour: Boolean = false,
)

/** Lo que hay que deshacer en el reset: la lista tal y como estaba antes de insertar. */
@Serializable
data class UndoEntry(val listId: Long, val previousItems: List<String>)

/** Qué pasa si el dedo empieza a arrastrar fuera de las cajas de dígitos (páginas 1 y 2). */
@Serializable
enum class OutsideMode(val label: String) {
    /** El dígito no cambia y se puede cambiar de página. */
    KEEP("Ignorar"),
    /** No deja deslizar si no se ha tocado una caja válida. */
    BLOCK("Bloquear"),
    /** El dígito vale 0 y se cambia de página. */
    ZERO("Dígito 0"),
}

/** Configuración secreta: pares, máscaras y cajas de las pantallas. */
@Serializable
data class MagicConfig(
    val pairs: List<ForcePair> = emptyList(),
    val masks: List<Mask> = emptyList(),
    /** Páginas 1 y 2: 10 cajas cada una; posición i = dígito (i+1)%10 (la última es el 0). */
    val digitBoxes: List<List<NRect?>> = List(2) { List(10) { null } },
    val iconBox: NRect? = null,
    val outsideMode: OutsideMode = OutsideMode.KEEP,
)

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun digitOfIndex(i: Int) = (i + 1) % 10
fun indexOfDigit(d: Int) = (d + 9) % 10
