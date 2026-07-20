package com.kraftplay.openiptv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.onKeyEvent
import android.view.KeyEvent
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kraftplay.openiptv.R
import com.kraftplay.openiptv.model.IptvChannel
import com.kraftplay.openiptv.model.EpgProgram
import com.kraftplay.openiptv.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onChannelClick: (IptvChannel) -> Unit,
    onSettingsClick: () -> Unit
) {
    val channels by viewModel.filteredChannels.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val showClock by viewModel.showClock.collectAsState()
    val hideUrl by viewModel.hidePlaylistUrl.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val currentPrograms by viewModel.currentPrograms.collectAsState()
    val nextPrograms by viewModel.nextPrograms.collectAsState()
    val listSize by viewModel.listSize.collectAsState()
    val isEpgLoading by viewModel.isEpgLoading.collectAsState()
    val channelViewingTimes by viewModel.channelViewingTimes.collectAsState()
    
    var currentTime by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    
    var showChannelMenu by remember { mutableStateOf<IptvChannel?>(null) }
    var showPasswordDialog by remember { mutableStateOf<Triple<IptvChannel, String, String>?>(null) }
    var tempPassword1 by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    var showEpgSchedule by remember { mutableStateOf<IptvChannel?>(null) }

    LaunchedEffect(showClock) {
        while (showClock) {
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("OpenIPTV", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            val currentPlaylist = playlists.find { it.isSelected }
                            if (currentPlaylist != null) {
                                val showUrl = !hideUrl
                                Text(if (showUrl) currentPlaylist.url else currentPlaylist.name, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        if (showClock) Text(currentTime, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                    }
                },
                actions = {
                    if (isEpgLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { playlists.find { it.isSelected }?.let { viewModel.loadPlaylist(it.url) }; viewModel.refreshAllEpg() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings)) },
                            onClick = { showMenu = false; onSettingsClick() },
                            leadingIcon = { Icon(Icons.Default.Settings, null) }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.no_playlists), style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onSettingsClick) {
                            Icon(Icons.Default.Add, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.add_first_playlist))
                        }
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        var isFocused by remember { mutableStateOf(false) }
                        FilterChip(
                            selected = category == selectedCategory,
                            onClick = { viewModel.selectCategory(category) },
                            label = { Text(category, fontSize = (fontSize - 2).sp) },
                            leadingIcon = { 
                                when(category) {
                                    "Favorites" -> Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp))
                                    "Recent" -> Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier
                                .onFocusChanged { isFocused = it.isFocused }
                                .scale(if (isFocused) 1.1f else 1f)
                                .border(
                                    width = if (isFocused) 2.dp else 0.dp,
                                    color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = MaterialTheme.shapes.small
                                ),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            ),
                            border = null
                        )
                    }
                }

                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    placeholder = { Text(stringResource(R.string.search)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { /* Done by flow */ })
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    val minSize = when(listSize) {
                        "Compact" -> 140.dp
                        "Large" -> 220.dp
                        else -> 160.dp
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = minSize),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(channels) { channel ->
                            ChannelCard(
                                channel = channel,
                                currentProgram = currentPrograms[channel.url],
                                nextProgram = nextPrograms[channel.url],
                                viewingTime = channelViewingTimes[channel.url] ?: 0L,
                                fontSize = fontSize,
                                listSize = listSize,
                                onClick = { 
                                    if (channel.isLocked) {
                                        showPasswordDialog = Triple(channel, "play", "")
                                    } else {
                                        onChannelClick(channel)
                                        viewModel.updateSetting("last_channel_url", channel.url)
                                    }
                                },
                                onLongClick = { showChannelMenu = channel }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showChannelMenu != null) {
        val channel = showChannelMenu!!
        Dialog(
            onDismissRequest = { showChannelMenu = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .width(320.dp)
                    .padding(16.dp)
            ) {
                val menuFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    menuFocusRequester.requestFocus()
                }

                Column(modifier = Modifier.padding(12.dp)) {
                    Text(channel.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp), maxLines = 1)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    TvMenuItem(
                        modifier = Modifier.focusRequester(menuFocusRequester),
                        onClick = { viewModel.toggleFavorite(channel.url); showChannelMenu = null },
                        icon = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        text = if (channel.isFavorite) stringResource(R.string.remove_from_favorites) else stringResource(R.string.add_to_favorites)
                    )
                    TvMenuItem(
                        onClick = { 
                            if (channel.isLocked) {
                                showPasswordDialog = Triple(channel, "unlock", "")
                            } else {
                                showPasswordDialog = Triple(channel, "lock", "")
                            }
                            showChannelMenu = null 
                        },
                        icon = if (channel.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        text = if (channel.isLocked) stringResource(R.string.unlock_channel) else stringResource(R.string.lock_channel)
                    )
                    if (channel.isLocked) {
                        TvMenuItem(
                            onClick = { showPasswordDialog = Triple(channel, "change_step1", ""); showChannelMenu = null },
                            icon = Icons.Default.Password,
                            text = stringResource(R.string.change_password)
                        )
                    }
                    TvMenuItem(
                        onClick = { showEpgSchedule = channel; showChannelMenu = null },
                        icon = Icons.Default.List,
                        text = "Full Schedule"
                    )
                    Spacer(Modifier.height(8.dp))
                    TvMenuItem(
                        onClick = { showChannelMenu = null },
                        icon = Icons.Default.Close,
                        text = stringResource(R.string.close)
                    )
                }
            }
        }
    }

    if (showPasswordDialog != null) {
        val (channel, action, oldPass) = showPasswordDialog!!
        val channelSavedPass = viewModel.getChannelPassword(channel.url) ?: ""
        
        val onConfirm = {
            when (action) {
                "lock" -> {
                    if (tempPassword1.isNotEmpty()) {
                        viewModel.lockChannel(channel.url, tempPassword1)
                        showPasswordDialog = null
                        tempPassword1 = ""
                    }
                }
                "play" -> {
                    if (tempPassword1 == channelSavedPass) {
                        onChannelClick(channel)
                        viewModel.updateSetting("last_channel_url", channel.url)
                        showPasswordDialog = null
                        tempPassword1 = ""
                    } else passwordError = true
                }
                "unlock" -> {
                    if (viewModel.unlockChannel(channel.url, tempPassword1)) {
                        showPasswordDialog = null
                        tempPassword1 = ""
                    } else passwordError = true
                }
                "change_step1" -> {
                    if (tempPassword1 == channelSavedPass) {
                        val currentTemp1 = tempPassword1
                        tempPassword1 = ""
                        showPasswordDialog = Triple(channel, "change_step2", currentTemp1)
                        passwordError = false
                    } else passwordError = true
                }
                "change_step2" -> {
                    if (tempPassword1.isNotEmpty()) {
                        viewModel.changeChannelPassword(channel.url, oldPass, tempPassword1)
                        showPasswordDialog = null
                        tempPassword1 = ""
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showPasswordDialog = null; tempPassword1 = ""; passwordError = false },
            title = { 
                Text(when(action) {
                    "lock" -> stringResource(R.string.setup_password)
                    "unlock", "play" -> stringResource(R.string.enter_password)
                    "change_step1" -> "Enter current password"
                    "change_step2" -> "Enter new password"
                    else -> ""
                })
            },
            text = {
                Column {
                    TextField(
                        value = tempPassword1,
                        onValueChange = { tempPassword1 = it },
                        label = { Text(stringResource(R.string.password)) },
                        isError = passwordError,
                        modifier = Modifier.fillMaxWidth().onKeyEvent {
                            if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER || it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                                if (it.nativeKeyEvent.action == KeyEvent.ACTION_UP) onConfirm()
                                true
                            } else false
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onConfirm() })
                    )
                    if (passwordError) {
                        Text(stringResource(R.string.incorrect_password), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = null; tempPassword1 = ""; passwordError = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showEpgSchedule != null) {
        val channel = showEpgSchedule!!
        val schedule by viewModel.getChannelSchedule(channel.url).collectAsState(emptyList())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        AlertDialog(
            onDismissRequest = { showEpgSchedule = null },
            title = { Text("Schedule: ${channel.name}") },
            text = {
                if (schedule.isEmpty()) {
                    Text("No program information")
                } else {
                    LazyColumn(modifier = Modifier.height(400.dp)) {
                        items(schedule) { program ->
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(timeFormat.format(program.start), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(program.title, fontWeight = FontWeight.Medium)
                                }
                                if (program.description?.isNotEmpty() == true) {
                                    Text(program.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEpgSchedule = null }) { Text(stringResource(R.string.close)) } }
        )
    }
}

@Composable
fun TvMenuItem(onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.05f else 1f)
            .padding(vertical = 4.dp)
            .border(if (isFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = MaterialTheme.shapes.small
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Icon(icon, null, tint = if(isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(12.dp))
            Text(text, color = if(isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if(isFocused) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCard(channel: IptvChannel, currentProgram: EpgProgram?, nextProgram: EpgProgram?, viewingTime: Long, fontSize: Float, listSize: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val height = when(listSize) {
        "Compact" -> 160.dp
        "Large" -> 240.dp
        else -> 200.dp
    }
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    var isFocused by remember { mutableStateOf(false) }
    var isLongPressRunning by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (isFocused) 8.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.08f else 1f)
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            )
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        if (event.nativeKeyEvent.repeatCount > 0 && !isLongPressRunning) {
                            isLongPressRunning = true
                            onLongClick()
                            return@onKeyEvent true
                        }
                    } else if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                        if (isLongPressRunning) {
                            isLongPressRunning = false
                            return@onKeyEvent true
                        }
                        // If it wasn't a long press, it's a short click
                        onClick()
                        return@onKeyEvent true
                    }
                }
                false
            }
            .clickable(enabled = false) {} // Just to show ripple if needed, but logic is in onKeyEvent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(8.dp).fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = fontSize.sp),
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (channel.isLocked) {
                    Icon(Icons.Default.Lock, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                } else if (currentProgram != null) {
                    val now = System.currentTimeMillis()
                    val total = (currentProgram.endTime - currentProgram.startTime).toFloat()
                    val progress = if (total > 0) (now - currentProgram.startTime) / total else 0f
                    
                    Text(
                        text = currentProgram.title,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = (fontSize - 2).sp),
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${timeFormat.format(currentProgram.start)} - ${timeFormat.format(currentProgram.stop)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = (fontSize - 4).sp),
                        color = Color.Gray
                    )
                    
                    LinearProgressIndicator(
                        progress = progress.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().height(2.dp).padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    if (nextProgram != null) {
                        Text(
                            text = "Next: ${nextProgram.title}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = (fontSize - 4).sp),
                            maxLines = 1,
                            color = Color.Gray
                        )
                    }
                } else {
                    Text(
                        text = "No program information",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = (fontSize - 2).sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (viewingTime > 0) {
                val hours = viewingTime / 3600
                val minutes = (viewingTime % 3600) / 60
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.padding(4.dp).align(Alignment.TopStart)
                ) {
                    Text(
                        text = String.format("%02d:%02d h", hours, minutes),
                        color = Color.White,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            if (channel.isFavorite) {
                Icon(Icons.Default.Favorite, null, tint = Color.Red, modifier = Modifier.size(16.dp).align(Alignment.TopEnd).padding(4.dp))
            }
        }
    }
}
