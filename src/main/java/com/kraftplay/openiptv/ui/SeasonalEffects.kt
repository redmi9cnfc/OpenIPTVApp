package com.kraftplay.openiptv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Decorative, non-interactive seasonal overlay. It does not allocate images or assets. */
@Composable
fun SeasonalEffects(themeMode: String) {
    if (themeMode != "Halloween" && themeMode != "Winter") return
    val transition = rememberInfiniteTransition(label = "seasonal-effects")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (themeMode == "Winter") 9000 else 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "seasonal-progress"
    )
    Canvas(Modifier.fillMaxSize()) {
        if (themeMode == "Winter") {
            val snow = Color(0xD9DDF5FF)
            for (i in 0 until 150) {
                val xBase = ((i * 73.7f) % size.width)
                val yBase = ((i * 97.3f) % size.height)
                val y = (yBase + progress * (size.height + 30f)) % (size.height + 30f) - 15f
                val drift = sin((progress * 2.0 * PI) + i.toDouble()) * 13.0
                val radius = if (i % 5 == 0) 2.7f else 1.5f
                drawCircle(snow, radius, Offset((xBase + drift.toFloat()).coerceIn(0f, size.width), y))
                if (i % 3 == 0) {
                    drawLine(snow.copy(alpha = 0.6f), Offset(xBase - 3f, y), Offset(xBase + 3f, y), 0.8f)
                    drawLine(snow.copy(alpha = 0.6f), Offset(xBase, y - 3f), Offset(xBase, y + 3f), 0.8f)
                }
            }
        } else {
            // Fine web strands in the upper corners, plus small animated spiders.
            val web = Color(0x66D5B5E8)
            for (corner in 0..1) {
                val cx = if (corner == 0) 0f else size.width
                val inward = if (corner == 0) 1f else -1f
                val cy = 0f
                for (ray in 0..12) {
                    val angle = (ray * 12.0 - 42.0) * PI / 180.0
                    val endX = cx + inward * cos(angle).toFloat() * 180f
                    val endY = cy + sin(angle).toFloat() * 180f
                    drawLine(web, Offset(cx, cy), Offset(endX, endY), 1f)
                }
                for (ring in 1..6) {
                    val r = ring * 35f
                    val points = (0..24).map { step ->
                        val angle = (step / 24f) * (PI / 2.4).toFloat() - 0.25f
                        Offset(cx + inward * cos(angle) * r, cy + sin(angle) * r)
                    }
                    for (j in 0 until points.lastIndex) drawLine(web, points[j], points[j + 1], 0.8f)
                }
            }
            for (i in 0..7) {
                val x = (size.width * (0.18f + i * 0.115f) + sin((progress * 2 * PI + i).toFloat()) * 28f).coerceIn(12f, size.width - 12f)
                val y = (progress * (size.height + 24f) + i * size.height / 3f) % (size.height + 24f) - 12f
                val spider = Offset(x, y)
                drawLine(Color(0xCC201329), Offset(x, y - 18f), spider, 1.1f)
                drawCircle(Color(0xFF201329), 3.5f, spider)
                drawCircle(Color(0xFFFF8A26), 0.8f, Offset(x - 1f, y - 0.5f))
                for (side in listOf(-1f, 1f)) for (leg in 0..2) {
                    val dy = (leg - 1) * 3f
                    drawLine(Color(0xCC201329), Offset(x + side * 2f, y + dy), Offset(x + side * 6f, y + dy - 2f), 0.8f)
                }
            }
        }
    }
}
