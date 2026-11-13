package com.listapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Raíz Compose común (Android e iOS). */
@Composable
fun ListAppRoot(kv: KeyValueStore, blobs: BlobStore) {
    val state = remember { AppState(kv, blobs) }
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        val screen = state.screen
        // En el escritorio simulado y en el editor de cajas se ocultan las barras del sistema para que
        // la pantalla coincida con las capturas; en el resto se ven (como una app normal).
        SystemBarsEffect(hidden = screen is Screen.Home || screen is Screen.Editor)
        when (screen) {
            Screen.Home -> HomeScreen(state)
            Screen.Lists -> ListsScreen(state)
            is Screen.Detail -> ListDetailScreen(state, screen.listId)
            Screen.Config -> ConfigScreen(state)
            is Screen.Editor -> EditorScreen(state, screen.target)
        }
    }
}
