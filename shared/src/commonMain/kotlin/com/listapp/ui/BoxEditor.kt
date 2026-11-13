package com.listapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listapp.domain.Mask
import com.listapp.domain.NRect
import com.listapp.domain.digitOfIndex
import kotlin.math.roundToInt

private val Accent = Color(0xFFFFD60A)
private val Idle = Color(0xFF00E5FF)

/** Caja editable sobre la captura. `fill`: si se pinta el relleno translúcido (las máscaras se ven tal cual). */
data class EditBox(val id: Long, val label: String, val rect: NRect, val selected: Boolean, val fill: Boolean = true)

/** Captura a pantalla completa con cajas que se arrastran (mover) y se estiran (asa de la esquina inferior derecha). */
@Composable
fun BoxCanvas(
    image: ImageBitmap?,
    page: Int,
    boxes: List<EditBox>,
    interactive: Boolean,
    onSelect: (Long) -> Unit,
    onChange: (Long, NRect) -> Unit,
    onTapImage: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    underlay: @Composable (IntSize) -> Unit = {},
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val sizeNow by rememberUpdatedState(size)
    val density = LocalDensity.current
    Box(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val s = sizeNow
                    if (s.width > 0) onTapImage(o.x / s.width, o.y / s.height)
                }
            },
    ) {
        PageImage(image, page)
        if (size.width > 0) underlay(size)
        if (size.width > 0) {
            for (b in boxes) key(b.id) {
                val rect by rememberUpdatedState(b.rect)
                val color = if (b.selected) Accent else Idle
                Box(
                    Modifier
                        .offset { IntOffset((b.rect.x * size.width).roundToInt(), (b.rect.y * size.height).roundToInt()) }
                        .size(
                            with(density) { (b.rect.w * size.width).toDp() },
                            with(density) { (b.rect.h * size.height).toDp() },
                        )
                        .background(if (b.fill) color.copy(alpha = if (b.selected) 0.28f else 0.16f) else Color.Transparent)
                        .border(if (b.selected) 2.dp else 1.dp, color)
                        .then(
                            if (!interactive) Modifier else Modifier
                                .pointerInput(b.id) { detectTapGestures { onSelect(b.id) } }
                                .pointerInput(b.id) {
                                    detectDragGestures(onDragStart = { onSelect(b.id) }) { change, drag ->
                                        change.consume()
                                        val s = sizeNow
                                        val r = rect
                                        onChange(b.id, r.copy(x = r.x + drag.x / s.width, y = r.y + drag.y / s.height).clamped())
                                    }
                                },
                        ),
                    contentAlignment = if (b.fill) Alignment.Center else Alignment.TopStart,
                ) {
                    Text(
                        b.label,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (b.fill) 14.sp else 9.sp,
                        maxLines = 1,
                    )
                }
            }
            // Asa de redimensionado de la caja seleccionada (fuera de la caja para que se pueda agarrar aunque sea pequeña).
            boxes.firstOrNull { it.selected }?.takeIf { interactive }?.let { b ->
                val rect by rememberUpdatedState(b.rect)
                val half = with(density) { 16.dp.roundToPx() }
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                ((b.rect.x + b.rect.w) * size.width).roundToInt() - half,
                                ((b.rect.y + b.rect.h) * size.height).roundToInt() - half,
                            )
                        }
                        .size(32.dp)
                        .background(Accent, CircleShape)
                        .pointerInput(b.id) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val s = sizeNow
                                val r = rect
                                onChange(b.id, r.copy(w = r.w + drag.x / s.width, h = r.h + drag.y / s.height).clamped())
                            }
                        },
                )
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .background(bg.copy(alpha = if (enabled) 1f else 0.4f), RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = 14.sp) }
}

private val DefaultBox = NRect(0.4f, 0.45f, 0.2f, 0.08f)

// ------------------------------------------------------------------ Pantalla del editor

@Composable
fun EditorScreen(state: AppState, target: EditTarget) {
    PlatformBackHandler(true) { state.screen = Screen.Config }
    val cfg = state.config

    var panelOpen by remember { mutableStateOf(true) }
    var selected by remember(target) { mutableStateOf<Long?>(null) }
    var viewPage by remember(target) {
        mutableStateOf(
            when (target) { EditTarget.Digits1 -> 0; EditTarget.Digits2 -> 1; EditTarget.Icon -> 2; EditTarget.Masks -> 0 },
        )
    }
    var viewClean by remember { mutableStateOf(false) }
    var eyedropper by remember { mutableStateOf(false) }

    val image = if (viewClean) state.images[Slots.CLEAN] else state.images[Slots.pages[viewPage]]
    val digitPage = if (target == EditTarget.Digits1) 0 else 1

    val boxes: List<EditBox> = when (target) {
        EditTarget.Digits1, EditTarget.Digits2 ->
            cfg.digitBoxes[digitPage].mapIndexedNotNull { i, r ->
                r?.let { EditBox(i.toLong(), digitOfIndex(i).toString(), it, selected == i.toLong()) }
            }
        EditTarget.Icon -> listOfNotNull(cfg.iconBox?.let { EditBox(0, "Icono", it, selected == 0L) })
        EditTarget.Masks -> cfg.masks.mapIndexed { i, m -> EditBox(m.id, "M${i + 1}", m.rect, selected == m.id, fill = false) }
    }

    fun setDigitBoxes(list: List<NRect?>) = state.updateConfig { c ->
        c.copy(digitBoxes = c.digitBoxes.mapIndexed { p, old -> if (p == digitPage) list else old })
    }

    fun changeBox(id: Long, r: NRect) {
        when (target) {
            EditTarget.Digits1, EditTarget.Digits2 ->
                setDigitBoxes(cfg.digitBoxes[digitPage].mapIndexed { i, old -> if (i.toLong() == id) r else old })
            EditTarget.Icon -> state.updateConfig { it.copy(iconBox = r) }
            EditTarget.Masks -> state.updateConfig { c -> c.copy(masks = c.masks.map { if (it.id == id) it.copy(rect = r) else it }) }
        }
    }

    fun updateMask(f: (Mask) -> Mask) {
        val id = selected ?: return
        state.updateConfig { c -> c.copy(masks = c.masks.map { if (it.id == id) f(it) else it }) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxCanvas(
            image = image,
            page = viewPage,
            boxes = boxes,
            interactive = !eyedropper,
            onSelect = { selected = it },
            onChange = ::changeBox,
            onTapImage = { fx, fy ->
                if (eyedropper && image != null) {
                    val px = (fx * image.width).toInt().coerceIn(0, image.width - 1)
                    val py = (fy * image.height).toInt().coerceIn(0, image.height - 1)
                    val buf = IntArray(1)
                    image.readPixels(buf, px, py, 1, 1)
                    val c = 0xFF000000 or (buf[0].toLong() and 0xFFFFFF)
                    updateMask { it.copy(color = c, useClean = false) }
                    eyedropper = false
                } else {
                    selected = null
                }
            },
            modifier = Modifier.fillMaxSize(),
            underlay = { size ->
                // En el editor de máscaras se ve el resultado real de cada máscara bajo su contorno.
                if (target == EditTarget.Masks) for (m in cfg.masks) MaskView(m, size, state.images[Slots.CLEAN])
            },
        )

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (target) {
                        EditTarget.Digits1 -> "Pantalla 1 · decenas"
                        EditTarget.Digits2 -> "Pantalla 2 · unidades"
                        EditTarget.Icon -> "Pantalla 3 · caja del icono"
                        EditTarget.Masks -> "Máscaras"
                    },
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = { panelOpen = !panelOpen }) { Text(if (panelOpen) "Ocultar" else "Mostrar") }
                Box(Modifier.size(8.dp))
                Button(onClick = { state.screen = Screen.Config }) { Text("Listo") }
            }
            if (panelOpen) {
                Column(
                    Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(2.dp))
                    when (target) {
                        EditTarget.Digits1, EditTarget.Digits2 -> DigitsPanel(
                            boxes = cfg.digitBoxes[digitPage],
                            selected = selected,
                            onSelect = { i ->
                                val cur = cfg.digitBoxes[digitPage]
                                if (cur[i.toInt()] == null) setDigitBoxes(cur.mapIndexed { k, r -> if (k == i.toInt()) DefaultBox else r })
                                selected = i
                            },
                            onGrid = {
                                setDigitBoxes(List(10) { i -> NRect(0.03f + (i % 5) * 0.19f, 0.55f + (i / 5) * 0.12f, 0.17f, 0.10f) })
                            },
                            onCopy = {
                                val other = cfg.digitBoxes[1 - digitPage]
                                setDigitBoxes(other)
                            },
                            onRemove = {
                                val s = selected?.toInt()
                                if (s != null) setDigitBoxes(cfg.digitBoxes[digitPage].mapIndexed { k, r -> if (k == s) null else r })
                            },
                            onClear = { setDigitBoxes(List(10) { null }); selected = null },
                            otherPage = 2 - digitPage,
                        )
                        EditTarget.Icon -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (cfg.iconBox == null) {
                                Button(onClick = { state.updateConfig { it.copy(iconBox = DefaultBox) }; selected = 0 }) { Text("Colocar caja") }
                            } else {
                                OutlinedButton(onClick = { state.updateConfig { it.copy(iconBox = null) }; selected = null }) { Text("Quitar caja") }
                            }
                            Text("Ajústala sobre el icono de la app de listas de la captura.", Modifier.weight(1f), fontSize = 13.sp)
                        }
                        EditTarget.Masks -> MasksPanel(
                            state = state,
                            selected = selected,
                            onSelect = { selected = it },
                            viewPage = viewPage,
                            viewClean = viewClean,
                            onView = { p, clean -> viewPage = p; viewClean = clean },
                            eyedropper = eyedropper,
                            onEyedropper = { eyedropper = it },
                            updateMask = ::updateMask,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitsPanel(
    boxes: List<NRect?>,
    selected: Long?,
    onSelect: (Long) -> Unit,
    onGrid: () -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
    onClear: () -> Unit,
    otherPage: Int,
) {
    Text(
        "Toca un dígito para colocar su caja; arrástrala para moverla y estira con el círculo amarillo. Punto = ya colocada.",
        fontSize = 13.sp,
    )
    for (row in 0..1) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for (i in row * 5 until row * 5 + 5) {
                Chip(digitOfIndex(i).toString() + if (boxes[i] != null) "·" else "", selected == i.toLong()) { onSelect(i.toLong()) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onGrid) { Text("Cuadrícula") }
        OutlinedButton(onClick = onCopy) { Text("Copiar de pantalla $otherPage") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRemove, enabled = selected != null) { Text("Quitar caja") }
        OutlinedButton(onClick = onClear) { Text("Quitar todas") }
    }
}

private val Swatches = listOf(0xFF000000, 0xFFFFFFFF, 0xFF1C1C1E, 0xFFF2F2F7, 0xFF0A84FF, 0xFF34C759, 0xFFFF3B30, 0xFF8E8E93)

@Composable
private fun MasksPanel(
    state: AppState,
    selected: Long?,
    onSelect: (Long) -> Unit,
    viewPage: Int,
    viewClean: Boolean,
    onView: (Int, Boolean) -> Unit,
    eyedropper: Boolean,
    onEyedropper: (Boolean) -> Unit,
    updateMask: ((Mask) -> Mask) -> Unit,
) {
    val cfg = state.config
    val hasClean = state.images.containsKey(Slots.CLEAN)
    val mask = cfg.masks.firstOrNull { it.id == selected }

    Text("Ver captura de fondo:", fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (p in 0..2) Chip("${p + 1}", !viewClean && viewPage == p) { onView(p, false) }
        Chip("Limpia", viewClean, enabled = hasClean) { onView(viewPage, true) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        cfg.masks.forEachIndexed { i, m -> Chip("M${i + 1}", m.id == selected) { onSelect(m.id) } }
        Chip("+ Nueva", false) {
            val id = (cfg.masks.maxOfOrNull { it.id } ?: 0L) + 1
            state.updateConfig { it.copy(masks = it.masks + Mask(id, NRect(0.05f, 0.01f, 0.22f, 0.04f), showClock = true)) }
            onSelect(id)
        }
    }
    if (mask == null) {
        Text("Toca una máscara (M1, M2…) o crea una nueva. Sirve para tapar la hora u otros elementos de la captura.", fontSize = 13.sp)
        return
    }

    Text("Relleno", fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("Color", !mask.useClean) { updateMask { it.copy(useClean = false) } }
        Chip("Imagen limpia", mask.useClean, enabled = hasClean) { updateMask { it.copy(useClean = true) } }
    }
    if (!mask.useClean) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (c in Swatches) {
                Box(
                    Modifier.size(32.dp).background(Color(argb(c)), CircleShape)
                        .border(if (mask.color == c) 3.dp else 1.dp, if (mask.color == c) Accent else Color.Gray, CircleShape)
                        .clickable { updateMask { it.copy(color = c) } },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            HexField(mask.color) { c -> updateMask { it.copy(color = c) } }
            Chip(if (eyedropper) "Toca la captura…" else "Pipeta", eyedropper) { onEyedropper(!eyedropper) }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Mostrar hora real", Modifier.weight(1f))
        Switch(mask.showClock, { v -> updateMask { it.copy(showClock = v) } })
    }
    if (mask.showClock) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Color de la hora", fontSize = 13.sp)
            for (c in listOf(0xFFFFFFFF, 0xFF000000, 0xFF8E8E93)) {
                Box(
                    Modifier.size(28.dp).background(Color(argb(c)), CircleShape)
                        .border(if (mask.clockColor == c) 3.dp else 1.dp, if (mask.clockColor == c) Accent else Color.Gray, CircleShape)
                        .clickable { updateMask { it.copy(clockColor = c) } },
                )
            }
            HexField(mask.clockColor, width = 110) { c -> updateMask { it.copy(clockColor = c) } }
        }
        Text("Tamaño del texto", fontSize = 13.sp)
        Slider(mask.clockScale, { v -> updateMask { it.copy(clockScale = v) } }, valueRange = 0.3f..1.0f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hora con 2 dígitos (09:41)", Modifier.weight(1f))
            Switch(mask.clockPadHour, { v -> updateMask { it.copy(clockPadHour = v) } })
        }
    }
    OutlinedButton(onClick = {
        state.updateConfig { c -> c.copy(masks = c.masks.filter { it.id != mask.id }) }
    }) { Text("Eliminar esta máscara", color = MaterialTheme.colorScheme.error) }
}

/** Campo «#RRGGBB». Solo aplica el cambio cuando el texto es un color válido. */
@Composable
private fun HexField(color: Long, width: Int = 120, onColor: (Long) -> Unit) {
    val current = "#" + (color and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
    var text by remember(current) { mutableStateOf(current) }
    OutlinedTextField(
        text,
        { t ->
            text = t
            parseHex(t)?.let(onColor)
        },
        Modifier.size(width.dp, 56.dp),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
    )
}

fun parseHex(t: String): Long? {
    val h = t.trim().removePrefix("#")
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return 0xFF000000 or v
}
