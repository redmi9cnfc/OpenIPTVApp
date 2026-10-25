package com.kraftplay.openiptv.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.kraftplay.openiptv.model.IptvChannel
import com.kraftplay.openiptv.util.CatchupUrlBuilder
import com.kraftplay.openiptv.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

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
    val configuration = LocalConfiguration.current
    val isLandscapeLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp > configuration.screenHeightDp

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
    val showResolutionOpt by viewModel.showResolution.collectAsState()
    val showFpsOpt by viewModel.showFps.collectAsState()
    val showCodecsOpt by viewModel.showCodecs.collectAsState()
    val showBitrateOpt by viewModel.showBitrate.collectAsState()
    val resFormat by viewModel.resolutionFormat.collectAsState()
    val uiStyle by viewModel.uiStyle.collectAsState()
    val bufferingLevel by viewModel.bufferingLevel.collectAsState()
    val playerGlassTint by viewModel.playerGlassTint.collectAsState()
    val epgProgressStyle by viewModel.epgProgressStyle.collectAsState()

    val selectedPlaylists by viewModel.playlists.collectAsState()
    val selectedPlaylist = selectedPlaylists.firstOrNull { it.isSelected }

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
    val currentChannel = remember(currentUrl, allChannels) {
        allChannels.find { it.url == currentUrl } ?: archiveLiveUrl?.let { live -> allChannels.find { it.url == live } }
    }

    val isKitTvChannel = remember(currentChannel, selectedPlaylist) {
        val chUrl = currentChannel?.url.orEmpty()
        val chName = currentChannel?.name.orEmpty()
        val plName = selectedPlaylist?.name.orEmpty()
        val plUrl = selectedPlaylist?.url.orEmpty()
        val plId = selectedPlaylist?.id.orEmpty()

        com.kraftplay.openiptv.model.isKitTvPlaylist(plName, plUrl, plId) ||
        com.kraftplay.openiptv.model.isKitTvPlaylist(chName, chUrl)
    }

    val showSourceOpt = showSourceSetting && !isKitTvChannel

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
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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

    val exoPlayer = remember(bufferingLevel) {
        val (minBuffer, maxBuffer) = when (bufferingLevel) {
            "Low" -> 15_000 to 30_000
            "High" -> 50_000 to 120_000
            else -> 30_000 to 60_000
        }
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuffer, maxBuffer, 1500, 3000)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    var showInfoPanel by remember { mutableStateOf(true) }
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
                val width = videoSize.width
                val height = videoSize.height
                if (width > 0 && height > 0) {
                    videoQuality = if (resFormat == "Labels") {
                        when {
                            height >= 2160 -> "4K UHD"
                            height >= 1080 -> "1080p FHD"
                            height >= 720 -> "720p HD"
                            else -> "SD ($height p)"
                        }
                    } else {
                        "${width}x${height}"
                    }
                    fps = "60 FPS"
                }
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
                        if (showInfoPanel) false
                        else {
                            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                event.nativeKeyEvent.repeatCount == 0) {
                                userInteracted()
                                switchToNextChannel()
                            }
                            true
                        }
                    }
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (showInfoPanel) false
                        else {
                            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                event.nativeKeyEvent.repeatCount == 0) {
                                userInteracted()
                                switchToPreviousChannel()
                            }
                            true
                        }
                    }
                    isConfirm -> {
                        if (showInfoPanel) false
                        else {
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
        val resizeModeVal = when (aspectRatioMode) {
            "Fit", "16:9", "4:3" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val boxScope = this
            val videoBoxModifier = when (aspectRatioMode) {
                "Full Screen", "Fit", "Fill" -> Modifier.fillMaxSize()
                else -> {
                    val safeWidth = boxScope.maxWidth.value.coerceAtLeast(1f)
                    val safeHeight = boxScope.maxHeight.value.coerceAtLeast(1f)
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
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(playbackError ?: tr("Ошибка воспроизведения"), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = {
                            retryCount = 0
                            playbackError = null
                            isBuffering = true
                            exoPlayer.seekToDefaultPosition()
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) { Text(tr("Повторить")) }
                    OutlinedButton(onClick = onExitChannel, shape = RoundedCornerShape(20.dp)) { Text(tr("Назад к каналам")) }
                }
            }
        }

        if (showInfoPanel) {
            val program = currentPrograms[currentUrl]
            val nextProg = nextPrograms[currentUrl]
            val totalTime = channelViewingTimes[currentUrl] ?: 0L
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            LaunchedEffect(Unit) {
                delay(100)
                runCatching { focusRequester.requestFocus() }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top Action Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val hours = totalTime / 3600
                        val minutes = (totalTime % 3600) / 60
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                String.format(Locale.getDefault(), "%02d:%02d h", hours, minutes),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExpressivePlayerIconButton(
                            onClick = { userInteracted(); showTracksDialog = true },
                            icon = Icons.Rounded.Audiotrack,
                            contentDescription = tr("Аудиодорожки")
                        )
                        ExpressivePlayerIconButton(
                            onClick = { userInteracted(); showSpeedDialog = true },
                            icon = Icons.Rounded.Speed,
                            contentDescription = tr("Скорость воспроизведения")
                        )
                        ExpressivePlayerIconButton(
                            onClick = { userInteracted(); onTriggerPip() },
                            icon = Icons.Rounded.PictureInPicture,
                            contentDescription = tr("Режим картинка-в-картинке")
                        )

                        if (clockEnabled) {
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    currentTime,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Recent Channels Carousel
                if (recentChannels.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recentChannels) { channel ->
                            ExpressiveRecentChannelCard(
                                channel = channel,
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

                // Center Playback Expressive Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isArchivePlayback) {
                        ExpressivePlaybackButton(
                            onClick = {
                                userInteracted()
                                val position = exoPlayer.currentPosition
                                exoPlayer.seekTo((position - 10_000L).coerceAtLeast(0L))
                            },
                            icon = Icons.Rounded.FastRewind,
                            size = 56.dp
                        )

                        ExpressivePlaybackButton(
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
                            icon = Icons.Rounded.FastForward,
                            size = 64.dp,
                            focusRequester = focusRequester,
                            accent = true
                        )

                        ExpressivePlaybackButton(
                            onClick = {
                                userInteracted()
                                val duration = exoPlayer.duration
                                val target = exoPlayer.currentPosition + 10_000L
                                exoPlayer.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
                            },
                            icon = Icons.Rounded.FastForward,
                            size = 56.dp
                        )
                    }

                    ExpressivePlaybackButton(
                        onClick = { userInteracted(); if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        size = 72.dp,
                        accent = true
                    )
                }

                // Bottom Channel Info & Stream Parameters Card (Static layout in Landscape)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            when (playerGlassTint) {
                                "OLED Black" -> Color.Black.copy(alpha = 0.95f)
                                "Transparent" -> Color.Transparent
                                else -> if (uiStyle == "LiquidGlass") Color.Black.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
                            }
                        )
                        .focusGroup()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (isArchivePlayback && archiveDurationMs > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = formatPlaybackTime(archivePositionMs),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium
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
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = formatPlaybackTime(archiveDurationMs),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = currentChannel?.logoUrl,
                            contentDescription = null,
                            modifier = Modifier.size(if (isLandscapeLayout) 48.dp else 56.dp).padding(end = 12.dp),
                            contentScale = ContentScale.Fit
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                currentChannel?.name ?: "Unknown",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = if (isLandscapeLayout) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = if (isLandscapeLayout) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        timeFormat.format(program.start),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    LinearProgressIndicator(
                                        progress = { progress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .width(if (isLandscapeLayout) 140.dp else 180.dp)
                                            .height(5.dp)
                                            .padding(horizontal = 6.dp)
                                            .clip(CircleShape),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Text(
                                        timeFormat.format(program.stop),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                if (nextProg != null) {
                                    Text(
                                        "${tr("Следующая:")} ${nextProg.title} (${timeFormat.format(nextProg.start)})",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Text(
                                    tr("Нет данных телепрограммы"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        val aspectRatioLabel = when (aspectRatioMode) {
                            "Full Screen" -> "Fill"
                            "16:9" -> "16:9"
                            "4:3" -> "4:3"
                            "Fit" -> "Fit"
                            else -> aspectRatioMode
                        }

                        ExpressivePlayerIconButton(
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
                            icon = when (aspectRatioMode) {
                                "16:9", "4:3" -> Icons.Rounded.AspectRatio
                                "Fit" -> Icons.Rounded.FitScreen
                                else -> Icons.Rounded.Fullscreen
                            },
                            contentDescription = aspectRatioLabel
                        )

                        ExpressivePlayerIconButton(
                            onClick = { userInteracted(); currentChannel?.let { viewModel.togglePinChannel(it.url) } },
                            icon = Icons.Rounded.PushPin,
                            contentDescription = null,
                            active = currentChannel?.isPinned == true
                        )

                        ExpressivePlayerIconButton(
                            onClick = { userInteracted(); currentChannel?.let { viewModel.toggleFavorite(it.url) } },
                            icon = if (currentChannel?.isFavorite == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            active = currentChannel?.isFavorite == true
                        )

                        ExpressivePlayerIconButton(
                            onClick = { userInteracted(); showArchiveDialog = true },
                            icon = Icons.Rounded.History,
                            contentDescription = tr("Архив"),
                            enabled = currentChannel != null
                        )

                        ExpressivePlayerIconButton(
                            onClick = onExitChannel,
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr("Назад")
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            if (showResolutionOpt) {
                                Text(
                                    "${tr("Разрешение")}: $videoQuality",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            if (showCodecsOpt) {
                                Text(
                                    "${tr("Видео")}: $videoCodec | ${tr("Аудио")}: $audioCodec",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val bitrateText = if (showBitrateOpt) "${tr("Битрейт")}: $bitRate" else ""
                            val fpsText = if (showFpsOpt) (if (bitrateText.isNotEmpty()) " | " else "") + "${tr("Кадры/сек")}: $fps" else ""
                            if (bitrateText.isNotEmpty() || fpsText.isNotEmpty()) {
                                Text(
                                    bitrateText + fpsText,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }

                            if (showSourceOpt) {
                                Text(
                                    text = "${tr("Источник")}: ${currentUrl.take(40)}...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Archive Dialog
        if (showArchiveDialog) {
            val now = System.currentTimeMillis()
            val channelKeys = remember(currentChannel?.url, currentChannel?.epgId, currentChannel?.tvgName, currentChannel?.name) {
                listOfNotNull(currentChannel?.epgId, currentChannel?.tvgName, currentChannel?.name)
                    .map { it.trim().lowercase(Locale.ROOT) }
                    .filter { it.isNotBlank() }
                    .toSet()
            }
            val archivePrograms = remember(epgPrograms, channelKeys, currentChannel?.catchupDays, now) {
                val providerDays = currentChannel?.catchupDays?.takeIf { it > 0 } ?: 14
                val historyDays = providerDays.coerceAtMost(14)
                val earliest = now - historyDays.toLong() * 24L * 60L * 60L * 1000L
                epgPrograms.asSequence()
                    .filter { it.channelId.trim().lowercase(Locale.ROOT) in channelKeys }
                    .filter { it.endTime <= now && it.endTime > 0L }
                    .filter { it.startTime >= earliest }
                    .sortedByDescending { it.startTime }
                    .toList()
            }

            AlertDialog(
                onDismissRequest = { showArchiveDialog = false },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text("${tr("Архив")} — ${currentChannel?.name ?: tr("Канал")}") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                val archiveUrl = currentChannel?.let {
                                    CatchupUrlBuilder.build(
                                        liveUrl = it.url,
                                        sourceTemplate = it.catchupSource,
                                        mode = if (catchupMode.equals("Auto", true)) (it.catchup?.takeIf { value -> value.isNotBlank() && !value.equals("none", true) && !value.equals("0", true) } ?: if (!it.catchupSource.isNullOrBlank()) "Default" else "Flussonic") else catchupMode,
                                        startTimeMs = archivedProgram.startTime,
                                        endTimeMs = archivedProgram.endTime
                                    )
                                }
                                GroupItem(
                                    onClick = {
                                        if (archiveUrl != null) {
                                            if (archiveLiveUrl == null) archiveLiveUrl = currentChannel.url
                                            currentUrl = archiveUrl
                                            showArchiveDialog = false
                                            playbackError = null
                                            isBuffering = true
                                            userInteracted()
                                        }
                                    }
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(archivedProgram.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${dateFormat.format(Date(archivedProgram.startTime))}–${dateFormat.format(Date(archivedProgram.endTime))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(tr("Нет доступных программ в архиве"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showArchiveDialog = false }) { Text(tr("Закрыть")) } }
            )
        }

        // Audio Tracks Dialog
        if (showTracksDialog) {
            val currentTracks = exoPlayer.currentTracks
            val audioGroup = currentTracks.groups.find { it.type == C.TRACK_TYPE_AUDIO }

            AlertDialog(
                onDismissRequest = { showTracksDialog = false },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text(tr("Аудиодорожки")) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        if (audioGroup != null) {
                            for (i in 0 until audioGroup.length) {
                                val format = audioGroup.getTrackFormat(i)
                                val isSelected = audioGroup.isTrackSelected(i)
                                val lang = format.language ?: "Audio ${i + 1}"
                                GroupItem(
                                    onClick = {
                                        userInteracted()
                                        val override = TrackSelectionOverride(audioGroup.mediaTrackGroup, i)
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(override)
                                            .build()
                                        showTracksDialog = false
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                                    ) {
                                        RadioButton(selected = isSelected, onClick = null)
                                        Spacer(Modifier.width(12.dp))
                                        Text(lang, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        } else {
                            Text(tr("Нет доступных аудиодорожек"))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showTracksDialog = false }) { Text(tr("Закрыть")) } }
            )
        }

        // Speed Dialog
        if (showSpeedDialog) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text(tr("Скорость воспроизведения")) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ButtonGroup(
                            options = listOf(
                                "0.5" to "0.5x",
                                "1.0" to "1.0x",
                                "1.25" to "1.25x",
                                "1.5" to "1.5x",
                                "2.0" to "2.0x"
                            ),
                            selected = playbackSpeed.toString(),
                            onSelect = {
                                userInteracted()
                                val spd = it.toFloatOrNull() ?: 1.0f
                                viewModel.updateSetting("playback_speed", spd)
                                showSpeedDialog = false
                            }
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { showSpeedDialog = false }) { Text(tr("Закрыть")) } }
            )
        }

        // Password Dialog
        if (showPasswordDialog != null) {
            val (channel, _, _) = showPasswordDialog!!
            val savedPassword = viewModel.getChannelPassword(channel.url) ?: ""
            AlertDialog(
                onDismissRequest = { showPasswordDialog = null; tempPassword = ""; passwordError = false },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text(tr("Введите пароль")) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = tempPassword,
                            onValueChange = { tempPassword = it },
                            label = { Text(tr("Пароль")) },
                            isError = passwordError,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (passwordError) {
                            Text(tr("Неверный пароль"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
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
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordDialog = null; tempPassword = ""; passwordError = false }) { Text(tr("Отмена")) }
                }
            )
        }

        // Persistent Clock Overlay
        if (clockEnabled && clockPosition != "Off") {
            val clockAlignment = when (clockPosition) {
                "Top left" -> Alignment.TopStart
                "Top right" -> Alignment.TopEnd
                "Bottom left" -> Alignment.BottomStart
                "Bottom right" -> Alignment.BottomEnd
                else -> Alignment.TopEnd
            }
            Box(
                modifier = Modifier
                    .align(clockAlignment)
                    .padding(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentTime,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExpressivePlayerIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String? = null,
    active: Boolean = false,
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.15f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "btnScale")
    val cs = MaterialTheme.colorScheme

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(CircleShape)
            .background(
                if (active) cs.primary
                else if (isFocused) cs.primaryContainer
                else cs.surfaceContainerHigh.copy(alpha = 0.85f)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) cs.onPrimary
            else if (isFocused) cs.onPrimaryContainer
            else cs.onSurface
        )
    }
}

@Composable
private fun ExpressivePlaybackButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    focusRequester: FocusRequester? = null,
    accent: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.2f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "playBtnScale")
    val cs = MaterialTheme.colorScheme

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(CircleShape)
            .background(
                if (accent) cs.primary
                else if (isFocused) cs.primaryContainer
                else cs.surfaceContainerHigh.copy(alpha = 0.85f)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) cs.onPrimary
            else if (isFocused) cs.onPrimaryContainer
            else cs.onSurface,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@Composable
private fun ExpressiveRecentChannelCard(
    channel: com.kraftplay.openiptv.model.IptvChannel,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "recentScale")
    val cs = MaterialTheme.colorScheme

    Surface(
        shape = CircleShape,
        color = if (isFocused) cs.primaryContainer else cs.surfaceContainerHigh.copy(alpha = 0.85f),
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier.size(22.dp).padding(end = 6.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                channel.name,
                color = if (isFocused) cs.onPrimaryContainer else cs.onSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
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
