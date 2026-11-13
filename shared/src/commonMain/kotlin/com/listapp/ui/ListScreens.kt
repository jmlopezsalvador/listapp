package com.listapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listapp.domain.MagicList

/** Flecha de «atrás» dibujada con Canvas. */
@Composable
fun BackArrow(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.size(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(22.dp)) {
            val s = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.7f, size.height * 0.08f), Offset(size.width * 0.25f, size.height * 0.5f), s.width, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.25f, size.height * 0.5f), Offset(size.width * 0.7f, size.height * 0.92f), s.width, StrokeCap.Round)
        }
    }
}

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

/** Botón redondo con «+». */
@Composable
fun PlusButton(onClick: () -> Unit) {
    val bg = MaterialTheme.colorScheme.primary
    val fg = MaterialTheme.colorScheme.onPrimary
    Box(
        Modifier.padding(end = 8.dp).size(40.dp).background(bg, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val w = 2.5.dp.toPx()
            drawLine(fg, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w, StrokeCap.Round)
            drawLine(fg, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w, StrokeCap.Round)
        }
    }
}

/** Una pantalla normal de la app: fondo del tema y márgenes seguros (notch, teclado…). */
@Composable
fun ScreenFrame(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) { content() }
}

// ------------------------------------------------------------------ Lista de listas

@Composable
fun ListsScreen(state: AppState) {
    var menuFor by remember { mutableStateOf<MagicList?>(null) }
    var creating by remember { mutableStateOf(false) }
    PlatformBackHandler(true) { state.screen = Screen.Home }

    ScreenFrame {
        TopBar("Mis listas", onBack = { state.screen = Screen.Home }) { PlusButton { creating = true } }
        if (state.lists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aún no tienes listas.\nPulsa + para crear una.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(state.lists, key = { _, l -> l.id }) { _, l ->
                Row(
                    Modifier.fillMaxWidth().clickable { state.screen = Screen.Detail(l.id) }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text("${l.items.size} elementos", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { menuFor = l }) { Text("···", fontSize = 20.sp) }
                }
                HorizontalDivider()
            }
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
    menuFor?.let { l ->
        var renaming by remember(l.id) { mutableStateOf(false) }
        if (renaming) {
            TextDialog(
                title = "Renombrar lista",
                initial = l.name,
                label = "Nombre",
                onDismiss = { renaming = false; menuFor = null },
                onOk = { n ->
                    state.updateLists { ls -> ls.map { if (it.id == l.id) it.copy(name = n.trim()) else it } }
                    renaming = false
                    menuFor = null
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { menuFor = null },
                title = { Text(l.name) },
                text = { Text("${l.items.size} elementos") },
                confirmButton = { TextButton(onClick = { renaming = true }) { Text("Renombrar") } },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            state.updateLists { ls -> ls.filter { it.id != l.id } }
                            menuFor = null
                        }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = { menuFor = null }) { Text("Cancelar") }
                    }
                },
            )
        }
    }
}

// ------------------------------------------------------------------ Elementos de una lista

@Composable
fun ListDetailScreen(state: AppState, listId: Long) {
    val list = state.lists.firstOrNull { it.id == listId }
    PlatformBackHandler(true) { state.screen = Screen.Lists }
    if (list == null) {
        LaunchedEffect(Unit) { state.screen = Screen.Lists }
        return
    }
    var editing by remember { mutableStateOf<Int?>(null) }
    var newItem by remember { mutableStateOf("") }

    fun addItem() {
        val t = newItem.trim()
        if (t.isEmpty()) return
        state.updateLists { ls -> ls.map { if (it.id == listId) it.copy(items = it.items + t) else it } }
        newItem = ""
    }

    ScreenFrame {
        TopBar(list.name, onBack = { state.screen = Screen.Lists })
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(list.items) { i, item ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = i }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${i + 1}",
                        Modifier.width(40.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                    Text(item, fontSize = 17.sp, modifier = Modifier.weight(1f))
                }
                HorizontalDivider()
            }
        }
        Row(
            Modifier.fillMaxWidth().imePadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                newItem,
                { newItem = it },
                Modifier.weight(1f),
                placeholder = { Text("Añadir elemento ${list.items.size + 1}") },
                singleLine = true,
            )
            Button(onClick = ::addItem, enabled = newItem.isNotBlank()) { Text("Añadir") }
        }
    }

    editing?.let { idx ->
        val current = list.items.getOrNull(idx)
        if (current == null) {
            editing = null
        } else {
            TextDialog(
                title = "Elemento ${idx + 1}",
                initial = current,
                label = "Texto",
                onDismiss = { editing = null },
                onOk = { t ->
                    state.updateLists { ls ->
                        ls.map { l -> if (l.id == listId) l.copy(items = l.items.toMutableList().also { it[idx] = t.trim() }) else l }
                    }
                    editing = null
                },
                allowBlank = true,
                extra = {
                    TextButton(onClick = {
                        state.updateLists { ls ->
                            ls.map { l -> if (l.id == listId) l.copy(items = l.items.toMutableList().also { it.removeAt(idx) }) else l }
                        }
                        editing = null
                    }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                },
            )
        }
    }
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
    extra: @Composable () -> Unit = {},
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onOk(text) }, enabled = allowBlank || text.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                extra()
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
