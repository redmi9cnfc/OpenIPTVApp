package com.kraftplay.openiptv.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.kraftplay.openiptv.R
import com.kraftplay.openiptv.model.IptvChannel
import com.kraftplay.openiptv.model.EpgProgram
import com.kraftplay.openiptv.util.CatchupUrlBuilder
import com.kraftplay.openiptv.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    url: String,
    onTriggerPip: () -> Unit,
    onExitChannel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val allChannels by viewModel.allChannels.collectAsState()
    val clockEnabled by viewModel.showClock.collectAsState()
    val clockPosition by viewModel.clockPosition.collectAsState()
    val currentPrograms by viewModel.currentPrograms.collectAsState()
    val epgPrograms by viewModel.epgPrograms.collectAsState()
    val catchupMode by viewModel.catchupMode.collectAsState()
    val nextPrograms by viewModel.nextPrograms.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val channelViewingTimes by viewModel.channelViewingTimes.collectAsState()
    
    val showSourceSetting by viewModel.showSource.collectAsState()
    val selectedPlaylists by viewModel.playlists.collectAsState()
    val selectedPlaylist = selectedPlaylists.firstOrNull { it.isSelected }
    // KitTV intentionally hides stream URLs; all other playlists always show their source.
    // Do not let the old global preference accidentally hide sources for third-party playlists.
    val showSourceOpt = !com.kraftplay.openiptv.model.isKitTvPlaylist(
        selectedPlaylist?.name.orEmpty(), selectedPlaylist?.url.orEmpty(), selectedPlaylist?.id.orEmpty()
    )
    val showResolutionOpt by viewModel.showResolution.collectAsState()
    val showFpsOpt by viewModel.showFps.collectAsState()
    val uiStyle by viewModel.uiStyle.collectAsState()
    
    var currentUrl by remember { mutableStateOf(url) }
    var archiveLiveUrl by remember { mutableStateOf<String?>(null) }
    val isArchivePlayback = archiveLiveUrl != null && currentUrl != archiveLiveUrl
    val decodedUrl = remember(currentUrl) {
        try {
            URLDecoder.decode(currentUrl, StandardCharsets.UTF_8.toString())
        } catch (e: Exception) {
            currentUrl
        }
    }
    val forceLandscape by viewModel.forceLandscape.collectAsState()
    val aspectRatioMode by viewModel.aspectRatioMode.collectAsState()
    val autoHideTimeout by viewModel.autoHideTimeout.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val audioVolumeBoost by viewModel.audioVolumeBoost.collectAsState()
    
    val activity = context as? Activity

    fun applyImmersiveMode() {
        val window = activity?.window ?: return
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    DisposableEffect(activity) {
        (activity as? com.kraftplay.openiptv.MainActivity)?.setPlayingVideoState(true)
        onDispose {
            (activity as? com.kraftplay.openiptv.MainActivity)?.setPlayingVideoState(false)
        }
    }

    DisposableEffect(activity, lifecycleOwner, forceLandscape) {
        val previousOrientation = activity?.requestedOrientation
        val window = activity?.window
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) applyImmersiveMode()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        applyImmersiveMode()
        if (forceLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = previousOrientation
                ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscapeLayout = configuration.screenWidthDp > configuration.screenHeightDp

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    var showInfoPanel by remember { mutableStateOf(true) }
    // Poll Media3 playback position so the archive timeline remains live while the player is open.
    var archivePositionMs by remember { mutableLongStateOf(0L) }
    var archiveDurationMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(exoPlayer, isArchivePlayback) {
        while (true) {
            archivePositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            archiveDurationMs = exoPlayer.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
            delay(500)
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var videoQuality by remember { mutableStateOf("Calculating...") }
    var fps by remember { mutableStateOf("") }
    var bitRate by remember { mutableStateOf("") }
    var videoCodec by remember { mutableStateOf("H.264") }
    var audioCodec by remember { mutableStateOf("AAC") }
    
    var showTracksDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf<Triple<IptvChannel, String, String>?>(null) }
    var tempPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }

    var retryCount by remember { mutableIntStateOf(0) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var currentTime by remember { mutableStateOf("") }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }
    var isSwipingHandled by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val playerRootFocusRequester = remember { FocusRequester() }

    fun userInteracted() {
        lastInteractionTime = System.currentTimeMillis()
        showInfoPanel = true
    }

    fun switchToNextChannel() {
        if (allChannels.isEmpty()) return
        val currentIndex = allChannels.indexOfFirst { it.url == currentUrl }
        val nextIndex = if (currentIndex != -1 && currentIndex < allChannels.size - 1) currentIndex + 1 else 0
        val targetChannel = allChannels[nextIndex]
        if (targetChannel.isLocked) {
            showPasswordDialog = Triple(targetChannel, "play", "")
            exoPlayer.pause()
        } else {
            archiveLiveUrl = null
            currentUrl = targetChannel.url
        }
        userInteracted()
    }

    fun switchToPreviousChannel() {
        if (allChannels.isEmpty()) return
        val currentIndex = allChannels.indexOfFirst { it.url == currentUrl }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else allChannels.size - 1
        val targetChannel = allChannels[prevIndex]
        if (targetChannel.isLocked) {
            showPasswordDialog = Triple(targetChannel, "play", "")
            exoPlayer.pause()
        } else {
            archiveLiveUrl = null
            currentUrl = targetChannel.url
        }
        userInteracted()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val height = videoSize.height
                if (showResolutionOpt) {
                    videoQuality = when {
                        height >= 2160 -> "4K UHD (${videoSize.width}x${videoSize.height})"
                        height >= 1080 -> "Full HD (${videoSize.width}x${videoSize.height})"
                        height >= 720 -> "HD (${videoSize.width}x${videoSize.height})"
                        else -> "SD (${videoSize.width}x${videoSize.height})"
                    }
                } else {
                    videoQuality = "${videoSize.width}x${videoSize.height}"
                }
                fps = "60 FPS"
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) playbackError = null
            }
            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playbackError = error.localizedMessage ?: "Could not play stream"
                if (retryCount < 1) {
                    retryCount += 1
                    playbackError = "Stream error. Retrying…"
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
            }
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_TRACKS_CHANGED) || events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) || events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    val currentPlayer = player as? ExoPlayer
                    val videoFormat = currentPlayer?.currentTracks?.groups
                        ?.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
                        ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let(group::getTrackFormat) }
                        ?: currentPlayer?.videoFormat
                    val audioFormat = currentPlayer?.currentTracks?.groups
                        ?.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
                        ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let(group::getTrackFormat) }
                        ?: currentPlayer?.audioFormat
                    videoCodec = videoFormat?.codecs
                        ?: videoFormat?.sampleMimeType?.substringAfter("/")?.uppercase(Locale.ROOT)
                        ?: "Detecting"
                    audioCodec = audioFormat?.codecs
                        ?: audioFormat?.sampleMimeType?.substringAfter("/")?.uppercase(Locale.ROOT)
                        ?: "Detecting"
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(playbackSpeed, audioVolumeBoost) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
        exoPlayer.volume = audioVolumeBoost
    }

    LaunchedEffect(currentUrl, retryCount) {
        isBuffering = true
        playbackError = null
        exoPlayer.setMediaItem(MediaItem.fromUri(decodedUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        val channel = allChannels.find { it.url == currentUrl }
        channel?.let { viewModel.addToRecent(it) }
        showInfoPanel = true
        lastInteractionTime = System.currentTimeMillis()
    }

    LaunchedEffect(currentUrl) {
        retryCount = 0
        playbackError = null
    }

    LaunchedEffect(showInfoPanel, autoHideTimeout, lastInteractionTime, showTracksDialog, showSpeedDialog, showPasswordDialog) {
        if (showInfoPanel) {
            if (showTracksDialog || showSpeedDialog || showPasswordDialog != null) {
                return@LaunchedEffect
            }
            if (autoHideTimeout > 0) {
                delay(autoHideTimeout * 1000L)
                showInfoPanel = false
                applyImmersiveMode()
            }
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
            if (diffSeconds > 0) {
                viewModel.updateChannelViewingTime(currentUrl, diffSeconds)
                sessionStartTime = now
            }
        }
    }

    BackHandler {
        onExitChannel()
    }

    LaunchedEffect(showInfoPanel) {
        if (!showInfoPanel) {
            delay(80)
            runCatching { playerRootFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { userInteracted() }
                )
            }
            .pointerInput(currentUrl, allChannels) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDragX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount
                    },
                    onDragEnd = {
                        userInteracted()
                        if (totalDragX < -60f) switchToNextChannel()
                        else if (totalDragX > 60f) switchToPreviousChannel()
                    }
                )
            }
            .focusRequester(playerRootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                val isConfirm = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                    keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                when {
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (showInfoPanel) {
                            // While the infobar is visible, let Compose move focus between its controls.
                            false
                        } else {
                            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                event.nativeKeyEvent.repeatCount == 0) {
                                userInteracted()
                                switchToNextChannel()
                            }
                            true
                        }
                    }
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (showInfoPanel) {
                            // Do not change channels while navigating the infobar.
                            false
                        } else {
                            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                event.nativeKeyEvent.repeatCount == 0) {
                                userInteracted()
                                switchToPreviousChannel()
                            }
                            true
                        }
                    }
                    isConfirm -> {
                        if (showInfoPanel) {
                            // Allow focused infobar buttons to receive OK and activate normally.
                            false
                        } else {
                            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                userInteracted()
                            }
                            true
                        }
                    }
                    else -> false
                }
            }
    ) {
        val targetRatio = when (aspectRatioMode) {
            "4:3" -> 4f / 3f
            else -> 16f / 9f
        }
        // Use the actual Compose constraints, not cached screen dimensions.
        // ScreenConfiguration can temporarily report stale/zero dimensions during
        // rotation, which can create invalid AndroidView sizes and crash playback.
        val resizeModeVal = when (aspectRatioMode) {
            "Fit", "16:9", "4:3" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val videoBoxModifier = when (aspectRatioMode) {
                "Full Screen", "Fit", "Fill" -> Modifier.fillMaxSize()
                else -> {
                    // In landscape, 4:3 uses the full available height and is
                    // centered horizontally; in portrait it fits the available width.
                    val safeWidth = maxWidth.value.coerceAtLeast(1f)
                    val safeHeight = maxHeight.value.coerceAtLeast(1f)
                    val fittedWidth = minOf(safeWidth, safeHeight * targetRatio).coerceAtLeast(1f)
                    val fittedHeight = (fittedWidth / targetRatio).coerceAtMost(safeHeight).coerceAtLeast(1f)
                    Modifier
                        .width(fittedWidth.dp)
                        .height(fittedHeight.dp)
                        .align(Alignment.Center)
                }
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        keepScreenOn = true
                        resizeMode = resizeModeVal
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.keepScreenOn = true
                    view.resizeMode = resizeModeVal
                },
                modifier = videoBoxModifier
            )
        }

        if (isBuffering && playbackError == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        if (playbackError != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(playbackError ?: "Playback error", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = {
                        retryCount = 0
                        playbackError = null
                        isBuffering = true
                        exoPlayer.seekToDefaultPosition()
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }) { Text("Retry") }
                    OutlinedButton(onClick = onExitChannel) { Text("Back to channels") }
                }
            }
        }

        if (showInfoPanel) {
            val currentChannel = allChannels.find { it.url == currentUrl } ?: archiveLiveUrl?.let { live -> allChannels.find { it.url == live } }
            val program = currentPrograms[currentUrl]
            val nextProg = nextPrograms[currentUrl]
            val totalTime = channelViewingTimes[currentUrl] ?: 0L
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            LaunchedEffect(Unit) {
                delay(100)
                runCatching { focusRequester.requestFocus() }
            }
            
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val hours = totalTime / 3600
                        val minutes = (totalTime % 3600) / 60
                        Text(String.format("%02d:%02d h", hours, minutes), color = Color.White, fontSize = 14.sp)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(onClick = { userInteracted(); showTracksDialog = true }) {
                            Icon(Icons.Default.Audiotrack, "Audio Tracks", tint = Color.White)
                        }
                        IconButton(onClick = { userInteracted(); showSpeedDialog = true }) {
                            Icon(Icons.Default.Speed, "Playback Speed", tint = Color.White)
                        }
                        IconButton(onClick = { userInteracted(); onTriggerPip() }) {
                            Icon(Icons.Default.PictureInPicture, stringResource(R.string.pip_mode), tint = Color.White)
                        }
                        val clockEnabled by viewModel.showClock.collectAsState()
                        if (clockEnabled) {
                            Text(currentTime, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }

                if (recentChannels.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp).align(Alignment.TopCenter),
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
                                    userInteracted()
                                    val fullChannel = allChannels.find { it.url == channel.url } ?: channel
                                    if (fullChannel.isLocked) {
                                        showPasswordDialog = Triple(fullChannel, "play", "")
                                        exoPlayer.pause()
                                    } else {
                                        archiveLiveUrl = null
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
                    if (isArchivePlayback) {
                    var rewindFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            userInteracted()
                            val position = exoPlayer.currentPosition
                            exoPlayer.seekTo((position - 10_000L).coerceAtLeast(0L))
                        },
                        modifier = Modifier.size(64.dp)
                            .onFocusChanged { rewindFocused = it.isFocused }
                            .background(if (rewindFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, MaterialTheme.shapes.extraLarge)
                    ) {
                        Icon(Icons.Default.FastRewind, contentDescription = "Back 10 seconds",
                            tint = if (rewindFocused) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(44.dp))
                    }
                    var fastForwardFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            userInteracted()
                            val liveUrl = archiveLiveUrl
                            if (liveUrl != null) {
                                currentUrl = liveUrl
                                archiveLiveUrl = null
                            } else {
                                exoPlayer.seekToDefaultPosition()
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { fastForwardFocused = it.isFocused }
                            .background(if (fastForwardFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, MaterialTheme.shapes.extraLarge)
                            .scale(if (fastForwardFocused) 1.2f else 1f)
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = stringResource(R.string.jump_to_live), tint = if(fastForwardFocused) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(48.dp))
                    }
                    
                    var forwardFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            userInteracted()
                            val duration = exoPlayer.duration
                            val target = exoPlayer.currentPosition + 10_000L
                            exoPlayer.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
                        },
                        modifier = Modifier.size(64.dp)
                            .onFocusChanged { forwardFocused = it.isFocused }
                            .background(if (forwardFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, MaterialTheme.shapes.extraLarge)
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = "Forward 10 seconds",
                            tint = if (forwardFocused) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(44.dp))
                    }
                    }
                    var playPauseFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { userInteracted(); if(isPlaying) exoPlayer.pause() else exoPlayer.play() },
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
                    .background(if (uiStyle == "LiquidGlass") Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.8f))
                    .focusGroup()
                    .padding(16.dp)
                ) {
                    if (isArchivePlayback && archiveDurationMs > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = formatPlaybackTime(archivePositionMs),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Slider(
                                value = archivePositionMs.coerceIn(0L, archiveDurationMs).toFloat(),
                                onValueChange = { value ->
                                    archivePositionMs = value.toLong()
                                    userInteracted()
                                },
                                onValueChangeFinished = {
                                    exoPlayer.seekTo(archivePositionMs.coerceIn(0L, archiveDurationMs))
                                },
                                valueRange = 0f..archiveDurationMs.toFloat(),
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                                )
                            )
                            Text(
                                text = formatPlaybackTime(archiveDurationMs),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = currentChannel?.logoUrl,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).padding(end = 12.dp),
                            contentScale = ContentScale.Fit
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                currentChannel?.name ?: "Unknown",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (program != null) {
                                val now = System.currentTimeMillis()
                                val total = (program.endTime - program.startTime).toFloat()
                                val progress = if (total > 0) (now - program.startTime) / total else 0f

                                Text(
                                    program.title,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
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
                                    Text(
                                        "Next: ${nextProg.title} (${timeFormat.format(nextProg.start)})",
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Text("No programme information", color = Color.Gray, fontSize = 14.sp)
                            }
                        }

                        val aspectRatioLabel = when (aspectRatioMode) {
                            "Full Screen" -> stringResource(R.string.aspect_ratio_fill)
                            "16:9" -> stringResource(R.string.aspect_ratio_16_9)
                            "4:3" -> stringResource(R.string.aspect_ratio_4_3)
                            "Fit" -> stringResource(R.string.aspect_ratio_fit)
                            else -> aspectRatioMode
                        }

                        FocusHighlightTextButton(
                            onClick = {
                                userInteracted()
                                val nextMode = when (aspectRatioMode) {
                                    "Full Screen" -> "16:9"
                                    "16:9" -> "4:3"
                                    "4:3" -> "Fit"
                                    else -> "Full Screen"
                                }
                                viewModel.updateSetting("aspect_ratio_mode", nextMode)
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = when (aspectRatioMode) {
                                    "16:9", "4:3" -> Icons.Default.AspectRatio
                                    "Fit" -> Icons.Default.FitScreen
                                    else -> Icons.Default.Fullscreen
                                },
                                contentDescription = "Aspect Ratio",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(aspectRatioLabel, color = Color.White, fontSize = 12.sp)
                        }
                        
                        FocusHighlightIconButton(onClick = { userInteracted(); currentChannel?.let { viewModel.togglePinChannel(it.url) } }) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = if (currentChannel?.isPinned == true) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        FocusHighlightIconButton(onClick = { userInteracted(); currentChannel?.let { viewModel.toggleFavorite(it.url) } }) {
                            Icon(
                                imageVector = if (currentChannel?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (currentChannel?.isFavorite == true) Color.Red else Color.White
                            )
                        }

                        // Archive button stays in the player's bottom action row beside Back/Exit.
                        FocusHighlightIconButton(
                            onClick = { userInteracted(); showArchiveDialog = true },
                            enabled = currentChannel != null
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Archive",
                                tint = if (currentChannel != null) Color.White else Color.Gray
                            )
                        }

                        FocusHighlightIconButton(onClick = onExitChannel) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
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

        if (showArchiveDialog) {
            val channel = allChannels.find { it.url == currentUrl }
                ?: archiveLiveUrl?.let { live -> allChannels.find { it.url == live } }
            val now = System.currentTimeMillis()
            val channelKeys = remember(channel?.url, channel?.epgId, channel?.tvgName, channel?.name) {
                listOfNotNull(channel?.epgId, channel?.tvgName, channel?.name)
                    .map { it.trim().lowercase(Locale.ROOT) }
                    .filter { it.isNotBlank() }
                    .toSet()
            }
            val archivePrograms = remember(epgPrograms, channelKeys, channel?.catchupDays, now) {
                val providerDays = channel?.catchupDays?.takeIf { it > 0 } ?: 14
                val historyDays = providerDays.coerceAtMost(14)
                val earliest = now - historyDays.toLong() * 24L * 60L * 60L * 1000L
                epgPrograms.asSequence()
                    .filter { it.channelId.trim().lowercase(Locale.ROOT) in channelKeys }
                    .filter { it.endTime <= now && it.endTime > 0L }
                    .filter { it.startTime >= earliest }
                    .sortedByDescending { it.startTime }
                    .toList()
            }
            val supportsCatchup = channel != null && (
                !channel.catchupSource.isNullOrBlank() ||
                (!channel.catchup.isNullOrBlank() && !channel.catchup.equals("none", true) && !channel.catchup.equals("0", true)) ||
                catchupMode.equals("Auto", true) || catchupMode.equals("Flussonic", true) || catchupMode.equals("Xtream", true)
            )
            AlertDialog(
                onDismissRequest = { showArchiveDialog = false },
                title = { Text("${stringResource(R.string.archive)} — ${channel?.name ?: stringResource(R.string.channel)}") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (archivePrograms.isNotEmpty()) {
                            val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                            val dayFormat = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }
                            var previousDay = ""
                            archivePrograms.forEach { archivedProgram ->
                                val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(archivedProgram.startTime))
                                if (dayKey != previousDay) {
                                    previousDay = dayKey
                                    Text(
                                        dayFormat.format(Date(archivedProgram.startTime)).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 3.dp)
                                    )
                                }
                                val archiveUrl = channel?.let {
                                    CatchupUrlBuilder.build(
                                        liveUrl = it.url,
                                        sourceTemplate = it.catchupSource,
                                        mode = if (catchupMode.equals("Auto", true)) (it.catchup?.takeIf { value -> value.isNotBlank() && !value.equals("none", true) && !value.equals("0", true) } ?: if (!it.catchupSource.isNullOrBlank()) "Default" else "Flussonic") else catchupMode,
                                        startTimeMs = archivedProgram.startTime,
                                        endTimeMs = archivedProgram.endTime
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        if (archiveUrl != null) {
                                            if (archiveLiveUrl == null) archiveLiveUrl = channel?.url
                                            currentUrl = archiveUrl
                                            showArchiveDialog = false
                                            playbackError = null
                                            isBuffering = true
                                            userInteracted()
                                        }
                                    },
                                    enabled = archiveUrl != null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(archivedProgram.title, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text(
                                            "${dateFormat.format(Date(archivedProgram.startTime))}–${dateFormat.format(Date(archivedProgram.endTime))}" +
                                                if (archiveUrl == null) " · ${stringResource(R.string.archive_format_unknown)}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (archiveUrl == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else if (supportsCatchup) {
                            Text(
                                stringResource(R.string.archive_no_epg),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            listOf(1, 2, 3, 6, 12, 24, 48, 72).forEach { hours ->
                                val startMs = now - hours.toLong() * 60L * 60L * 1000L
                                val archiveUrl = channel?.let {
                                    if (!it.catchupSource.isNullOrBlank()) CatchupUrlBuilder.build(
                                        liveUrl = it.url,
                                        sourceTemplate = it.catchupSource,
                                        mode = if (catchupMode.equals("Auto", true)) (it.catchup ?: "Default") else catchupMode,
                                        startTimeMs = startMs,
                                        endTimeMs = now
                                    ) else CatchupUrlBuilder.buildRelative(
                                        liveUrl = it.url,
                                        mode = if (catchupMode.equals("Auto", true)) (it.catchup?.takeIf { value -> value.isNotBlank() && !value.equals("none", true) && !value.equals("0", true) } ?: "Flussonic") else catchupMode,
                                        secondsBack = hours.toLong() * 60L * 60L
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        if (archiveUrl != null) {
                                            if (archiveLiveUrl == null) archiveLiveUrl = channel?.url
                                            currentUrl = archiveUrl
                                            showArchiveDialog = false
                                            playbackError = null
                                            isBuffering = true
                                            userInteracted()
                                        }
                                    },
                                    enabled = archiveUrl != null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val actionText = if (archiveUrl == null) stringResource(R.string.archive_format_unknown) else stringResource(R.string.archive_open)
                                    Text("${stringResource(R.string.archive_last_hours, hours)} · $actionText")
                                }
                            }
                        } else {
                            Text(stringResource(R.string.archive_no_metadata))
                            listOf(1, 2, 3, 6, 12, 24, 48, 72).forEach { hours ->
                                val archiveUrl = channel?.let { CatchupUrlBuilder.buildRelative(it.url, "Flussonic", hours.toLong() * 3600L) }
                                TextButton(
                                    onClick = {
                                        if (archiveUrl != null && channel != null) {
                                            if (archiveLiveUrl == null) archiveLiveUrl = channel.url
                                            currentUrl = archiveUrl
                                            showArchiveDialog = false
                                            playbackError = null
                                            isBuffering = true
                                            userInteracted()
                                        }
                                    },
                                    enabled = archiveUrl != null,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(stringResource(R.string.archive_rewind_hours, hours)) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showArchiveDialog = false }) { Text(stringResource(R.string.close)) } }
            )
        }

        if (showTracksDialog) {
            val currentTracks = exoPlayer.currentTracks
            val audioGroup = currentTracks.groups.find { it.type == C.TRACK_TYPE_AUDIO }

            AlertDialog(
                onDismissRequest = { showTracksDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth(0.85f).wrapContentHeight().heightIn(max = 320.dp),
                title = { Text(stringResource(R.string.audio_tracks)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        if (audioGroup != null) {
                            for (i in 0 until audioGroup.length) {
                                val format = audioGroup.getTrackFormat(i)
                                val isSelected = audioGroup.isTrackSelected(i)
                                val lang = format.language ?: "Audio ${i + 1}"
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        userInteracted()
                                        val override = TrackSelectionOverride(audioGroup.mediaTrackGroup, i)
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(override)
                                            .build()
                                        showTracksDialog = false
                                    }.padding(vertical = 8.dp)
                                ) {
                                    RadioButton(selected = isSelected, onClick = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(lang, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        } else {
                            Text(stringResource(R.string.no_audio_tracks))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTracksDialog = false }) { Text(stringResource(R.string.close)) }
                }
            )
        }

        if (showSpeedDialog) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth(0.85f).wrapContentHeight().heightIn(max = 320.dp),
                title = { Text(stringResource(R.string.playback_speed)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    userInteracted()
                                    viewModel.updateSetting("playback_speed", speed)
                                    showSpeedDialog = false
                                }.padding(vertical = 8.dp)
                            ) {
                                RadioButton(selected = playbackSpeed == speed, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text("${speed}x", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) { Text(stringResource(R.string.close)) }
                }
            )
        }

        if (showPasswordDialog != null) {
            val (channel, _, _) = showPasswordDialog!!
            val savedPassword = viewModel.getChannelPassword(channel.url) ?: ""
            AlertDialog(
                onDismissRequest = { showPasswordDialog = null; tempPassword = ""; passwordError = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth(0.85f).wrapContentHeight().heightIn(max = 320.dp),
                title = { Text(stringResource(R.string.enter_password)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        TextField(
                            value = tempPassword,
                            onValueChange = { tempPassword = it },
                            label = { Text(stringResource(R.string.password)) },
                            isError = passwordError,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                        )
                        if (passwordError) {
                            Text(stringResource(R.string.incorrect_password), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        userInteracted()
                        if (tempPassword == savedPassword) {
                            currentUrl = channel.url
                            showPasswordDialog = null
                            tempPassword = ""
                            passwordError = false
                            exoPlayer.play()
                        } else {
                            passwordError = true
                        }
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordDialog = null; tempPassword = ""; passwordError = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        // Persistent clock overlay: independent of the auto-hiding player controls.
        if (clockEnabled && clockPosition != "Off") {
            val clockAlignment = when (clockPosition) {
                "Top left" -> Alignment.TopStart
                "Top right" -> Alignment.TopEnd
                "Bottom left" -> Alignment.BottomStart
                "Bottom right" -> Alignment.BottomEnd
                else -> Alignment.TopEnd
            }
            Text(
                text = currentTime,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(clockAlignment)
                    .padding(18.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun FocusHighlightIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .background(
                if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        content = content
    )
}

@Composable
private fun FocusHighlightTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    TextButton(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .background(
                if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            ),
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun RecentChannelCard(channel: IptvChannel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Black.copy(alpha = 0.6f),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier.size(24.dp).padding(end = 4.dp),
                contentScale = ContentScale.Fit
            )
            Text(channel.name, color = Color.White, fontSize = 12.sp, maxLines = 1)
        }
    }
}


private fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
