package com.listapp.domain

import kotlin.math.abs

/**
 * Convierte capturas del escritorio en una capa con transparencia (para mover los iconos sobre un fondo fijo).
 * Los píxeles son enteros ARGB (0xAARRGGBB), sin premultiplicar. Lógica pura, sin dependencias de plataforma.
 */
object Matting {
    private fun r(p: Int) = (p ushr 16) and 255
    private fun g(p: Int) = (p ushr 8) and 255
    private fun b(p: Int) = p and 255
    private fun pack(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun clamp255(v: Float) = if (v < 0f) 0 else if (v > 255f) 255 else (v + 0.5f).toInt()

    /** Color RGB más repetido (muestreando): es el color liso del fondo de pantalla. Devuelve 0xRRGGBB. */
    fun dominantColor(px: IntArray): Int {
        if (px.isEmpty()) return 0
        val step = maxOf(1, px.size / 40000)
        val counts = HashMap<Int, Int>()
        var best = px[0] and 0xFFFFFF
        var bestN = 0
        var i = 0
        while (i < px.size) {
            val c = px[i] and 0xFFFFFF
            val n = (counts[c] ?: 0) + 1
            counts[c] = n
            if (n > bestN) {
                bestN = n
                best = c
            }
            i += step
        }
        return best
    }

    /**
     * Dos capturas idénticas salvo el fondo (una con fondo oscuro, otra con fondo claro).
     * Recupera la transparencia exacta de cada píxel: bordes suaves, sombras y cristal incluidos.
     * Los colores de fondo reales se detectan solos (no hace falta que sean negro y blanco puros).
     */
    fun blackWhite(dark: IntArray, light: IntArray): IntArray {
        require(dark.size == light.size) { "Las dos capturas deben tener el mismo tamaño" }
        val kd = dominantColor(dark)
        val kl = dominantColor(light)
        val bg = floatArrayOf(r(kd).toFloat(), g(kd).toFloat(), b(kd).toFloat())
        val wh = floatArrayOf(r(kl).toFloat(), g(kl).toFloat(), b(kl).toFloat())
        val den = FloatArray(3) { maxOf(1f, wh[it] - bg[it]) }
        // Solo se usan los canales donde hay contraste suficiente entre los dos fondos.
        val use = BooleanArray(3) { den[it] >= 40f }
        if (use.none { it }) for (c in 0..2) use[c] = true
        val nUse = use.count { it }

        val out = IntArray(dark.size)
        val dc = FloatArray(3)
        val lc = FloatArray(3)
        for (i in dark.indices) {
            val d = dark[i]
            val l = light[i]
            dc[0] = r(d).toFloat(); dc[1] = g(d).toFloat(); dc[2] = b(d).toFloat()
            lc[0] = r(l).toFloat(); lc[1] = g(l).toFloat(); lc[2] = b(l).toFloat()
            var sum = 0f
            for (c in 0..2) if (use[c]) sum += (lc[c] - dc[c]) / den[c]
            val alpha = (1f - sum / nUse).coerceIn(0f, 1f)
            if (alpha < 1f / 255f) {
                out[i] = 0
                continue
            }
            // sobre el fondo oscuro: píxel = alpha*F + (1-alpha)*bg  →  F = (píxel - (1-alpha)*bg) / alpha
            out[i] = pack(
                clamp255(alpha * 255f),
                clamp255((dc[0] - (1f - alpha) * bg[0]) / alpha),
                clamp255((dc[1] - (1f - alpha) * bg[1]) / alpha),
                clamp255((dc[2] - (1f - alpha) * bg[2]) / alpha),
            )
        }
        return out
    }

    /**
     * Una sola captura sobre un fondo de color liso (se detecta solo). Lo parecido al fondo pasa a transparente y
     * los bordes se aclaran quitando el tinte del color de fondo. Menos exacto que `blackWhite`.
     */
    fun colorKey(img: IntArray, low: Int = 8, high: Int = 48): IntArray {
        val k = dominantColor(img)
        val kr = r(k)
        val kg = g(k)
        val kb = b(k)
        val range = (high - low).toFloat()
        val out = IntArray(img.size)
        for (i in img.indices) {
            val p = img[i]
            val pr = r(p)
            val pg = g(p)
            val pb = b(p)
            val dist = maxOf(abs(pr - kr), abs(pg - kg), abs(pb - kb))
            val alpha = ((dist - low) / range).coerceIn(0f, 1f)
            if (alpha <= 0f) {
                out[i] = 0
                continue
            }
            out[i] = if (alpha >= 1f) {
                pack(255, pr, pg, pb)
            } else {
                // quita el tinte del fondo: F = K + (I - K) / alpha
                pack(
                    clamp255(alpha * 255f),
                    clamp255(kr + (pr - kr) / alpha),
                    clamp255(kg + (pg - kg) / alpha),
                    clamp255(kb + (pb - kb) / alpha),
                )
            }
        }
        return out
    }

    /** Formato del fichero de caché de una capa: ancho y alto (Int, 4 bytes cada uno) y luego los píxeles ARGB. */
    fun encodeLayer(w: Int, h: Int, px: IntArray): ByteArray {
        val out = ByteArray(8 + px.size * 4)
        putInt(out, 0, w)
        putInt(out, 4, h)
        for (i in px.indices) putInt(out, 8 + i * 4, px[i])
        return out
    }

    /** Inverso de `encodeLayer`. Devuelve null si el fichero está dañado. */
    fun decodeLayer(bytes: ByteArray): Triple<Int, Int, IntArray>? {
        if (bytes.size < 8) return null
        val w = getInt(bytes, 0)
        val h = getInt(bytes, 4)
        if (w <= 0 || h <= 0 || bytes.size != 8 + w * h * 4) return null
        val px = IntArray(w * h) { getInt(bytes, 8 + it * 4) }
        return Triple(w, h, px)
    }

    private fun putInt(a: ByteArray, o: Int, v: Int) {
        a[o] = (v ushr 24).toByte()
        a[o + 1] = (v ushr 16).toByte()
        a[o + 2] = (v ushr 8).toByte()
        a[o + 3] = v.toByte()
    }

    private fun getInt(a: ByteArray, o: Int) =
        ((a[o].toInt() and 255) shl 24) or ((a[o + 1].toInt() and 255) shl 16) or
            ((a[o + 2].toInt() and 255) shl 8) or (a[o + 3].toInt() and 255)
}
