package com.listapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier

/** Raíz Compose común (Android e iOS). */
@Composable
fun ListAppRoot(kv: KeyValueStore, blobs: BlobStore) {
    val scope = rememberCoroutineScope()
    val state = remember { AppState(kv, blobs, scope) }
    val dark = isSystemInDarkTheme()
    val screen = state.screen
    val listScreen = screen is Screen.Lists || screen is Screen.Detail
    val look = rememberListLook(state.config.listStyle)

    val content: @Composable () -> Unit = {
        // En el escritorio simulado y en el editor de cajas se ocultan las barras del sistema para que
        // la pantalla coincida con las capturas; en el resto se ven (como una app normal).
        SystemBarsEffect(
            hidden = screen is Screen.Home || screen is Screen.Editor,
            // Iconos de la barra de estado oscuros si el fondo es claro (en las listas depende del aspecto elegido).
            darkIcons = if (listScreen) look.lightBackground else !dark,
        )
        when (screen) {
            Screen.Home -> HomeScreen(state)
            Screen.Lists -> ListsScreen(state)
            is Screen.Detail -> ListDetailScreen(state, screen.listId)
            Screen.Config -> ConfigScreen(state)
            Screen.ListStyle -> ListStyleScreen(state)
            is Screen.Editor -> EditorScreen(state, screen.target)
        }
        if (state.showGuide) GuideDialog { state.showGuide = false }
    }

    if (listScreen) {
        // La app de listas «falsa» usa el aspecto personalizado por el usuario.
        ListLookTheme(look, content)
    } else {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            // Surface fija el color del texto (contentColor) acorde al fondo. Sin ella el texto sale negro por defecto:
            // en iOS (modo oscuro forzado) quedaba negro sobre negro.
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                content()
            }
        }
    }
}
