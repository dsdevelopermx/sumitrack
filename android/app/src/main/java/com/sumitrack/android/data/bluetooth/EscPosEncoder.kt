package com.sumitrack.android.data.bluetooth

import android.graphics.Bitmap
import android.graphics.Color

// Convierte un Bitmap a monocromo (umbral de luminancia) empacado 8 píxeles por byte, envuelto en
// el comando ESC/POS "GS v 0" (impresión raster) — comando estándar soportado por la inmensa
// mayoría de impresoras térmicas Bluetooth compatibles ESC/POS (ver Dev Notes de la historia).
fun bitmapToEscPosRaster(bitmap: Bitmap): ByteArray =
    packEscPosRaster(bitmap.width, bitmap.height) { x, y ->
        val pixel = bitmap.getPixel(x, y)
        (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3 < 128
    }

// Empacado puro (sin Bitmap/Color) — separado de bitmapToEscPosRaster para ser testeable en JVM
// puro (Review Finding del code review de esta historia: EscPosEncoder era el único archivo nuevo
// sin cobertura de test, precisamente porque su firma original dependía de android.graphics.Bitmap).
// Los campos de ancho/alto del comando GS v 0 son de 2 bytes (máximo 65535) — un ticket
// desproporcionadamente largo truncaría ese valor silenciosamente y produciría una imagen corrupta
// en la impresora; require() lo rechaza con un error claro en vez de eso.
fun packEscPosRaster(width: Int, height: Int, isBlackPixel: (x: Int, y: Int) -> Boolean): ByteArray {
    require(height in 1..0xFFFF) { "Alto de ticket fuera de rango para ESC/POS raster (1-65535px): $height" }
    val widthBytes = (width + 7) / 8
    require(widthBytes in 1..0xFFFF) { "Ancho de ticket fuera de rango para ESC/POS raster" }

    val header = byteArrayOf(
        0x1D, 0x76, 0x30, 0x00,
        (widthBytes and 0xFF).toByte(), ((widthBytes shr 8) and 0xFF).toByte(),
        (height and 0xFF).toByte(), ((height shr 8) and 0xFF).toByte(),
    )
    val body = ByteArray(widthBytes * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (isBlackPixel(x, y)) {
                val byteIndex = y * widthBytes + x / 8
                body[byteIndex] = (body[byteIndex].toInt() or (0x80 shr (x % 8))).toByte()
            }
        }
    }
    return header + body
}
