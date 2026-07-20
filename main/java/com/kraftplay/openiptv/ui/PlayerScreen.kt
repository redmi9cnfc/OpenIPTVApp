package com.kraftplay.openiptv.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(viewModel: MainViewModel, url: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val allChannels by viewModel.allChannels.collectAsState()
    val currentPrograms by viewModel.currentPrograms.collectAsState()
    val nextPrograms by viewModel.nextPrograms.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val channelViewingTimes by viewModel.channelViewingTimes.collectAsState()
    
    val showSourceOpt by viewModel.showSource.collectAsState()
    val showResolutionOpt by viewModel.showResolution.collectAsState()
    val showFpsOpt by viewModel.showFps.collectAsState()
    val resFormatOpt by viewModel.resolutionFormat.collectAsState()
    
    var currentUrl by remember { mutableStateOf(url) }
    val forceLandscape by viewModel.forceLandscape.collectAsState()
    val fullScreenDefault by viewModel.fullScreenByDefault.collectAsState()
    val bufferingLevel by viewModel.bufferingLevel.collectAsState()
    val autoHideTimeout by viewModel.autoHideTimeout.collectAsState()
    val backgroundPlayback by viewModel.backgroundPlayback.collectAsState()
    
    val activity = context as? Activity
    
    var showInfoPanel by remember { mutableStateOf(false) }
    var videoQuality by remember { mutableStateOf("") }
    var bitRate by remember { mutableStateOf("") }
    var fps by remember { mutableStateOf("") }
    var currentTime by remember { mutableStateOf("") }
    var videoCodec by remember { mutableStateOf("Unknown") }
    var audioCodec by remember { mutableStateOf("Unknown") }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }

    var showPasswordDialog by remember { mutableStateOf<Triple<IptvChannel, String, String>?>(null) }
    var tempPass by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // Full screen and Orientation logic with better Activity handling
    LaunchedEffect(forceLandscape, fullScreenDefault) {
        val window = activity?.window ?: return@LaunchedEffect
        if (fullScreenDefault) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
        
        if (forceLandscape) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
                    videoQuality = if (resFormatOpt == "Labels") {
                        when {
                            videoSize.height >= 2160 -> "4K"
                            videoSize.height >= 1080 -> "Full HD"
                            videoSize.height >= 720 -> "HD"
                            else -> "SD"
                        }
                    } else {
                        "${videoSize.width}x${videoSize.height}"
                    }
                    fps = "60 FPS"
                }
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
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
        isBuffering = true
        exoPlayer.setMediaItem(MediaItem.fromUri(decodedUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        val channel = allChannels.find { it.url == currentUrl }
        channel?.let { viewModel.addToRecent(it) }
        showInfoPanel = true
    }

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
        if (showPasswordDialog != null) return // Prevent multiple dialogs
        
        val index = allChannels.indexOfFirst { it.url == currentUrl }
        if (index != -1) {
            val nextIndex = if (next) (index + 1) % allChannels.size else (index - 1 + allChannels.size) % allChannels.size
            val nextChannel = allChannels[nextIndex]
            if (nextChannel.isLocked) {
                showPasswordDialog = Triple(nextChannel, "play", "")
                exoPlayer.pause()
            } else {
                currentUrl = nextChannel.url
                viewModel.updateSetting("last_channel_url", currentUrl)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && !backgroundPlayback) exoPlayer.pause()
            else if (event == Lifecycle.Event.ON_RESUME) {
                if (showPasswordDialog == null) exoPlayer.play()
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

        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        if (showInfoPanel) {
            val currentChannel = allChannels.find { it.url == currentUrl }
            val program = currentPrograms[currentUrl]
            val nextProg = nextPrograms[currentUrl]
            val totalTime = channelViewingTimes[currentUrl] ?: 0L

            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    val hours = totalTime / 3600
                    val minutes = (totalTime % 3600) / 60
                    Text(String.format("%02d:%02d h", hours, minutes), color = Color.White, fontSize = 14.sp)
                    
                    val clockEnabled by viewModel.showClock.collectAsState()
                    if (clockEnabled) {
                        Text(currentTime, color = Color.White, fontSize = 14.sp)
                    }
                }

                if (recentChannels.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp).align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentChannels) { channel ->
                            var isFocused by remember { mutableStateOf(false) }
                            RecentChannelCard(
                                channel = channel,
                                modifier = Modifier
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .scale(if (isFocused) 1.1f else 1f)
                                    .border(if (isFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                                onClick = {
                                    val fullChannel = allChannels.find { it.url == channel.url } ?: channel
                                    if (fullChannel.isLocked) {
                                        showPasswordDialog = Triple(fullChannel, "play", "")
                                        exoPlayer.pause()
                                    } else {
                                        currentUrl = fullChannel.url
                                    }
                                }
                            )
                        }
                    }
                }

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
                            .scale(if (fastForwardFocused) 1.2f else 1f)
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
                            .scale(if (playPauseFocused) 1.2f else 1f)
                    ) {
                        Icon(
                            imageVector = if(isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if(isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                            tint = if(playPauseFocused) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

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
                                .scale(if (favoriteFocused) 1.2f else 1f)
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
                            if (showResolutionOpt) {
                                Text("${stringResource(R.string.resolution)}: $videoQuality", color = Color.Green, fontSize = 12.sp)
                            }
                            Text("${stringResource(R.string.video)}: $videoCodec | ${stringResource(R.string.audio)}: $audioCodec", color = Color.LightGray, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val bitrateStr = "${stringResource(R.string.bitrate)}: $bitRate"
                            val fpsStr = if(showFpsOpt) " | ${stringResource(R.string.fps)}: $fps" else ""
                            Text(bitrateStr + fpsStr, color = Color.LightGray, fontSize = 12.sp)
                            
                            if (showSourceOpt) {
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
        }

        if (showPasswordDialog != null) {
            val (channel, action, _) = showPasswordDialog!!
            val savedPass = viewModel.getChannelPassword(channel.url) ?: ""
            
            val onCheck = {
                if (tempPass == savedPass) {
                    currentUrl = channel.url
                    viewModel.updateSetting("last_channel_url", currentUrl)
                    showPasswordDialog = null
                    tempPass = ""
                    exoPlayer.play()
                } else {
                    passwordError = true
                }
            }

            AlertDialog(
                onDismissRequest = { 
                    showPasswordDialog = null
                    tempPass = ""
                    passwordError = false
                    exoPlayer.play() 
                },
                title = { Text(stringResource(R.string.enter_password)) },
                text = {
                    Column {
                        TextField(
                            value = tempPass,
                            onValueChange = { tempPass = it },
                            label = { Text(stringResource(R.string.password)) },
                            isError = passwordError,
                            modifier = Modifier.fillMaxWidth().onKeyEvent {
                                if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER || it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                                    if (it.nativeKeyEvent.action == KeyEvent.ACTION_UP) onCheck()
                                    true
                                } else false
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onCheck() })
                        )
                        if (passwordError) {
                            Text(stringResource(R.string.incorrect_password), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onCheck) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showPasswordDialog = null
                        tempPass = ""
                        passwordError = false
                        exoPlayer.play() 
                    }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}

@Composable
fun RecentChannelCard(channel: IptvChannel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.size(width = 120.dp, height = 80.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.8f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.size(40.dp), contentScale = ContentScale.Fit)
            Text(channel.name, color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp), maxLines = 1)
        }
    }
}
