package com.listapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.RadioButton
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val gray = MaterialTheme.colorScheme.onSurfaceVariant

            // ---------------- Efecto
            Collapsible(state, "efecto", "Efecto", "Las palabras que aparecerán en las listas") {
                Text(
                    "Elige en qué listas quieres que aparezca una palabra y cuál. Cuando alguien diga un número, la palabra aparecerá en ese puesto de esa lista. " +
                        "Si la palabra ya estaba en la lista, se cambia de sitio; si no, sustituye a la que había en ese puesto. Si la lista es más corta que el número, no se toca.",
                    fontSize = 13.sp,
                    color = gray,
                )
                if (cfg.pairs.isEmpty()) Text("Aún no hay palabras.", color = gray)
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
                Button(onClick = { editing = ForcePair(-1, "", "") }) { Text("Añadir palabra") }
            }

            // ---------------- Escritorio
            Collapsible(state, "escritorio", "Escritorio", "Cómo se ve y se comporta la pantalla de inicio") {
                Collapsible(state, "esc_modo", "Modo y capturas", "Las capturas de tu móvil y cómo se mueven", nested = true) {
                    Text(
                        "Pantalla completa: cada página es una captura entera que se desliza al cambiar de página. " +
                            "Iconos deslizantes: el fondo de pantalla se queda quieto y solo se deslizan los iconos, como en un móvil de verdad " +
                            "(hacen falta más capturas, te explicamos cómo).",
                        fontSize = 13.sp,
                        color = gray,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (m in ScreenMode.entries) Chip(m.label, cfg.mode == m) { state.setMode(m) }
                    }
                    HorizontalDivider()
                    if (cfg.mode == ScreenMode.FULL) {
                        Text(
                            "Capturas hechas con el propio móvil: la 1 es para la primera cifra, la 2 para la segunda y la 3 es la que tiene el icono de la app de listas.",
                            fontSize = 13.sp,
                            color = gray,
                        )
                        for (p in 0..2) {
                            val slot = Slots.pages[p]
                            val has = state.images.containsKey(slot)
                            Text("Pantalla ${p + 1}" + if (has) "  ✓" else "  (sin captura)", fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { choose(slot) }) { Text(if (has) "Cambiar" else "Elegir") }
                                if (has) OutlinedButton(onClick = { state.clearImage(slot) }) { Text("Quitar") }
                            }
                            BoxesButton(state, p)
                        }
                    } else {
                        LayersSection(state, ::choose)
                    }
                }

                Collapsible(state, "esc_dedo", "Dedo fuera de las cajas", "Qué pasa si fallas al deslizar", nested = true) {
                    Text(
                        "Qué pasa si empiezas a deslizar, en las pantallas 1 o 2, fuera de una caja de cifra. " +
                            "Ignorar: se cambia de página y se queda la cifra anterior. " +
                            "Bloquear: no deja deslizar. Dígito 0: cuenta como un 0.",
                        fontSize = 13.sp,
                        color = gray,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (m in com.listapp.domain.OutsideMode.entries) {
                            Chip(m.label, cfg.outsideMode == m) { state.updateConfig { it.copy(outsideMode = m) } }
                        }
                    }
                }

                Collapsible(state, "esc_botones", "Botones ocultos", "Reset y configuración", nested = true) {
                    Text(
                        "Reset y configuración no son botones visibles: se activan manteniendo el dedo quieto sobre su zona. " +
                            "Aquí eliges dónde está cada zona (una sola posición para todas las pantallas) y cuánto tiempo hay que mantener.",
                        fontSize = 13.sp,
                        color = gray,
                    )
                    Text("Reset: ${seconds(cfg.resetHoldMs)} · Configuración: ${seconds(cfg.configHoldMs)}")
                    Button(onClick = { state.screen = Screen.Editor(EditTarget.Hidden) }) { Text("Posición y tiempos") }
                }

                Collapsible(state, "esc_mascaras", "Máscaras", "Tapar la hora y poner la real", nested = true) {
                    Text(
                        if (cfg.mode == ScreenMode.FULL) {
                            "Tapan elementos de la captura (por ejemplo la hora) y pueden mostrar la hora real. La «imagen limpia» es una captura sin esos elementos, para rellenar sin que se note."
                        } else {
                            "Tapan elementos de la captura (por ejemplo la hora de la barra superior) y pueden mostrar la hora real. Quedan fijas: no se deslizan."
                        },
                        fontSize = 13.sp,
                        color = gray,
                    )
                    if (cfg.mode == ScreenMode.FULL) {
                        val hasClean = state.images.containsKey(Slots.CLEAN)
                        Text("Imagen limpia" + if (hasClean) "  ✓" else "  (opcional)", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { choose(Slots.CLEAN) }) { Text(if (hasClean) "Cambiar" else "Elegir") }
                            if (hasClean) OutlinedButton(onClick = { state.clearImage(Slots.CLEAN) }) { Text("Quitar") }
                        }
                    }
                    Button(onClick = { state.screen = Screen.Editor(EditTarget.Masks) }) { Text("Editar máscaras (${cfg.masks.size})") }
                }
            }

            // ---------------- Aspecto de las listas
            Collapsible(state, "aspecto", "Aspecto de las listas", "Colores, letra y estilo de la app de listas") {
                Text(
                    "Cambia cómo se ve la app de listas: colores de fondo y de letra, filas con o sin separadores, tamaño y tipo de letra… " +
                        "Mientras lo cambias ves una vista previa al momento.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { state.screen = Screen.ListStyle }) { Text("Personalizar") }
            }

            // ---------------- Icono y nombre de la app
            val changer = rememberAppIconChanger()
            var curIcon by remember { mutableStateOf(changer.currentIcon()) }
            var curName by remember { mutableStateOf(changer.currentName()) }
            var pendingChange by remember { mutableStateOf<Pair<String, String>?>(null) }
            val iconLabel = changer.icons.firstOrNull { it.id == curIcon }?.label ?: curIcon
            val nameLabel = changer.names.firstOrNull { it.id == curName }?.label
            Collapsible(
                state, "icono_app", "Icono y nombre de la app",
                subtitle = if (nameLabel != null) "$iconLabel · $nameLabel" else iconLabel,
                trailing = {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp))) { IconImage(curIcon) }
                },
            ) {
                // Al tocar un icono o un nombre no se cambia nada: antes hay que confirmarlo.
                AppIconSection(changer, curIcon, curName) { i, n ->
                    if (i != curIcon || n != curName) pendingChange = i to n
                }
            }
            pendingChange?.let { (i, n) ->
                val iconChanged = i != curIcon
                val what = if (iconChanged) {
                    "El icono de la app pasará a ser «${changer.icons.firstOrNull { it.id == i }?.label ?: i}»."
                } else {
                    "El nombre de la app pasará a ser «${changer.names.firstOrNull { it.id == n }?.label ?: n}»."
                }
                AlertDialog(
                    onDismissRequest = { pendingChange = null },
                    title = { Text(if (iconChanged) "¿Cambiar icono?" else "¿Cambiar nombre?") },
                    text = {
                        Text(
                            what + "\n\n" + if (changer.restartsApp) {
                                "La aplicación se reiniciará por completo (se cerrará y volverá a abrir)."
                            } else {
                                "El sistema mostrará un aviso para confirmar el cambio."
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingChange = null
                            if (changer.select(i, n)) {
                                curIcon = i
                                curName = n
                            }
                        }) { Text("Cambiar") }
                    },
                    dismissButton = { TextButton(onClick = { pendingChange = null }) { Text("Cancelar") } },
                )
            }

            // ---------------- Instrucciones
            Collapsible(state, "instrucciones", "Instrucciones", "Cómo funciona ListApp") { Instructions { state.showGuide = true } }

            Button(onClick = { state.reset() }, modifier = Modifier.fillMaxWidth()) { Text("Cerrar y volver al escritorio") }
            Text(
                "Desarrollado por WikiCode - Adri & Semi 2026",
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 32.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
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

/** Sección desplegable de la configuración. `nested`: versión más pequeña para las que van dentro de otra. */
@Composable
private fun Collapsible(
    state: AppState,
    id: String,
    title: String,
    subtitle: String? = null,
    nested: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val open = state.openSections[id] == true
    val shape = RoundedCornerShape(if (nested) 12.dp else 16.dp)
    val bg = if (nested) MaterialTheme.colorScheme.surface.copy(alpha = 0.75f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    Column(Modifier.fillMaxWidth().clip(shape).background(bg)) {
        Row(
            Modifier.fillMaxWidth().clickable { state.openSections[id] = !open }.padding(horizontal = 16.dp, vertical = if (nested) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = if (nested) 16.sp else 20.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) {
                trailing()
                Box(Modifier.width(12.dp))
            }
            Text(if (open) "▴" else "▾", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        }
        if (open) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { content() }
        }
    }
}

/** Elegir el icono (cuadrícula con miniaturas) y, en Android, el nombre del lanzador. Se aplica al tocar, sin botón de guardar. */
@Composable
private fun AppIconSection(changer: AppIconChanger, iconId: String, nameId: String, onSelect: (String, String) -> Unit) {
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    Text("Icono", fontWeight = FontWeight.SemiBold)
    for (row in changer.icons.chunked(4)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (icon in row) {
                val picked = icon.id == iconId
                Column(
                    Modifier.weight(1f).clickable { onSelect(icon.id, nameId) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val shape = RoundedCornerShape(14.dp) // ≈ 22 % de 64 dp
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(shape)
                            .border(if (picked) 3.dp else 1.dp, if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape),
                    ) { IconImage(icon.id) }
                    Text(
                        icon.label,
                        fontSize = 10.sp,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = gray,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            repeat(4 - row.size) { Box(Modifier.weight(1f)) }
        }
    }
    if (changer.names.isNotEmpty()) {
        Text("Nombre", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        for (n in changer.names) {
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(iconId, n.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = n.id == nameId, onClick = { onSelect(iconId, n.id) })
                Text(n.label)
            }
        }
    }
    Text(
        if (changer.names.isNotEmpty()) {
            "Al elegir uno se te pedirá confirmación. La app se reinicia para aplicarlo y el móvil puede tardar unos segundos en actualizar el icono y el nombre en el lanzador."
        } else {
            "Al elegir uno se te pedirá confirmación. En iPhone el nombre no se puede cambiar y el sistema muestra siempre su propio aviso al cambiar de icono."
        },
        fontSize = 12.sp,
        color = gray,
    )
}

/** Qué es la app, cómo se hace el efecto y para qué sirve cada parte de la configuración, sin tecnicismos. */
@Composable
private fun Instructions(onGuide: () -> Unit) {
    val topics = listOf(
        "¿Qué es ListApp?" to
            "Una herramienta de magia disfrazada. Parece el escritorio de un móvil normal y una app de listas de verdad, pero sin que nadie lo note " +
            "hace aparecer, en el número que diga un espectador, las palabras que tú has preparado.",
        "El efecto, paso a paso" to
            "1. Enseñas el móvil y cuentas que haces listas de todo: juegos, música, países…\n" +
            "2. Un espectador dice un número, por ejemplo el 37. Si es de una sola cifra, piensa en él con un cero delante: el 7 es 07.\n" +
            "3. En la primera pantalla del escritorio pones el dedo sobre la primera cifra (el 3) y deslizas a la siguiente pantalla. " +
            "En la segunda pones el dedo sobre la segunda cifra (el 7) y deslizas otra vez. Nadie ve las cajas de las cifras: parece que solo cambias de página.\n" +
            "4. En la tercera pantalla tocas el icono de la app de listas.\n" +
            "5. Entras en cada lista y pides al espectador que lea el elemento de ese número: ahí estará tu palabra.\n" +
            "6. Para repetirlo, mantén el dedo quieto en la zona oculta de reset (o cierra la app).",
        "Efecto" to
            "Aquí preparas las palabras. Pulsa «Añadir palabra», elige una de tus listas y escribe la palabra que quieres que salga. " +
            "Puedes poner una palabra por lista. Si la palabra ya estaba en la lista, se cambia de sitio; si no, ocupa el lugar de la que había, " +
            "así la lista nunca crece. Si una lista es más corta que el número elegido, se deja como está.",
        "Escritorio: modo y capturas" to
            "Aquí montas el escritorio falso con capturas de pantalla de tu móvil. Hay dos formas. " +
            "«Pantalla completa» es la más sencilla: haces una captura de cada una de las tres pantallas de iconos y se mueven enteras al deslizar. " +
            "«Iconos deslizantes» es más realista: el fondo se queda quieto y solo se mueven los iconos, como en un móvil de verdad; " +
            "necesita más capturas y la propia pantalla te explica cómo hacerlas. " +
            "En ambos casos marcas con el dedo las cajas de las cifras (pantallas 1 y 2) y la caja del icono de la app de listas (pantalla 3). El público no las ve.",
        "Escritorio: dedo fuera de las cajas" to
            "Decide qué pasa si empiezas a deslizar fuera de una caja de cifra. «Ignorar»: se cambia de página y se queda la cifra anterior. " +
            "«Bloquear»: no deja deslizar, así notas enseguida que has fallado. «Dígito 0»: cuenta como un cero.",
        "Escritorio: botones ocultos" to
            "El reset y la configuración no se ven: se activan manteniendo el dedo quieto en su zona el tiempo que elijas (1,5 segundos al principio). " +
            "Puedes cambiarles el sitio, el tamaño y el tiempo.",
        "Escritorio: máscaras" to
            "Sirven para tapar algo de la captura que delataría el truco, como la hora, y poner encima la hora real de tu móvil, siempre al día. " +
            "Se pueden rellenar con un color o con el fondo.",
        "Zonas fijas (iconos deslizantes)" to
            "Son las partes que en un móvil real no se mueven al cambiar de pantalla, como la barra de arriba o el dock de abajo.",
        "Aspecto de las listas" to
            "Aquí cambias cómo se ve la app de listas para que se parezca a la que usas de verdad: el color del fondo y de la letra, " +
            "si las filas son tarjetas o líneas con o sin separadores, el tamaño y el tipo de letra, y lo apretadas que van las filas. " +
            "Arriba tienes una vista previa que cambia al momento.",
        "Icono y nombre de la app" to
            "Puedes cambiar el icono y el nombre con el que aparece ListApp en tu móvil, por ejemplo para que parezca otra app de listas. " +
            "En Android cambian los dos; en iPhone solo el icono, y el sistema muestra un aviso. Antes de cambiar te pide confirmación " +
            "y la app se reinicia; el móvil puede tardar unos segundos en actualizar el lanzador.",
        "Un consejo" to
            "Prueba el efecto varias veces antes de actuar: practica a deslizar rápido y comprueba que las palabras aparecen donde deben.",
    )
    Button(onClick = onGuide) { Text("Ver la guía rápida") }
    for ((title, body) in topics) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Iconos deslizantes: fondo fijo, capturas de cada página y zonas fijas. */
@Composable
private fun LayersSection(state: AppState, choose: (String) -> Unit) {
    val cfg = state.config
    val gray = MaterialTheme.colorScheme.onSurfaceVariant

    Section("Fondo")
    Text(
        "La imagen original del fondo de pantalla de tu móvil (no una captura). Se queda quieta cuando cambias de página.",
        fontSize = 13.sp,
        color = gray,
    )
    val hasWall = state.images.containsKey(Slots.WALL)
    Text("Fondo" + if (hasWall) "  ✓" else "  (sin imagen)", fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { choose(Slots.WALL) }) { Text(if (hasWall) "Cambiar" else "Elegir") }
        if (hasWall) OutlinedButton(onClick = { state.clearImage(Slots.WALL) }) { Text("Quitar") }
        if (hasWall) Button(onClick = { state.screen = Screen.Editor(EditTarget.Wallpaper) }) { Text("Ajustar") }
    }

    HorizontalDivider()

    Section("Capturas de los iconos")
    Text(
        "Necesitas dos capturas de cada pantalla de iconos, iguales salvo por el fondo: una con fondo negro y otra con fondo blanco.",
        fontSize = 13.sp,
        color = gray,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
        ExampleScreenshot(dark = true, caption = "Fondo negro")
        ExampleScreenshot(dark = false, caption = "Fondo blanco")
    }
    Text(
        "1) En Ajustes → Fondo de pantalla elige un color liso NEGRO y haz una captura de cada pantalla de iconos.\n" +
            "2) Cambia el fondo a un color liso BLANCO y repite las capturas, con los mismos iconos en el mismo sitio.\n" +
            "3) Elige aquí las capturas. La app se encarga de dejar los iconos sueltos sobre tu fondo.",
        fontSize = 13.sp,
        color = gray,
    )
    for (p in 0..2) {
        val ready = state.layers.containsKey(p)
        Text("Pantalla ${p + 1}" + if (ready) "  ✓" else "", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((slot, label) in listOf(Slots.bwDark(p) to "Fondo negro", Slots.bwLight(p) to "Fondo blanco")) {
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
    Text(
        "Partes que no se deslizan en el móvil real: la barra superior y el dock. Se dibujan con la captura de la pantalla 1.",
        fontSize = 13.sp,
        color = gray,
    )
    Button(onClick = { state.screen = Screen.Editor(EditTarget.Fixed) }) { Text("Zonas fijas (${cfg.fixedZones.size})") }
}

/** Dibujo de ejemplo de una captura: rectángulo vertical con fondo liso e iconos como cuadrados de colores. */
@Composable
private fun ExampleScreenshot(dark: Boolean, caption: String) {
    val colors = listOf(Color(0xFFFF6347), Color(0xFF3CB371), Color(0xFFFFD700), Color(0xFFBA55D3), Color(0xFF1E90FF), Color(0xFFFF8C00))
    val shape = RoundedCornerShape(10.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .width(104.dp)
                .aspectRatio(0.46f)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape),
        ) {
            drawRect(if (dark) Color.Black else Color.White)
            val cols = 4
            val rows = 5
            val pad = size.width * 0.1f
            val cell = (size.width - pad * 2) / cols
            val icon = cell * 0.72f
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    drawRoundRect(
                        colors[(r * cols + c) % colors.size],
                        topLeft = Offset(pad + c * cell + (cell - icon) / 2, size.height * 0.1f + r * cell * 1.3f),
                        size = Size(icon, icon),
                        cornerRadius = CornerRadius(icon * 0.25f),
                    )
                }
            }
            // el dock, abajo
            drawRoundRect(
                if (dark) Color(0x40FFFFFF) else Color(0x30000000),
                topLeft = Offset(size.width * 0.05f, size.height * 0.86f),
                size = Size(size.width * 0.9f, size.height * 0.11f),
                cornerRadius = CornerRadius(size.width * 0.12f),
            )
        }
        Text(caption, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
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
        title = { Text(if (initial.id < 0) "Nueva palabra" else "Editar palabra") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (listNames.isEmpty() && list.isBlank()) {
                    Text("No quedan listas sin palabra. Crea otra lista en la app o borra una palabra.", color = MaterialTheme.colorScheme.error)
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
