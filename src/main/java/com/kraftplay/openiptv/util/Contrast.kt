package com.kraftplay.openiptv.util

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private fun srgbChannel(value: Int): Double {
    val c = value / 255.0
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

fun relativeLuminance(rgb: Int): Double {
    val r = srgbChannel((rgb shr 16) and 0xFF)
    val g = srgbChannel((rgb shr 8) and 0xFF)
    val b = srgbChannel(rgb and 0xFF)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** WCAG contrast ratio for opaque 0xRRGGBB colors. */
fun contrastRatio(foreground: Int, background: Int): Double {
    val a = relativeLuminance(foreground)
    val b = relativeLuminance(background)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

/** Alpha-composite foreground over background, returning opaque 0xRRGGBB. */
fun compositeOver(foreground: Int, background: Int, alpha: Float): Int {
    val a = alpha.coerceIn(0f, 1f)
    fun mix(shift: Int): Int {
        val f = (foreground shr shift) and 0xFF
        val b = (background shr shift) and 0xFF
        return (f * a + b * (1 - a)).roundToInt().coerceIn(0, 255)
    }
    return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}
