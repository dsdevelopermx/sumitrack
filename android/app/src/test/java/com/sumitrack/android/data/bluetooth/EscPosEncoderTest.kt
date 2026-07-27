package com.sumitrack.android.data.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EscPosEncoderTest {

    @Test
    fun `header encodes GS v 0 command with correct width bytes and height`() {
        val result = packEscPosRaster(width = 16, height = 3) { _, _ -> false }

        assertEquals(0x1D, result[0].toInt() and 0xFF)
        assertEquals(0x76, result[1].toInt() and 0xFF)
        assertEquals(0x30, result[2].toInt() and 0xFF)
        assertEquals(0x00, result[3].toInt() and 0xFF)
        assertEquals(2, result[4].toInt()) // 16 px / 8 = 2 bytes de ancho
        assertEquals(0, result[5].toInt())
        assertEquals(3, result[6].toInt()) // alto = 3
        assertEquals(0, result[7].toInt())
    }

    @Test
    fun `body length equals widthBytes times height`() {
        val result = packEscPosRaster(width = 17, height = 5) { _, _ -> false }
        val widthBytes = 3 // (17 + 7) / 8
        assertEquals(8 + widthBytes * 5, result.size)
    }

    @Test
    fun `a single black pixel sets exactly one bit in the correct byte`() {
        // Pixel negro en x=9 (byte 1, bit 6 desde la izquierda -> 0x80 shr (9 % 8) = 0x80 shr 1 = 0x40)
        val result = packEscPosRaster(width = 16, height = 1) { x, y -> x == 9 && y == 0 }

        val widthBytes = 2
        val bodyStart = 8
        assertEquals(0x00, result[bodyStart].toInt() and 0xFF)
        assertEquals(0x40, result[bodyStart + 1].toInt() and 0xFF)
        assertEquals(8 + widthBytes, result.size)
    }

    @Test
    fun `all-white image produces an all-zero body`() {
        val result = packEscPosRaster(width = 24, height = 2) { _, _ -> false }
        val body = result.copyOfRange(8, result.size)
        assertEquals(true, body.all { it == 0.toByte() })
    }

    @Test
    fun `height of zero throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            packEscPosRaster(width = 8, height = 0) { _, _ -> false }
        }
    }

    @Test
    fun `height above the 65535 raster limit throws instead of silently truncating`() {
        assertThrows(IllegalArgumentException::class.java) {
            packEscPosRaster(width = 8, height = 0x10000) { _, _ -> false }
        }
    }

    @Test
    fun `height exactly at the 65535 limit does not throw`() {
        // No se materializa el body completo (64K*1 = 64KB, aceptable) para validar el límite exacto.
        packEscPosRaster(width = 8, height = 0xFFFF) { _, _ -> false }
    }
}
