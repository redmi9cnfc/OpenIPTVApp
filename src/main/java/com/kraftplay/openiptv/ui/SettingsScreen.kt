package com.kraftplay.openiptv.ui

import android.net.Uri
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kraftplay.openiptv.service.WifiTransferServer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraftplay.openiptv.R
import com.kraftplay.openiptv.lsposed.LSPosedStatus
import com.kraftplay.openiptv.model.CustomThemeConfig
import com.kraftplay.openiptv.model.EpgSource
import com.kraftplay.openiptv.model.Playlist
import com.kraftplay.openiptv.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var activeUpdateDownloadId by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId != activeUpdateDownloadId) return
                val manager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(completedId)
                manager.query(query).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        Toast.makeText(ctx, "Could not access the downloaded APK", Toast.LENGTH_LONG).show()
                        return
                    }
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status != DownloadManager.STATUS_SUCCESSFUL) {
                        Toast.makeText(ctx, "Update download failed", Toast.LENGTH_LONG).show()
                        return
                    }
                }
                val apkUri = manager.getUriForDownloadedFile(completedId)
                if (apkUri == null) {
                    Toast.makeText(ctx, "APK downloaded, but the file could not be found", Toast.LENGTH_LONG).show()
                    return
                }
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { ctx.startActivity(installIntent) }
                    .onFailure { Toast.makeText(ctx, "Could not open the Android installer", Toast.LENGTH_LONG).show() }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else if (Build.VERSION.SDK_INT >= 26) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val clipboardManager = LocalClipboardManager.current
    val playlists by viewModel.playlists.collectAsState()
    val epgSources by viewModel.epgSources.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val showClock by viewModel.showClock.collectAsState()
    val language by viewModel.language.collectAsState()
    val hidePlaylistUrl by viewModel.hidePlaylistUrl.collectAsState()
    val listSize by viewModel.listSize.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val uiStyle by viewModel.uiStyle.collectAsState()
    val catDisplayMode by viewModel.categoryDisplayMode.collectAsState()
    val customThemeJson by viewModel.customThemeJson.collectAsState()
    val aspectRatioMode by viewModel.aspectRatioMode.collectAsState()
    
    val showSource by viewModel.showSource.collectAsState()
    val showResolution by viewModel.showResolution.collectAsState()
    val showFps by viewModel.showFps.collectAsState()
    val resFormat by viewModel.resolutionFormat.collectAsState()
    
    val autoStartLast by viewModel.autoStartLast.collectAsState()
    val forceLandscape by viewModel.forceLandscape.collectAsState()
    val backgroundPlayback by viewModel.backgroundPlayback.collectAsState()
    val autoHideTimeout by viewModel.autoHideTimeout.collectAsState()
    val bufferingLevel by viewModel.bufferingLevel.collectAsState()
    val backBehavior by viewModel.backBehavior.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val hiddenCategories by viewModel.hiddenCategories.collectAsState()
    val catchupMode by viewModel.catchupMode.collectAsState()
    
    val isEpgLoading by viewModel.isEpgLoading.collectAsState()
    val epgStatus by viewModel.epgStatus.collectAsState()

    val formattedEpgStatus = remember(epgStatus) {
        when {
            epgStatus == "LOADING" -> context.getString(R.string.epg_loading_status)
            epgStatus == "CLEARING" -> context.getString(R.string.epg_clearing)
            epgStatus == "CLEARED" -> context.getString(R.string.epg_cleared)
            epgStatus == "EPG_UPDATED" -> context.getString(R.string.epg_updated)
            epgStatus.startsWith("LOADED:") -> {
                val parts = epgStatus.split(":")
                val count = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val pct = parts.getOrNull(2) ?: "100%"
                context.getString(R.string.epg_programs_loaded, count, pct)
            }
            else -> epgStatus
        }
    }

    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var showWifiDeviceTransfer by remember { mutableStateOf(false) }
    var showAddEpgDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showCustomUiDialog by remember { mutableStateOf(false) }
    var customJsonText by remember { mutableStateOf(customThemeJson) }

    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var editingEpgSource by remember { mutableStateOf<EpgSource?>(null) }
    var showPasswordPrompt by remember { mutableStateOf<Playlist?>(null) }
    var inputPass by remember { mutableStateOf("") }
    var passError by remember { mutableStateOf(false) }

    var importJsonText by remember { mutableStateOf("") }
    var transferUrl by remember { mutableStateOf("") }
    var transferShareUrl by remember { mutableStateOf<String?>(null) }
    var transferStatus by remember { mutableStateOf("") }
    var receivingMode by remember { mutableStateOf(false) }
    var transferBusy by remember { mutableStateOf(false) }
    var showTransferImportConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val transferServer = remember(viewModel) {
        WifiTransferServer(
            exportPayload = { viewModel.exportWifiTransferJson() }
        )
    }
    DisposableEffect(transferServer) { onDispose { transferServer.stop() } }
    var tempName by remember { mutableStateOf("") }
    var tempUrl by remember { mutableStateOf("") }

    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val playlistFilePicker = if (activityResultRegistryOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "playlist.m3u"
                    viewModel.importPlaylistFile(uri, fileName)
                    Toast.makeText(context, "Импорт плейлиста: $fileName", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "Could not read file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    } else null

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues).fillMaxSize().padding(horizontal = 16.dp)
        ) {
            item { SectionHeader(stringResource(R.string.playlists)) }
            items(playlists) { playlist ->
                PlaylistCard(
                    name = playlist.name,
                    url = playlist.url,
                    selected = playlist.isSelected,
                    channelCount = playlist.channelCount,
                    enabled = playlist.enabled,
                    hasPassword = !playlist.password.isNullOrBlank(),
                    onSelect = {
                        if (!playlist.enabled) {
                            Toast.makeText(context, context.getString(R.string.playlist_disabled), Toast.LENGTH_SHORT).show()
                        } else if (!playlist.password.isNullOrBlank()) {
                            showPasswordPrompt = playlist
                            inputPass = ""
                            passError = false
                        } else {
                            viewModel.selectPlaylist(playlist.id)
                        }
                    },
                    onSettings = { editingPlaylist = playlist },
                    onDelete = { viewModel.deletePlaylist(playlist.id) }
                )
            }
            item {
                TvButton(onClick = { showAddPlaylistDialog = true; tempName = ""; tempUrl = "" }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_playlist))
                }
            }

            item {
                TvButton(
                    onClick = { showWifiDeviceTransfer = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Wifi, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.wifi_transfer_title))
                }
            }

            item {
                TvButton(
                    onClick = {
                        if (playlistFilePicker != null) {
                            try {
                                playlistFilePicker.launch(arrayOf("*/*"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "No file manager is available on this device", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "File import is not supported on this device", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Upload, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_file_formats))
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.epg_sources))
                    if (isEpgLoading) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(formattedEpgStatus, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    } else if (formattedEpgStatus.isNotEmpty()) {
                        Text(formattedEpgStatus, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            items(epgSources) { source ->
                EpgCard(
                    name = source.name,
                    url = source.url,
                    enabled = source.enabled,
                    hasPassword = !source.password.isNullOrBlank(),
                    onSettings = { editingEpgSource = source },
                    onDelete = { viewModel.deleteEpgSource(source.id) }
                )
            }
            item {
                TvButton(onClick = { showAddEpgDialog = true; tempName = ""; tempUrl = "" }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_epg))
                }
            }
            item {
                val epgDbSize by viewModel.epgDbSize.collectAsState()
                val sizeText = String.format(java.util.Locale.US, "%.1f MB", epgDbSize)
                TvButton(
                    onClick = { viewModel.clearAllEpg() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.clear_all_epg, sizeText))
                }
            }

            item {
                SectionHeader(stringResource(R.string.custom_interface_header))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvButton(
                        onClick = { customJsonText = customThemeJson; showCustomUiDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Code, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.edit_custom_ui))
                    }
                    TvButton(
                        onClick = {
                            viewModel.resetCustomTheme()
                            Toast.makeText(context, context.getString(R.string.reset_ui), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.reset_ui))
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.android_auto_header))
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.lsposed_status), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        val isLspActive = LSPosedStatus.isActive
                        Text(
                            text = if (isLspActive) stringResource(R.string.lsposed_active) else stringResource(R.string.lsposed_inactive),
                            color = if (isLspActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.manage_categories))
                
                Text(stringResource(R.string.category_display_mode), modifier = Modifier.padding(top = 8.dp))
                Row {
                    TvFilterChip(
                        selected = catDisplayMode == "Chips",
                        onClick = { viewModel.setCategoryDisplayMode("Chips") },
                        label = { Text(stringResource(R.string.mode_chips)) }
                    )
                    TvFilterChip(
                        selected = catDisplayMode == "Folders",
                        onClick = { viewModel.setCategoryDisplayMode("Folders") },
                        label = { Text(stringResource(R.string.mode_folders)) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.archive_mode), modifier = Modifier.padding(top = 12.dp))
                Text(stringResource(R.string.archive_mode_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Auto", "Default", "Append", "Shift", "Flussonic", "Xtream").forEach { mode ->
                        TvFilterChip(
                            selected = catchupMode == mode,
                            onClick = { viewModel.updateSetting("catchup_mode", mode) },
                            label = { Text(when (mode) { "Auto" -> "Auto"; "Default" -> "Default"; "Append" -> "Append"; "Shift" -> "Shift"; "Flussonic" -> "Flussonic"; else -> "Xtream" }) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TvButton(onClick = { showCategoryDialog = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(stringResource(R.string.manage_categories))
                }
            }

            item {
                SectionHeader(stringResource(R.string.backup_restore))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvButton(
                        onClick = {
                            val json = viewModel.exportBackupJson()
                            clipboardManager.setText(AnnotatedString(json))
                            Toast.makeText(context, context.getString(R.string.backup_copied), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Upload, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.export_backup))
                    }
                    TvButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.import_backup))
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.ui_settings))
                LanguageSelection(language) { viewModel.updateSetting("language", it) }
                
                Text(stringResource(R.string.ui_style), modifier = Modifier.padding(top = 8.dp))
                Row {
                    TvFilterChip(selected = uiStyle == "Material3", onClick = { viewModel.updateSetting("ui_style", "Material3") }, label = { Text("Material You 3") })
                    TvFilterChip(selected = uiStyle == "LiquidGlass", onClick = { viewModel.updateSetting("ui_style", "LiquidGlass") }, label = { Text("Liquid Glass") })
                }

                Text(stringResource(R.string.theme), modifier = Modifier.padding(top = 8.dp))
                val themeLabels = mapOf(
                    "Light" to stringResource(R.string.theme_light),
                    "Dark" to stringResource(R.string.theme_dark),
                    "AMOLED" to stringResource(R.string.theme_amoled)
                )
                Row {
                    listOf("Light", "Dark", "AMOLED").forEach { mode ->
                        TvFilterChip(
                            selected = themeMode == mode,
                            onClick = { viewModel.updateSetting("theme_mode", mode) },
                            label = { Text(themeLabels[mode] ?: mode) }
                        )
                    }
                }

                Text(stringResource(R.string.group_sorting), modifier = Modifier.padding(top = 8.dp))
                val sortOrder by viewModel.groupSortOrder.collectAsState()
                Row {
                    TvFilterChip(selected = sortOrder == "Provider", onClick = { viewModel.updateSetting("group_sort_order", "Provider") }, label = { Text(stringResource(R.string.sort_provider)) })
                    TvFilterChip(selected = sortOrder == "Alphabetical", onClick = { viewModel.updateSetting("group_sort_order", "Alphabetical") }, label = { Text(stringResource(R.string.sort_alphabetical)) })
                }

                Text(stringResource(R.string.accent_color), modifier = Modifier.padding(top = 8.dp))
                val colorLabels = mapOf(
                    "Blue" to stringResource(R.string.color_blue),
                    "Green" to stringResource(R.string.color_green),
                    "Red" to stringResource(R.string.color_red)
                )
                Row {
                    listOf("Blue", "Green", "Red").forEach { color ->
                        TvFilterChip(
                            selected = accentColor == color,
                            onClick = { viewModel.updateSetting("accent_color", color) },
                            label = { Text(colorLabels[color] ?: color) }
                        )
                    }
                }

                Text(stringResource(R.string.list_size), modifier = Modifier.padding(top = 8.dp))
                val sizeLabels = mapOf(
                    "Compact" to stringResource(R.string.size_compact),
                    "Standard" to stringResource(R.string.size_standard),
                    "Large" to stringResource(R.string.size_large)
                )
                Row {
                    listOf("Compact", "Standard", "Large").forEach { size ->
                        TvFilterChip(
                            selected = listSize == size,
                            onClick = { viewModel.updateSetting("list_size", size) },
                            label = { Text(sizeLabels[size] ?: size) }
                        )
                    }
                }

                Text("${stringResource(R.string.font_size)}: ${fontSize.toInt()}", modifier = Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.updateSetting("font_size", (fontSize - 1f).coerceAtLeast(10f)) }) {
                        Icon(Icons.Default.Remove, null)
                    }
                    Slider(
                        value = fontSize, 
                        onValueChange = { viewModel.updateSetting("font_size", it) }, 
                        valueRange = 10f..24f,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.updateSetting("font_size", (fontSize + 1f).coerceAtMost(24f)) }) {
                        Icon(Icons.Default.Add, null)
                    }
                }
                
                TvSwitchSetting(stringResource(R.string.show_clock), showClock) { viewModel.updateSetting("show_clock", it) }
                TvSwitchSetting(stringResource(R.string.hide_playlist_url), hidePlaylistUrl) { viewModel.updateSetting("hide_playlist_url", it) }

                SectionHeader(stringResource(R.string.player)) 
                
                Text(stringResource(R.string.aspect_ratio), modifier = Modifier.padding(top = 8.dp))
                val ratioLabels = mapOf(
                    "Fit" to stringResource(R.string.ratio_fit),
                    "Fill" to stringResource(R.string.ratio_fill),
                    "16:9" to "16:9",
                    "4:3" to "4:3"
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("Fit", "Fill", "16:9", "4:3").forEach { ratio ->
                        TvFilterChip(
                            selected = aspectRatioMode == ratio,
                            onClick = { viewModel.updateSetting("aspect_ratio_mode", ratio) },
                            label = { Text(ratioLabels[ratio] ?: ratio) }
                        )
                    }
                }

                TvSwitchSetting(stringResource(R.string.show_source), showSource) { viewModel.updateSetting("show_source", it) }
                TvSwitchSetting(stringResource(R.string.show_resolution), showResolution) { viewModel.updateSetting("show_resolution", it) }
                TvSwitchSetting(stringResource(R.string.show_fps), showFps) { viewModel.updateSetting("show_fps", it) }
                
                Text(stringResource(R.string.res_format), modifier = Modifier.padding(top = 8.dp))
                Row {
                    TvFilterChip(selected = resFormat == "Dimensions", onClick = { viewModel.updateSetting("resolution_format", "Dimensions") }, label = { Text(stringResource(R.string.res_format_dims)) })
                    TvFilterChip(selected = resFormat == "Labels", onClick = { viewModel.updateSetting("resolution_format", "Labels") }, label = { Text(stringResource(R.string.res_format_label)) })
                }

                TvSwitchSetting(stringResource(R.string.auto_start_last), autoStartLast) { viewModel.updateSetting("auto_start_last", it) }
                TvSwitchSetting(stringResource(R.string.landscape_mode), forceLandscape, stringResource(R.string.landscape_mode_desc)) { viewModel.updateSetting("force_landscape", it) }
                TvSwitchSetting(stringResource(R.string.background_playback), backgroundPlayback) { viewModel.updateSetting("background_playback", it) }
                
                Text(stringResource(R.string.auto_hide_info) + ": " + (if(autoHideTimeout == 0) stringResource(R.string.never) else stringResource(R.string.seconds, autoHideTimeout)))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(0, 3, 5, 10).forEach { s ->
                        TvFilterChip(selected = autoHideTimeout == s, onClick = { viewModel.updateSetting("auto_hide_timeout", s) }, label = { Text(if(s==0) stringResource(R.string.never) else stringResource(R.string.seconds, s)) })
                    }
                }

                Text(stringResource(R.string.buffering), modifier = Modifier.padding(top = 8.dp))
                val bufferLabels = mapOf(
                    "Low" to stringResource(R.string.buffer_low),
                    "Standard" to stringResource(R.string.buffer_standard),
                    "High" to stringResource(R.string.buffer_high)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Low", "Standard", "High").forEach { b ->
                        TvFilterChip(selected = bufferingLevel == b, onClick = { viewModel.updateSetting("buffering_level", b) }, label = { Text(bufferLabels[b] ?: b) })
                    }
                }

                Text(stringResource(R.string.sleep_timer), modifier = Modifier.padding(top = 8.dp))
                val sleepMinutes by viewModel.sleepTimer.collectAsState()
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0, 15, 30, 60, 120).forEach { m ->
                        TvFilterChip(selected = sleepMinutes == m, onClick = { viewModel.sleepTimer.value = m }, label = { Text(if(m==0) stringResource(R.string.off) else stringResource(R.string.minutes, m)) })
                    }
                }

                SectionHeader(stringResource(R.string.back_behavior))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Exit", "Back to list", "Confirm").forEach { b ->
                        val label = when (b) { "Exit" -> stringResource(R.string.back_exit); "Back to list" -> stringResource(R.string.back_to_list); else -> stringResource(R.string.back_confirm) }
                        TvFilterChip(selected = backBehavior == b, onClick = { viewModel.updateSetting("back_behavior", b) }, label = { Text(label) })
                    }
                }

                SectionHeader(stringResource(R.string.community))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openExternalLink(context, "https://t.me/+IB-WMBTo7ZxkNmZi") }, modifier = Modifier.weight(1f)) {
                        Text("Telegram")
                    }
                    OutlinedButton(onClick = { openExternalLink(context, "https://github.com/redmi9cnfc/OpenIPTVApp") }, modifier = Modifier.weight(1f)) {
                        Text("GitHub")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                var checkingUpdate by remember { mutableStateOf(false) }
                TvButton(
                    onClick = {
                        if (!checkingUpdate) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                !context.packageManager.canRequestPackageInstalls()
                            ) {
                                // Android does not show a permission popup here: it opens a
                                // system settings page. Some Android TV builds omit the
                                // dedicated page, so fall back to this app's settings.
                                val permissionIntent = Intent(
                                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + context.packageName)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                val opened = runCatching {
                                    context.startActivity(permissionIntent)
                                }.isSuccess
                                if (!opened) {
                                    val fallback = Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + context.packageName)
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { context.startActivity(fallback) }
                                }
                                Toast.makeText(context, "Allow OpenIPTV to install unknown apps in Android Settings, then return and tap Update app again.", Toast.LENGTH_LONG).show()
                            } else {
                                checkingUpdate = true
                                scope.launch(Dispatchers.IO) {
                                    val result = runCatching {
                                        val connection = (URL("https://api.github.com/repos/redmi9cnfc/OpenIPTVApp/releases/latest").openConnection() as HttpURLConnection).apply {
                                            connectTimeout = 15000
                                            readTimeout = 15000
                                            setRequestProperty("Accept", "application/vnd.github+json")
                                            setRequestProperty("User-Agent", "OpenIPTVApp-Updater")
                                        }
                                        try {
                                            val body = connection.inputStream.bufferedReader().use { it.readText() }
                                            val tag = Regex("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(body)?.groupValues?.get(1)
                                            val assetsPart = body.substringAfter("\"assets\"").substringBefore("\"reactions\"")
                                            val apk = Regex("\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+\\.apk(?:\\?[^\\\"]*)?)\\\"")
                                                .find(assetsPart)?.groupValues?.get(1)
                                            tag to apk
                                        } finally { connection.disconnect() }
                                    }
                                    withContext(Dispatchers.Main) {
                                        checkingUpdate = false
                                        val (tag, apkUrl) = result.getOrNull() ?: (null to null)
                                        val current = runCatching {
                                            @Suppress("DEPRECATION")
                                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                                        }.getOrNull()
                                        if (tag == null) {
                                            Toast.makeText(context, "Could not check GitHub releases. Check your internet connection.", Toast.LENGTH_LONG).show()
                                        } else if (tag.removePrefix("v").equals(current?.removePrefix("v"), true)) {
                                            Toast.makeText(context, "Latest version already installed ($tag)", Toast.LENGTH_LONG).show()
                                        } else if (apkUrl.isNullOrBlank()) {
                                            Toast.makeText(context, "Version $tag was found, but the release has no APK attached.", Toast.LENGTH_LONG).show()
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/redmi9cnfc/OpenIPTVApp/releases/latest")).addCategory(Intent.CATEGORY_BROWSABLE)) }
                                        } else {
                                            runCatching {
                                                val request = DownloadManager.Request(Uri.parse(apkUrl))
                                                    .setTitle("OpenIPTV — обновление $tag")
                                                    .setDescription("Downloading app update")
                                                    .setMimeType("application/vnd.android.package-archive")
                                                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "OpenIPTV-update.apk")
                                                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                                activeUpdateDownloadId = manager.enqueue(request)
                                                Toast.makeText(context, "Version $tag found. Downloading APK; the system installer will open when the download finishes.", Toast.LENGTH_LONG).show()
                                            }.onFailure {
                                                Toast.makeText(context, "Update download error: ${it.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (checkingUpdate) stringResource(R.string.checking_updates) else stringResource(R.string.update_app))
                }
                Spacer(modifier = Modifier.height(8.dp))
                TvButton(onClick = { viewModel.clearData() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.clear_data))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.version) + " 5.0", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (editingPlaylist != null) {
        val target = editingPlaylist!!
        ItemSettingsDialog(
            title = stringResource(R.string.playlist_settings),
            name = target.name,
            url = target.url,
            enabled = target.enabled,
            password = target.password ?: "",
            autoUpdateInterval = target.autoUpdateInterval,
            onDismiss = { editingPlaylist = null },
            onSave = { name, url, enabled, pass, interval ->
                viewModel.updatePlaylist(
                    target.copy(
                        name = name,
                        url = url,
                        enabled = enabled,
                        password = pass.ifBlank { null },
                        autoUpdateInterval = interval
                    )
                )
            }
        )
    }

    if (editingEpgSource != null) {
        val target = editingEpgSource!!
        ItemSettingsDialog(
            title = stringResource(R.string.epg_settings),
            name = target.name,
            url = target.url,
            enabled = target.enabled,
            password = target.password ?: "",
            autoUpdateInterval = target.autoUpdateInterval,
            onDismiss = { editingEpgSource = null },
            onSave = { name, url, enabled, pass, interval ->
                viewModel.updateEpgSource(
                    target.copy(
                        name = name,
                        url = url,
                        enabled = enabled,
                        password = pass.ifBlank { null },
                        autoUpdateInterval = interval
                    )
                )
            }
        )
    }

    if (showPasswordPrompt != null) {
        val target = showPasswordPrompt!!
        AlertDialog(
            onDismissRequest = { showPasswordPrompt = null },
            title = { Text(stringResource(R.string.enter_password)) },
            text = {
                Column {
                    Text(target.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = inputPass,
                        onValueChange = { inputPass = it },
                        label = { Text(stringResource(R.string.password)) },
                        isError = passError,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    if (passError) {
                        Text(stringResource(R.string.incorrect_password), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (inputPass == target.password) {
                        viewModel.selectPlaylist(target.id)
                        showPasswordPrompt = null
                    } else {
                        passError = true
                    }
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordPrompt = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCustomUiDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUiDialog = false },
            title = { Text(stringResource(R.string.edit_custom_ui)) },
            text = {
                Column {
                    TextField(
                        value = customJsonText,
                        onValueChange = { customJsonText = it },
                        placeholder = { Text("Paste Custom Theme JSON...") },
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { customJsonText = CustomThemeConfig.SAMPLE_CYBERPUNK }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.paste_sample_ui))
                        }
                        TextButton(onClick = {
                            val palettes = listOf(
                                listOf("#0D1117", "#58A6FF", "#161B22", "#F0F6FC", "#21262D"),
                                listOf("#101820", "#FEE715", "#1D2A35", "#FFFFFF", "#263746"),
                                listOf("#1B1025", "#FF6B9A", "#2A1838", "#FFF4FA", "#351D45"),
                                listOf("#0B1D18", "#57E389", "#122B22", "#E8FFF1", "#193A2D"),
                                listOf("#171717", "#FFB454", "#242424", "#F5F5F5", "#303030"),
                                listOf("#10152B", "#A78BFA", "#1C2340", "#F3F0FF", "#272F54")
                            )
                            val colors = palettes.random()
                            val radius = listOf(12, 16, 20, 24, 28).random()
                            val alpha = listOf(0.82, 0.88, 0.92, 0.96).random()
                            customJsonText = """
                                {
                                  "enabled": true,
                                  "backgroundColorHex": "${colors[0]}",
                                  "primaryColorHex": "${colors[1]}",
                                  "surfaceColorHex": "${colors[2]}",
                                  "textColorHex": "${colors[3]}",
                                  "topBarColorHex": "${colors[4]}",
                                  "cardCornerRadiusDp": $radius,
                                  "cardAlpha": $alpha
                                }
                            """.trimIndent()
                        }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.random_sample))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.updateCustomTheme(customJsonText)) {
                        Toast.makeText(context, context.getString(R.string.custom_ui_updated), Toast.LENGTH_SHORT).show()
                        showCustomUiDialog = false
                    } else {
                        Toast.makeText(context, context.getString(R.string.custom_ui_invalid), Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUiDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text(stringResource(R.string.manage_categories)) },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(categories.filter { it != "All" && it != "Favorites" && it != "Recent" }) { cat ->
                        val isHidden = hiddenCategories.contains(cat)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(if (isHidden) "$cat (${stringResource(R.string.category_disabled_suffix)})" else cat, modifier = Modifier.weight(1f), color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Switch(checked = !isHidden, onCheckedChange = { viewModel.toggleHideCategory(cat) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCategoryDialog = false }) { Text(stringResource(R.string.close)) } }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.import_backup)) },
            text = {
                TextField(
                    value = importJsonText,
                    onValueChange = { importJsonText = it },
                    placeholder = { Text("Paste JSON here...") },
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.importBackupJson(importJsonText)) {
                        Toast.makeText(context, context.getString(R.string.backup_success), Toast.LENGTH_SHORT).show()
                        showImportDialog = false
                    } else {
                        Toast.makeText(context, context.getString(R.string.backup_error), Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showTransferImportConfirm) {
        AlertDialog(
            onDismissRequest = { showTransferImportConfirm = false },
            title = { Text(stringResource(R.string.wifi_receive_confirm_title)) },
            text = { Text(stringResource(R.string.wifi_receive_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showTransferImportConfirm = false
                    transferBusy = true
                    transferStatus = context.getString(R.string.wifi_connecting)
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val url = URL(transferUrl)
                                require(url.protocol == "http") { "Only HTTP addresses on the local network are supported" }
                                (url.openConnection() as HttpURLConnection).run {
                                    connectTimeout = 7000
                                    readTimeout = 15000
                                    requestMethod = "GET"
                                    setRequestProperty("Accept", "application/json")
                                    try {
                                        if (responseCode !in 200..299) error("Устройство ответило с ошибкой HTTP $responseCode")
                                        inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                                    } finally { disconnect() }
                                }
                            }
                        }
                        transferStatus = result.fold(
                            onSuccess = { payload ->
                                if (viewModel.importBackupJson(payload)) context.getString(R.string.wifi_receive_success)
                                else context.getString(R.string.wifi_receive_invalid)
                            },
                            onFailure = { context.getString(R.string.wifi_transfer_error, it.localizedMessage ?: context.getString(R.string.wifi_connect_wifi)) }
                        )
                        transferBusy = false
                    }
                }) { Text(stringResource(R.string.wifi_receive_replace)) }
            },
            dismissButton = { TextButton(onClick = { showTransferImportConfirm = false }) { Text(stringResource(R.string.wifi_cancel)) } }
        )
    }

    if (showWifiDeviceTransfer) {
        WifiTransferDialog(viewModel = viewModel, onDismiss = { showWifiDeviceTransfer = false })
    }

    if (showAddPlaylistDialog) {
        AddDialog(stringResource(R.string.new_playlist), tempName, tempUrl, { tempName = it }, { tempUrl = it }, { showAddPlaylistDialog = false }, { viewModel.addPlaylist(tempName, tempUrl); showAddPlaylistDialog = false })
    }
    if (showAddEpgDialog) {
        AddDialog(stringResource(R.string.add_epg), tempName, tempUrl, { tempName = it }, { tempUrl = it }, { showAddEpgDialog = false }, { viewModel.addEpgSource(tempName, tempUrl); showAddEpgDialog = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemSettingsDialog(
    title: String,
    name: String,
    url: String,
    enabled: Boolean,
    password: String,
    autoUpdateInterval: String,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, enabled: Boolean, password: String, interval: String) -> Unit
) {
    var editName by remember { mutableStateOf(name) }
    var editUrl by remember { mutableStateOf(url) }
    var editEnabled by remember { mutableStateOf(enabled) }
    var isLocked by remember { mutableStateOf(password.isNotBlank()) }
    var editPassword by remember { mutableStateOf(password) }
    var editInterval by remember { mutableStateOf(autoUpdateInterval) }

    val updateIntervals = listOf("On launch", "15 min", "30 min", "1 hour", "1 day", "Off")
    val updateIntervalLabels = mapOf(
        "On launch" to stringResource(R.string.update_on_launch),
        "15 min" to stringResource(R.string.update_15m),
        "30 min" to stringResource(R.string.update_30m),
        "1 hour" to stringResource(R.string.update_1h),
        "1 day" to stringResource(R.string.update_1d),
        "Off" to stringResource(R.string.update_disabled)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                TextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = editUrl,
                    onValueChange = { editUrl = it },
                    label = { Text(stringResource(R.string.url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.enable_item), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(checked = editEnabled, onCheckedChange = { editEnabled = it })
                }
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.password_lock), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(checked = isLocked, onCheckedChange = { 
                        isLocked = it
                        if (!it) editPassword = ""
                    })
                }

                if (isLocked) {
                    Spacer(Modifier.height(4.dp))
                    TextField(
                        value = editPassword,
                        onValueChange = { editPassword = it },
                        label = { Text(stringResource(R.string.password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.auto_update_interval), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    updateIntervals.forEach { interval ->
                        TvFilterChip(
                            selected = editInterval == interval,
                            onClick = { editInterval = interval },
                            label = { Text(updateIntervalLabels[interval] ?: interval, fontSize = 10.sp) }
                        )
                    }
                }

            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(editName, editUrl, editEnabled, if (isLocked) editPassword else "", editInterval)
                onDismiss()
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun SectionHeader(title: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun TvSwitchSetting(title: String, checked: Boolean, description: String? = null, onCheckedChange: (Boolean) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.02f else 1f)
            .padding(vertical = 4.dp)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f))
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            if (description != null) {
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun TvFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(end = 8.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.1f else 1f)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            label()
        }
    }
}

@Composable
fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ),
    content: @Composable RowScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.05f else 1f)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.extraLarge
            ),
        colors = colors,
        content = content
    )
}

@Composable
fun LanguageSelection(current: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.language), modifier = Modifier.weight(1f))
        TvTextButton(onClick = { onSelect("en") }, selected = current == "en") { Text("EN") }
        TvTextButton(onClick = { onSelect("ru") }, selected = current == "ru") { Text("RU") }
    }
}

@Composable
fun TvTextButton(onClick: () -> Unit, selected: Boolean, content: @Composable RowScope.() -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.1f else 1f)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.small
            ),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        content = content
    )
}

@Composable
fun PlaylistCard(
    name: String,
    url: String,
    selected: Boolean,
    channelCount: Int,
    enabled: Boolean = true,
    hasPassword: Boolean = false,
    onSelect: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .onFocusChanged { isFocused = it.isFocused }
        .scale(if (isFocused) 1.02f else 1f)
        .border(
            width = if (isFocused) 2.dp else 0.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
            shape = MaterialTheme.shapes.medium
        )
        .clickable { onSelect() }, 
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontWeight = if (selected) FontWeight.Bold else null,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                    if (!enabled) {
                        Text(" (${stringResource(R.string.playlist_disabled)})", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    } else if (channelCount > 0) {
                        Text(" ($channelCount ${stringResource(R.string.channels_count)})", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                    if (hasPassword) {
                        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(start = 4.dp))
                    }
                }
                Text(url, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.close), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun EpgCard(
    name: String,
    url: String,
    enabled: Boolean = true,
    hasPassword: Boolean = false,
    onSettings: () -> Unit,
    onDelete: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .onFocusChanged { isFocused = it.isFocused }
        .scale(if (isFocused) 1.02f else 1f)
        .border(
            width = if (isFocused) 2.dp else 0.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
            shape = MaterialTheme.shapes.medium
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                    if (!enabled) {
                        Text(" (${stringResource(R.string.epg_disabled)})", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                    if (hasPassword) {
                        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(start = 4.dp))
                    }
                }
                Text(url, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.close), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun AddDialog(title: String, name: String, url: String, onName: (String) -> Unit, onUrl: (String) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column { TextField(name, onName, label = { Text(stringResource(R.string.name)) }); Spacer(Modifier.height(8.dp)); TextField(url, onUrl, label = { Text(stringResource(R.string.url)) }) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}


private fun openExternalLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    val baseIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        // Keep the URL scheme generic so Chrome, Firefox, Edge, Opera, Brave,
        // Android TV browsers and any other installed browser can handle it.
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // Prefer Android's app chooser, then fall back to the regular resolver.
    // Some Android TV builds have a broken chooser/resolver implementation.
    val opened = runCatching {
        context.startActivity(Intent.createChooser(baseIntent, "Open link with"))
    }.isSuccess || runCatching {
        context.startActivity(baseIntent)
    }.isSuccess

    if (!opened) {
        Toast.makeText(
            context,
            "Не удалось открыть ссылку. Проверьте, что Chrome или другой браузер включён.",
            Toast.LENGTH_LONG
        ).show()
    }
}
