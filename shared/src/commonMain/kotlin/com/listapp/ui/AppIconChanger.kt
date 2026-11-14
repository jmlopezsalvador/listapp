package com.listapp.ui

import androidx.compose.runtime.Composable

/** Un icono o un nombre elegible. El `id` coincide con el alias de Android (`.Icon_<icono>_<nombre>`) y con el appiconset de iOS. */
data class AppIconOption(val id: String, val label: String)

/**
 * Cambia, ya instalada la app, el icono y el nombre que enseña el móvil en el lanzador.
 * Solo se puede elegir entre los preestablecidos (son recursos compilados): Android cambia icono y nombre con alias de actividad;
 * iOS solo el icono (iconos alternativos) y siempre muestra su propio aviso. Las listas las genera `tools/generar_iconos.py`.
 */
interface AppIconChanger {
    val icons: List<AppIconOption>

    /** Vacía en iOS: la interfaz oculta entonces el selector de nombre. */
    val names: List<AppIconOption>

    /** true si aplicar el cambio reinicia la app (Android); en iOS no se reinicia y el sistema muestra su propio aviso. */
    val restartsApp: Boolean

    fun currentIcon(): String
    fun currentName(): String

    /** Aplica icono y nombre (en iOS el nombre se ignora). En Android reinicia la app en el nuevo alias. Devuelve false si el sistema no lo permite. */
    fun select(iconId: String, nameId: String): Boolean
}

@Composable
expect fun rememberAppIconChanger(): AppIconChanger
