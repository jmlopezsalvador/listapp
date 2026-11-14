package com.listapp.ui

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Un icono que se puede poner en la caja del icono de la pantalla 3. */
data class FakeIcon(val id: String, val label: String)

/** Iconos incrustados en la app (ver `FakeIconsData`, generado por `tools/generar_iconos.py`). */
object FakeIcons {
    val all: List<FakeIcon> = FakeIconsData.ids.map { FakeIcon(it, FakeIconsData.labels[it] ?: it) }

    private val cache = HashMap<String, ImageBitmap>()

    /** El bitmap del icono (se decodifica la primera vez y se guarda). null si el id no existe. */
    @OptIn(ExperimentalEncodingApi::class)
    fun image(id: String): ImageBitmap? {
        cache[id]?.let { return it }
        val parts = FakeIconsData.parts[id] ?: return null
        val bmp = decodeImage(Base64.decode(parts.joinToString(""))) ?: return null
        cache[id] = bmp
        return bmp
    }
}
