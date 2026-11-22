package com.kraftplay.openiptv.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap

object QrCodeGenerator {

    /**
     * Создает полноценный стандартный QR-код с использованием ZXing QRCodeWriter.
     */
    fun generateQrBitmapAndroid(content: String, width: Int = 512, height: Int = 512): Bitmap {
        if (content.isBlank()) {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
        }
        val safeContent = if (content.length > 2500) content.take(2500) else content
        return try {
            encodeQr(safeContent, width, height)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                encodeQr(content.take(1500), width, height)
            } catch (e2: Exception) {
                e2.printStackTrace()
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.WHITE)
                }
            }
        }
    }

    private fun encodeQr(text: String, width: Int, height: Int): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1)
        }
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints)
        val matrixWidth = bitMatrix.width
        val matrixHeight = bitMatrix.height
        val pixels = IntArray(matrixWidth * matrixHeight)
        for (y in 0 until matrixHeight) {
            val offset = y * matrixWidth
            for (x in 0 until matrixWidth) {
                pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
        }
    }

    fun generateQrBitmap(content: String, width: Int = 512, height: Int = 512): ImageBitmap {
        return generateQrBitmapAndroid(content, width, height).asImageBitmap()
    }
}
