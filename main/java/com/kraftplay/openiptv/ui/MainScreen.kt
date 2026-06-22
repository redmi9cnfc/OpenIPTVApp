package com.kraftplay.openiptv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.onKeyEvent
import android.view.KeyEvent
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
    val globalPassword by viewModel.parentalPassword.collectAsState()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentTime by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    
    var showChannelMenu by remember { mutableStateOf<IptvChannel?>(null) }
    var showPasswordDialog by remember { mutableStateOf<Pair<IptvChannel, String>?>(null) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var tempPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    var showEpgSchedule by remember { mutableStateOf<IptvChannel?>(null) }

    LaunchedEffect(showClock) {
        while (showClock) {
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Text(stringResource(R.string.categories), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()
                LazyColumn {
                    items(categories) { category ->
                        NavigationDrawerItem(
                            label = { Text(category, fontSize = fontSize.sp) },
                            selected = category == selectedCategory,
                            onClick = { viewModel.selectCategory(category); scope.launch { drawerState.close() } },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            icon = { if(category == "Favorites") Icon(Icons.Default.Favorite, null) }
                        )
                    }
                }
            }
        }
    ) {
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
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, stringResource(R.string.categories)) } },
                    actions = {
                        if (isEpgLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp)
                        IconButton(onClick = { playlists.find { it.isSelected }?.let { viewModel.loadPlaylist(it.url) }; viewModel.refreshAllEpg() }) { Icon(Icons.Default.Refresh, stringResource(R.string.save_reload)) }
                        IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
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
                    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { category ->
                            FilterChip(
                                selected = category == selectedCategory,
                                onClick = { viewModel.selectCategory(category) },
                                label = { Text(category, fontSize = (fontSize - 2).sp) },
                                leadingIcon = { if(category == "Favorites") Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp)) }
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
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        val minSize = when(listSize) {
                            "Compact" -> 140.dp
                            "Large" -> 220.dp
                            else -> 180.dp
                        }
                        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = minSize), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(channels) { channel ->
                                ChannelCard(
                                    channel = channel,
                                    currentProgram = currentPrograms[channel.name] ?: currentPrograms[channel.epgId ?: ""],
                                    nextProgram = nextPrograms[channel.name] ?: nextPrograms[channel.epgId ?: ""],
                                    viewingTime = channelViewingTimes[channel.url] ?: 0L,
                                    fontSize = fontSize,
                                    listSize = listSize,
                                    onClick = { 
                                        if (channel.isLocked) {
                                            showPasswordDialog = channel to "play"
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
    }

    if (showChannelMenu != null) {
        val channel = showChannelMenu!!
        AlertDialog(
            onDismissRequest = { showChannelMenu = null },
            title = { Text(channel.name) },
            text = {
                Column {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.toggleFavorite(channel.url); showChannelMenu = null }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (channel.isFavorite) stringResource(R.string.remove_from_favorites) else stringResource(R.string.add_to_favorites))
                        }
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { 
                            if (channel.isLocked) {
                                showPasswordDialog = channel to "unlock"
                            } else {
                                viewModel.lockChannel(channel.url)
                            }
                            showChannelMenu = null 
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(if (channel.isLocked) Icons.Default.LockOpen else Icons.Default.Lock, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (channel.isLocked) stringResource(R.string.unlock_channel) else stringResource(R.string.lock_channel))
                        }
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showChangePasswordDialog = true; showChannelMenu = null }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Password, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.change_password))
                        }
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showEpgSchedule = channel; showChannelMenu = null }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.List, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Full Schedule")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChannelMenu = null }) { Text(stringResource(R.string.close)) } }
        )
    }

    if (showEpgSchedule != null) {
        val channel = showEpgSchedule!!
        val schedule by viewModel.getChannelSchedule(channel.epgId ?: channel.name).collectAsState(emptyList())
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

    if (showChangePasswordDialog) {
        var currentCheck by remember { mutableStateOf("") }
        var newPwd by remember { mutableStateOf("") }
        var step by remember { mutableStateOf(1) }
        var error by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text(if(step == 1) "Confirm current password" else stringResource(R.string.set_password)) },
            text = {
                TextField(if(step == 1) currentCheck else newPwd, { if(step==1) currentCheck = it else newPwd = it }, 
                label = { Text(stringResource(R.string.password)) }, isError = error)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (step == 1) {
                        if (currentCheck == globalPassword) { step = 2; error = false } else error = true
                    } else {
                        viewModel.setParentalPassword(newPwd); showChangePasswordDialog = false
                    }
                }) { Text("Next / OK") }
            }
        )
    }

    if (showPasswordDialog != null) {
        val (channel, action) = showPasswordDialog!!
        AlertDialog(
            onDismissRequest = { showPasswordDialog = null; tempPassword = ""; passwordError = false },
            title = { Text(stringResource(R.string.enter_password)) },
            text = {
                Column {
                    TextField(
                        value = tempPassword,
                        onValueChange = { tempPassword = it },
                        label = { Text(stringResource(R.string.password)) },
                        isError = passwordError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passwordError) {
                        Text(stringResource(R.string.incorrect_password), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tempPassword == globalPassword) {
                        if (action == "play") {
                            onChannelClick(channel)
                            viewModel.updateSetting("last_channel_url", channel.url)
                        } else if (action == "unlock") {
                            viewModel.unlockChannel(channel.url)
                        }
                        showPasswordDialog = null
                        tempPassword = ""
                    } else {
                        passwordError = true
                    }
                }) { Text(if(action == "unlock") "Unlock" else "OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = null; tempPassword = ""; passwordError = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
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
    var isLongPressedOnTv by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .onKeyEvent { 
                if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                    when (it.nativeKeyEvent.action) {
                        KeyEvent.ACTION_DOWN -> {
                            if (it.nativeKeyEvent.isLongPress) {
                                isLongPressedOnTv = true
                                onLongClick()
                                return@onKeyEvent true
                            }
                        }
                        KeyEvent.ACTION_UP -> {
                            if (isLongPressedOnTv) {
                                isLongPressedOnTv = false
                                return@onKeyEvent true
                            }
                        }
                    }
                }
                false
            }
            .combinedClickable(
                onClick = { if (!isLongPressedOnTv) onClick() }, 
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
            
            // Top Left Viewing Time
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
