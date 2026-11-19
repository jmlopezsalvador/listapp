package com.listapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import com.listapp.domain.NRect
import com.listapp.domain.IconBoxMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
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
import kotlinx.coroutines.launch
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
    val buzz = rememberBuzzer()
    val buzzNow by rememberUpdatedState(buzz)
    val hapticScope = androidx.compose.runtime.rememberCoroutineScope()

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

    // Dígito candidato de cada página (0 = decenas, 1 = unidades): se decide al poner el dedo (dónde cae) y se confirma
    // en cuanto la página cambia. Así no depende de que la animación termine ni de que el siguiente deslizamiento
    // (si se hace enseguida) pise la posición del anterior. -1 = ninguno.
    val candidates = remember { IntArray(2) { -1 } }
    LaunchedEffect(pager) {
        var prev = pager.currentPage
        snapshotFlow { pager.currentPage }.collect { cur ->
            if (cur == prev + 1 && prev in 0..1) {
                val d = candidates[prev]
                if (d >= 0) {
                    if (prev == 0) state.tens = d else state.units = d
                    buzzNow(1) // confirma que el dígito quedó fijado
                } else if (cfg.outsideMode == OutsideMode.BLOCK) {
                    pager.scrollToPage(prev)
                    return@collect
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
                    if (downPage in 0..1) {
                        val hit = hitIndex(downPage, down.position)
                        candidates[downPage] = when {
                            hit >= 0 -> digitOfIndex(hit)
                            cfg.outsideMode == OutsideMode.ZERO -> 0
                            else -> -1
                        }
                    }
                    if (blocked) down.consume()
                    val p = down.position
                    val s = sizeNow
                    // Botones ocultos de reset y configuración: hay que mantener el dedo quieto dentro de su caja el tiempo configurado.
                    val corner = when {
                        s.width <= 0 -> 0
                        cfg.resetBox.contains(p.x / s.width, p.y / s.height) -> 1
                        cfg.configBox.contains(p.x / s.width, p.y / s.height) -> 2
                        else -> 0
                    }
                    val holdMs = (if (corner == 1) cfg.resetHoldMs else cfg.configHoldMs).toLong()
                    var moved = false
                    if (corner != 0) {
                        val r = withTimeoutOrNull<Int>(holdMs) {
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
                                // Doble vibración y, justo después, la acción (el gesto no puede suspenderse aquí).
                                val isReset = corner == 1
                                buzz(2)
                                hapticScope.launch {
                                    kotlinx.coroutines.delay(130)
                                    if (isReset) state.reset() else state.screen = Screen.Config
                                }
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
                    FullPage(state, page, showHint = true)
                    MaskLayer(state, Modifier.fillMaxSize())
                }
            }
        } else {
            // Modo capas: el fondo y las máscaras quedan fijos; solo se desliza la capa de iconos.
            WallpaperLayer(state)
            HorizontalPager(pager, Modifier.fillMaxSize()) { page -> LayerPage(state, page, showHint = true) }
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
                "Pantalla ${page + 1}\nsin captura",
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

/** Capa de iconos de una página (con transparencia). En la pantalla 3 con la caja en modo «Icono», abre un hueco y dibuja el icono. */
@Composable
fun LayerPage(state: AppState, page: Int, modifier: Modifier = Modifier.fillMaxSize(), showHint: Boolean = false) {
    val img = state.layers[page]
    val box = state.config.iconBox
    val withIcon = page == 2 && state.config.iconMode == IconBoxMode.ICON && box != null
    if (img != null) {
        // Offscreen: el «borrado» solo afecta a esta capa y deja ver el fondo de pantalla de verdad (también al deslizar).
        Box(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Medium)
            if (withIcon && box != null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        Color.Black,
                        topLeft = Offset(box.x * size.width, box.y * size.height),
                        size = androidx.compose.ui.geometry.Size(box.w * size.width, box.h * size.height),
                        blendMode = BlendMode.Clear,
                    )
                }
                CenteredIcon(state.config.iconId, state.config.iconScale, box)
            }
        }
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Pantalla ${page + 1}\n" + (state.layerStatus[page] ?: "sin capa"),
                color = Color(0x99FFFFFF),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (showHint) ConfigHint(state)
        }
    }
}

/** Una página en modo pantalla completa: la captura y, en la pantalla 3 con la caja en modo «Icono», el icono encima. */
@Composable
fun FullPage(state: AppState, page: Int, modifier: Modifier = Modifier.fillMaxSize(), showHint: Boolean = false) {
    val img = state.images[Slots.pages[page]]
    val box = state.config.iconBox
    Box(modifier) {
        PageImage(img, page)
        if (img == null) {
            if (showHint) ConfigHint(state)
        } else if (page == 2 && state.config.iconMode == IconBoxMode.ICON && box != null) {
            FractionBox(box) { IconBackdrop(state, img, box) }
            CenteredIcon(state.config.iconId, state.config.iconScale, box)
        }
    }
}

/** Una caja colocada por fracciones de la pantalla (0..1) dentro de un contenedor que ocupa toda la pantalla. */
@Composable
fun FractionBox(rect: NRect, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier.offset(maxWidth * rect.x, maxHeight * rect.y).size(maxWidth * rect.w, maxHeight * rect.h),
            content = content,
        )
    }
}

/** Tamaño normal del icono: fracción del ancho de pantalla (≈ lo que mide un icono en un móvil). */
const val ICON_BASE_WIDTH_FRACTION = 0.17f

/**
 * El icono en el centro de la caja, cuadrado, con su tamaño (ancho de pantalla × base × zoom) y sin deformarse
 * aunque la caja sea más grande o más pequeña. La caja solo manda en la posición y en lo que se tapa.
 */
@Composable
fun CenteredIcon(id: String, scale: Float, box: NRect) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth * ICON_BASE_WIDTH_FRACTION * scale
        val cx = maxWidth * (box.x + box.w / 2f)
        val cy = maxHeight * (box.y + box.h / 2f)
        Box(Modifier.offset(cx - side / 2f, cy - side / 2f).size(side)) { IconImage(id) }
    }
}

/** El icono elegido, con esquinas redondeadas (22 %) como los de un móvil. */
@Composable
fun IconImage(id: String, modifier: Modifier = Modifier.fillMaxSize()) {
    // si el id ya no existe (p. ej. un icono retirado) se usa el primero de la lista
    val img = remember(id) { FakeIcons.image(id) ?: FakeIcons.all.firstOrNull()?.let { FakeIcons.image(it.id) } } ?: return
    BoxWithConstraints(modifier) {
        Image(
            img,
            null,
            Modifier.fillMaxSize().clip(RoundedCornerShape(minOf(maxWidth, maxHeight) * 0.22f)),
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** Lo que tapa el icono original de la captura: el mismo trozo de la «imagen limpia» o, sin ella, el color de al lado. */
@Composable
private fun IconBackdrop(state: AppState, page: ImageBitmap, box: NRect) {
    val clean = state.images[Slots.CLEAN]
    if (clean != null) {
        Canvas(Modifier.fillMaxSize()) {
            drawImage(
                clean,
                srcOffset = IntOffset((box.x * clean.width).roundToInt(), (box.y * clean.height).roundToInt()),
                srcSize = IntSize(
                    (box.w * clean.width).roundToInt().coerceAtLeast(1),
                    (box.h * clean.height).roundToInt().coerceAtLeast(1),
                ),
                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.Medium,
            )
        }
    } else {
        val color = remember(page, box) {
            val px = ((box.x * page.width).toInt() - 3).coerceIn(0, page.width - 1)
            val py = (box.y * page.height).toInt().coerceIn(0, page.height - 1)
            val buf = IntArray(1)
            page.readPixels(buf, px, py, 1, 1)
            Color(buf[0] or (0xFF shl 24))
        }
        Box(Modifier.fillMaxSize().background(color))
    }
}

/** Aviso de «app nueva»: enseña dónde está el botón oculto de configuración y cómo usarlo. */
@Composable
fun ConfigHint(state: AppState) {
    val box = state.config.configBox
    Box(Modifier.fillMaxSize()) {
        FractionBox(box) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    Color(0xCCFFFFFF),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            }
            Text(
                "Configuración",
                Modifier.align(Alignment.Center),
                color = Color.White,
                fontSize = 10.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Column(
            Modifier.align(Alignment.Center).offset(y = 110.dp).padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Para empezar, mantén pulsado el recuadro «Configuración» durante ${seconds(state.config.configHoldMs)}",
                color = Color(0xCCFFFFFF),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(onClick = { state.showGuide = true }) { Text("Guía") }
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
        FullPage(state, page, modifier)
    } else {
        Box(modifier) {
            WallpaperLayer(state)
            LayerPage(state, page)
            FixedZonesLayer(state)
        }
    }
}
