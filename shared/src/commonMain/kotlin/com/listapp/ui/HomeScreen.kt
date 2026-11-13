package com.listapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listapp.domain.OutsideMode
import com.listapp.domain.digitOfIndex
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/**
 * Escritorio simulado: 3 páginas deslizables (capturas del móvil).
 * - Página 1 y 2: el punto donde se empieza a arrastrar hacia la siguiente página da el dígito.
 * - Página 3: la caja del icono abre la app de listas.
 * - Esquina superior izquierda: reset. Esquina superior derecha: configuración (ambos invisibles).
 */
@Composable
fun HomeScreen(state: AppState) {
    val pager = rememberPagerState { 3 }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val cornerPx = with(density) { 72.dp.toPx() }

    val cfg by rememberUpdatedState(state.config)
    val sizeNow by rememberUpdatedState(size)

    // Último «dedo abajo»: página en la que estaba el escritorio y posición.
    var downPage by remember { mutableStateOf(-1) }
    var downPos by remember { mutableStateOf(Offset.Zero) }

    // Modo «bloquear»: solo se puede deslizar si el dedo empieza en una caja válida
    // (los gestos no válidos se consumen antes de llegar al pager).
    fun hitIndex(page: Int, pos: Offset): Int {
        val s = sizeNow
        if (s.width <= 0 || page !in 0..1) return -1
        return cfg.digitBoxes.getOrNull(page).orEmpty().indexOfFirst { it?.contains(pos.x / s.width, pos.y / s.height) == true }
    }

    // Volver a la primera página cuando se hace reset.
    LaunchedEffect(state.resetCount) { pager.scrollToPage(0) }

    // Dígito: al pasar de la página p a la p+1 se mira dónde empezó el dedo en la página p.
    LaunchedEffect(pager) {
        var prev = pager.settledPage
        snapshotFlow { pager.settledPage }.collect { cur ->
            if (cur == prev + 1 && prev in 0..1 && downPage == prev && sizeNow.width > 0) {
                val hit = hitIndex(prev, downPos)
                when {
                    hit >= 0 -> {
                        val d = digitOfIndex(hit)
                        if (prev == 0) state.tens = d else state.units = d
                    }
                    cfg.outsideMode == OutsideMode.ZERO -> if (prev == 0) state.tens = 0 else state.units = 0
                    cfg.outsideMode == OutsideMode.BLOCK -> { pager.scrollToPage(prev); prev = cur - 1; return@collect }
                    else -> {}
                }
            }
            prev = cur
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size = it }
            // Observa los dedos sin consumirlos, para no estorbar al deslizamiento del pager.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    downPage = pager.currentPage
                    downPos = down.position
                    val blocked = cfg.outsideMode == OutsideMode.BLOCK && downPage in 0..1 &&
                        !(sizeNow.width > 0 && hitIndex(downPage, down.position) >= 0)
                    if (blocked) down.consume()
                    var moved = false
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val c = ev.changes.firstOrNull() ?: break
                        if (blocked) ev.changes.forEach { it.consume() }
                        if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        if (c.changedToUp() || ev.changes.none { it.pressed }) {
                            if (!moved) {
                                val p = down.position
                                val s = sizeNow
                                when {
                                    p.x < cornerPx && p.y < cornerPx -> state.reset()
                                    p.x > s.width - cornerPx && p.y < cornerPx -> state.screen = Screen.Config
                                    pager.currentPage == 2 && !pager.isScrollInProgress && s.width > 0 &&
                                        cfg.iconBox?.contains(p.x / s.width, p.y / s.height) == true -> state.enterListApp()
                                }
                            }
                            break
                        }
                    }
                }
            },
    ) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            // Las máscaras (comunes en config) se dibujan dentro de cada página: viajan con su captura al deslizar.
            Box(Modifier.fillMaxSize()) {
                PageImage(state.images[Slots.pages[page]], page)
                MaskLayer(state, Modifier.fillMaxSize())
            }
        }
    }
}

/** Imagen de una página a pantalla completa (o un marcador si aún no hay captura). */
@Composable
fun PageImage(image: ImageBitmap?, page: Int, modifier: Modifier = Modifier.fillMaxSize()) {
    if (image != null) {
        Image(image, null, modifier, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Medium)
    } else {
        Box(
            modifier.background(Brush.verticalGradient(listOf(Color(0xFF1B2A41), Color(0xFF0B1320)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Pantalla ${page + 1}\nsin captura\n\nConfiguración: esquina superior derecha",
                color = Color(0x99FFFFFF),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** Hora real del sistema (se refresca sola). */
@Composable
fun rememberClock(pad: Boolean): String {
    var text by remember { mutableStateOf(formatNow(pad)) }
    LaunchedEffect(pad) {
        while (true) {
            text = formatNow(pad)
            kotlinx.coroutines.delay(1000)
        }
    }
    return text
}

private fun formatNow(pad: Boolean): String {
    val t = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val h = if (pad) t.hour.toString().padStart(2, '0') else t.hour.toString()
    return "$h:${t.minute.toString().padStart(2, '0')}"
}

/** Máscaras comunes a las tres páginas (se configuran una vez); cada página dibuja las suyas y se mueven con ella. */
@Composable
fun MaskLayer(state: AppState, modifier: Modifier = Modifier) {
    val clean = state.images[Slots.CLEAN]
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { size = it }) {
        if (size.width > 0) {
            for (m in state.config.masks) MaskView(m, size, clean)
        }
    }
}

@Composable
fun MaskView(mask: com.listapp.domain.Mask, size: IntSize, clean: ImageBitmap?, outline: Boolean = false) {
    val density = LocalDensity.current
    val r = mask.rect
    val wPx = (r.w * size.width).roundToInt().coerceAtLeast(1)
    val hPx = (r.h * size.height).roundToInt().coerceAtLeast(1)
    val xPx = (r.x * size.width).roundToInt()
    val yPx = (r.y * size.height).roundToInt()
    Box(
        Modifier
            .offset { IntOffset(xPx, yPx) }
            .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() }),
        contentAlignment = Alignment.Center,
    ) {
        if (mask.useClean && clean != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    clean,
                    srcOffset = IntOffset((r.x * clean.width).roundToInt(), (r.y * clean.height).roundToInt()),
                    srcSize = IntSize(
                        (r.w * clean.width).roundToInt().coerceAtLeast(1),
                        (r.h * clean.height).roundToInt().coerceAtLeast(1),
                    ),
                    dstSize = IntSize(wPx, hPx),
                    filterQuality = FilterQuality.Medium,
                )
            }
        } else {
            Box(Modifier.fillMaxSize().background(Color(argb(mask.color))))
        }
        if (mask.showClock) {
            val clock = rememberClock(mask.clockPadHour)
            Text(
                clock,
                color = Color(argb(mask.clockColor)),
                fontSize = with(density) { (hPx * mask.clockScale).toSp() },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/** Long ARGB (0xAARRGGBB) → Int que entiende `Color(Int)`. */
fun argb(v: Long): Int = v.toInt()
