package com.kraftplay.openiptv.ui

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kraftplay.openiptv.service.WifiTransferReceiverServer
import com.kraftplay.openiptv.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val SERVICE_TYPE = "_openiptv-transfer._tcp."

@Composable
fun WifiTransferDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    var mode by remember { mutableStateOf("choose") }
    var status by remember { mutableStateOf(tr("Выберите действие")) }
    var busy by remember { mutableStateOf(false) }
    var showCameraScanner by remember { mutableStateOf(false) }
    var cameraScannerTitle by remember { mutableStateOf("") }
    var showReceiveQr by remember { mutableStateOf(false) }
    val devices = remember { mutableStateListOf<NsdServiceInfo>() }
    var registration by remember { mutableStateOf<NsdManager.RegistrationListener?>(null) }
    var discovery by remember { mutableStateOf<NsdManager.DiscoveryListener?>(null) }
    val receiver = remember(viewModel) { WifiTransferReceiverServer { payload -> viewModel.importBackupJson(payload) } }

    fun stopDiscovery() { discovery?.let { runCatching { manager.stopServiceDiscovery(it) } }; discovery = null }
    fun stopRegistration() { registration?.let { runCatching { manager.unregisterService(it) } }; registration = null }
    fun cleanup() { stopDiscovery(); stopRegistration(); receiver.stop() }

    fun startReceiver() {
        cleanup(); mode = "receive"; status = tr("Ожидание подключения второго устройства...")
        try {
            val port = receiver.start()
            val deviceModel = android.os.Build.MODEL.take(18)
            val info = NsdServiceInfo().apply {
                serviceName = "OpenIPTV-$deviceModel"
                serviceType = SERVICE_TYPE
                this.port = port
                setAttribute("path", receiver.path)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    status = tr("Устройство видно в сети Wi-Fi")
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    status = tr("Не удалось зарегистрировать устройство в сети ({0}). Проверьте Wi‑Fi.", errorCode)
                }
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) { }
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { }
            }
            registration = listener
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            status = e.localizedMessage ?: tr("Не удалось запустить приём")
        }
    }

    fun startDiscovery() {
        cleanup(); devices.clear(); mode = "send"; status = tr("Ищем устройства, готовые принять плейлист…")
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                status = tr("Поиск устройств… На втором устройстве откройте «Получить на это устройство».")
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("openiptv-transfer")) manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) { }
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        if (si.host != null && devices.none { it.serviceName == si.serviceName }) {
                            devices.add(si)
                            status = tr("Выберите устройство-получатель")
                        }
                    }
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) { devices.removeAll { it.serviceName == service.serviceName } }
            override fun onDiscoveryStopped(serviceType: String) { }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                status = tr("Не удалось начать поиск ({0}).", errorCode)
                stopDiscovery()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { stopDiscovery() }
        }
        discovery = listener
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { status = tr("Ошибка поиска: {0}", it.localizedMessage ?: "") }
    }

    var qrScanInput by remember { mutableStateOf("") }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    DisposableEffect(Unit) { onDispose { cleanup() } }

    InPageDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Передача по WIFI и QR-коду")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                when (mode) {
                    "choose" -> {
                        Text(tr("Передача плейлистов, EPG и ВСЕХ настроек приложения:"))
                        Button(
                            onClick = {
                                cameraScannerTitle = tr("Принять настройки по QR-коду (Сканировать)")
                                showCameraScanner = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Принять настройки по QR-коду (Сканировать)"))
                        }
                        Button(
                            onClick = { showReceiveQr = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Принять настройки по QR-коду (Показать QR)"))
                        }
                        Button(
                            onClick = { mode = "show_qr" },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Icon(Icons.Default.Send, null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Отдать настройки по QR-коду (Показать)"))
                        }
                        Button(
                            onClick = {
                                cameraScannerTitle = tr("Отдать настройки по QR-коду (Сканировать)")
                                showCameraScanner = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Отдать настройки по QR-коду (Сканировать)"))
                        }
                        OutlinedButton(onClick = { startDiscovery() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Wifi, null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Передать — выбрать устройство"))
                        }
                        OutlinedButton(onClick = { startReceiver() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Devices, null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Получить на это устройство"))
                        }
                    }
                    "receive" -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Wifi,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Text(
                                text = status,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Text(
                                text = tr("Оставьте этот экран открытым до завершения передачи. На устройстве-отправителе выберите «Передать — выбрать устройство» и нажмите на имя этого устройства."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Text(
                                text = tr("Имя этого устройства в сети: {0}", "OpenIPTV-${android.os.Build.MODEL.take(18)}"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                    }
                    "show_qr" -> {
                        val transferData = remember(viewModel) { viewModel.exportWifiTransferJson() }
                        val qrImage = remember(transferData) { com.kraftplay.openiptv.util.QrCodeGenerator.generateQrBitmap(transferData, 360, 360) }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = qrImage,
                                contentDescription = "QR Code Sync",
                                modifier = Modifier.size(190.dp)
                            )
                            Text(tr("Покажите этот QR-код второму устройству для передачи ВСЕХ плейлистов, EPG и настроек"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { showCameraScanner = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.QrCodeScanner, null)
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Включить камеру для сканирования"))
                            }
                        }
                    }
                    "scan_qr" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { showCameraScanner = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.QrCodeScanner, null)
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Открыть мини-камеру сканера QR"))
                            }
                            Text(tr("Или вставьте текст QR-кода / JSON дампа со второго устройства:"))
                            OutlinedTextField(
                                value = qrScanInput,
                                onValueChange = { qrScanInput = it },
                                placeholder = { Text(tr("Нажмите 'Из буфера' или вставьте текст QR-кода")) },
                                modifier = Modifier.fillMaxWidth().height(100.dp)
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrBlank()) {
                                            qrScanInput = clip
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(tr("Из буфера"))
                                }
                                Button(
                                    onClick = {
                                        if (qrScanInput.isNotBlank()) {
                                            busy = true
                                            scope.launch {
                                                val res = processAndApplyQrCode(context, viewModel, qrScanInput)
                                                status = res
                                                busy = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(tr("Применить"))
                                }
                            }
                            if (status.isNotBlank()) {
                                Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { mode = "show_qr" }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp)); Text(tr("Переключить на Показать мой QR"))
                            }
                        }
                    }
                    "send" -> {
                        Text(status)
                        if (devices.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(devices.toList(), key = { it.serviceName }) { device ->
                                ListItem(
                                    headlineContent = { Text(device.serviceName.removePrefix("OpenIPTV-")) },
                                    supportingContent = { Text(tr("Готово к приёму · Wi-Fi")) },
                                    leadingContent = { Icon(Icons.Default.Devices, null) },
                                    modifier = Modifier.clickable(enabled = !busy) {
                                        busy = true
                                        status = tr("Передаём плейлист и EPG на {0}…", device.serviceName)
                                        stopDiscovery()
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val host = device.host?.hostAddress ?: error(tr("Устройство недоступно"))
                                                    val path = device.attributes["path"]?.toString(Charsets.UTF_8) ?: error(tr("Адрес приёма не найден"))
                                                    val conn = URL("http://$host:${device.port}$path").openConnection() as HttpURLConnection
                                                    try {
                                                        conn.requestMethod = "POST"; conn.doOutput = true
                                                        conn.connectTimeout = 5000; conn.readTimeout = 20000
                                                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                                        val data = viewModel.exportWifiTransferJson().toByteArray(Charsets.UTF_8)
                                                        conn.setFixedLengthStreamingMode(data.size)
                                                        conn.outputStream.use { it.write(data) }
                                                        if (conn.responseCode == 200) tr("Передача завершена успешно")
                                                        else error(tr("Устройство отклонило импорт ({0})", conn.responseCode))
                                                    } finally { conn.disconnect() }
                                                }
                                            }
                                            status = result.getOrElse { tr("Ошибка передачи: {0}", it.localizedMessage ?: tr("проверьте соединение")) }
                                            busy = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Готово")) } },
        dismissButton = { if (mode != "choose") TextButton(onClick = { cleanup(); mode = "choose"; status = tr("Выберите действие") }) { Text(tr("Назад")) } }
    )

    if (showCameraScanner) {
        QrCodeScannerDialog(
            viewModel = viewModel,
            title = cameraScannerTitle.ifBlank { tr("Принять настройки по QR (Сканер)") },
            onDismiss = { showCameraScanner = false }
        )
    }

    if (showReceiveQr) {
        ShowReceiveQrDialog(
            viewModel = viewModel,
            onDismiss = { showReceiveQr = false }
        )
    }
}
