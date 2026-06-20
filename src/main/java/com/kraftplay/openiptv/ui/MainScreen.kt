package com.kraftplay.openiptv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val listSize by viewModel.listSize.collectAsState()
    
    val isEpgLoading by viewModel.isEpgLoading.collectAsState()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var currentTime by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    
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
                Text(
                    stringResource(R.string.categories),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                HorizontalDivider()
                LazyColumn {
                    items(categories) { category ->
                        NavigationDrawerItem(
                            label = { Text(category, fontSize = fontSize.sp) },
                            selected = category == selectedCategory,
                            onClick = {
                                viewModel.selectCategory(category)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
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
                                    val showUrl = !hideUrl || playlists.size <= 1
                                    Text(
                                        if (showUrl) currentPlaylist.url else currentPlaylist.name,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            if (showClock) {
                                Text(currentTime, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, stringResource(R.string.categories))
                        }
                    },
                    actions = {
                        if (isEpgLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp)
                        }
                        IconButton(onClick = { playlists.find { it.isSelected }?.let { viewModel.loadPlaylist(it.url) }; viewModel.refreshAllEpg() }) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.save_reload))
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, stringResource(R.string.settings))
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { category ->
                        FilterChip(
                            selected = category == selectedCategory,
                            onClick = { viewModel.selectCategory(category) },
                            label = { Text(category, fontSize = (fontSize - 2).sp) }
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
                        "Compact" -> 100.dp
                        "Large" -> 180.dp
                        else -> 130.dp
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = minSize),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(channels) { channel ->
                            ChannelCard(
                                channel = channel,
                                currentProgram = currentPrograms[channel.epgId ?: ""] ?: currentPrograms[channel.name],
                                fontSize = fontSize,
                                listSize = listSize,
                                onClick = { onChannelClick(channel); viewModel.updateSetting("last_channel_url", channel.url) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelCard(channel: IptvChannel, currentProgram: EpgProgram?, fontSize: Float, listSize: String, onClick: () -> Unit) {
    val height = when(listSize) {
        "Compact" -> 140.dp
        "Large" -> 220.dp
        else -> 180.dp
    }
    Card(
        modifier = Modifier.fillMaxWidth().height(height).clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(8.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.weight(1f).fillMaxWidth().padding(4.dp), contentScale = ContentScale.Fit)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = channel.name, style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSize.sp), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Medium)
            if (currentProgram != null) {
                Text(text = currentProgram.title, style = MaterialTheme.typography.bodySmall.copy(fontSize = (fontSize - 2).sp), maxLines = if(listSize == "Compact") 1 else 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
