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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
internal fun LargeHeader(title: String, subtitle: String, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
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
internal fun PlusSign(color: Color, sizeDp: Int = 20) {
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
fun ScreenFrame(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) { content() }
}

@Composable
internal fun SwipeAction(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
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

    // ---- Reordenar listas (mantener pulsado y arrastrar, como los elementos)
    val lazy = rememberLazyListState()
    val look = LocalListLook.current
    val slotPx = with(LocalDensity.current) { ((look?.rowHeight ?: 54.dp) + 32.dp).toPx() } // alto de fila (h + 22) + 10
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current

    fun settleDrag() {
        val id = dragId ?: return
        var i = state.lists.indexOfFirst { it.id == id }
        val firstIdx = lazy.firstVisibleItemIndex
        val firstOff = lazy.firstVisibleItemScrollOffset
        var swapped = false
        while (i >= 0) {
            val last = state.lists.lastIndex
            if (dragOffset > slotPx / 2 && i < last) {
                state.updateLists { ls -> ls.toMutableList().also { it.add(i + 1, it.removeAt(i)) } }
                dragOffset -= slotPx; i++; swapped = true
            } else if (dragOffset < -slotPx / 2 && i > 0) {
                state.updateLists { ls -> ls.toMutableList().also { it.add(i - 1, it.removeAt(i)) } }
                dragOffset += slotPx; i--; swapped = true
            } else break
        }
        if (swapped) lazy.requestScrollToItem(firstIdx, firstOff)
        if (i == 0 && dragOffset < 0f) dragOffset = 0f
        if (i == state.lists.lastIndex && dragOffset > 0f) dragOffset = 0f
    }

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
                dragOffset += lazy.dispatchRawDelta(step)
                settleDrag()
            }
        }
    }

    ScreenFrame {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                LargeHeader("Mis listas", "${state.lists.size} listas", onBack = { state.screen = Screen.Home })
                if (state.lists.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Aún no tienes listas.\nPulsa + para crear una.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), state = lazy, contentPadding = PaddingValues(bottom = 96.dp)) {
                    itemsIndexed(state.lists, key = { _, l -> l.id }) { _, l ->
                        val dragging = dragId == l.id
                        ListRowCard(
                            name = l.name,
                            count = l.items.size,
                            dragging = dragging,
                            modifier = if (dragging) {
                                Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }
                            } else {
                                Modifier.animateItem()
                            },
                            onOpen = { state.screen = Screen.Detail(l.id) },
                            onRename = { renaming = l },
                            onDelete = { state.updateLists { ls -> ls.filter { it.id != l.id } } },
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragOffset = 0f
                                dragId = l.id
                            },
                            onDrag = { dy -> dragOffset += dy; settleDrag() },
                            onDragEnd = { dragId = null; dragOffset = 0f },
                        )
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
        NewListDialog(
            onDismiss = { creating = false },
            onOk = { name, items ->
                val id = state.nextListId()
                state.updateLists { it + MagicList(id, name.trim(), items) }
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

/** Una lista en la «lista de listas»: tarjeta o fila según el aspecto elegido; deslizar descubre Renombrar y Borrar. */
@Composable
internal fun ListRowCard(
    name: String,
    count: Int,
    dragging: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val look = LocalListLook.current ?: return
    val h = look.rowHeight + 22.dp
    val scope = rememberCoroutineScope()
    var suppressTap by remember { mutableStateOf(false) }
    val dragStart by rememberUpdatedState(onDragStart)
    val dragMove by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val shape = RoundedCornerShape(if (look.cards) look.corner else 0.dp)
    SwipeRow(
        actionsWidth = 176.dp,
        corner = if (look.cards) look.corner else 0.dp,
        enabled = !dragging,
        modifier = modifier
            .then(if (look.cards) Modifier.padding(horizontal = 16.dp, vertical = 5.dp).height(h) else Modifier.height(h + 10.dp))
            .then(if (dragging) Modifier.shadow(10.dp, shape) else Modifier),
        actions = {
            SwipeAction("Renombrar", MaterialTheme.colorScheme.primary, Modifier.weight(1f), onRename)
            SwipeAction("Borrar", Color(0xFFD32F2F), Modifier.weight(1f), onDelete)
        },
    ) { close, open ->
        Row(
            Modifier
                .fillMaxSize()
                .background(if (look.cards) look.card else look.bg)
                .rowDivider(look)
                .clickable { if (suppressTap) suppressTap = false else if (open) close() else onOpen() }
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
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$count elementos", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ Elementos de una lista

/** Fila en pantalla: id estable (para animar y reordenar) + texto. */
private data class ItemRow(val id: Long, val text: String)

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
    val look = LocalListLook.current
    // Hueco de cada fila: alto de la tarjeta + 3 de margen arriba y abajo.
    val slotPx = with(density) { ((look?.rowHeight ?: 54.dp) + 6.dp).toPx() }

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
        // LazyColumn «sigue» a la fila que estaba arriba cuando cambia de sitio y desplaza la lista: hay que fijar el scroll.
        val firstIdx = lazy.firstVisibleItemIndex
        val firstOff = lazy.firstVisibleItemScrollOffset
        var swapped = false
        while (i >= 0) {
            if (dragOffset > slotPx / 2 && i < rows.lastIndex) {
                rows.add(i + 1, rows.removeAt(i)); dragOffset -= slotPx; i++; swapped = true
            } else if (dragOffset < -slotPx / 2 && i > 0) {
                rows.add(i - 1, rows.removeAt(i)); dragOffset += slotPx; i--; swapped = true
            } else break
        }
        if (swapped) lazy.requestScrollToItem(firstIdx, firstOff)
        // En los extremos no hay con quién intercambiarse: la fila no puede salirse de su hueco hacia fuera.
        if (i == 0 && dragOffset < 0f) dragOffset = 0f
        if (i == rows.lastIndex && dragOffset > 0f) dragOffset = 0f
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
                // Solo se suma lo que la lista se ha desplazado de verdad (en los extremos no se mueve).
                dragOffset += lazy.dispatchRawDelta(step)
                settleDrag()
            }
        }
    }

    // Tocar en una zona vacía quita el foco: se guarda el cambio y se cierra el teclado.
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    ScreenFrame(Modifier.pointerInput(Unit) { detectTapGestures { focusManager.clearFocus(); keyboard?.hide() } }) {
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
                        .height(look?.rowHeight ?: 54.dp)
                        .clip(RoundedCornerShape(look?.corner ?: 16.dp))
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
internal fun ItemCard(
    number: Int,
    text: String,
    editing: Boolean,
    dragging: Boolean,
    modifier: Modifier,
    onTap: () -> Unit,
    onCommit: (String) -> Unit,
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
    val look = LocalListLook.current ?: return
    val shape = RoundedCornerShape(if (look.cards) look.corner else 0.dp)

    SwipeRow(
        actionsWidth = 96.dp,
        corner = if (look.cards) look.corner else 0.dp,
        modifier = modifier
            .then(
                if (look.cards) Modifier.padding(horizontal = 16.dp, vertical = 3.dp).height(look.rowHeight)
                else Modifier.height(look.rowHeight + 6.dp),
            )
            .then(if (dragging) Modifier.shadow(10.dp, shape) else Modifier),
        enabled = !editing && !dragging,
        onFullSwipe = onDelete,
        actions = { SwipeAction("Borrar", Color(0xFFD32F2F), Modifier.weight(1f), onDelete) },
    ) { close, open ->
        Row(
            Modifier
                .fillMaxSize()
                .background(if (look.cards) look.card else look.bg)
                .rowDivider(look)
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
            if (look.style.numbers == com.listapp.domain.NumberStyle.CIRCLE) {
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
            } else {
                Text(
                    "$number",
                    Modifier.width(30.dp),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (editing) {
                EditableText(text, onCommit, Modifier.weight(1f))
            } else {
                Text(text, Modifier.weight(1f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Campo de texto en línea. Guarda (una sola vez) al pulsar «Hecho», al perder el foco o al desaparecer el campo
 * (cambiar de fila, salir de la pantalla…); «Hecho» además cierra el teclado.
 */
@Composable
private fun EditableText(initial: String, onCommit: (String) -> Unit, modifier: Modifier) {
    var draft by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    val draftNow by rememberUpdatedState(draft)
    val commit by rememberUpdatedState(onCommit)
    fun save() {
        if (committed) return
        committed = true
        commit(draftNow)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(Unit) { onDispose { save() } }
    BasicTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged {
                if (focused && !it.isFocused) save()
                focused = it.isFocused
            },
        singleLine = true,
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save(); keyboard?.hide(); focusManager.clearFocus() }),
    )
}

/** Diálogo de «Nueva lista»: nombre y, opcionalmente, elementos importados desde texto plano. */
@Composable
fun NewListDialog(onDismiss: () -> Unit, onOk: (String, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<String>>(emptyList()) }
    var importing by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    if (importing) {
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text("Importar elementos") },
            text = {
                Column {
                    Text(
                        "Pega texto plano. Cada línea (separada por un salto de línea) será un elemento de la lista, " +
                            "en su número de orden. Las líneas vacías se ignoran.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        importText,
                        { importText = it },
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        placeholder = { Text("Uno\nDos\nTres") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    items = parseImportedLines(importText)
                    importing = false
                }) { Text("Cargar") }
            },
            dismissButton = { TextButton(onClick = { importing = false }) { Text("Cancelar") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva lista") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                if (items.isNotEmpty()) {
                    Text(
                        "Se cargarán ${items.size} elementos",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(onClick = { importText = items.joinToString("\n"); importing = true }) {
                    Text(if (items.isEmpty()) "Importar" else "Cambiar importación")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onOk(name, items) }, enabled = name.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Cada línea no vacía del texto pegado es un elemento. */
internal fun parseImportedLines(text: String): List<String> =
    text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

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
        text = {
            OutlinedTextField(
                text, { text = it }, label = { Text(label) }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (allowBlank || text.isNotBlank()) onOk(text) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onOk(text) }, enabled = allowBlank || text.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
