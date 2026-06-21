package com.kraftplay.openiptv.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.kraftplay.openiptv.model.IptvChannel
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
    val recentChannels by viewModel.recentChannels.collectAsState()
    val channelViewingTimes by viewModel.channelViewingTimes.collectAsState()
    
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

    // Immersive mode logic for mobile
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            val window = activity?.window
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
        delay(5000)
        if (autoHideTimeout > 0) showInfoPanel = false
    }

    LaunchedEffect(Unit) {
        while(true) {
            currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            exoPlayer.videoFormat?.let { bitRate = "${(it.bitrate / 1024)} kbps" }
            delay(1000)
        }
    }

    // Per-channel viewing time tracking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(currentUrl) {
        sessionStartTime = System.currentTimeMillis()
        while(true) {
            delay(60000) // Update every minute
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
            currentUrl = channels[nextIndex].url
            viewModel.updateSetting("last_channel_url", currentUrl)
        }
    }

    DisposableEffect(forceLandscape) {
        if (forceLandscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && !backgroundPlayback) exoPlayer.pause()
            else if (event == Lifecycle.Event.ON_RESUME) exoPlayer.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); exoPlayer.release() }
    }

    var dragAccumulated by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .onKeyEvent { keyEvent ->
            when (keyEvent.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { switchChannel(false); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { switchChannel(true); true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { showInfoPanel = !showInfoPanel; true }
                else -> false
            }
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
            val program = currentPrograms[currentChannel?.epgId ?: currentChannel?.name ?: ""]
            val totalTime = channelViewingTimes[currentUrl] ?: 0L
            
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
                            RecentChannelCard(channel) { currentUrl = channel.url }
                        }
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
                            modifier = Modifier.size(48.dp).padding(end = 12.dp),
                            contentScale = ContentScale.Fit
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentChannel?.name ?: "Unknown", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            if (program != null) {
                                Text("NOW: ${program.title}", color = Color.White, fontSize = 18.sp)
                                Text(program.description ?: "", color = Color.Gray, fontSize = 14.sp, maxLines = 1)
                            }
                        }
                        IconButton(onClick = { currentChannel?.let { viewModel.toggleFavorite(it.url) } }) {
                            Icon(
                                imageVector = if (currentChannel?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (currentChannel?.isFavorite == true) Color.Red else Color.White
                            )
                        }
                    }
                    
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Resolution: $videoQuality", color = Color.Green, fontSize = 12.sp)
                            Text("Video: $videoCodec | Audio: $audioCodec", color = Color.LightGray, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Bitrate: $bitRate | FPS: $fps", color = Color.LightGray, fontSize = 12.sp)
                            Text("Source: ${currentUrl.take(40)}...", color = Color.Gray, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
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
