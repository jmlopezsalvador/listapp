package com.listapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Lo que depende del sistema se declara aquí (`expect`) y se implementa en androidMain / iosMain.
 * Todo lo demás va en común.
 */

/**
 * Oculta (modo inmersivo) o muestra las barras del sistema. `darkIcons`: iconos oscuros (para fondos claros).
 * En iOS las gestiona ContentView.swift.
 */
@Composable
expect fun SystemBarsEffect(hidden: Boolean, darkIcons: Boolean)

/** Botón «atrás» del sistema (Android). En iOS no hace nada. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Devuelve una función que abre la galería; al elegir una imagen llama a `onPicked` con sus bytes. */
@Composable
expect fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit

/** Decodifica PNG/JPEG a un bitmap de Compose (null si falla). */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?

/** Crea un bitmap a partir de píxeles ARGB (0xAARRGGBB, sin premultiplicar) de `w` × `h`. */
expect fun imageFromPixels(w: Int, h: Int, argb: IntArray): ImageBitmap

/** Almacenamiento clave-valor local. Android: SharedPreferences. iOS: NSUserDefaults. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/** Ficheros internos de la app (las capturas). */
interface BlobStore {
    fun exists(name: String): Boolean
    fun read(name: String): ByteArray?
    fun write(name: String, bytes: ByteArray)
    fun delete(name: String)
}
