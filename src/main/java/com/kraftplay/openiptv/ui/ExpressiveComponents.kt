package com.kraftplay.openiptv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent

// ============================================================================
// MATERIAL 3 EXPRESSIVE SETTINGS COMPONENTS
// Modern, animated, customizable settings UI based on Material 3 Expressive
// ============================================================================

enum class ShapeProfile { 
    SINE, CLOVER, BURST, SCALLOP, PENTAGON, HEXAGON, OCTAGON, STAR, SUNNY, PILL, TEARDROP, DIAMOND, SQUARE, CIRCLE 
}

data class WavySpec(
    val profile: ShapeProfile = ShapeProfile.SINE,
    val waves: Int = 10,
    val amplitudeFraction: Float = 0.08f
)

fun parseShapeProfile(name: String): ShapeProfile = when (name.lowercase()) {
    "clover", "клевер" -> ShapeProfile.CLOVER
    "burst", "вспышка" -> ShapeProfile.BURST
    "scallop", "фестон", "волна" -> ShapeProfile.SCALLOP
    "pentagon", "пятиугольник" -> ShapeProfile.PENTAGON
    "hexagon", "шестиугольник" -> ShapeProfile.HEXAGON
    "octagon", "восьмиугольник" -> ShapeProfile.OCTAGON
    "star", "звезда" -> ShapeProfile.STAR
    "sunny", "солнце", "солнышко" -> ShapeProfile.SUNNY
    "pill", "капсула" -> ShapeProfile.PILL
    "teardrop", "капля" -> ShapeProfile.TEARDROP
    "diamond", "ромб" -> ShapeProfile.DIAMOND
    "square", "квадрат" -> ShapeProfile.SQUARE
    "circle", "круг" -> ShapeProfile.CIRCLE
    else -> ShapeProfile.SINE
}

@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
    strokeWidth: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressiveLoader")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loaderRot"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loaderWave"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loaderPulse"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val strokePx = strokeWidth.toPx()
            val baseRadius = (min(this.size.width, this.size.height) - strokePx) / 2f
            val numWaves = 8
            val maxAmp = baseRadius * 0.12f * (0.5f + 0.5f * sin(wavePhase))

            // Background Track Circle
            drawCircle(
                color = trackColor,
                radius = baseRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )

            // Wavy Morphing Animated Path
            val path = Path()
            val steps = 100
            for (i in 0..steps) {
                val angleDeg = (i * 360f / steps) + rotation
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val waveOffset = sin(angleRad * numWaves + wavePhase) * maxAmp
                val r = baseRadius + waveOffset
                val x = center.x + (r * cos(angleRad)).toFloat()
                val y = center.y + (r * sin(angleRad)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            drawPath(
                path = path,
                color = color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }

        // Center Pulsing TV / Play Icon
        Box(
            modifier = Modifier
                .size(size * 0.45f)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(size * 0.28f)
            )
        }
    }
}

@Composable
fun ExpressiveAmbientCanvas(enabled: Boolean = true) {
    if (!enabled) return
    val infiniteTransition = rememberInfiniteTransition(label = "ambientCanvas")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientPhase"
    )
    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
    val secondaryColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f)

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val center1 = Offset(
                    x = size.width * (0.3f + 0.2f * cos(Math.toRadians(phase.toDouble())).toFloat()),
                    y = size.height * (0.2f + 0.15f * sin(Math.toRadians(phase.toDouble())).toFloat())
                )
                val center2 = Offset(
                    x = size.width * (0.7f - 0.2f * sin(Math.toRadians(phase.toDouble())).toFloat()),
                    y = size.height * (0.8f - 0.15f * cos(Math.toRadians(phase.toDouble())).toFloat())
                )
                drawCircle(color = primaryColor, radius = size.width * 0.45f, center = center1)
                drawCircle(color = secondaryColor, radius = size.width * 0.5f, center = center2)
            }
    )
}

fun Modifier.wavyBackground(
    spec: WavySpec,
    color: Color,
    rotationProvider: () -> Float = { 0f }
): Modifier = this.drawBehind {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f
    val rot = rotationProvider()
    val maxAmp = radius * spec.amplitudeFraction

    if (spec.profile == ShapeProfile.CIRCLE) {
        drawCircle(color = color, radius = radius, center = center)
        return@drawBehind
    }

    rotate(rot, center) {
        when (spec.profile) {
            ShapeProfile.SQUARE -> {
                val corner = radius * 0.45f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    cornerRadius = CornerRadius(corner, corner)
                )
            }
            ShapeProfile.PILL -> {
                val corner = radius * 0.85f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(center.x - radius, center.y - radius * 0.75f),
                    size = Size(radius * 2f, radius * 1.5f),
                    cornerRadius = CornerRadius(corner, corner)
                )
            }
            ShapeProfile.DIAMOND, ShapeProfile.PENTAGON, ShapeProfile.HEXAGON, ShapeProfile.OCTAGON, ShapeProfile.STAR, ShapeProfile.BURST -> {
                val sides = when (spec.profile) {
                    ShapeProfile.DIAMOND -> 4
                    ShapeProfile.PENTAGON -> 5
                    ShapeProfile.HEXAGON -> 6
                    ShapeProfile.OCTAGON -> 8
                    ShapeProfile.STAR -> 10
                    ShapeProfile.BURST -> 24
                    else -> 4
                }
                val path = Path()
                for (i in 0 until sides) {
                    val angleDeg = (i * 360f / sides) - 90f
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val r = if (spec.profile == ShapeProfile.STAR && i % 2 != 0) radius * 0.5f
                            else if (spec.profile == ShapeProfile.BURST && i % 2 != 0) radius * 0.75f
                            else radius
                    val x = center.x + (r * cos(angleRad)).toFloat()
                    val y = center.y + (r * sin(angleRad)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color)
            }
            ShapeProfile.TEARDROP -> {
                val path = Path()
                path.moveTo(center.x, center.y - radius)
                path.cubicTo(
                    center.x + radius, center.y - radius * 0.5f,
                    center.x + radius, center.y + radius,
                    center.x, center.y + radius
                )
                path.cubicTo(
                    center.x - radius, center.y + radius,
                    center.x - radius, center.y - radius * 0.5f,
                    center.x, center.y - radius
                )
                path.close()
                drawPath(path, color)
            }
            ShapeProfile.SINE, ShapeProfile.CLOVER, ShapeProfile.SCALLOP, ShapeProfile.SUNNY -> {
                val waves = when (spec.profile) {
                    ShapeProfile.CLOVER -> 4
                    ShapeProfile.SCALLOP -> 8
                    ShapeProfile.SUNNY -> 8
                    else -> spec.waves
                }
                val amp = if (spec.profile == ShapeProfile.SUNNY) radius * 0.18f else maxAmp
                val path = Path()
                val steps = 120
                for (i in 0..steps) {
                    val angleDeg = i * 360f / steps
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val waveOffset = sin(angleRad * waves) * amp
                    val r = radius - amp + waveOffset
                    val x = center.x + (r * cos(angleRad)).toFloat()
                    val y = center.y + (r * sin(angleRad)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color)
            }
            else -> {
                drawCircle(color = color, radius = radius, center = center)
            }
        }
    }
}

@Composable
fun HeroCard(
    ready: Boolean,
    title: String,
    subtitle: String,
    stats: List<Pair<String, String>> = emptyList(),
    badgeShape: String = "Cookie",
    badgeColorName: String = "Purple",
    rotationEnabled: Boolean = true,
    uiStyle: String = "Material3"
) {
    val cs = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "heroRotTransition")
    val rotationAngle by if (rotationEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(16000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "heroRotAngle"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val overrideContainer = getBadgeColor(badgeColorName, if (ready) cs.primary else cs.tertiary)
    val overrideContent = getBadgeContentColor(badgeColorName, if (ready) cs.onPrimary else cs.onTertiary)

    val container by animateColorAsState(
        if (ready) cs.primaryContainer else cs.tertiaryContainer,
        label = "heroBg"
    )
    val spec = remember(badgeShape) { WavySpec(parseShapeProfile(badgeShape), 10, 0.09f) }

    val activeUiStyle = if (uiStyle == "LiquidGlass" || LocalUiStyle.current == "LiquidGlass") "LiquidGlass" else "Material3"

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (activeUiStyle == "LiquidGlass") container.copy(alpha = 0.35f) else container)
            .then(
                if (activeUiStyle == "LiquidGlass") {
                    Modifier.border(
                        1.dp,
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.06f))),
                        RoundedCornerShape(28.dp)
                    )
                } else Modifier
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .wavyBackground(spec, overrideContainer) { rotationAngle },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (ready) Icons.Rounded.Check else Icons.Rounded.Tv,
                    contentDescription = null,
                    tint = overrideContent,
                    modifier = Modifier.size(36.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    tr(title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (ready) cs.onPrimaryContainer else cs.onTertiaryContainer
                )
                Text(
                    tr(subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = (if (ready) cs.onPrimaryContainer else cs.onTertiaryContainer).copy(alpha = 0.85f)
                )
            }
        }

        if (stats.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stats.forEach { (label, value) ->
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(cs.surfaceBright.copy(alpha = 0.8f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "${tr(label)}: ${tr(value)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupItem(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    glowEnabled: Boolean = true,
    cornerRadiusDp: Int = 22,
    uiStyle: String = "Material3",
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        if (pressed) (cornerRadiusDp - 6).coerceAtLeast(8).dp else cornerRadiusDp.dp,
        spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "groupCorner"
    )
    val cs = MaterialTheme.colorScheme

    val glowAlpha by animateFloatAsState(
        if (pressed && glowEnabled) 0.85f else 0f,
        tween(250),
        label = "glowAlpha"
    )

    val activeUiStyle = if (uiStyle == "LiquidGlass" || LocalUiStyle.current == "LiquidGlass") "LiquidGlass" else "Material3"

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(if (activeUiStyle == "LiquidGlass") cs.surfaceContainerHigh.copy(alpha = 0.30f) else cs.surfaceContainerHigh)
            .then(
                if (activeUiStyle == "LiquidGlass") {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        ),
                        shape = RoundedCornerShape(corner)
                    )
                } else Modifier
            )
            .then(
                if (glowAlpha > 0f) {
                    Modifier.border(2.dp, cs.primary.copy(alpha = glowAlpha), RoundedCornerShape(corner))
                } else Modifier
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        content()
    }
}

fun getBadgeColor(colorName: String, fallbackContainer: Color): Color {
    return when (colorName.trim().lowercase(java.util.Locale.ROOT)) {
        "purple", "фиолетовый", "sirenevy", "сиреневый" -> Color(0xFF9C27B0)
        "cyan", "бирюзовый" -> Color(0xFF00E5FF)
        "gold", "золотой" -> Color(0xFFFFD600)
        "lime", "лаймовый" -> Color(0xFF76FF03)
        "orange", "оранжевый" -> Color(0xFFFF6D00)
        "pink", "розовый" -> Color(0xFFFF4081)
        "red", "красный" -> Color(0xFFF44336)
        "blue", "синий" -> Color(0xFF2196F3)
        "green", "зеленый" -> Color(0xFF4CAF50)
        else -> Color(0xFF9C27B0)
    }
}

fun getBadgeContentColor(colorName: String, fallbackContent: Color): Color {
    return when (colorName.trim().lowercase(java.util.Locale.ROOT)) {
        "cyan", "бирюзовый", "gold", "золотой", "lime", "лаймовый" -> Color.Black
        "purple", "фиолетовый", "orange", "оранжевый", "pink", "розовый", "red", "красный", "blue", "синий", "green", "зеленый" -> Color.White
        else -> fallbackContent
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 42.dp,
    shapeName: String = "Cookie",
    badgeColorName: String = "Purple",
    rotationEnabled: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badgeRotation")
    val angle by if (rotationEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "badgeAngle"
        )
    } else remember { mutableFloatStateOf(0f) }

    val overrideContainer = getBadgeColor(badgeColorName, containerColor)
    val overrideContent = getBadgeContentColor(badgeColorName, contentColor)

    val profile = remember(shapeName) { parseShapeProfile(shapeName) }
    val spec = remember(profile) { WavySpec(profile, 8, 0.07f) }
    Box(
        Modifier
            .size(size)
            .wavyBackground(spec, overrideContainer, rotationProvider = { angle }),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = overrideContent,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@Composable
fun ItemTitle(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    shapeName: String = "Cookie",
    badgeColorName: String = "Purple",
    rotationEnabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        IconBadge(icon = icon, shapeName = shapeName, badgeColorName = badgeColorName, rotationEnabled = rotationEnabled)
        Column(Modifier.weight(1f)) {
            Text(
                tr(title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    tr(subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapeName: String = "Cookie",
    badgeColorName: String = "Purple",
    rotationEnabled: Boolean = true,
    uiStyle: String = "Material3"
) {
    GroupItem(onClick = { onCheckedChange(!checked) }, uiStyle = uiStyle) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.weight(1f)) {
                ItemTitle(icon, title, subtitle, shapeName = shapeName, badgeColorName = badgeColorName, rotationEnabled = rotationEnabled)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun ActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    shapeName: String = "Cookie",
    badgeColorName: String = "Purple",
    rotationEnabled: Boolean = true,
    uiStyle: String = "Material3"
) {
    GroupItem(onClick = onClick, uiStyle = uiStyle) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.weight(1f)) {
                ItemTitle(icon, title, subtitle, shapeName = shapeName, badgeColorName = badgeColorName, rotationEnabled = rotationEnabled)
            }
            if (trailing != null) trailing()
            else Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SliderItem(
    icon: ImageVector,
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
    steps: Int = 0,
    shapeName: String = "Cookie",
    badgeColorName: String = "Purple",
    rotationEnabled: Boolean = true,
    uiStyle: String = "Material3"
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }

    val stepDelta = remember(range, steps) {
        if (steps > 0) (range.endInclusive - range.start) / (steps + 1)
        else (range.endInclusive - range.start) / 20f
    }

    GroupItem(uiStyle = uiStyle) {
        Column(
            Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .scale(if (isFocused) 1.02f else 1f)
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(22.dp)
                )
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            val newVal = (local - stepDelta).coerceIn(range.start, range.endInclusive)
                            local = newVal
                            onCommit(newVal)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val newVal = (local + stepDelta).coerceIn(range.start, range.endInclusive)
                            local = newVal
                            onCommit(newVal)
                            true
                        }
                        else -> false
                    }
                }
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconBadge(icon, shapeName = shapeName, badgeColorName = badgeColorName, rotationEnabled = rotationEnabled)
                Text(
                    tr(title),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        format(local),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Slider(
                value = local,
                onValueChange = { local = it },
                onValueChangeFinished = { onCommit(local) },
                valueRange = range,
                steps = steps,
                modifier = Modifier.padding(start = 56.dp).focusProperties { canFocus = false }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonGroup(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    uiStyle: String = "Material3"
) {
    val cs = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (id, label) ->
            val isSel = id == selected

            val scale by animateFloatAsState(
                if (isSel) 1.05f else 1f,
                spring(dampingRatio = 0.6f, stiffness = 500f),
                label = "buttonScale"
            )
            val corner by animateDpAsState(
                if (isSel) 20.dp else 12.dp,
                spring(dampingRatio = 0.6f, stiffness = 500f),
                label = "buttonCorner"
            )
            val activeUiStyle = if (uiStyle == "LiquidGlass" || LocalUiStyle.current == "LiquidGlass") "LiquidGlass" else "Material3"
            val bg = if (activeUiStyle == "LiquidGlass") {
                if (isSel) cs.primary.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f)
            } else {
                if (isSel) cs.primary else cs.secondaryContainer
            }
            val textColor = if (activeUiStyle == "LiquidGlass") {
                if (isSel) cs.onPrimary else cs.onSurface
            } else {
                if (isSel) cs.onPrimary else cs.onSecondaryContainer
            }

            Box(
                Modifier
                    .scale(scale)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(bg)
                    .then(
                        if (activeUiStyle == "LiquidGlass") {
                            Modifier.border(
                                width = 1.dp,
                                color = if (isSel) cs.primary else Color.White.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(corner)
                            )
                        } else Modifier
                    )
                    .clickable { onSelect(id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tr(label),
                    color = textColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ChoiceItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    shapeName: String = "Cookie",
    badgeColorName: String = "Purple",
    rotationEnabled: Boolean = true,
    uiStyle: String = "Material3"
) {
    GroupItem(uiStyle = uiStyle) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ItemTitle(icon, title, subtitle, shapeName = shapeName, badgeColorName = badgeColorName, rotationEnabled = rotationEnabled)
            ButtonGroup(options, selected, onSelect, uiStyle = uiStyle)
        }
    }
}

@Composable
fun CategoryTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    shapeName: String = "Cookie",
    badgeColorName: String = "Purple",
    rotationEnabled: Boolean = true,
    uiStyle: String = "Material3"
) {
    val cs = MaterialTheme.colorScheme
    val overrideContainer = getBadgeColor(badgeColorName, cs.primaryContainer)
    val overrideContent = getBadgeContentColor(badgeColorName, cs.onPrimaryContainer)

    val profile = remember(shapeName) { parseShapeProfile(shapeName) }
    val expressiveSpec = remember(profile) { WavySpec(profile, 10, 0.08f) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "tileRotation")
    val continuousRot by if (rotationEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "tileContinuousRot"
        )
    } else remember { mutableFloatStateOf(0f) }

    val rot = continuousRot + if (pressed) 25f else 0f
    val corner by animateDpAsState(
        if (pressed) 14.dp else 24.dp,
        spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "tileCorner"
    )

    val activeUiStyle = if (uiStyle == "LiquidGlass" || LocalUiStyle.current == "LiquidGlass") "LiquidGlass" else "Material3"

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(if (activeUiStyle == "LiquidGlass") cs.surfaceContainerHigh.copy(alpha = 0.30f) else cs.surfaceContainerHigh)
            .then(
                if (activeUiStyle == "LiquidGlass") {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        ),
                        shape = RoundedCornerShape(corner)
                    )
                } else Modifier
            )
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            Modifier
                .size(48.dp)
                .wavyBackground(expressiveSpec, overrideContainer) { rot },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = overrideContent, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onSurface)
                if (badgeCount > 0) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(cs.primary)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("$badgeCount", style = MaterialTheme.typography.labelSmall, color = cs.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                tr(subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = cs.onSurfaceVariant)
    }
}

@Composable
fun SettingsSection(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Text(
                tr(title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (subtitle != null) {
                Text(
                    tr(subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        content()
    }
}

@Composable
fun SettingsSearch(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(tr("Поиск настроек: тема, EPG, плеер...")) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = cs.primary) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = null)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = cs.primary,
            unfocusedBorderColor = cs.outlineVariant,
            focusedContainerColor = cs.surfaceContainerHigh,
            unfocusedContainerColor = cs.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    )
}

@Composable
fun WavyEpgProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavyProgress")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val filledWidth = (width * progress.coerceIn(0f, 1f))

        if (filledWidth < width) {
            drawLine(
                color = trackColor,
                start = Offset(filledWidth, centerY),
                end = Offset(width, centerY),
                strokeWidth = (height * 0.5f).coerceAtLeast(2.dp.toPx()),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        if (filledWidth > 0f) {
            val wavePath = Path()
            val waveLength = 20.dp.toPx()
            val amplitude = (height * 0.4f).coerceAtLeast(2.dp.toPx())

            wavePath.moveTo(0f, centerY)
            var x = 0f
            val step = 2.dp.toPx()
            while (x <= filledWidth) {
                val y = centerY + sin((x / waveLength) * 2f * Math.PI.toFloat() - phase) * amplitude
                wavePath.lineTo(x, y)
                x += step
            }

            drawPath(
                path = wavePath,
                color = color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (height * 0.7f).coerceAtLeast(2.dp.toPx()),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
    }
}

@Composable
fun GlowEpgProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glowProgress")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val filledWidth = (width * progress.coerceIn(0f, 1f))

        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = height,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        if (filledWidth > 0f) {
            val glowColor = color.copy(alpha = glowAlpha)
            val brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                colors = listOf(color.copy(alpha = 0.6f), color, Color.White),
                startX = 0f,
                endX = filledWidth
            )

            drawLine(
                color = glowColor.copy(alpha = glowAlpha * 0.5f),
                start = Offset(0f, centerY),
                end = Offset(filledWidth, centerY),
                strokeWidth = height * 1.8f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            drawLine(
                brush = brush,
                start = Offset(0f, centerY),
                end = Offset(filledWidth, centerY),
                strokeWidth = height,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
fun SolidEpgProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.clip(CircleShape),
        color = color,
        trackColor = trackColor
    )
}

@Composable
fun EpgProgressBar(
    progress: Float,
    style: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    when (style.trim().lowercase(java.util.Locale.ROOT)) {
        "wavy", "волнистый" -> WavyEpgProgressBar(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor
        )
        "glow", "glowing", "светящийся" -> GlowEpgProgressBar(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor
        )
        else -> SolidEpgProgressBar(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor
        )
    }
}
