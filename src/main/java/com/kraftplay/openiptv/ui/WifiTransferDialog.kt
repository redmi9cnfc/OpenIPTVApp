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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kraftplay.openiptv.R
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
    var status by remember { mutableStateOf(context.getString(R.string.wifi_dialog_choose_action)) }
    var busy by remember { mutableStateOf(false) }
    var showCameraScanner by remember { mutableStateOf(false) }
    val devices = remember { mutableStateListOf<NsdServiceInfo>() }
    var registration by remember { mutableStateOf<NsdManager.RegistrationListener?>(null) }
    var discovery by remember { mutableStateOf<NsdManager.DiscoveryListener?>(null) }
    val receiver = remember(viewModel) { WifiTransferReceiverServer { payload -> viewModel.importBackupJson(payload) } }

    fun stopDiscovery() { discovery?.let { runCatching { manager.stopServiceDiscovery(it) } }; discovery = null }
    fun stopRegistration() { registration?.let { runCatching { manager.unregisterService(it) } }; registration = null }
    fun cleanup() { stopDiscovery(); stopRegistration(); receiver.stop() }

    fun startReceiver() {
        cleanup(); mode = "receive"; status = context.getString(R.string.wifi_dialog_waiting_sender)
        try {
            val port = receiver.start()
            val info = NsdServiceInfo().apply {
                serviceName = "OpenIPTV-${android.os.Build.MODEL.take(18)}"
                serviceType = SERVICE_TYPE
                this.port = port
                setAttribute("path", receiver.path)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) { status = context.getString(R.string.wifi_dialog_device_visible) }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { status = context.getString(R.string.wifi_dialog_registration_failed, errorCode) }
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) { }
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { }
            }
            registration = listener
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) { status = e.localizedMessage ?: context.getString(R.string.wifi_dialog_receive_start_failed) }
    }

    fun startDiscovery() {
        cleanup(); devices.clear(); mode = "send"; status = context.getString(R.string.wifi_dialog_searching_devices)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) { status = context.getString(R.string.wifi_dialog_searching_instructions) }
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("openiptv-transfer")) manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) { }
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        if (si.host != null && devices.none { it.serviceName == si.serviceName }) {
                            devices.add(si); status = context.getString(R.string.wifi_dialog_select_receiver)
                        }
                    }
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) { devices.removeAll { it.serviceName == service.serviceName } }
            override fun onDiscoveryStopped(serviceType: String) { }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { status = context.getString(R.string.wifi_dialog_discovery_failed, errorCode); stopDiscovery() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { stopDiscovery() }
        }
        discovery = listener
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { status = context.getString(R.string.wifi_dialog_discovery_error, it.localizedMessage ?: "") }
    }

    var qrScanInput by remember { mutableStateOf("") }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    DisposableEffect(Unit) { onDispose { cleanup() } }

    InPageDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wifi_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                when (mode) {
                    "choose" -> {
                        Text("Передача плейлистов, EPG и ВСЕХ настроек приложения:")
                        Button(
                            onClick = { showCameraScanner = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Принять по QR (Сканировать камерой)")
                        }
                        Button(
                            onClick = { mode = "show_qr" },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Icon(Icons.Default.Send, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Отдать по QR (Показать мой код)")
                        }
                        OutlinedButton(onClick = { mode = "scan_qr" }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Вставить текст QR-кода / JSON")
                        }
                        OutlinedButton(onClick = { startDiscovery() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Wifi, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.wifi_dialog_send_choose))
                        }
                        OutlinedButton(onClick = { startReceiver() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Devices, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.wifi_dialog_receive_this))
                        }
                    }
                    "show_qr", "receive" -> {
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
                            Text("Покажите этот QR-код второму устройству для передачи ВСЕХ плейлистов, EPG и настроек", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { showCameraScanner = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.QrCodeScanner, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Включить камеру для сканирования")
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
                                Text("Открыть мини-камеру сканера QR")
                            }
                            Text("Или вставьте текст QR-кода / JSON дампа со второго устройства:")
                            OutlinedTextField(
                                value = qrScanInput,
                                onValueChange = { qrScanInput = it },
                                placeholder = { Text("Нажмите 'Из буфера' или вставьте текст QR-кода") },
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
                                    Text("Из буфера")
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
                                    Text("Применить")
                                }
                            }
                            if (status.isNotBlank()) {
                                Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { mode = "show_qr" }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp)); Text("Переключить на Показать мой QR")
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
                                    supportingContent = { Text(stringResource(R.string.wifi_dialog_ready_wifi)) },
                                    leadingContent = { Icon(Icons.Default.Devices, null) },
                                    modifier = Modifier.clickable(enabled = !busy) {
                                        busy = true
                                        status = context.getString(R.string.wifi_dialog_sending_to, device.serviceName)
                                        stopDiscovery()
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val host = device.host?.hostAddress ?: error(context.getString(R.string.wifi_dialog_unreachable))
                                                    val path = device.attributes["path"]?.toString(Charsets.UTF_8) ?: error(context.getString(R.string.wifi_dialog_path_not_found))
                                                    val conn = URL("http://$host:${device.port}$path").openConnection() as HttpURLConnection
                                                    try {
                                                        conn.requestMethod = "POST"; conn.doOutput = true
                                                        conn.connectTimeout = 5000; conn.readTimeout = 20000
                                                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                                        val data = viewModel.exportWifiTransferJson().toByteArray(Charsets.UTF_8)
                                                        conn.setFixedLengthStreamingMode(data.size)
                                                        conn.outputStream.use { it.write(data) }
                                                        if (conn.responseCode == 200) context.getString(R.string.wifi_dialog_success)
                                                        else error(context.getString(R.string.wifi_dialog_rejected, conn.responseCode))
                                                    } finally { conn.disconnect() }
                                                }
                                            }
                                            status = result.getOrElse { context.getString(R.string.wifi_dialog_error, it.localizedMessage ?: context.getString(R.string.wifi_dialog_check_connection)) }
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wifi_dialog_done)) } },
        dismissButton = { if (mode != "choose") TextButton(onClick = { cleanup(); mode = "choose"; status = context.getString(R.string.wifi_dialog_choose_action) }) { Text(stringResource(R.string.wifi_dialog_back)) } }
    )

    if (showCameraScanner) {
        QrCodeScannerDialog(
            viewModel = viewModel,
            onDismiss = { showCameraScanner = false }
        )
    }
}
