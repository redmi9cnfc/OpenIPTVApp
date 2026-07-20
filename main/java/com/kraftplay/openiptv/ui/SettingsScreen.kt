package com.kraftplay.openiptv.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraftplay.openiptv.R
import com.kraftplay.openiptv.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val epgSources by viewModel.epgSources.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val showClock by viewModel.showClock.collectAsState()
    val language by viewModel.language.collectAsState()
    val hidePlaylistUrl by viewModel.hidePlaylistUrl.collectAsState()
    val listSize by viewModel.listSize.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    
    val showSource by viewModel.showSource.collectAsState()
    val showResolution by viewModel.showResolution.collectAsState()
    val showFps by viewModel.showFps.collectAsState()
    val resFormat by viewModel.resolutionFormat.collectAsState()
    
    val autoStartLast by viewModel.autoStartLast.collectAsState()
    val fullScreenDefault by viewModel.fullScreenByDefault.collectAsState()
    val reloadStreamToggle by viewModel.reloadStreamOnToggle.collectAsState()
    val forceLandscape by viewModel.forceLandscape.collectAsState()
    val backgroundPlayback by viewModel.backgroundPlayback.collectAsState()
    val autoHideTimeout by viewModel.autoHideTimeout.collectAsState()
    val bufferingLevel by viewModel.bufferingLevel.collectAsState()
    val backBehavior by viewModel.backBehavior.collectAsState()
    
    val isEpgLoading by viewModel.isEpgLoading.collectAsState()
    val epgStatus by viewModel.epgStatus.collectAsState()

    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var showAddEpgDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }
    var tempUrl by remember { mutableStateOf("") }

    Scaffold(
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
                PlaylistCard(playlist.name, playlist.url, playlist.isSelected, playlist.channelCount,
                    onSelect = { viewModel.selectPlaylist(playlist.id) },
                    onDelete = { viewModel.deletePlaylist(playlist.id) }
                )
            }
            item {
                TvButton(onClick = { showAddPlaylistDialog = true; tempName = ""; tempUrl = "" }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_playlist))
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.epg_sources))
                    if (isEpgLoading) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(epgStatus, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    } else if (epgStatus.isNotEmpty()) {
                        Text(stringResource(R.string.epg_loaded), fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            items(epgSources) { source ->
                PlaylistCard(source.name, source.url, false, 0,
                    onSelect = { },
                    onDelete = { viewModel.deleteEpgSource(source.id) }
                )
            }
            item {
                TvButton(onClick = { showAddEpgDialog = true; tempName = ""; tempUrl = "" }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_epg))
                }
            }

            item {
                SectionHeader(stringResource(R.string.ui_settings))
                LanguageSelection(language) { viewModel.updateSetting("language", it) }
                
                Text(stringResource(R.string.theme), modifier = Modifier.padding(top = 8.dp))
                Row {
                    listOf("Light", "Dark", "AMOLED").forEach { mode ->
                        TvFilterChip(selected = themeMode == mode, onClick = { viewModel.updateSetting("theme_mode", mode) }, label = { Text(mode) })
                    }
                }

                Text(stringResource(R.string.group_sorting), modifier = Modifier.padding(top = 8.dp))
                val sortOrder by viewModel.groupSortOrder.collectAsState()
                Row {
                    TvFilterChip(selected = sortOrder == "Provider", onClick = { viewModel.updateSetting("group_sort_order", "Provider") }, label = { Text(stringResource(R.string.sort_provider)) })
                    TvFilterChip(selected = sortOrder == "Alphabetical", onClick = { viewModel.updateSetting("group_sort_order", "Alphabetical") }, label = { Text(stringResource(R.string.sort_alphabetical)) })
                }

                Text(stringResource(R.string.accent_color), modifier = Modifier.padding(top = 8.dp))
                Row {
                    listOf("Blue", "Green", "Red").forEach { color ->
                        TvFilterChip(selected = accentColor == color, onClick = { viewModel.updateSetting("accent_color", color) }, label = { Text(color) })
                    }
                }

                Text(stringResource(R.string.list_size), modifier = Modifier.padding(top = 8.dp))
                Row {
                    listOf("Compact", "Standard", "Large").forEach { size ->
                        TvFilterChip(selected = listSize == size, onClick = { viewModel.updateSetting("list_size", size) }, label = { Text(size) })
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
                TvSwitchSetting(stringResource(R.string.show_source), showSource) { viewModel.updateSetting("show_source", it) }
                TvSwitchSetting(stringResource(R.string.show_resolution), showResolution) { viewModel.updateSetting("show_resolution", it) }
                TvSwitchSetting(stringResource(R.string.show_fps), showFps) { viewModel.updateSetting("show_fps", it) }
                
                Text(stringResource(R.string.res_format), modifier = Modifier.padding(top = 8.dp))
                Row {
                    TvFilterChip(selected = resFormat == "Dimensions", onClick = { viewModel.updateSetting("resolution_format", "Dimensions") }, label = { Text(stringResource(R.string.res_format_dims)) })
                    TvFilterChip(selected = resFormat == "Labels", onClick = { viewModel.updateSetting("resolution_format", "Labels") }, label = { Text(stringResource(R.string.res_format_label)) })
                }

                TvSwitchSetting(stringResource(R.string.auto_start_last), autoStartLast) { viewModel.updateSetting("auto_start_last", it) }
                TvSwitchSetting(stringResource(R.string.full_screen), fullScreenDefault) { viewModel.updateSetting("full_screen_default", it) }
                TvSwitchSetting(stringResource(R.string.reload_stream), reloadStreamToggle, stringResource(R.string.reload_stream_desc)) { viewModel.updateSetting("reload_stream_toggle", it) }
                TvSwitchSetting(stringResource(R.string.landscape_mode), forceLandscape, stringResource(R.string.landscape_mode_desc)) { viewModel.updateSetting("force_landscape", it) }
                TvSwitchSetting(stringResource(R.string.background_playback), backgroundPlayback) { viewModel.updateSetting("background_playback", it) }
                
                Text(stringResource(R.string.auto_hide_info) + ": " + (if(autoHideTimeout == 0) stringResource(R.string.never) else stringResource(R.string.seconds, autoHideTimeout)))
                Row {
                    listOf(0, 3, 5, 10).forEach { s ->
                        TvFilterChip(selected = autoHideTimeout == s, onClick = { viewModel.updateSetting("auto_hide_timeout", s) }, label = { Text(if(s==0) stringResource(R.string.never) else stringResource(R.string.seconds, s)) })
                    }
                }

                Text(stringResource(R.string.buffering), modifier = Modifier.padding(top = 8.dp))
                Row {
                    listOf("Low", "Standard", "High").forEach { b ->
                        TvFilterChip(selected = bufferingLevel == b, onClick = { viewModel.updateSetting("buffering_level", b) }, label = { Text(b) })
                    }
                }

                Text(stringResource(R.string.sleep_timer), modifier = Modifier.padding(top = 8.dp))
                val sleepMinutes by viewModel.sleepTimer.collectAsState()
                Row {
                    listOf(0, 15, 30, 60, 120).forEach { m ->
                        TvFilterChip(selected = sleepMinutes == m, onClick = { viewModel.sleepTimer.value = m }, label = { Text(if(m==0) stringResource(R.string.off) else stringResource(R.string.minutes, m)) })
                    }
                }

                SectionHeader(stringResource(R.string.back_behavior))
                Row {
                    listOf("Exit", "Back to list", "Confirm").forEach { b ->
                        TvFilterChip(selected = backBehavior == b, onClick = { viewModel.updateSetting("back_behavior", b) }, label = { Text(b) })
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                TvButton(onClick = { viewModel.clearData() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.clear_data))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.version) + " 4.5.3", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showAddPlaylistDialog) {
        AddDialog(stringResource(R.string.new_playlist), tempName, tempUrl, { tempName = it }, { tempUrl = it }, { showAddPlaylistDialog = false }, { viewModel.addPlaylist(tempName, tempUrl); showAddPlaylistDialog = false })
    }
    if (showAddEpgDialog) {
        AddDialog(stringResource(R.string.add_epg), tempName, tempUrl, { tempName = it }, { tempUrl = it }, { showAddEpgDialog = false }, { viewModel.addEpgSource(tempName, tempUrl); showAddEpgDialog = false })
    }
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
    Column(modifier = Modifier
        .fillMaxWidth()
        .onFocusChanged { isFocused = it.isFocused }
        .scale(if (isFocused) 1.02f else 1f)
        .clickable { onCheckedChange(!checked) }
        .padding(vertical = 8.dp)
        .border(if (isFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
        .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (description != null) {
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}

@Composable
fun TvFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = Modifier
            .padding(end = 8.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.1f else 1f)
            .border(if (isFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
    )
}

@Composable
fun TvButton(onClick: () -> Unit, modifier: Modifier = Modifier, colors: ButtonColors = ButtonDefaults.buttonColors(), content: @Composable RowScope.() -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.05f else 1f)
            .border(if (isFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraLarge),
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
            .border(if (isFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant),
        content = content
    )
}

@Composable
fun PlaylistCard(name: String, url: String, selected: Boolean, channelCount: Int, onSelect: () -> Unit, onDelete: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .onFocusChanged { isFocused = it.isFocused }
        .scale(if (isFocused) 1.02f else 1f)
        .border(if (isFocused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
        .clickable { onSelect() }, 
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontWeight = if (selected) FontWeight.Bold else null)
                    if (channelCount > 0) {
                        Text(" ($channelCount ${stringResource(R.string.channels_count)})", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                Text(url, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
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
