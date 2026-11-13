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
import androidx.compose.material3.Slider
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
import com.listapp.domain.ScreenMode

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

            // ---------------- Modo del escritorio
            Section("Modo del escritorio")
            Text(
                "Pantalla completa: cada página es una captura entera que se desliza. " +
                    "Capas: el fondo de pantalla queda fijo y solo se deslizan los iconos (como en el iPhone). " +
                    "Capas blanco/negro: dos capturas por página, resultado exacto. Capas color único: una captura por página sobre un color liso, más rápido pero menos fino.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (m in ScreenMode.entries) Chip(m.label, cfg.mode == m) { state.setMode(m) }
            }

            HorizontalDivider()

            if (cfg.mode == ScreenMode.FULL) {
                // ---------------- Pantallas (captura completa)
                Section("Pantallas del escritorio")
                Text(
                    "Capturas hechas con el propio móvil: 1 = decenas, 2 = unidades, 3 = la que tiene el icono de la app de listas.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (p in 0..2) {
                    val slot = Slots.pages[p]
                    val has = state.images.containsKey(slot)
                    Text("Pantalla ${p + 1}" + if (has) "  ✓" else "  (sin captura)", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { choose(slot) }) { Text(if (has) "Cambiar" else "Elegir") }
                        if (has) OutlinedButton(onClick = { state.clearImage(slot) }) { Text("Quitar") }
                        BoxesButton(state, p)
                    }
                }
            } else {
                LayersSection(state, ::choose)
            }

            HorizontalDivider()

            // ---------------- Máscaras
            Section("Máscaras (comunes a las 3 pantallas)")
            Text(
                if (cfg.mode == ScreenMode.FULL) {
                    "Tapan elementos de la captura (p. ej. la hora) y pueden mostrar la hora real. La «imagen limpia» es una captura sin esos elementos, para rellenar sin que se note."
                } else {
                    "Tapan elementos de la capa (p. ej. la hora de la barra superior) y pueden mostrar la hora real."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (cfg.mode == ScreenMode.FULL) {
                val hasClean = state.images.containsKey(Slots.CLEAN)
                Text("Imagen limpia" + if (hasClean) "  ✓" else "  (opcional)", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { choose(Slots.CLEAN) }) { Text(if (hasClean) "Cambiar" else "Elegir") }
                    if (hasClean) OutlinedButton(onClick = { state.clearImage(Slots.CLEAN) }) { Text("Quitar") }
                }
            } else {
                Text(
                    "En modo capas las máscaras son fijas (no se deslizan) y pueden rellenarse con el fondo de pantalla.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

/** Botón que abre el editor de cajas de una página: dígitos (1 y 2) o icono (3). */
@Composable
private fun BoxesButton(state: AppState, p: Int) {
    val cfg = state.config
    val target = listOf(EditTarget.Digits1, EditTarget.Digits2, EditTarget.Icon)[p]
    Button(onClick = { state.screen = Screen.Editor(target) }) {
        Text(if (p < 2) "Cajas (${cfg.digitBoxes[p].count { it != null }}/10)" else if (cfg.iconBox != null) "Caja del icono ✓" else "Caja del icono")
    }
}

/** Modo capas: fondo fijo, capturas de cada página y zonas fijas. */
@Composable
private fun LayersSection(state: AppState, choose: (String) -> Unit) {
    val cfg = state.config
    val bw = cfg.mode == ScreenMode.LAYERS_BW
    val gray = MaterialTheme.colorScheme.onSurfaceVariant

    Section("Fondo fijo")
    Text("El fondo de pantalla de tu móvil (la imagen original, no una captura). No se mueve al cambiar de página.", fontSize = 13.sp, color = gray)
    val hasWall = state.images.containsKey(Slots.WALL)
    Text("Fondo" + if (hasWall) "  ✓" else "  (sin imagen)", fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { choose(Slots.WALL) }) { Text(if (hasWall) "Cambiar" else "Elegir") }
        if (hasWall) OutlinedButton(onClick = { state.clearImage(Slots.WALL) }) { Text("Quitar") }
        if (hasWall) Button(onClick = { state.screen = Screen.Editor(EditTarget.Wallpaper) }) { Text("Ajustar") }
    }

    HorizontalDivider()

    Section("Capas de iconos")
    Text(
        if (bw) {
            "1) En Ajustes → Fondo de pantalla pon un color liso NEGRO y haz una captura de cada pantalla de iconos. " +
                "2) Repite con un color liso BLANCO (mismas páginas, mismos iconos, sin moverlos). " +
                "3) Elige aquí las capturas. La app calcula sola la transparencia de los iconos."
        } else {
            "Pon como fondo de pantalla un color liso que no aparezca en tus iconos (por ejemplo verde puro) y haz una captura de cada pantalla de iconos. " +
                "La app detecta sola ese color y lo hace transparente."
        },
        fontSize = 13.sp,
        color = gray,
    )
    if (!bw) {
        var tol by remember(cfg.keyTolerance) { mutableStateOf(cfg.keyTolerance.toFloat()) }
        Text("Tolerancia del color: ${tol.toInt()}", fontWeight = FontWeight.SemiBold)
        Slider(tol, { tol = it }, valueRange = 20f..200f, onValueChangeFinished = { state.setKeyTolerance(tol.toInt()) })
        Text(
            "Más alta = los píxeles parecidos al fondo (carpetas translúcidas, sombras) se vuelven más transparentes. " +
                "Para cristal translúcido el modo blanco/negro es más exacto.",
            fontSize = 12.sp,
            color = gray,
        )
    }
    for (p in 0..2) {
        val slots = if (bw) listOf(Slots.bwDark(p) to "Fondo negro", Slots.bwLight(p) to "Fondo blanco") else listOf(Slots.key(p) to "Captura")
        val ready = state.layers.containsKey(p)
        Text("Pantalla ${p + 1}" + if (ready) "  ✓" else "", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((slot, label) in slots) {
                val has = state.rawPresent[slot] == true
                OutlinedButton(onClick = { choose(slot) }) { Text(label + if (has) " ✓" else "") }
            }
        }
        BoxesButton(state, p)
        val status = state.layerStatus[p]
        if (status != null && status != "Lista") {
            Text(status, fontSize = 12.sp, color = if (status.startsWith("Error")) MaterialTheme.colorScheme.error else gray)
        }
    }

    HorizontalDivider()

    Section("Zonas fijas")
    Text("Partes que no se deslizan en el móvil real: la barra superior y el dock. Se dibujan con la capa de la pantalla 1.", fontSize = 13.sp, color = gray)
    Button(onClick = { state.screen = Screen.Editor(EditTarget.Fixed) }) { Text("Zonas fijas (${cfg.fixedZones.size})") }
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
