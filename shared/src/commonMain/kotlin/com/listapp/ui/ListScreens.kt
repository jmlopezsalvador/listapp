package com.listapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.listapp.domain.MagicList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Piezas comunes

/** Flecha de «atrás» dibujada con Canvas. */
@Composable
fun BackArrow(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.size(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(22.dp)) {
            val w = 2.5.dp.toPx()
            drawLine(color, Offset(size.width * 0.7f, size.height * 0.08f), Offset(size.width * 0.25f, size.height * 0.5f), w, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.25f, size.height * 0.5f), Offset(size.width * 0.7f, size.height * 0.92f), w, StrokeCap.Round)
        }
    }
}

/** Barra superior sencilla (la usa la configuración). */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) BackArrow(onBack) else Box(Modifier.width(12.dp))
        Text(
            title,
            Modifier.weight(1f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

/** Cabecera grande de las pantallas de listas: título + subtítulo. */
@Composable
private fun LargeHeader(title: String, subtitle: String, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) BackArrow(onBack) else Box(Modifier.width(16.dp))
            Box(Modifier.weight(1f))
            trailing()
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Signo «+» dibujado con Canvas. */
@Composable
private fun PlusSign(color: Color, sizeDp: Int = 20) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val w = 2.5.dp.toPx()
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w, StrokeCap.Round)
        drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w, StrokeCap.Round)
    }
}

/** Botón redondo con «+». */
@Composable
fun PlusButton(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { PlusSign(MaterialTheme.colorScheme.onPrimary, 18) }
}

/** Una pantalla normal de la app: fondo del tema y márgenes seguros (notch, teclado…). */
@Composable
fun ScreenFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) { content() }
}

@Composable
private fun cardColor() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

@Composable
private fun SwipeAction(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxHeight().background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

// ------------------------------------------------------------------ Lista de listas

@Composable
fun ListsScreen(state: AppState) {
    var renaming by remember { mutableStateOf<MagicList?>(null) }
    var creating by remember { mutableStateOf(false) }
    PlatformBackHandler(true) { state.screen = Screen.Home }

    ScreenFrame {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                LargeHeader("Mis listas", "${state.lists.size} listas", onBack = { state.screen = Screen.Home })
                if (state.lists.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Aún no tienes listas.\nPulsa + para crear una.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    itemsIndexed(state.lists, key = { _, l -> l.id }) { _, l ->
                        SwipeRow(
                            actionsWidth = 176.dp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).height(76.dp),
                            actions = {
                                SwipeAction("Renombrar", MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { renaming = l }
                                SwipeAction("Borrar", Color(0xFFD32F2F), Modifier.weight(1f)) {
                                    state.updateLists { ls -> ls.filter { it.id != l.id } }
                                }
                            },
                        ) { close, open ->
                            Row(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                    .clickable { if (open) close() else state.screen = Screen.Detail(l.id) }
                                    .padding(horizontal = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(l.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${l.items.size} elementos", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("›", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { creating = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { PlusSign(MaterialTheme.colorScheme.onPrimary, 22) }
        }
    }

    if (creating) {
        TextDialog(
            title = "Nueva lista",
            initial = "",
            label = "Nombre",
            onDismiss = { creating = false },
            onOk = { name ->
                val id = state.nextListId()
                state.updateLists { it + MagicList(id, name.trim()) }
                creating = false
                state.screen = Screen.Detail(id)
            },
        )
    }
    renaming?.let { l ->
        TextDialog(
            title = "Renombrar lista",
            initial = l.name,
            label = "Nombre",
            onDismiss = { renaming = null },
            onOk = { n ->
                state.updateLists { ls -> ls.map { if (it.id == l.id) it.copy(name = n.trim()) else it } }
                renaming = null
            },
        )
    }
}

// ------------------------------------------------------------------ Elementos de una lista

/** Fila en pantalla: id estable (para animar y reordenar) + texto. */
private data class ItemRow(val id: Long, val text: String)

private val SlotHeight = 60.dp // 54 de tarjeta + 3 de margen arriba y abajo
private const val REORDER_HOLD_MS = 1000L

@Composable
fun ListDetailScreen(state: AppState, listId: Long) {
    val list = state.lists.firstOrNull { it.id == listId }
    PlatformBackHandler(true) { state.screen = Screen.Lists }
    if (list == null) {
        LaunchedEffect(Unit) { state.screen = Screen.Lists }
        return
    }

    val idGen = remember(listId) { longArrayOf(0L) }
    fun newId() = idGen[0]++
    val rows = remember(listId) { mutableStateListOf<ItemRow>().also { r -> list.items.forEach { r.add(ItemRow(newId(), it)) } } }
    var editingId by remember(listId) { mutableStateOf<Long?>(null) }
    val lazy = rememberLazyListState()
    val density = LocalDensity.current
    val slotPx = with(density) { SlotHeight.toPx() }

    // Guarda en la lista real (las filas vacías en edición no se guardan).
    fun persist() {
        val texts = rows.filter { it.text.isNotBlank() }.map { it.text }
        state.updateLists { ls -> ls.map { if (it.id == listId) it.copy(items = texts) else it } }
    }

    fun commitRow(id: Long, text: String) {
        val i = rows.indexOfFirst { it.id == id }
        if (i < 0) return
        val t = text.trim()
        if (t.isEmpty()) rows.removeAt(i) else rows[i] = rows[i].copy(text = t)
        if (editingId == id) editingId = null
        persist()
    }

    /** «Siguiente» en el teclado: guarda esta fila y abre otra nueva justo debajo. */
    fun nextRow(id: Long, text: String) {
        val i = rows.indexOfFirst { it.id == id }
        if (i < 0) return
        val t = text.trim()
        if (t.isEmpty()) { commitRow(id, text); return }
        rows[i] = rows[i].copy(text = t)
        val nr = ItemRow(newId(), "")
        rows.add(i + 1, nr)
        editingId = nr.id
        persist()
    }

    fun addAtEnd() {
        val nr = ItemRow(newId(), "")
        rows.add(nr)
        editingId = nr.id
    }

    fun deleteRow(id: Long) {
        rows.removeAll { it.id == id }
        if (editingId == id) editingId = null
        persist()
    }

    // Que la fila en edición quede a la vista (también al salir el teclado).
    LaunchedEffect(editingId) {
        val id = editingId ?: return@LaunchedEffect
        delay(300)
        val i = rows.indexOfFirst { it.id == id }
        if (i < 0) return@LaunchedEffect
        val v = lazy.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }
        if (v == null || v.offset < 0 || v.offset + v.size > lazy.layoutInfo.viewportEndOffset) lazy.animateScrollToItem(i)
    }

    // ---- Reordenar (mantener pulsado 1 s y arrastrar)
    var dragId by remember(listId) { mutableStateOf<Long?>(null) }
    var dragOffset by remember(listId) { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current

    fun settleDrag() {
        val id = dragId ?: return
        var i = rows.indexOfFirst { it.id == id }
        while (i >= 0) {
            if (dragOffset > slotPx / 2 && i < rows.lastIndex) {
                rows.add(i + 1, rows.removeAt(i)); dragOffset -= slotPx; i++
            } else if (dragOffset < -slotPx / 2 && i > 0) {
                rows.add(i - 1, rows.removeAt(i)); dragOffset += slotPx; i--
            } else break
        }
    }

    // Desplazamiento automático al arrastrar cerca del borde.
    LaunchedEffect(dragId) {
        val id = dragId ?: return@LaunchedEffect
        while (true) {
            delay(16)
            val info = lazy.layoutInfo
            val v = info.visibleItemsInfo.firstOrNull { it.key == id } ?: continue
            val top = v.offset + dragOffset
            val bottom = top + v.size
            val step = when {
                top < 90f -> -16f
                bottom > info.viewportEndOffset - 90f -> 16f
                else -> 0f
            }
            if (step != 0f) {
                lazy.dispatchRawDelta(step)
                dragOffset += step
                settleDrag()
            }
        }
    }

    ScreenFrame {
        LargeHeader(
            list.name,
            "${rows.count { it.text.isNotBlank() }} elementos",
            onBack = { state.screen = Screen.Lists },
            trailing = { PlusButton { addAtEnd() } },
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().imePadding(),
            state = lazy,
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            itemsIndexed(rows, key = { _, r -> r.id }) { idx, r ->
                val dragging = dragId == r.id
                ItemCard(
                    number = idx + 1,
                    text = r.text,
                    editing = editingId == r.id,
                    dragging = dragging,
                    modifier = if (dragging) {
                        Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }
                    } else {
                        Modifier.animateItem()
                    },
                    onTap = { editingId = r.id },
                    onCommit = { commitRow(r.id, it) },
                    onNext = { nextRow(r.id, it) },
                    onDelete = { deleteRow(r.id) },
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        editingId = null
                        dragOffset = 0f
                        dragId = r.id
                    },
                    onDrag = { dy -> dragOffset += dy; settleDrag() },
                    onDragEnd = { dragId = null; dragOffset = 0f; persist() },
                )
            }
            item(key = "add") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { addAtEnd() }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { PlusSign(MaterialTheme.colorScheme.primary, 16) }
                    Spacer(Modifier.width(12.dp))
                    Text("Añadir elemento", color = MaterialTheme.colorScheme.primary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ItemCard(
    number: Int,
    text: String,
    editing: Boolean,
    dragging: Boolean,
    modifier: Modifier,
    onTap: () -> Unit,
    onCommit: (String) -> Unit,
    onNext: (String) -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var suppressTap by remember { mutableStateOf(false) }
    // El detector de gestos no se reinicia al cambiar de posición, así que usa siempre las últimas funciones.
    val dragStart by rememberUpdatedState(onDragStart)
    val dragMove by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val shape = RoundedCornerShape(16.dp)

    SwipeRow(
        actionsWidth = 96.dp,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .height(54.dp)
            .then(if (dragging) Modifier.shadow(10.dp, shape) else Modifier),
        enabled = !editing && !dragging,
        onFullSwipe = onDelete,
        actions = { SwipeAction("Borrar", Color(0xFFD32F2F), Modifier.weight(1f), onDelete) },
    ) { close, open ->
        Row(
            Modifier
                .fillMaxSize()
                .background(
                    if (dragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                )
                .clickable(enabled = !editing) {
                    if (suppressTap) suppressTap = false else if (open) close() else onTap()
                }
                .longPressReorder(
                    key = Unit,
                    holdMs = REORDER_HOLD_MS,
                    onStart = { suppressTap = true; dragStart() },
                    onDrag = { dy -> dragMove(dy) },
                    onEnd = {
                        scope.launch { delay(200); suppressTap = false }
                        dragEnd()
                    },
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(30.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$number",
                    fontSize = if (number < 100) 13.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (editing) {
                EditableText(text, onCommit, onNext, Modifier.weight(1f))
            } else {
                Text(text, Modifier.weight(1f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Campo de texto en línea: guarda al perder el foco; «Siguiente» guarda y abre otra fila debajo. */
@Composable
private fun EditableText(initial: String, onCommit: (String) -> Unit, onNext: (String) -> Unit, modifier: Modifier) {
    var draft by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged {
                if (focused && !it.isFocused) onCommit(draft)
                focused = it.isFocused
            },
        singleLine = true,
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext(draft) }),
    )
}

/** Diálogo con un campo de texto. */
@Composable
fun TextDialog(
    title: String,
    initial: String,
    label: String,
    onDismiss: () -> Unit,
    onOk: (String) -> Unit,
    allowBlank: Boolean = false,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onOk(text) }, enabled = allowBlank || text.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
