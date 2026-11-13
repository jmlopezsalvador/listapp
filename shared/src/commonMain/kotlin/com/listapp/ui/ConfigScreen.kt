package com.listapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listapp.domain.ForcePair

/** Menú secreto: pares lista → palabra, capturas de las 3 pantallas, cajas y máscaras. */
@Composable
fun ConfigScreen(state: AppState) {
    PlatformBackHandler(true) { state.screen = Screen.Home }
    val cfg = state.config
    var editing by remember { mutableStateOf<ForcePair?>(null) }
    var pendingSlot by remember { mutableStateOf<String?>(null) }
    val pick = rememberImagePicker { bytes -> pendingSlot?.let { state.setImage(it, bytes) } }

    fun choose(slot: String) {
        pendingSlot = slot
        pick()
    }

    ScreenFrame {
        TopBar("Configuración", onBack = { state.screen = Screen.Home })
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---------------- Pares
            Section("Pares lista → palabra")
            Text(
                "La palabra se escribirá en la posición del número elegido (decenas = pantalla 1, unidades = pantalla 2) de la lista con ese nombre. Si la palabra ya está en la lista, se mueve a esa posición. Si la lista es más corta que el número, no se toca.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (cfg.pairs.isEmpty()) Text("Aún no hay pares.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (p in cfg.pairs) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.listName, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("→", Modifier.padding(horizontal = 8.dp))
                    Text(p.word, Modifier.weight(1f))
                    TextButton(onClick = { editing = p }) { Text("Editar") }
                }
                val exists = state.lists.any { it.name.trim().equals(p.listName.trim(), ignoreCase = true) }
                if (!exists) Text("⚠ No existe una lista con este nombre.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = { editing = ForcePair(-1, "", "") }) { Text("Añadir par") }

            HorizontalDivider()

            // ---------------- Dedo fuera de las cajas
            Section("Dedo fuera de las cajas")
            Text(
                "Qué pasa si se empieza a deslizar en las pantallas 1 o 2 fuera de una caja de dígito. Ignorar: el dígito no cambia y se cambia de página. " +
                    "Bloquear: no deja deslizar. Dígito 0: vale 0 y se cambia de página.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in com.listapp.domain.OutsideMode.entries) {
                    Chip(m.label, cfg.outsideMode == m) { state.updateConfig { it.copy(outsideMode = m) } }
                }
            }

            HorizontalDivider()

            // ---------------- Pantallas
            Section("Pantallas del escritorio")
            Text(
                "Capturas hechas con el propio móvil: 1 = decenas, 2 = unidades, 3 = la que tiene el icono de la app de listas.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (p in 0..2) {
                val slot = Slots.pages[p]
                val has = state.images.containsKey(slot)
                val target = listOf(EditTarget.Digits1, EditTarget.Digits2, EditTarget.Icon)[p]
                Text("Pantalla ${p + 1}" + if (has) "  ✓" else "  (sin captura)", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { choose(slot) }) { Text(if (has) "Cambiar" else "Elegir") }
                    if (has) OutlinedButton(onClick = { state.clearImage(slot) }) { Text("Quitar") }
                    Button(onClick = { state.screen = Screen.Editor(target) }) {
                        Text(if (p < 2) "Cajas (${cfg.digitBoxes[p].count { it != null }}/10)" else if (cfg.iconBox != null) "Caja del icono ✓" else "Caja del icono")
                    }
                }
            }

            HorizontalDivider()

            // ---------------- Máscaras
            Section("Máscaras (comunes a las 3 pantallas)")
            Text(
                "Tapan elementos de la captura (p. ej. la hora) y pueden mostrar la hora real. La «imagen limpia» es una captura sin esos elementos, para rellenar sin que se note.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val hasClean = state.images.containsKey(Slots.CLEAN)
            Text("Imagen limpia" + if (hasClean) "  ✓" else "  (opcional)", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { choose(Slots.CLEAN) }) { Text(if (hasClean) "Cambiar" else "Elegir") }
                if (hasClean) OutlinedButton(onClick = { state.clearImage(Slots.CLEAN) }) { Text("Quitar") }
            }
            Button(onClick = { state.screen = Screen.Editor(EditTarget.Masks) }) { Text("Editar máscaras (${cfg.masks.size})") }

            HorizontalDivider()
            Button(onClick = { state.reset() }, modifier = Modifier.fillMaxWidth()) { Text("Cerrar y volver al escritorio") }
            Text("", Modifier.width(1.dp).padding(bottom = 24.dp))
        }
    }

    editing?.let { p ->
        PairDialog(
            initial = p,
            listNames = state.lists.map { it.name }.filter { n ->
                // solo las listas que aún no tienen par (la del par que se edita sí se mantiene)
                n.trim().equals(p.listName.trim(), ignoreCase = true) ||
                    cfg.pairs.none { it.listName.trim().equals(n.trim(), ignoreCase = true) }
            },
            onDismiss = { editing = null },
            onSave = { np ->
                state.updateConfig { c ->
                    if (np.id < 0) {
                        c.copy(pairs = c.pairs + np.copy(id = (c.pairs.maxOfOrNull { it.id } ?: 0L) + 1))
                    } else {
                        c.copy(pairs = c.pairs.map { if (it.id == np.id) np else it })
                    }
                }
                editing = null
            },
            onDelete = if (p.id >= 0) {
                {
                    state.updateConfig { c -> c.copy(pairs = c.pairs.filter { it.id != p.id }) }
                    editing = null
                }
            } else null,
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun PairDialog(
    initial: ForcePair,
    listNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (ForcePair) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var list by remember { mutableStateOf(initial.listName) }
    var word by remember { mutableStateOf(initial.word) }
    var open by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id < 0) "Nuevo par" else "Editar par") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (listNames.isEmpty() && list.isBlank()) {
                    Text("No quedan listas sin par. Crea otra lista en la app o borra un par.", color = MaterialTheme.colorScheme.error)
                } else {
                    Box {
                        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(list.ifBlank { "Elegir lista…" }, Modifier.weight(1f))
                            Text("▾")
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            for (n in listNames) DropdownMenuItem(text = { Text(n) }, onClick = { list = n; open = false })
                        }
                    }
                }
                OutlinedTextField(word, { word = it }, label = { Text("Palabra") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(initial.copy(listName = list.trim(), word = word.trim())) }, enabled = list.isNotBlank() && word.isNotBlank()) {
                Text("Guardar")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
