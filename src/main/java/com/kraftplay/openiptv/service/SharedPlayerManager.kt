package com.kraftplay.openiptv.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kraftplay.openiptv.model.IptvChannel

@OptIn(UnstableApi::class)
object SharedPlayerManager {

    @Volatile
    private var player: ExoPlayer? = null
    private var currentBufferingLevel: String = "Standard"

    @Synchronized
    fun getOrCreatePlayer(context: Context, bufferingLevel: String = currentBufferingLevel): ExoPlayer {
        val appContext = context.applicationContext
        val existing = player
        if (existing != null) {
            return existing
        }

        currentBufferingLevel = bufferingLevel
        val (minBuffer, maxBuffer) = when (bufferingLevel) {
            "Low" -> 15_000 to 30_000
            "High" -> 50_000 to 120_000
            else -> 30_000 to 60_000
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuffer, maxBuffer, 1500, 3000)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(25_000)
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")

        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(httpDataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(appContext)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build().also { created ->
                player = created
            }
    }

    /** Запускает поток в общем плеере с передачей очереди и метаданных в OpenIPTV Audio */
    fun playStream(
        context: Context,
        streamUrl: String,
        channel: IptvChannel?,
        allChannels: List<IptvChannel>,
        bufferingLevel: String = "Standard"
    ) {
        val appContext = context.applicationContext
        ensureServiceRunning(appContext)
        val exo = getOrCreatePlayer(appContext, bufferingLevel)

        // Если этот же канал уже играет (например, вернулись в OpenAUTO из навигатора) — не перезапускаем поток
        if (exo.currentMediaItem?.mediaId == streamUrl && (exo.isPlaying || exo.playbackState == ExoPlayer.STATE_BUFFERING)) {
            return
        }

        if (allChannels.isNotEmpty() && allChannels.any { it.url == streamUrl }) {
            val queue = allChannels.map { buildMediaItem(it.url, it) }
            val startIndex = allChannels.indexOfFirst { it.url == streamUrl }.coerceAtLeast(0)
            exo.setMediaItems(queue, startIndex, C.TIME_UNSET)
        } else {
            exo.setMediaItem(buildMediaItem(streamUrl, channel))
        }

        exo.prepare()
        exo.playWhenReady = true
    }

    fun buildMediaItem(url: String, channel: IptvChannel?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(channel?.name ?: "OpenIPTV")
            .setArtist(channel?.category ?: "OpenIPTV Live")
            .setSubtitle(channel?.tvgName ?: channel?.category)
            .setArtworkUri(channel?.logoUrl?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .build()

        return MediaItem.Builder()
            .setMediaId(url)
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()
    }

    fun ensureServiceRunning(context: Context) {
        val intent = Intent(context, PlaybackService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun findFallbackStreamUrl(currentUrl: String, channel: IptvChannel?, allChannels: List<IptvChannel>): String? {
        if (allChannels.isEmpty()) return null
        val cleanCurrent = currentUrl.trim().lowercase()
        val targetName = channel?.name?.trim()?.lowercase() ?: ""
        val targetTvg = channel?.tvgName?.trim()?.lowercase() ?: ""
        val targetEpg = channel?.epgId?.trim()?.lowercase() ?: ""

        val matches = allChannels.filter { item ->
            val itemUrl = item.url.trim().lowercase()
            itemUrl != cleanCurrent && (
                (targetName.isNotEmpty() && item.name.trim().lowercase() == targetName) ||
                (targetTvg.isNotEmpty() && !item.tvgName.isNullOrBlank() && item.tvgName.trim().lowercase() == targetTvg) ||
                (targetEpg.isNotEmpty() && !item.epgId.isNullOrBlank() && item.epgId.trim().lowercase() == targetEpg)
            )
        }
        return matches.firstOrNull()?.url
    }

    @Synchronized
    fun stopPlayback() {
        player?.let { exo ->
            exo.stop()
            exo.clearMediaItems()
        }
    }

    @Synchronized
    fun releasePlayer() {
        player?.release()
        player = null
    }
}
