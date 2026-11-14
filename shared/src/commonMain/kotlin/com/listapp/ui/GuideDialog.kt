package com.listapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private class GuideStep(val title: String, val body: String, val art: @Composable () -> Unit)

private val Steps = listOf(
    GuideStep(
        "Bienvenido a ListApp",
        "Es una herramienta de magia: parece tu móvil de siempre con una app de listas de verdad, pero hace aparecer tus palabras en el número " +
            "que diga el espectador. Esta guía te enseña lo mínimo para empezar.",
    ) { ArtIntro() },
    GuideStep(
        "1. Prepara las palabras",
        "Entra en Configuración (mantén pulsado el recuadro de la esquina) → «Efecto» → «Añadir palabra». Elige una lista y escribe la palabra " +
            "que quieres que salga. Ya hay listas de ejemplo; puedes crear las tuyas dentro de la app de listas.",
    ) { ArtWords() },
    GuideStep(
        "2. Haz tus capturas",
        "Haz tres capturas de las pantallas de iconos de tu móvil. Las dos primeras servirán para las cifras; la tercera debe tener el icono " +
            "de la app de listas. Después, en Configuración → «Escritorio» → «Modo y capturas», elígelas.",
    ) { ArtShots() },
    GuideStep(
        "3. Marca las cajas",
        "En cada captura marca cajas invisibles. En las pantallas 1 y 2, diez cajas: una por cada cifra (el botón «Cuadrícula» las coloca de golpe " +
            "y luego las ajustas). En la pantalla 3, una caja sobre el icono de la app de listas.",
    ) { ArtBoxes() },
    GuideStep(
        "4. En directo",
        "Pide un número de dos cifras, por ejemplo el 37. En la pantalla 1 pon el dedo sobre el 3 y desliza; en la 2, sobre el 7 y desliza; " +
            "en la 3, toca el icono. Entra en cada lista y pide que lean ese número: ahí estará tu palabra. " +
            "Para repetir, mantén el dedo quieto en la zona oculta de reset (arriba a la izquierda).",
    ) { ArtLive() },
    GuideStep(
        "Y ahora, a explorar",
        "Practica varias veces antes de actuar. Cuando te sientas cómodo, descubre las opciones avanzadas en Configuración: iconos deslizantes, " +
            "máscaras para la hora, el aspecto de las listas o cambiar el icono y el nombre de la app. Todo está explicado en «Instrucciones».",
    ) { ArtExplore() },
)

/** Guía de introducción: pocos pasos con ilustraciones para la configuración mínima y el uso delante del público. */
@Composable
fun GuideDialog(onClose: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val last = step == Steps.lastIndex
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Guía rápida", Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${step + 1} / ${Steps.size}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onClose) { Text("Cerrar") }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) { Steps[step].art() }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(Steps[step].title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(Steps[step].body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    for (i in Steps.indices) {
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (i == step) 9.dp else 7.dp)
                                .background(if (i == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) OutlinedButton(onClick = { step-- }) { Text("Anterior") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { if (last) onClose() else step++ }) { Text(if (last) "Empezar" else "Siguiente") }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Ilustraciones (dibujadas con Compose)

private val Wallpaper = Brush.verticalGradient(listOf(Color(0xFF2B6A8F), Color(0xFF5B4060)))
private val IconColors = listOf(Color(0xFFFF6347), Color(0xFF3CB371), Color(0xFFFFD700), Color(0xFFBA55D3), Color(0xFF1E90FF), Color(0xFFFF8C00))
private val Accent = Color(0xFFFFD60A)

/** Un móvil dibujado: marco, fondo de pantalla y el contenido que se le pase. */
@Composable
private fun Phone(width: Dp = 72.dp, label: String? = null, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.width(width).aspectRatio(0.5f).clip(shape).background(Wallpaper).border(2.dp, Color(0xFF9AA7B8), shape),
            content = content,
        )
        if (label != null) Text(label, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Cuadrícula de iconos de colores; `special` es la celda (fila, columna) del icono blanco de la app de listas. */
@Composable
private fun IconGrid(special: Pair<Int, Int>? = null) {
    Canvas(Modifier.fillMaxSize().padding(5.dp)) {
        val cols = 4
        val rows = 6
        val cw = size.width / cols
        val ch = size.height / (rows + 1)
        val s = minOf(cw, ch) * 0.7f
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val color = if (special == r to c) Color.White else IconColors[(r * cols + c) % IconColors.size]
                drawRoundRect(
                    color,
                    topLeft = Offset(c * cw + (cw - s) / 2, r * ch + (ch - s) / 2),
                    size = Size(s, s),
                    cornerRadius = CornerRadius(s * 0.25f),
                )
            }
        }
    }
}

/** Posición (centro) de una celda de `IconGrid` dentro de un móvil de ancho `w`, como fracción (x, y) del móvil. */
private fun cellCenter(row: Int, col: Int): Pair<Float, Float> = ((col + 0.5f) / 4f) to ((row + 0.5f) / 7f)

@Composable
private fun MiniList(highlight: Int = 1) {
    Column(Modifier.fillMaxSize().background(Color.White).padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Juegos", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        for ((i, n) in listOf("36", "37", "38", "39", "40").withIndex()) {
            val hot = i == highlight
            Row(
                Modifier.fillMaxWidth().background(if (hot) Accent else Color(0xFFEFEFF4), RoundedCornerShape(4.dp)).padding(horizontal = 3.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(n, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(Modifier.width(4.dp))
                Text(if (hot) "Splendor" else "· · ·", fontSize = 7.sp, color = Color.Black)
            }
        }
    }
}

@Composable
private fun Pill(text: String, hot: Boolean = false) {
    Box(
        Modifier
            .background(if (hot) Accent else MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, fontSize = 13.sp, color = if (hot) Color.Black else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Arrow(text: String = "→") = Text(text, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)

@Composable
private fun ArtIntro() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Phone(width = 78.dp, label = "Escritorio") { IconGrid() }
        Arrow()
        Phone(width = 78.dp, label = "Tu lista") { MiniList() }
    }
}

@Composable
private fun ArtWords() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Juegos de mesa")
            Arrow()
            Pill("Splendor", hot = true)
        }
        Text("el espectador dice 37", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Arrow("↓")
        Row(
            Modifier.width(210.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("37", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 13.sp)
            Spacer(Modifier.width(10.dp))
            Text("Splendor", color = Color.Black, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ArtShots() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Phone(label = "1ª · cifra 1") { IconGrid() }
        Phone(label = "2ª · cifra 2") { IconGrid() }
        Phone(label = "3ª · app de listas") { IconGrid(special = 1 to 1) }
    }
}

/** Una caja invisible dibujada para la ilustración. */
@Composable
private fun DrawnBox(text: String = "", modifier: Modifier = Modifier) {
    Box(modifier.border(1.dp, Accent, RoundedCornerShape(3.dp)).background(Accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        if (text.isNotEmpty()) Text(text, fontSize = 7.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ArtBoxes() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Phone(width = 82.dp, label = "Pantallas 1 y 2") {
            IconGrid()
            // diez cajas (1..9 y 0) en dos filas de cinco
            Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 4.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (row in 0..1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (c in 0..4) DrawnBox("${(row * 5 + c + 1) % 10}", Modifier.size(width = 15.dp, height = 15.dp))
                    }
                }
            }
        }
        Phone(width = 82.dp, label = "Pantalla 3") {
            IconGrid(special = 1 to 1)
            val (fx, fy) = cellCenter(1, 1)
            Box(Modifier.fillMaxSize()) {
                BoxWithOffset(fx, fy) { DrawnBox("icono", Modifier.size(24.dp)) }
            }
        }
    }
}

/** Coloca un elemento centrado en una fracción (x, y) del móvil. */
@Composable
private fun BoxScope.BoxWithOffset(fx: Float, fy: Float, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.matchParentSize()) {
        Box(Modifier.offset(maxWidth * fx - 12.dp, maxHeight * fy - 12.dp)) { content() }
    }
}

/** Un dedo (círculo amarillo) sobre el móvil. */
@Composable
private fun BoxScope.Finger(fx: Float, fy: Float) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.matchParentSize()) {
        Box(Modifier.offset(maxWidth * fx - 8.dp, maxHeight * fy - 8.dp).size(16.dp).background(Accent.copy(alpha = 0.85f), CircleShape).border(2.dp, Color.White, CircleShape))
    }
}

@Composable
private fun ArtLive() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Phone(label = "Pantalla 1") {
            IconGrid()
            Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 4.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { for (c in 1..3) DrawnBox("$c", Modifier.size(15.dp)) }
            }
            Finger(0.7f, 0.82f)
        }
        Arrow()
        Phone(label = "Pantalla 2") {
            IconGrid()
            Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 4.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { for (c in listOf(5, 6, 7)) DrawnBox("$c", Modifier.size(15.dp)) }
            }
            Finger(0.7f, 0.82f)
        }
        Arrow()
        Phone(label = "Pantalla 3") {
            IconGrid(special = 1 to 1)
            val (fx, fy) = cellCenter(1, 1)
            Finger(fx, fy)
        }
    }
}

@Composable
private fun ArtExplore() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("⚙", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill("Iconos deslizantes"); Pill("Máscaras") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill("Aspecto de las listas") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill("Icono y nombre"); Pill("Instrucciones", hot = true) }
    }
}
