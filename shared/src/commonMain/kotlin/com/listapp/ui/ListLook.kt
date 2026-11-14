package com.listapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.listapp.domain.FontStyle
import com.listapp.domain.ListStyle
import com.listapp.domain.RowStyle
import com.listapp.domain.ThemeMode

/** El aspecto de la app de listas ya resuelto (colores definitivos, medidas…). */
class ListLook(
    val style: ListStyle,
    val colors: ColorScheme,
    val bg: Color,
    val text: Color,
    val accent: Color,
    val card: Color,
    val divider: Color,
) {
    val cards: Boolean get() = style.rows == RowStyle.CARDS
    val rowHeight: Dp get() = style.density.dp.dp
    val corner: Dp get() = style.corners.dp.dp
    val lightBackground: Boolean get() = bg.luminance() > 0.5f
}

val LocalListLook = compositionLocalOf<ListLook?> { null }

private fun colorOf(v: Long) = Color(argb(v))

/** Combina el tema (claro/oscuro/sistema) con los colores personalizados del usuario. */
@Composable
fun rememberListLook(style: ListStyle): ListLook {
    val sysDark = isSystemInDarkTheme()
    val dark = when (style.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> sysDark
    }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val bg = style.bg?.let(::colorOf) ?: base.background
    val text = style.text?.let(::colorOf) ?: base.onBackground
    val accent = style.accent?.let(::colorOf) ?: base.primary
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val card = style.card?.let(::colorOf)
        ?: if (style.bg != null || style.text != null) text.copy(alpha = 0.08f).compositeOver(bg)
        else base.surfaceVariant.copy(alpha = 0.55f).compositeOver(bg)
    val colors = base.copy(
        background = bg,
        surface = bg,
        surfaceContainerHigh = bg,
        surfaceContainerHighest = bg,
        onBackground = text,
        onSurface = text,
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.25f).compositeOver(bg),
        onPrimaryContainer = text,
        surfaceVariant = card,
        onSurfaceVariant = text.copy(alpha = 0.65f),
    )
    return ListLook(style, colors, bg, text, accent, card, text.copy(alpha = 0.15f))
}

/** Aplica el aspecto personalizado (colores, tamaño y tipo de letra) a todo lo que contiene. */
@Composable
fun ListLookTheme(look: ListLook, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val family = when (look.style.font) {
        FontStyle.SANS -> FontFamily.Default
        FontStyle.SERIF -> FontFamily.Serif
        FontStyle.MONO -> FontFamily.Monospace
    }
    CompositionLocalProvider(
        LocalListLook provides look,
        LocalDensity provides Density(density.density, density.fontScale * look.style.fontSize.scale),
    ) {
        MaterialTheme(colorScheme = look.colors) {
            Surface(Modifier.fillMaxSize(), color = look.bg, contentColor = look.text) {
                ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = family)) { content() }
            }
        }
    }
}

/** Línea fina bajo la fila cuando el estilo es «con separadores». */
fun Modifier.rowDivider(look: ListLook): Modifier =
    if (look.style.rows != RowStyle.DIVIDERS) {
        this
    } else {
        drawBehind {
            drawLine(
                look.divider,
                Offset(16.dp.toPx(), size.height - 0.5.dp.toPx()),
                Offset(size.width - 16.dp.toPx(), size.height - 0.5.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
