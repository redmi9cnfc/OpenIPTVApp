package com.kraftplay.openiptv.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.kraftplay.openiptv.viewmodel.MainViewModel
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(viewModel: MainViewModel, url: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val forceLandscape by viewModel.forceLandscape.collectAsState()
    val backgroundPlayback by viewModel.backgroundPlayback.collectAsState()
    val autoHideTimeout by viewModel.autoHideTimeout.collectAsState()
    val bufferingLevel by viewModel.bufferingLevel.collectAsState()

    val activity = context as? Activity
    
    val decodedUrl = remember(url) {
        try {
            URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
        } catch (e: Exception) {
            url
        }
    }

    val exoPlayer = remember(decodedUrl, bufferingLevel) {
        val (minBuf, maxBuf) = when(bufferingLevel) {
            "Low" -> 500 to 2000
            "High" -> 30000 to 60000
            else -> 5000 to 15000
        }
        
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuf, maxBuf, 1000, 1500)
            .build()
            
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                val mediaItem = MediaItem.fromUri(decodedUrl)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(forceLandscape) {
        if (forceLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(lifecycleOwner, backgroundPlayback) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> if (!backgroundPlayback) exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = if(autoHideTimeout == 0) -1 else autoHideTimeout * 1000
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
