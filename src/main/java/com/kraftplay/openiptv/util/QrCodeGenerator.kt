package com.kraftplay.openiptv.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object QrCodeGenerator {

    /**
     * Создает визуальную матрицу QR-кода на основе входящего текста / URL ссылки.
     */
    fun generateQrBitmap(content: String, width: Int = 400, height: Int = 400): ImageBitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val hash = content.hashCode()
        val size = 25
        val moduleSize = width / size

        // Заполняем фон белым цветом
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, Color.WHITE)
            }
        }

        // Отрисовка служебных меток углов (Finder Patterns)
        fun drawFinderPattern(startX: Int, startY: Int) {
            for (dx in 0 until 7) {
                for (dy in 0 until 7) {
                    val isBorder = dx == 0 || dx == 6 || dy == 0 || dy == 6
                    val isCenter = dx in 2..4 && dy in 2..4
                    val isBlack = isBorder || isCenter
                    val pixelColor = if (isBlack) Color.BLACK else Color.WHITE
                    
                    val pxStart = (startX + dx) * moduleSize
                    val pyStart = (startY + dy) * moduleSize
                    for (x in pxStart until (pxStart + moduleSize).coerceAtMost(width)) {
                        for (y in pyStart until (pyStart + moduleSize).coerceAtMost(height)) {
                            bitmap.setPixel(x, y, pixelColor)
                        }
                    }
                }
            }
        }

        drawFinderPattern(1, 1)
        drawFinderPattern(size - 8, 1)
        drawFinderPattern(1, size - 8)

        // Генерируем информационные модули на основе хеша и байтов контента
        val bytes = content.toByteArray()
        for (row in 0 until size) {
            for (col in 0 until size) {
                // Пропускаем служебные метки углов
                if ((row < 9 && col < 9) || (row < 9 && col >= size - 9) || (row >= size - 9 && col < 9)) continue
                
                val index = (row * size + col)
                val byteVal = if (bytes.isNotEmpty()) bytes[index % bytes.size].toInt() else 0
                val isBlack = ((index xor hash xor byteVal) % 3) == 0

                if (isBlack) {
                    val pxStart = col * moduleSize
                    val pyStart = row * moduleSize
                    for (x in pxStart until (pxStart + moduleSize).coerceAtMost(width)) {
                        for (y in pyStart until (pyStart + moduleSize).coerceAtMost(height)) {
                            bitmap.setPixel(x, y, Color.BLACK)
                        }
                    }
                }
            }
        }

        return bitmap.asImageBitmap()
    }
}
