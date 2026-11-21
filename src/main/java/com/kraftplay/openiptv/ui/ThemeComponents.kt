package com.kraftplay.openiptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val LocalIsAuto = staticCompositionLocalOf { false }

@Composable
fun InPageDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    val isAuto = LocalIsAuto.current

    if (isAuto) {
        BackHandler(onBack = onDismissRequest)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth(0.92f)
                    .padding(16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = shape,
                color = containerColor,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (title != null) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                            title()
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (text != null) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                            text()
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    if (confirmButton != null || dismissButton != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (dismissButton != null) {
                                dismissButton()
                                Spacer(Modifier.width(8.dp))
                            }
                            if (confirmButton != null) {
                                confirmButton()
                            }
                        }
                    }
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            text = text,
            confirmButton = { confirmButton?.invoke() },
            dismissButton = dismissButton?.let { { it.invoke() } },
            modifier = modifier,
            shape = shape,
            containerColor = containerColor
        )
    }
}

@Composable
fun StyledCard(
    modifier: Modifier = Modifier,
    uiStyle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    if (uiStyle == "LiquidGlass") {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.1f)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                content()
            }
        }
    } else {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
fun StyledBackground(
    uiStyle: String,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (uiStyle == "LiquidGlass") {
            // Dynamic blobs or gradients for glass effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            radius = 2000f
                        )
                    )
            )
        }
        content()
    }
}
