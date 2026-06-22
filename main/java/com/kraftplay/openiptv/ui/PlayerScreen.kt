package com.kraftplay.openiptv.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.kraftplay.openiptv.R
import com.kraftplay.openiptv.model.IptvChannel
import com.kraftplay.openiptv.model.EpgProgram
import com.kraftplay.openiptv.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(viewModel: MainViewModel, url: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val channels by viewModel.filteredChannels.collectAsState()
    val currentPrograms by viewModel.currentPrograms.collectAsState()
    val nextPrograms by viewModel.nextPrograms.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val channelViewingTimes by viewModel.channelViewingTimes.collectAsState()
    val globalPassword by viewModel.parentalPassword.collectAsState()
    
    var currentUrl by remember { mutableStateOf(url) }
    val forceLandscape by viewModel.forceLandscape.collectAsState()
    val backgroundPlayback by viewModel.backgroundPlayback.collectAsState()
    val autoHideTimeout by viewModel.autoHideTimeout.collectAsState()
    val bufferingLevel by viewModel.bufferingLevel.collectAsState()
    
    val activity = context as? Activity
    
    var showInfoPanel by remember { mutableStateOf(false) }
    var videoQuality by remember { mutableStateOf("") }
    var bitRate by remember { mutableStateOf("") }
    var fps by remember { mutableStateOf("") }
    var currentTime by remember { mutableStateOf("") }
    var videoCodec by remember { mutableStateOf("Unknown") }
    var audioCodec by remember { mutableStateOf("Unknown") }
    var isPlaying by remember { mutableStateOf(true) }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var nextUrlAfterPassword by remember { mutableStateOf("") }
    var tempPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // Keep screen on and immersive mode
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (window != null) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    val decodedUrl = remember(currentUrl) {
        try { URLDecoder.decode(currentUrl, StandardCharsets.UTF_8.toString()) } catch (e: Exception) { currentUrl }
    }

    val exoPlayer = remember(bufferingLevel) {
        val (minBuf, maxBuf) = when(bufferingLevel) {
            "Low" -> 15000 to 30000
            "High" -> 60000 to 120000
            else -> 30000 to 60000
        }
        val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(minBuf, maxBuf, 1500, 3000).build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    videoQuality = "${videoSize.width}x${videoSize.height}"
                    fps = "60 FPS"
                }
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_TRACKS_CHANGED) || events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                        val formatV = (player as? ExoPlayer)?.videoFormat
                        videoCodec = formatV?.sampleMimeType?.substringAfter("/") ?: "Unknown"
                        val formatA = (player as? ExoPlayer)?.audioFormat
                        audioCodec = formatA?.sampleMimeType?.substringAfter("/") ?: "Unknown"
                    }
                }
            })
        }
    }

    LaunchedEffect(decodedUrl) {
        exoPlayer.setMediaItem(MediaItem.fromUri(decodedUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        val channel = channels.find { it.url == currentUrl }
        channel?.let { viewModel.addToRecent(it) }
        showInfoPanel = true
    }

    // Auto-hide panel effect
    LaunchedEffect(showInfoPanel, autoHideTimeout) {
        if (showInfoPanel && autoHideTimeout > 0) {
            delay(autoHideTimeout * 1000L)
            showInfoPanel = false
        }
    }

    LaunchedEffect(Unit) {
        while(true) {
            currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            exoPlayer.videoFormat?.let { bitRate = "${(it.bitrate / 1024)} kbps" }
            delay(1000)
        }
    }

    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(currentUrl) {
        sessionStartTime = System.currentTimeMillis()
        while(true) {
            delay(60000)
            val now = System.currentTimeMillis()
            val diffSeconds = (now - sessionStartTime) / 1000
            viewModel.updateChannelViewingTime(currentUrl, diffSeconds)
            sessionStartTime = now
        }
    }

    fun switchChannel(next: Boolean) {
        val index = channels.indexOfFirst { it.url == currentUrl }
        if (index != -1) {
            val nextIndex = if (next) (index + 1) % channels.size else (index - 1 + channels.size) % channels.size
            val nextChannel = channels[nextIndex]
            if (nextChannel.isLocked) {
                nextUrlAfterPassword = nextChannel.url
                showPasswordDialog = true
                exoPlayer.pause()
            } else {
                currentUrl = nextChannel.url
                viewModel.updateSetting("last_channel_url", currentUrl)
            }
        }
    }

    DisposableEffect(forceLandscape) {
        if (forceLandscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && !backgroundPlayback) exoPlayer.pause()
            else if (event == Lifecycle.Event.ON_RESUME) {
                if (!showPasswordDialog) exoPlayer.play()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); exoPlayer.release() }
    }

    var dragAccumulated by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .onKeyEvent { keyEvent ->
            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { if(!showInfoPanel) switchChannel(false); false }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { if(!showInfoPanel) switchChannel(true); false }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { 
                        showInfoPanel = !showInfoPanel
                        true 
                    }
                    else -> false
                }
            } else false
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { dragAccumulated = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    dragAccumulated += dragAmount
                },
                onDragEnd = {
                    if (dragAccumulated > 150) switchChannel(false)
                    else if (dragAccumulated < -150) switchChannel(true)
                }
            )
        }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            showInfoPanel = !showInfoPanel
        }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showInfoPanel) {
            val currentChannel = channels.find { it.url == currentUrl }
            val program = currentPrograms[currentChannel?.name ?: ""] ?: currentPrograms[currentChannel?.epgId ?: ""]
            val nextProg = nextPrograms[currentChannel?.name ?: ""] ?: nextPrograms[currentChannel?.epgId ?: ""]
            val totalTime = channelViewingTimes[currentUrl] ?: 0L

            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
            ) {
                // Top Info
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hours = totalTime / 3600
                    val minutes = (totalTime % 3600) / 60
                    Text(String.format("%02d:%02d h", hours, minutes), color = Color.White, fontSize = 14.sp)
                    
                    val clockEnabled by viewModel.showClock.collectAsState()
                    if (clockEnabled) {
                        Text(currentTime, color = Color.White, fontSize = 14.sp)
                    }
                }

                // Recent Channels
                if (recentChannels.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp).align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentChannels) { channel ->
                            RecentChannelCard(channel) {
                                if (channel.isLocked) {
                                    nextUrlAfterPassword = channel.url
                                    showPasswordDialog = true
                                    exoPlayer.pause()
                                } else {
                                    currentUrl = channel.url
                                }
                            }
                        }
                    }
                }

                // Central Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var fastForwardFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { exoPlayer.seekToDefaultPosition() },
                        modifier = Modifier
                            .size(64.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { fastForwardFocused = it.isFocused }
                            .background(if (fastForwardFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, MaterialTheme.shapes.extraLarge)
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = stringResource(R.string.jump_to_live), tint = if(fastForwardFocused) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(48.dp))
                    }
                    
                    var playPauseFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { if(isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        modifier = Modifier
                            .size(80.dp)
                            .onFocusChanged { playPauseFocused = it.isFocused }
                            .background(if (playPauseFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, MaterialTheme.shapes.extraLarge)
                    ) {
                        Icon(
                            imageVector = if(isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if(isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                            tint = if(playPauseFocused) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                // Bottom Info Panel
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = currentChannel?.logoUrl,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).padding(end = 12.dp),
                            contentScale = ContentScale.Fit
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentChannel?.name ?: "Unknown", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            if (program != null) {
                                val now = System.currentTimeMillis()
                                val total = (program.endTime - program.startTime).toFloat()
                                val progress = if (total > 0) (now - program.startTime) / total else 0f

                                Text(program.title, color = Color.White, fontSize = 18.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(timeFormat.format(program.start), color = Color.Gray, fontSize = 12.sp)
                                    LinearProgressIndicator(
                                        progress = progress.coerceIn(0f, 1f),
                                        modifier = Modifier.width(200.dp).height(4.dp).padding(horizontal = 8.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(timeFormat.format(program.stop), color = Color.Gray, fontSize = 12.sp)
                                }
                                if (nextProg != null) {
                                    Text("Next: ${nextProg.title} (${timeFormat.format(nextProg.start)})", color = Color.LightGray, fontSize = 14.sp)
                                }
                            } else {
                                Text("No program information", color = Color.Gray, fontSize = 14.sp)
                            }
                        }

                        var favoriteFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { currentChannel?.let { viewModel.toggleFavorite(it.url) } },
                            modifier = Modifier
                                .onFocusChanged { favoriteFocused = it.isFocused }
                                .background(if (favoriteFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, MaterialTheme.shapes.extraLarge)
                        ) {
                            Icon(
                                imageVector = if (currentChannel?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (currentChannel?.isFavorite == true) Color.Red else (if(favoriteFocused) MaterialTheme.colorScheme.primary else Color.White)
                            )
                        }
                    }
                    
                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${stringResource(R.string.resolution)}: $videoQuality", color = Color.Green, fontSize = 12.sp)
                            Text("${stringResource(R.string.video)}: $videoCodec | ${stringResource(R.string.audio)}: $audioCodec", color = Color.LightGray, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${stringResource(R.string.bitrate)}: $bitRate | ${stringResource(R.string.fps)}: $fps", color = Color.LightGray, fontSize = 12.sp)
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            Text(
                                text = "${stringResource(R.string.source)}: ${currentUrl.take(40)}...",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                maxLines = 1,
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(currentUrl))
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showPasswordDialog = false; tempPassword = ""; passwordError = false; exoPlayer.play() },
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
                            currentUrl = nextUrlAfterPassword
                            viewModel.updateSetting("last_channel_url", currentUrl)
                            showPasswordDialog = false
                            tempPassword = ""
                            exoPlayer.play()
                        } else {
                            passwordError = true
                        }
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordDialog = false; tempPassword = ""; passwordError = false; exoPlayer.play() }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}

@Composable
fun RecentChannelCard(channel: IptvChannel, onClick: () -> Unit) {
    Card(
        modifier = Modifier.size(width = 120.dp, height = 80.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.8f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.size(40.dp), contentScale = ContentScale.Fit)
            Text(channel.name, color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp), maxLines = 1)
        }
    }
}
