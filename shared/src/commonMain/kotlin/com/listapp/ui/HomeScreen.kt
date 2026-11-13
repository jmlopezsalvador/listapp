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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listapp.domain.OutsideMode
import com.listapp.domain.ScreenMode
import com.listapp.domain.digitOfIndex
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max
import kotlin.math.roundToInt

/** Tiempo que hay que mantener el dedo quieto en las esquinas ocultas (reset / configuración). */
private const val HOLD_MS = 1500L

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
    val haptic = LocalHapticFeedback.current

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
                    val p = down.position
                    val s = sizeNow
                    // Reset (arriba izquierda) y configuración (arriba derecha): hay que mantener el dedo quieto HOLD_MS.
                    val corner = when {
                        p.x < cornerPx && p.y < cornerPx -> 1
                        p.x > s.width - cornerPx && p.y < cornerPx -> 2
                        else -> 0
                    }
                    var moved = false
                    if (corner != 0) {
                        val r = withTimeoutOrNull<Int>(HOLD_MS) {
                            var res = 0 // 0 = sigue pulsado, 1 = se movió, 2 = soltó antes de tiempo
                            while (res == 0) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                if (blocked) ev.changes.forEach { it.consume() }
                                val c = ev.changes.firstOrNull()
                                res = when {
                                    c == null -> 2
                                    (c.position - p).getDistance() > viewConfiguration.touchSlop -> 1
                                    c.changedToUp() || ev.changes.none { it.pressed } -> 2
                                    else -> 0
                                }
                            }
                            res
                        }
                        when (r) {
                            null -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (corner == 1) state.reset() else state.screen = Screen.Config
                                return@awaitEachGesture
                            }
                            1 -> moved = true
                            else -> return@awaitEachGesture
                        }
                    }
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val c = ev.changes.firstOrNull() ?: break
                        if (blocked) ev.changes.forEach { it.consume() }
                        if ((c.position - p).getDistance() > viewConfiguration.touchSlop) moved = true
                        if (c.changedToUp() || ev.changes.none { it.pressed }) {
                            // Toque corto sobre la caja del icono (página 3): abre la app de listas.
                            if (!moved && pager.currentPage == 2 && !pager.isScrollInProgress && s.width > 0 &&
                                cfg.iconBox?.contains(p.x / s.width, p.y / s.height) == true
                            ) state.enterListApp()
                            break
                        }
                    }
                }
            },
    ) {
        if (cfg.mode == ScreenMode.FULL) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                // Las máscaras (comunes en config) se dibujan dentro de cada página: viajan con su captura al deslizar.
                Box(Modifier.fillMaxSize()) {
                    PageImage(state.images[Slots.pages[page]], page)
                    MaskLayer(state, Modifier.fillMaxSize())
                }
            }
        } else {
            // Modo capas: el fondo y las máscaras quedan fijos; solo se desliza la capa de iconos.
            WallpaperLayer(state)
            HorizontalPager(pager, Modifier.fillMaxSize()) { page -> LayerPage(state, page) }
            FixedZonesLayer(state)
            MaskLayer(state, Modifier.fillMaxSize())
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

/** Máscaras comunes a las tres páginas (se configuran una vez). En pantalla completa cada página dibuja las suyas y viajan con ella; en modo capas son fijas. */
@Composable
fun MaskLayer(state: AppState, modifier: Modifier = Modifier) {
    val clean = state.images[Slots.CLEAN]
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { size = it }) {
        if (size.width > 0) {
            for (m in state.config.masks) MaskView(m, size, clean, state)
        }
    }
}

@Composable
fun MaskView(mask: com.listapp.domain.Mask, size: IntSize, clean: ImageBitmap?, state: AppState? = null) {
    val density = LocalDensity.current
    val r = mask.rect
    val wPx = (r.w * size.width).roundToInt().coerceAtLeast(1)
    val hPx = (r.h * size.height).roundToInt().coerceAtLeast(1)
    val xPx = (r.x * size.width).roundToInt()
    val yPx = (r.y * size.height).roundToInt()
    val layersMode = state != null && state.config.mode != ScreenMode.FULL
    Box(
        Modifier
            .offset { IntOffset(xPx, yPx) }
            .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() }),
        contentAlignment = Alignment.Center,
    ) {
        if (mask.useClean && layersMode) {
            // Modo capas: se rellena con el trozo del fondo de pantalla que hay detrás.
            CropOf(size, xPx, yPx) { WallpaperLayer(state!!) }
        } else if (mask.useClean && clean != null) {
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

// ------------------------------------------------------------------ Modo capas

/**
 * Muestra, recortado a la caja actual, el trozo (xPx, yPx) de un contenido pensado para la pantalla entera.
 * Sirve para rellenar una máscara con el fondo o dibujar una zona fija.
 */
@Composable
private fun CropOf(full: IntSize, xPx: Int, yPx: Int, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Box(
            Modifier.layout { measurable, constraints ->
                // El contenido mide la pantalla entera y se coloca desplazado para que se vea justo este trozo.
                val placeable = measurable.measure(Constraints.fixed(full.width, full.height))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-xPx, -yPx) }
            },
        ) { content() }
    }
}

/** Fondo de pantalla fijo (modo capas): «rellenar pantalla» con zoom y desplazamiento configurables. */
@Composable
fun WallpaperLayer(state: AppState, modifier: Modifier = Modifier.fillMaxSize()) {
    val img = state.images[Slots.WALL]
    val cfg = state.config
    if (img == null) {
        Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF1B2A41), Color(0xFF0B1320)))))
        return
    }
    Canvas(modifier.clipToBounds()) {
        val scale = max(size.width / img.width, size.height / img.height) * cfg.wallpaperZoom
        val dw = img.width * scale
        val dh = img.height * scale
        val ox = (size.width - dw) / 2f + cfg.wallpaperX * size.width
        val oy = (size.height - dh) / 2f + cfg.wallpaperY * size.height
        drawImage(
            img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** Capa de iconos de una página (con transparencia). */
@Composable
fun LayerPage(state: AppState, page: Int, modifier: Modifier = Modifier.fillMaxSize()) {
    val img = state.layers[page]
    if (img != null) {
        Image(img, null, modifier, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Medium)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Pantalla ${page + 1}\n" + (state.layerStatus[page] ?: "sin capa") + "\n\nConfiguración: esquina superior derecha",
                color = Color(0x99FFFFFF),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** Zonas que no se deslizan (barra superior, dock…): fondo + capa de la pantalla 1 recortados a cada zona. */
@Composable
fun FixedZonesLayer(state: AppState, modifier: Modifier = Modifier.fillMaxSize()) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(modifier.onSizeChanged { size = it }) {
        if (size.width > 0) {
            for (z in state.config.fixedZones) {
                val xPx = (z.x * size.width).roundToInt()
                val yPx = (z.y * size.height).roundToInt()
                val wPx = (z.w * size.width).roundToInt().coerceAtLeast(1)
                val hPx = (z.h * size.height).roundToInt().coerceAtLeast(1)
                Box(
                    Modifier
                        .offset { IntOffset(xPx, yPx) }
                        .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() }),
                ) {
                    CropOf(size, xPx, yPx) {
                        WallpaperLayer(state)
                        state.layers[0]?.let {
                            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Medium)
                        }
                    }
                }
            }
        }
    }
}

/** Vista previa de una página tal como se verá (para los editores): captura completa, o fondo + capa + zonas fijas. */
@Composable
fun PagePreview(state: AppState, page: Int, modifier: Modifier = Modifier.fillMaxSize()) {
    if (state.config.mode == ScreenMode.FULL) {
        PageImage(state.images[Slots.pages[page]], page, modifier)
    } else {
        Box(modifier) {
            WallpaperLayer(state)
            LayerPage(state, page)
            FixedZonesLayer(state)
        }
    }
}
