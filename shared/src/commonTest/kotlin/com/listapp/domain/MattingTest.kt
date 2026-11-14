package com.listapp.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MattingTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun over(fr: Int, fg: Int, fb: Int, alpha: Float, br: Int, bg: Int, bb: Int) = argb(
        255,
        (alpha * fr + (1 - alpha) * br + 0.5f).toInt(),
        (alpha * fg + (1 - alpha) * bg + 0.5f).toInt(),
        (alpha * fb + (1 - alpha) * bb + 0.5f).toInt(),
    )

    /** 100 píxeles: 60 de fondo, 20 opacos, 10 semitransparentes (0.5) y 10 de borde (0.25). */
    private fun scene(bgR: Int, bgG: Int, bgB: Int): IntArray {
        val alphas = FloatArray(100) { if (it < 60) 0f else if (it < 80) 1f else if (it < 90) 0.5f else 0.25f }
        return IntArray(100) { over(200, 40, 90, alphas[it], bgR, bgG, bgB) }
    }

    @Test fun dominantColorFindsBackground() {
        assertEquals(0x000000, Matting.dominantColor(scene(0, 0, 0)))
        assertEquals(0xFFFFFF, Matting.dominantColor(scene(255, 255, 255)))
    }

    @Test fun blackWhiteRecoversAlphaAndColor() {
        val out = Matting.blackWhite(scene(0, 0, 0), scene(255, 255, 255))
        for (i in 0 until 60) assertEquals(0, out[i] ushr 24, "fondo $i")
        for (i in 60 until 80) {
            assertEquals(255, out[i] ushr 24)
            assertEquals(200, (out[i] ushr 16) and 255)
        }
        for (i in 80 until 90) {
            assertTrue(abs((out[i] ushr 24) - 128) <= 2, "alpha 0.5")
            assertTrue(abs(((out[i] ushr 16) and 255) - 200) <= 3, "color recuperado")
        }
        for (i in 90 until 100) assertTrue(abs((out[i] ushr 24) - 64) <= 2, "alpha 0.25")
    }

    @Test fun blackWhiteWorksWithNonPureBackgrounds() {
        val out = Matting.blackWhite(scene(20, 10, 15), scene(235, 240, 230))
        assertEquals(0, out[0] ushr 24)
        assertEquals(255, out[70] ushr 24)
        assertTrue(abs((out[85] ushr 24) - 128) <= 3)
    }

    @Test fun layerCodecRoundTrips() {
        val px = intArrayOf(argb(255, 1, 2, 3), argb(0, 0, 0, 0), argb(128, 250, 251, 252), -1, 0, 7)
        val d = assertNotNull(Matting.decodeLayer(Matting.encodeLayer(3, 2, px)))
        assertEquals(3, d.first)
        assertEquals(2, d.second)
        assertEquals(px.toList(), d.third.toList())
        assertNull(Matting.decodeLayer(ByteArray(3)))
    }
}
