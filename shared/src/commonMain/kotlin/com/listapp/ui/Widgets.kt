package com.listapp.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fila que se desliza hacia la izquierda y descubre acciones (como en las apps de notas y listas del móvil).
 * - Soltar a medias deja las acciones abiertas; soltar casi a cero las cierra.
 * - Si `onFullSwipe` no es null, deslizar hasta casi el final lo ejecuta directamente.
 */
@Composable
fun SwipeRow(
    actionsWidth: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    corner: Dp = 16.dp,
    onFullSwipe: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable (close: () -> Unit, open: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val actionPx = with(density) { actionsWidth.toPx() }
    var width by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val full by rememberUpdatedState(onFullSwipe)
    val enabledNow by rememberUpdatedState(enabled)

    fun animateTo(target: Float, then: () -> Unit = {}) {
        scope.launch {
            animate(offset, target) { v, _ -> offset = v }
            then()
        }
    }

    fun settle() {
        val f = full
        when {
            f != null && offset < -width * 0.65f -> animateTo(-width) { f(); offset = 0f }
            offset < -actionPx * 0.5f -> animateTo(-actionPx)
            else -> animateTo(0f)
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .onSizeChanged { width = it.width.toFloat() },
    ) {
        if (offset < -1f) {
            Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
                Row(Modifier.width(with(density) { max(actionPx, -offset).toDp() }).fillMaxHeight(), content = actions)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                // Siempre presente: quitar/poner este detector a mitad de un gesto cancelaría los gestos de los hijos
                // (p. ej. el de mantener pulsado para mover). Se activa o no por dentro.
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = ::settle, onDragCancel = ::settle) { change, dx ->
                        if (enabledNow) {
                            change.consume()
                            offset = (offset + dx).coerceIn(-width, 0f)
                        }
                    }
                },
        ) { content({ animateTo(0f) }, offset < -1f) }
    }
}

/**
 * Tras mantener el dedo quieto `holdMs` se activa el modo «mover»: `onStart`, luego `onDrag(dy)` con cada movimiento vertical
 * y `onEnd` al soltar. Si el dedo se mueve antes de tiempo (scroll, deslizar) no hace nada.
 */
fun Modifier.longPressReorder(
    key: Any?,
    holdMs: Long,
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: () -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val held = withTimeoutOrNull<Boolean>(holdMs) {
            var ok = true
            while (ok) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id }
                ok = c != null && c.pressed && !c.isConsumed && (c.position - down.position).getDistance() <= slop
            }
            false
        }
        if (held == null) {
            onStart()
            try {
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed || c.changedToUp()) {
                        ev.changes.forEach { it.consume() }
                        break
                    }
                    val dy = c.positionChange().y
                    ev.changes.forEach { it.consume() }
                    if (dy != 0f) onDrag(dy)
                }
            } finally {
                // Siempre se cierra el movimiento (también si el gesto se cancela), para guardar y no dejar la fila «agarrada».
                onEnd()
            }
        }
    }
}
