package com.listapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Lo que depende del sistema se declara aquí (`expect`) y se implementa en androidMain / iosMain.
 * Todo lo demás va en común.
 */

/** Oculta (modo inmersivo) o muestra las barras del sistema. En iOS las gestiona ContentView.swift. */
@Composable
expect fun SystemBarsEffect(hidden: Boolean)

/** Botón «atrás» del sistema (Android). En iOS no hace nada. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Devuelve una función que abre la galería; al elegir una imagen llama a `onPicked` con sus bytes. */
@Composable
expect fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit

/** Decodifica PNG/JPEG a un bitmap de Compose (null si falla). */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?

/** Almacenamiento clave-valor local. Android: SharedPreferences. iOS: NSUserDefaults. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/** Ficheros internos de la app (las capturas). */
interface BlobStore {
    fun read(name: String): ByteArray?
    fun write(name: String, bytes: ByteArray)
    fun delete(name: String)
}
