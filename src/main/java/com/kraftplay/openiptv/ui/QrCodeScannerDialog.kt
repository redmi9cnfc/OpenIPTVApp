package com.kraftplay.openiptv.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.kraftplay.openiptv.service.WifiTransferReceiverServer
import com.kraftplay.openiptv.util.QrCodeGenerator
import com.kraftplay.openiptv.viewmodel.MainViewModel
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections
import java.util.EnumMap
import java.util.concurrent.Executors

fun getLocalIpv4Address(): String? = try {
    Collections.list(NetworkInterface.getNetworkInterfaces()).asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { Collections.list(it.inetAddresses).asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { addr ->
            val host = addr.hostAddress.orEmpty()
            !addr.isLoopbackAddress && (host.startsWith("192.") || host.startsWith("10.") || host.matches(Regex("172\\.(1[6-9]|2[0-9]|3[01])\\..*")))
        }
        ?.hostAddress
} catch (_: Exception) { null }

/**
 * Автоматически обрабатывает и применяет данные QR-кода в MainViewModel
 */
fun processAndApplyQrCode(context: Context, viewModel: MainViewModel, qrContent: String): String {
    val trimmed = qrContent.trim()
    if (trimmed.isBlank()) {
        return tr("QR-код пуст")
    }

    // 1. Проверяем, является ли это прямым HTTP-запросом передачи на второе устройство
    if (trimmed.contains("/receive/")) {
        return try {
            val url = URL(trimmed)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val data = viewModel.exportWifiTransferJson().toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(data.size)
            conn.outputStream.use { it.write(data) }
            if (conn.responseCode == 200) {
                tr("ВСЕ настройки, плейлисты и EPG успешно переданы на устройство!")
            } else {
                tr("Ошибка передачи на устройство (код {0})", conn.responseCode)
            }
        } catch (e: Exception) {
            tr("Ошибка отправки данных: {0}", e.localizedMessage)
        }
    }

    // 2. Проверяем, является ли это полным дампом бэкапа JSON
    if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
        trimmed.contains("\"playlists\"") || trimmed.contains("\"epgSources\"") || trimmed.contains("\"appSettings\"")
    ) {
        val success = viewModel.importBackupJson(trimmed)
        return if (success) {
            tr("Успешно импортированы ВСЕ настройки, плейлисты и EPG!")
        } else {
            tr("Ошибка структуры резервной копии")
        }
    }

    // 3. Проверяем, является ли это EPG источником
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        val lower = trimmed.lowercase()
        if (lower.contains(".xml") || lower.contains(".xml.gz") || lower.contains(".xz") || lower.contains("epg") || lower.contains("xmltv")) {
            viewModel.addEpgSource("QR EPG Source", trimmed)
            return tr("Источник EPG успешно добавлен из QR-кода!")
        }
    }

    // 4. Если это прямая M3U / M3U8 ссылка или плейлист URL
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("udp://", ignoreCase = true) || trimmed.startsWith("rtmp://", ignoreCase = true) ||
        trimmed.startsWith("rtsp://", ignoreCase = true)
    ) {
        viewModel.addPlaylist("QR Playlist", trimmed)
        return tr("Плейлист успешно добавлен из QR-кода!")
    }

    // 5. Если это сырой текст M3U плейлиста (#EXTM3U)
    if (trimmed.startsWith("#EXTM3U", ignoreCase = true)) {
        return try {
            val file = File(context.filesDir, "qr_imported_${System.currentTimeMillis()}.m3u")
            file.writeText(trimmed)
            viewModel.addPlaylist("QR M3U Playlist", file.absolutePath)
            tr("Плейлист M3U сохранен и добавлен!")
        } catch (e: Exception) {
            tr("Ошибка сохранения M3U: {0}", e.localizedMessage)
        }
    }

    // 6. Попытка импорта как JSON бэкапа (если не подошли шаблоны выше)
    return if (viewModel.importBackupJson(trimmed)) {
        tr("Настройки успешно применены!")
    } else {
        viewModel.addPlaylist("QR Data", trimmed)
        tr("Данные QR-кода добавлены в плейлисты!")
    }
}

/**
 * Компактный диалог сканирования QR-кода с помощью встроенной мини-камеры (CameraX + ZXing)
 */
@Composable
fun QrCodeScannerDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    title: String = tr("Принять настройки по QR (Сканер)"),
    onSuccess: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, tr("Для сканирования QR-кода необходим доступ к камере"), Toast.LENGTH_LONG).show()
        }
    }

    var manualInputMode by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    var scanResultStatus by remember { mutableStateOf<String?>(null) }

    InPageDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(title, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = tr("Закрыть"))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (scanResultStatus != null) {
                    Text(
                        text = scanResultStatus!!,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                } else if (!manualInputMode) {
                    if (!hasCameraPermission) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                tr("Нажмите кнопку ниже, чтобы включить мини-камеру и отсканировать QR-код с настройками или плейлистом..."),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Предоставить доступ к камере"))
                            }
                            OutlinedButton(
                                onClick = { manualInputMode = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(tr("Ввести или вставить вручную"))
                            }
                        }
                    } else {
                        // Камера активна
                        MiniCameraScannerView(
                            onQrCodeScanned = { qrContent ->
                                triggerVibration(context)
                                val status = processAndApplyQrCode(context, viewModel, qrContent)
                                scanResultStatus = status
                                Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                                onSuccess(status)
                            },
                            onManualClick = { manualInputMode = true }
                        )
                    }
                } else {
                    // Ручной ввод / Вставка
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            tr("Вставьте код или URL из буфера обмена:"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = manualText,
                            onValueChange = { manualText = it },
                            placeholder = { Text(tr("http://... или JSON бэкап...")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        manualText = clip
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(tr("Из буфера"))
                            }
                            Button(
                                onClick = {
                                    if (manualText.isNotBlank()) {
                                        val status = processAndApplyQrCode(context, viewModel, manualText)
                                        scanResultStatus = status
                                        Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                                        onSuccess(status)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(tr("Применить"))
                            }
                        }
                        if (hasCameraPermission) {
                            OutlinedButton(
                                onClick = { manualInputMode = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Вернуться к камере"))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (scanResultStatus != null) {
                Button(onClick = onDismiss) {
                    Text(tr("Готово"))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(tr("Закрыть"))
                }
            }
        }
    )
}

/**
 * Диалог для ПРИЕМА настроек через отображение QR-кода на экране устройства
 */
@Composable
fun ShowReceiveQrDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf(tr("Запуск сервера приема по QR...")) }
    var isSuccess by remember { mutableStateOf(false) }

    val receiverServer = remember(viewModel) {
        WifiTransferReceiverServer { payload ->
            val success = viewModel.importBackupJson(payload)
            if (success) {
                triggerVibration(context)
                statusText = tr("ВСЕ настройки, плейлисты и EPG успешно получены и применены!")
                isSuccess = true
            } else {
                statusText = tr("Ошибка импорта полученных данных")
            }
            success
        }
    }

    var qrImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    DisposableEffect(Unit) {
        try {
            val port = receiverServer.start()
            val ip = getLocalIpv4Address()
            if (ip != null) {
                val receiverUrl = "http://$ip:$port${receiverServer.path}"
                qrImage = QrCodeGenerator.generateQrBitmap(receiverUrl, 512, 512)
                statusText = tr("Покажите этот QR-код второму устройству. При сканировании второе устройство передаст ВСЕ свои настройки, плейлисты и EPG на этот экран.")
            } else {
                statusText = tr("Подключитесь к сети Wi-Fi для приема настроек по QR-коду")
            }
        } catch (e: Exception) {
            statusText = tr("Ошибка запуска локального приема: {0}", e.localizedMessage)
        }
        onDispose {
            receiverServer.stop()
        }
    }

    InPageDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(tr("Принять настройки (QR-приёмник)"), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (qrImage != null && !isSuccess) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = qrImage!!,
                            contentDescription = "Receive QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(if (isSuccess) tr("Готово") else tr("Закрыть"))
            }
        }
    )
}

/**
 * Диалог для отображения/экспорта QR-кода настроек, плейлистов или EPG ("отдавать по QR")
 */
@Composable
fun ShowQrCodeDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Все настройки, 1: Плейлист, 2: EPG
    val playlists by viewModel.playlists.collectAsState()
    val epgSources by viewModel.epgSources.collectAsState()

    val fullBackupJson = remember(viewModel) { viewModel.exportQrTransferJson() }
    val currentPlaylistUrl = remember(playlists) { playlists.firstOrNull { it.isSelected }?.url ?: playlists.firstOrNull()?.url ?: "" }
    val currentEpgUrl = remember(epgSources) { epgSources.firstOrNull { it.enabled }?.url ?: epgSources.firstOrNull()?.url ?: "" }

    val qrContent = when (selectedTab) {
        0 -> fullBackupJson
        1 -> currentPlaylistUrl
        2 -> currentEpgUrl
        else -> fullBackupJson
    }

    val qrImage = remember(qrContent) {
        QrCodeGenerator.generateQrBitmap(qrContent, 512, 512)
    }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    InPageDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(tr("Отдать настройки (Мой QR-код)"), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Вкладки переключения контента QR
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) {
                        Text(tr("Все настройки"), fontSize = 11.sp)
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) {
                        Text(tr("Плейлист"), fontSize = 11.sp)
                    }
                    SegmentedButton(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) {
                        Text(tr("EPG"), fontSize = 11.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = qrImage,
                        contentDescription = "QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Text(
                    text = when (selectedTab) {
                        0 -> tr("Покажите этот QR-код другому устройству для передачи ВСЕХ настроек, плейлистов и EPG.")
                        1 -> if (currentPlaylistUrl.isNotBlank()) tr("QR-код ссылки на выбранный плейлист.") else tr("Нет активных плейлистов")
                        2 -> if (currentEpgUrl.isNotBlank()) tr("QR-код ссылки на выбранный источник ТВ-программы (EPG).") else tr("Нет активных источников EPG")
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                OutlinedButton(
                    onClick = {
                        if (qrContent.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(qrContent))
                            Toast.makeText(context, tr("Скопировано в буфер обмена"), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Скопировать данные QR"))
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(tr("Закрыть"))
            }
        }
    )
}

@Composable
private fun MiniCameraScannerView(
    onQrCodeScanned: (String) -> Unit,
    onManualClick: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isTorchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var isAlreadyScanned by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(
                                cameraExecutor,
                                QrCodeAnalyzer { resultText ->
                                    if (!isAlreadyScanned) {
                                        isAlreadyScanned = true
                                        onQrCodeScanned(resultText)
                                    }
                                }
                            )
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalyzer
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Анимация сканирующей линии (лазер)
        val scanAnim = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            scanAnim.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }

        // Визуальная рамка-прицел сканера
        Box(
            modifier = Modifier
                .size(190.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
        ) {
            // Сканирующая красная/зеленая линия
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .offset(y = (190 * scanAnim.value).dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        // Текст подсказки поверх камеры
        Text(
            text = tr("Наведите камеру на QR-код"),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )

        // Кнопка вспышки (фонарик)
        IconButton(
            onClick = {
                isTorchOn = !isTorchOn
                camera?.cameraControl?.enableTorch(isTorchOn)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = tr("Фонарик"),
                tint = if (isTorchOn) Color.Yellow else Color.White
            )
        }

        // Кнопка ручного ввода
        IconButton(
            onClick = onManualClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = tr("Ручной ввод"),
                tint = Color.White
            )
        }
    }
}

/**
 * ZXing Анализатор кадров для CameraX ImageAnalysis
 */
private class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            put(DecodeHintType.TRY_HARDER, true)
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
        }
        setHints(hints)
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            try {
                val plane = mediaImage.planes[0]
                val buffer = plane.buffer
                val rowStride = plane.rowStride
                val width = imageProxy.width
                val height = imageProxy.height

                val yData = ByteArray(width * height)
                val pos = buffer.position()
                for (y in 0 until height) {
                    buffer.position(pos + y * rowStride)
                    val lengthToRead = width.coerceAtMost(buffer.remaining())
                    buffer.get(yData, y * width, lengthToRead)
                }

                val source = PlanarYUVLuminanceSource(
                    yData, width, height, 0, 0, width, height, false
                )
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

                val result = reader.decodeWithState(binaryBitmap)
                if (result != null && !result.text.isNullOrBlank()) {
                    onQrCodeScanned(result.text)
                }
            } catch (e: Exception) {
                // Игнорируем ошибки кадров без QR-кода
            } finally {
                reader.reset()
            }
        }
        imageProxy.close()
    }
}

private fun triggerVibration(context: Context) {
    try {
        val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(100)
        }
    } catch (e: Exception) {
        // Игнорируем отсутствие вибромотора или разрешений
    }
}
