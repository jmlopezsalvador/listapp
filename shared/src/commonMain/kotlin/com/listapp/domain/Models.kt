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

/** Cómo se construye el escritorio: captura completa, o capas (fondo fijo + iconos que se deslizan). */
@Serializable
enum class ScreenMode(val label: String) {
    /** Cada página es una captura completa que se desliza entera. */
    FULL("Pantalla completa"),

    /** Fondo fijo + iconos que se deslizan; la capa de iconos se obtiene con dos capturas (fondo negro y fondo blanco). */
    LAYERS_BW("Iconos deslizantes"),
}

/** Qué es la caja de la pantalla 3 que abre la app de listas. */
@Serializable
enum class IconBoxMode(val label: String) {
    /** Solo un botón invisible sobre el icono de la captura. */
    INVISIBLE("Invisible"),

    /** Tapa el icono de la captura con el fondo y dibuja encima uno de los iconos de la app. */
    ICON("Icono"),
}

/** Aspecto de la app de listas (todo opcional: «null» = el color automático del tema). */
@Serializable
enum class ThemeMode(val label: String) { SYSTEM("Del sistema"), LIGHT("Claro"), DARK("Oscuro") }

@Serializable
enum class RowStyle(val label: String) { CARDS("Tarjetas"), DIVIDERS("Con separadores"), PLAIN("Sin separadores") }

@Serializable
enum class CornerStyle(val label: String, val dp: Int) { SQUARE("Rectas", 0), SOFT("Suaves", 10), ROUND("Redondas", 18) }

@Serializable
enum class NumberStyle(val label: String) { CIRCLE("En círculo"), PLAIN("Solo el número") }

@Serializable
enum class FontSize(val label: String, val scale: Float) { SMALL("Pequeña", 0.9f), NORMAL("Normal", 1f), LARGE("Grande", 1.15f) }

@Serializable
enum class FontStyle(val label: String) { SANS("Normal"), SERIF("Con serifa"), MONO("Monoespaciada") }

@Serializable
enum class RowDensity(val label: String, val dp: Int) { COMPACT("Compacta", 46), NORMAL("Normal", 54), ROOMY("Amplia", 66) }

@Serializable
data class ListStyle(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Colores 0xAARRGGBB; null = automático. */
    val bg: Long? = null,
    val text: Long? = null,
    val accent: Long? = null,
    val card: Long? = null,
    val rows: RowStyle = RowStyle.CARDS,
    val corners: CornerStyle = CornerStyle.ROUND,
    val numbers: NumberStyle = NumberStyle.CIRCLE,
    val fontSize: FontSize = FontSize.NORMAL,
    val font: FontStyle = FontStyle.SANS,
    val density: RowDensity = RowDensity.NORMAL,
)

/** Configuración secreta: pares, máscaras y cajas de las pantallas. */
@Serializable
data class MagicConfig(
    val pairs: List<ForcePair> = emptyList(),
    val masks: List<Mask> = emptyList(),
    /** Páginas 1 y 2: 10 cajas cada una; posición i = dígito (i+1)%10 (la última es el 0). */
    val digitBoxes: List<List<NRect?>> = List(2) { List(10) { null } },
    val iconBox: NRect? = null,
    val iconMode: IconBoxMode = IconBoxMode.INVISIBLE,
    /** Icono que se dibuja en la caja cuando el modo es ICON (id de `FakeIcons`). */
    val iconId: String = "apple_notes",
    /** Zoom del icono (1 = tamaño normal, ≈ 17 % del ancho de pantalla). No depende del tamaño de la caja. */
    val iconScale: Float = 1f,
    val outsideMode: OutsideMode = OutsideMode.KEEP,
    val mode: ScreenMode = ScreenMode.FULL,
    /** Modo capas: ajuste del fondo fijo (zoom sobre «rellenar pantalla» y desplazamiento en fracciones de pantalla). */
    val wallpaperZoom: Float = 1f,
    val wallpaperX: Float = 0f,
    val wallpaperY: Float = 0f,
    /** Modo capas: zonas que no se deslizan (barra superior, dock…): se dibujan con la capa de la pantalla 1. */
    val fixedZones: List<NRect> = emptyList(),
    /** Botones ocultos: caja de cada uno (fracciones de pantalla) y tiempo que hay que mantener el dedo quieto. */
    val resetBox: NRect = NRect(0f, 0f, 0.2f, 0.07f),
    val configBox: NRect = NRect(0.8f, 0f, 0.2f, 0.07f),
    val resetHoldMs: Int = 1500,
    val configHoldMs: Int = 1500,
    val listStyle: ListStyle = ListStyle(),
)

// coerceInputValues: un valor desconocido (p. ej. un modo que ya no existe) vuelve al valor por defecto en vez de fallar.
val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

fun digitOfIndex(i: Int) = (i + 1) % 10
fun indexOfDigit(d: Int) = (d + 9) % 10
