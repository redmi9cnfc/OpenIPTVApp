package com.kraftplay.openiptv.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.focusable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kraftplay.openiptv.R
import com.kraftplay.openiptv.model.EpgProgram
import com.kraftplay.openiptv.model.IptvChannel
import com.kraftplay.openiptv.service.EpgReminderManager
import com.kraftplay.openiptv.viewmodel.MainViewModel
import com.kraftplay.openiptv.util.CatchupUrlBuilder
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun getCategoryDisplayName(category: String): String {
    return when(category) {
        "All" -> stringResource(R.string.cat_all)
        "Favorites" -> stringResource(R.string.cat_favorites)
        "Recent" -> stringResource(R.string.cat_recent)
        else -> category
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onChannelClick: (IptvChannel) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val channels by viewModel.filteredChannels.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val hiddenCategories by viewModel.hiddenCategories.collectAsState()
    val visibleCategories = categories.filter { it in listOf("All", "Favorites", "Recent") || !hiddenCategories.contains(it) }
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val categoryDisplayMode by viewModel.categoryDisplayMode.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    BackHandler(enabled = categoryDisplayMode == "Folders" && selectedFolder != null) { viewModel.selectFolder(null) }
    val categoryCounts by viewModel.categoryChannelCounts.collectAsState()

    val isLoading by viewModel.isLoading.collectAsState()
    val playlistError by viewModel.playlistError.collectAsState()
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
    val uiStyle by viewModel.uiStyle.collectAsState()
    val catchupMode by viewModel.catchupMode.collectAsState()
    
    var currentTime by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showPlaylistMenu by remember { mutableStateOf(false) }
    
    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var showWifiTransfer by remember { mutableStateOf(false) }
    val playlistFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { selected ->
            val name = context.contentResolver.query(selected, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else "playlist.m3u"
            } ?: "playlist.m3u"
            viewModel.importPlaylistFile(selected, name)
        }
    }
    var tempPlaylistName by remember { mutableStateOf("") }
    var tempPlaylistUrl by remember { mutableStateOf("") }

    var showChannelMenu by remember { mutableStateOf<IptvChannel?>(null) }
    var showPasswordDialog by remember { mutableStateOf<Triple<IptvChannel, String, String>?>(null) }
    var tempPassword1 by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    var showEpgSchedule by remember { mutableStateOf<IptvChannel?>(null) }
    var showEpgSearchDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showClock) {
        while (showClock) {
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("OpenIPTV", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            val currentPlaylist = playlists.find { it.isSelected }
                            if (currentPlaylist != null) {
                                val showUrl = !hideUrl
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (showUrl) currentPlaylist.url else currentPlaylist.name,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box {
                                        IconButton(
                                            onClick = { showPlaylistMenu = true },
                                            modifier = Modifier.size(28.dp)
                                        ) { Icon(Icons.Default.Check, contentDescription = "Select playlist", modifier = Modifier.size(18.dp)) }
                                        DropdownMenu(
                                            expanded = showPlaylistMenu,
                                            onDismissRequest = { showPlaylistMenu = false }
                                        ) {
                                            playlists.filter { it.enabled }.forEach { playlist ->
                                                DropdownMenuItem(
                                                    text = { Text((if (playlist.isSelected) "✓ " else "") + playlist.name, maxLines = 1) },
                                                    onClick = {
                                                        showPlaylistMenu = false
                                                        viewModel.selectPlaylist(playlist.id)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (showClock) Text(currentTime, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isEpgLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp)
                        IconButton(onClick = { isSearchExpanded = !isSearchExpanded }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Search, stringResource(R.string.search), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {
                            val nextMode = if (categoryDisplayMode == "Chips") "Folders" else "Chips"
                            viewModel.setCategoryDisplayMode(nextMode)
                        }) {
                            Icon(if (categoryDisplayMode == "Chips") Icons.Default.Folder else Icons.Default.ViewStream, "Toggle Display Mode")
                        }
                        IconButton(onClick = { showEpgSearchDialog = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.ManageSearch, stringResource(R.string.epg_search), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {
                            playlists.find { it.isSelected }?.let { viewModel.loadPlaylist(it.url) }
                            viewModel.refreshAllEpg()
                        }) {
                            Icon(Icons.Default.Refresh, "Refresh", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (!playlistError.isNullOrBlank()) {
                Text(
                    text = playlistError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            if (playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.no_playlists), style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            tempPlaylistName = ""
                            tempPlaylistUrl = ""
                            showAddPlaylistDialog = true
                        }, modifier = Modifier.fillMaxWidth(0.85f)) {
                            Icon(Icons.Default.Link, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.add_first_playlist))
                        }
                        OutlinedButton(
                            onClick = { playlistFilePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Icon(Icons.Default.FileOpen, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.import_file))
                        }
                        OutlinedButton(
                            onClick = { showWifiTransfer = true },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Icon(Icons.Default.Wifi, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.wifi_transfer_title))
                        }
                    }
                }
            } else {
                if (isSearchExpanded || searchQuery.isNotEmpty()) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        placeholder = { Text(stringResource(R.string.search)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                viewModel.updateSearchQuery("")
                                isSearchExpanded = false
                            }) {
                                Icon(Icons.Default.Close, null)
                            }
                        },
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                    )
                }

                if (categoryDisplayMode == "Chips") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleCategories) { category ->
                            var isFocused by remember { mutableStateOf(false) }
                            FilterChip(
                                selected = category == selectedCategory,
                                onClick = { viewModel.selectCategory(category) },
                                label = { Text(getCategoryDisplayName(category), fontSize = (fontSize - 2).sp) },
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
                                    uiStyle = uiStyle,
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
                } else {
                    // Folders Display Mode
                    if (selectedFolder == null) {
                        Text(stringResource(R.string.categories), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(visibleCategories) { category ->
                                val count = categoryCounts[category] ?: 0
                                CategoryFolderCard(
                                    title = category,
                                    count = count,
                                    uiStyle = uiStyle,
                                    onClick = { viewModel.selectFolder(category) }
                                )
                            }
                        }
                    } else {
                        // Inside selected Category Folder
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            TextButton(onClick = { viewModel.selectFolder(null) }) {
                                Icon(Icons.Default.ArrowBack, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.back_to_folders), fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(getCategoryDisplayName(selectedFolder ?: ""), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

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
                                    uiStyle = uiStyle,
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
    }

    if (showAddPlaylistDialog) {
        AddDialog(
            title = stringResource(R.string.new_playlist),
            name = tempPlaylistName,
            url = tempPlaylistUrl,
            onName = { tempPlaylistName = it },
            onUrl = { tempPlaylistUrl = it },
            onDismiss = { showAddPlaylistDialog = false },
            onConfirm = {
                viewModel.addPlaylist(tempPlaylistName, tempPlaylistUrl)
                showAddPlaylistDialog = false
            }
        )
    }

    if (showWifiTransfer) {
        WifiTransferDialog(viewModel = viewModel, onDismiss = { showWifiTransfer = false })
    }

    if (showEpgSearchDialog) {
        var epgQuery by remember { mutableStateOf("") }
        val searchResults by viewModel.searchEpgPrograms(epgQuery).collectAsState(emptyList())
        val timeFormat = SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault())

        AlertDialog(
            onDismissRequest = { showEpgSearchDialog = false },
            title = { Text(stringResource(R.string.epg_search)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                    TextField(
                        value = epgQuery,
                        onValueChange = { epgQuery = it },
                        placeholder = { Text(stringResource(R.string.search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(searchResults) { program ->
                            val matchedChannel = channels.find { it.epgId?.equals(program.channelId, ignoreCase = true) == true || it.name.equals(program.channelId, ignoreCase = true) }
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(program.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("${timeFormat.format(program.start)} | Channel: ${program.channelId}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (program.endTime <= System.currentTimeMillis() && matchedChannel != null) {
                                        val archiveUrl = CatchupUrlBuilder.build(
                                            liveUrl = matchedChannel.url,
                                            sourceTemplate = matchedChannel.catchupSource,
                                            mode = if (catchupMode == "Auto") (matchedChannel.catchup ?: "Auto") else catchupMode,
                                            startTimeMs = program.startTime,
                                            endTimeMs = program.endTime
                                        )
                                        if (archiveUrl != null) {
                                            TextButton(onClick = {
                                                showEpgSearchDialog = false
                                                onChannelClick(matchedChannel.copy(url = archiveUrl, name = "${matchedChannel.name} — ${program.title}"))
                                            }) { Text("Archive") }
                                        }
                                    }
                                    IconButton(onClick = {
                                        EpgReminderManager.scheduleReminder(
                                            context = context,
                                            programTitle = program.title,
                                            channelName = program.channelId,
                                            channelUrl = matchedChannel?.url ?: "",
                                            startTimeMs = program.startTime
                                        )
                                        Toast.makeText(context, context.getString(R.string.reminder_set), Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.NotificationsActive, stringResource(R.string.set_reminder))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEpgSearchDialog = false }) { Text(stringResource(R.string.close)) } }
        )
    }

    if (showChannelMenu != null) {
        val channel = showChannelMenu!!
        var ignoreLongPressRelease by remember(channel.url) { mutableStateOf(true) }
        LaunchedEffect(channel.url) {
            // If the key-up was delivered to the old window, do not block the next user click.
            delay(700)
            ignoreLongPressRelease = false
        }
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
                    // A long OK opens this dialog while the physical key is still held.
                    // Consume that key's release so it cannot activate the focused first item.
                    .onPreviewKeyEvent { event ->
                        val keyCode = event.nativeKeyEvent.keyCode
                        val isConfirm = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                        if (isConfirm && event.nativeKeyEvent.action == KeyEvent.ACTION_UP && ignoreLongPressRelease) {
                            ignoreLongPressRelease = false
                            true
                        } else {
                            false
                        }
                    }
            ) {
                val menuFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    delay(100)
                    runCatching { menuFocusRequester.requestFocus() }
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
                        onClick = { viewModel.togglePinChannel(channel.url); showChannelMenu = null },
                        icon = Icons.Default.PushPin,
                        text = if (channel.isPinned) stringResource(R.string.unpin_channel) else stringResource(R.string.pin_channel)
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
                    Text("No programme information")
                } else {
                    LazyColumn(modifier = Modifier.height(400.dp)) {
                        items(schedule) { program ->
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(timeFormat.format(program.start), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(program.title, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.weight(1f))
                                    if (program.endTime <= System.currentTimeMillis()) {
                                        val archiveUrl = CatchupUrlBuilder.build(
                                            liveUrl = channel.url,
                                            sourceTemplate = channel.catchupSource,
                                            mode = if (catchupMode == "Auto") (channel.catchup ?: "Auto") else catchupMode,
                                            startTimeMs = program.startTime,
                                            endTimeMs = program.endTime
                                        )
                                        if (archiveUrl != null) {
                                            TextButton(onClick = {
                                                showEpgSchedule = null
                                                onChannelClick(channel.copy(url = archiveUrl, name = "${channel.name} — ${program.title}"))
                                            }) { Text("Archive") }
                                        }
                                    }
                                    IconButton(onClick = {
                                        EpgReminderManager.scheduleReminder(
                                            context = context,
                                            programTitle = program.title,
                                            channelName = channel.name,
                                            channelUrl = channel.url,
                                            startTimeMs = program.startTime
                                        )
                                        Toast.makeText(context, context.getString(R.string.reminder_set), Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.NotificationsActive, stringResource(R.string.set_reminder))
                                    }
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
fun CategoryFolderCard(title: String, count: Int, uiStyle: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = if (uiStyle == "LiquidGlass") RoundedCornerShape(24.dp) else MaterialTheme.shapes.medium,
        tonalElevation = if (isFocused) 8.dp else 1.dp,
        color = if (uiStyle == "LiquidGlass") Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.08f else 1f)
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = if (uiStyle == "LiquidGlass") RoundedCornerShape(24.dp) else MaterialTheme.shapes.medium
            )
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = when(title) {
                    "Favorites" -> Icons.Default.Favorite
                    "Recent" -> Icons.Default.History
                    else -> Icons.Default.Folder
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(getCategoryDisplayName(title), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text(stringResource(R.string.folder_channels_count, count), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChannelCard(channel: IptvChannel, currentProgram: EpgProgram?, nextProgram: EpgProgram?, viewingTime: Long, fontSize: Float, listSize: String, uiStyle: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val height = when(listSize) {
        "Compact" -> 145.dp
        "Large" -> 220.dp
        else -> 185.dp
    }
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    var isFocused by remember { mutableStateOf(false) }
    var isLongPressHandled by remember { mutableStateOf(false) }
    var isOkKeyDown by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    val keyScope = rememberCoroutineScope()

    Surface(
        shape = if (uiStyle == "LiquidGlass") RoundedCornerShape(24.dp) else MaterialTheme.shapes.medium,
        tonalElevation = if (isFocused) 8.dp else 1.dp,
        color = if (uiStyle == "LiquidGlass") Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.08f else 1f)
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = if (uiStyle == "LiquidGlass") RoundedCornerShape(24.dp) else MaterialTheme.shapes.medium
            )
            // Handle remote OK before clickable semantics. Some TV remotes do not
            // emit repeat events, so use a timer rather than relying on repeatCount.
            .focusable()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                val isConfirm = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (!isConfirm) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (!isOkKeyDown) {
                            isOkKeyDown = true
                            isLongPressHandled = false
                            longPressJob?.cancel()
                            longPressJob = keyScope.launch {
                                delay(550)
                                if (isOkKeyDown && !isLongPressHandled) {
                                    isLongPressHandled = true
                                    onLongClick()
                                }
                            }
                        }
                        true
                    }
                    KeyEvent.ACTION_UP -> {
                        isOkKeyDown = false
                        longPressJob?.cancel()
                        longPressJob = null
                        // Keep the long-press flag until the next ACTION_DOWN so a
                        // combinedClickable release cannot trigger a normal click and close the menu.
                        if (!isLongPressHandled) onClick()
                        true
                    }
                    else -> true
                }
            }
            .combinedClickable(
                onClick = { if (!isLongPressHandled) onClick() },
                onLongClick = onLongClick
            )
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
                    val hasCatchup = !channel.catchupSource.isNullOrBlank() ||
                        (!channel.catchup.isNullOrBlank() &&
                         !channel.catchup.equals("none", true) &&
                         !channel.catchup.equals("0", true))
                    if (hasCatchup) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(18.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(5.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
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
                        text = "No programme information",
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

            Row(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (channel.isPinned) {
                    Icon(Icons.Default.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                if (channel.isFavorite) {
                    Icon(Icons.Default.Favorite, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
