package com.listapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listapp.domain.CornerStyle
import com.listapp.domain.FontSize
import com.listapp.domain.FontStyle
import com.listapp.domain.ListStyle
import com.listapp.domain.NumberStyle
import com.listapp.domain.RowDensity
import com.listapp.domain.RowStyle
import com.listapp.domain.ThemeMode

private val BgSwatches = listOf(0xFFFFFFFF, 0xFFF5F5F5, 0xFFFFF8E1, 0xFFE3F2FD, 0xFFE8F5E9, 0xFFFCE4EC, 0xFF212121, 0xFF121212, 0xFF000000, 0xFF1A237E)
private val TextSwatches = listOf(0xFF000000, 0xFF212121, 0xFF424242, 0xFF3E2723, 0xFF0D47A1, 0xFFFFFFFF, 0xFFF5F5F5, 0xFFFFE082)
private val AccentSwatches = listOf(0xFF6750A4, 0xFF1E88E5, 0xFF43A047, 0xFFE53935, 0xFFFB8C00, 0xFFD81B60, 0xFF00897B, 0xFF000000, 0xFFFFFFFF)
private val CardSwatches = listOf(0xFFFFFFFF, 0xFFEEEEEE, 0xFFE0E0E0, 0xFFFFF3E0, 0xFFE8EAF6, 0xFF2C2C2E, 0xFF1E1E1E, 0xFF000000)

/** Personalizador del aspecto de la app de listas, con una vista previa fija que se actualiza al momento. */
@Composable
fun ListStyleScreen(state: AppState) {
    PlatformBackHandler(true) { state.screen = Screen.Config }
    val style = state.config.listStyle
    var previewKind by remember { mutableIntStateOf(0) }
    val look = rememberListLook(style)
    fun update(f: (ListStyle) -> ListStyle) = state.updateConfig { it.copy(listStyle = f(it.listStyle)) }

    ScreenFrame {
        TopBar("Aspecto de las listas", onBack = { state.screen = Screen.Config })

        // ---- Vista previa (fija arriba)
        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(260.dp)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape),
        ) {
            ListLookTheme(look) { if (previewKind == 0) ItemsPreview() else ListsPreview() }
            // Capa transparente: la vista previa no se toca (los toques no llegan a las filas).
            Box(Modifier.matchParentSizeCompat().pointerInput(Unit) { detectTapGestures { } })
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Ver elementos", previewKind == 0) { previewKind = 0 }
            Chip("Ver listas", previewKind == 1) { previewKind = 1 }
        }

        // ---- Opciones
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OptionRow("Tema", ThemeMode.entries, style.theme, { it.label }) { v -> update { it.copy(theme = v) } }

            Text("Colores", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            ColorChooser("Fondo", style.bg, BgSwatches) { c -> update { it.copy(bg = c) } }
            ColorChooser("Letra", style.text, TextSwatches) { c -> update { it.copy(text = c) } }
            ColorChooser("Detalles (números, botones)", style.accent, AccentSwatches) { c -> update { it.copy(accent = c) } }
            if (style.rows == RowStyle.CARDS) ColorChooser("Tarjetas", style.card, CardSwatches) { c -> update { it.copy(card = c) } }

            OptionRow("Filas", RowStyle.entries, style.rows, { it.label }) { v -> update { it.copy(rows = v) } }
            if (style.rows == RowStyle.CARDS) {
                OptionRow("Esquinas", CornerStyle.entries, style.corners, { it.label }) { v -> update { it.copy(corners = v) } }
            }
            OptionRow("Números", NumberStyle.entries, style.numbers, { it.label }) { v -> update { it.copy(numbers = v) } }
            OptionRow("Tamaño de letra", FontSize.entries, style.fontSize, { it.label }) { v -> update { it.copy(fontSize = v) } }
            OptionRow("Tipo de letra", FontStyle.entries, style.font, { it.label }) { v -> update { it.copy(font = v) } }
            OptionRow("Densidad", RowDensity.entries, style.density, { it.label }) { v -> update { it.copy(density = v) } }

            OutlinedButton(onClick = { update { ListStyle() } }) { Text("Restablecer el aspecto") }
            Button(onClick = { state.screen = Screen.Config }, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
            Box(Modifier.height(24.dp))
        }
    }
}

/** `matchParentSize` solo existe dentro de un Box; esto lo usa sin importar el ámbito. */
private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxSize()

@Composable
private fun <T> OptionRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (o in options) Chip(label(o), o == selected) { onSelect(o) }
        }
    }
}

/** Elegir un color: «Auto», una muestra de la paleta o un código #RRGGBB. */
@Composable
private fun ColorChooser(label: String, value: Long?, swatches: List<Long>, onChange: (Long?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip("Auto", value == null) { onChange(null) }
            for (c in swatches) {
                val picked = value == c
                Box(
                    Modifier
                        .size(32.dp)
                        .background(Color(argb(c)), CircleShape)
                        .border(if (picked) 3.dp else 1.dp, if (picked) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                        .clickable { onChange(c) },
                )
            }
        }
        if (value != null) HexField(value) { onChange(it) }
    }
}

// ------------------------------------------------------------------ Vistas previas

@Composable
private fun ItemsPreview() {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp)) {
            Text("Juegos de mesa", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("43 elementos", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for ((i, t) in listOf("Catan", "Carcassonne", "Ticket to Ride", "Pandemic", "Dixit").withIndex()) {
            ItemCard(
                number = i + 1,
                text = t,
                editing = false,
                dragging = false,
                modifier = Modifier,
                onTap = {},
                onCommit = {},
                onDelete = {},
                onDragStart = {},
                onDrag = {},
                onDragEnd = {},
            )
        }
    }
}

@Composable
private fun ListsPreview() {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp)) {
            Text("Mis listas", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("3 listas", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for ((name, n) in listOf("Juegos de mesa" to 43, "Música" to 38, "Países" to 47)) {
            ListRowCard(
                name, n, dragging = false, modifier = Modifier,
                onOpen = {}, onRename = {}, onDelete = {}, onDragStart = {}, onDrag = {}, onDragEnd = {},
            )
        }
    }
}
