package com.kraftplay.openiptv.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.kraftplay.openiptv.R
import com.kraftplay.openiptv.model.IptvChannel
import com.kraftplay.openiptv.model.Playlist
import com.kraftplay.openiptv.parser.M3uParser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
open class PlaybackService : MediaLibraryService() {

    companion object {
        private const val ROOT_ID = "ROOT"
        private const val TAB_FAVORITES = "TAB_FAVORITES"
        private const val TAB_RECENT = "TAB_RECENT"
        private const val TAB_ALL = "TAB_ALL"
        private const val TAB_FOLDERS = "TAB_FOLDERS"
        private const val CAT_PREFIX = "CAT_"

        private const val CMD_TOGGLE_FAVORITE = "com.kraftplay.openiptv.CMD_TOGGLE_FAVORITE"
        private const val CMD_RELOAD_STREAM = "com.kraftplay.openiptv.CMD_RELOAD_STREAM"

        private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1
        private const val CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2
        private const val EXTRA_MEDIA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
    }

    protected var mediaLibrarySession: MediaLibrarySession? = null
    protected val executor = Executors.newSingleThreadExecutor()
    protected val client = OkHttpClient()
    protected var cachedChannels = listOf<IptvChannel>()
    protected var favoriteUrls = mutableSetOf<String>()
    protected val mainHandler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private var lastBrowsedList: List<IptvChannel> = emptyList()

    private fun promoteToForeground() {
        runCatching {
            val channelId = "playback_service_channel"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "OpenAUDIO Playback",
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("OpenAUDIO")
                .setContentText("IPTV Audio Service")
                .setSmallIcon(com.kraftplay.openiptv.R.mipmap.ic_launcher)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    101,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(101, notification)
            }
        }
    }

    override fun onCreate() {
        promoteToForeground()
        super.onCreate()

        val player = SharedPlayerManager.getOrCreatePlayer(this)
        val sessionId = "OpenIPTVSession_${this.javaClass.simpleName}_${System.identityHashCode(this)}"
        val builder = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setId(sessionId)

        getPendingActivityIntent()?.let { builder.setSessionActivity(it) }
        mediaLibrarySession = builder.build()

        attachPlaybackRecovery(player)
        loadData()
        updateCustomLayout(player)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        return super.onStartCommand(intent, flags, startId)
    }

    open fun getPendingActivityIntent(): PendingIntent? {
        val intent = Intent(this, com.kraftplay.openiptv.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    protected fun loadData() {
        val prefs = getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        favoriteUrls = (prefs.getStringSet("favorites", emptySet()) ?: emptySet()).toMutableSet()

        val cacheFile = File(cacheDir, "channels_cache.json")
        if (cacheFile.exists()) {
            runCatching {
                val jsonText = cacheFile.readText()
                if (jsonText.isNotBlank()) {
                    cachedChannels = Json { ignoreUnknownKeys = true }.decodeFromString(jsonText)
                }
            }
        }

        if (cachedChannels.isEmpty()) {
            executor.execute {
                val jsonStr = prefs.getString("playlists_json", "[]") ?: "[]"
                val playlists = runCatching {
                    Json { ignoreUnknownKeys = true }.decodeFromString<List<Playlist>>(jsonStr)
                }.getOrDefault(emptyList())

                val selected = playlists.find { it.isSelected && it.enabled }
                    ?: playlists.firstOrNull { it.enabled }
                    ?: playlists.firstOrNull()

                selected?.let { playlist ->
                    runCatching {
                        val request = Request.Builder().url(playlist.url).build()
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body?.let { body ->
                                    val parsed = M3uParser.parse(body.byteStream())
                                    cachedChannels = parsed
                                    runCatching { cacheFile.writeText(Json.encodeToString(parsed)) }
                                    notifyLibraryChanged()
                                }
                            }
                        }
                    }
                }
            }
        } else {
            notifyLibraryChanged()
        }
    }

    private fun notifyLibraryChanged() {
        mainHandler.post {
            mediaLibrarySession?.notifyChildrenChanged(ROOT_ID, 4, null)
            mediaLibrarySession?.notifyChildrenChanged(TAB_ALL, cachedChannels.size, null)
            mediaLibrarySession?.notifyChildrenChanged(TAB_FAVORITES, favoriteUrls.size, null)
        }
    }

    fun currentPlayer(): ExoPlayer? = mediaLibrarySession?.player as? ExoPlayer

    private fun attachPlaybackRecovery(player: ExoPlayer) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    retryCount = 0
                    updateCustomLayout(player)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val url = mediaItem?.mediaId.orEmpty()
                if (url.isNotEmpty()) {
                    recordRecent(url)
                    getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("last_channel_url", url)
                        .apply()
                }
                updateCustomLayout(player)
            }

            override fun onPlayerError(error: PlaybackException) {
                retryCount++
                if (retryCount <= 5) {
                    mainHandler.postDelayed({
                        runCatching {
                            player.prepare()
                            player.playWhenReady = true
                        }
                    }, 1000L * retryCount)
                }
            }
        })
    }

    private fun updateCustomLayout(player: Player) {
        val session = mediaLibrarySession ?: return
        val currentUrl = player.currentMediaItem?.mediaId.orEmpty()
        val isFav = currentUrl.isNotEmpty() && favoriteUrls.contains(currentUrl)

        val favButton = CommandButton.Builder()
            .setDisplayName(if (isFav) "Убрать из избранного" else "В избранное")
            .setIconResId(
                if (isFav) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
            .setSessionCommand(SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
            .build()

        val reloadButton = CommandButton.Builder()
            .setDisplayName("Обновить поток")
            .setIconResId(android.R.drawable.ic_popup_sync)
            .setSessionCommand(SessionCommand(CMD_RELOAD_STREAM, Bundle.EMPTY))
            .build()

        session.setCustomLayout(ImmutableList.of(favButton, reloadButton))
    }

    private fun toggleFavorite(url: String) {
        if (url.isBlank()) return
        val prefs = getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        val updated = (prefs.getStringSet("favorites", emptySet()) ?: emptySet()).toMutableSet()
        if (updated.contains(url)) {
            updated.remove(url)
        } else {
            updated.add(url)
        }
        favoriteUrls = updated
        prefs.edit().putStringSet("favorites", updated).apply()
        mediaLibrarySession?.notifyChildrenChanged(TAB_FAVORITES, updated.size, null)
        currentPlayer()?.let { updateCustomLayout(it) }
    }

    private fun recordRecent(url: String) {
        val recents = LinkedHashSet<String>().apply { add(url) }
        recents.addAll(recentUrls().filter { it != url })
        getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("recent_channels", recents.take(30).joinToString("\n"))
            .apply()
        mediaLibrarySession?.notifyChildrenChanged(TAB_RECENT, recents.size.coerceAtMost(30), null)
    }

    private fun recentUrls(): List<String> =
        getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
            .getString("recent_channels", "").orEmpty()
            .split("\n").filter { it.isNotEmpty() }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(CMD_RELOAD_STREAM, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_TOGGLE_FAVORITE -> {
                    val currentUrl = session.player.currentMediaItem?.mediaId.orEmpty()
                    toggleFavorite(currentUrl)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CMD_RELOAD_STREAM -> {
                    session.player.prepare()
                    session.player.playWhenReady = true
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: android.content.Intent
        ): Boolean {
            @Suppress("DEPRECATION")
            val keyEvent = intent.getParcelableExtra<KeyEvent>(android.content.Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                val prefs = getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
                val nextAction = prefs.getString("wheel_next_action", "NextChannel") ?: "NextChannel"
                val prevAction = prefs.getString("wheel_prev_action", "PrevChannel") ?: "PrevChannel"

                val player = session.player
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        when (nextAction) {
                            "Skip10" -> player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                            "Skip30" -> player.seekTo((player.currentPosition + 30_000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                            "Skip60" -> player.seekTo((player.currentPosition + 60_000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                            else -> if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(0, 0L)
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        when (prevAction) {
                            "Rewind10" -> player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                            "Rewind30" -> player.seekTo((player.currentPosition - 30_000L).coerceAtLeast(0L))
                            "Rewind60" -> player.seekTo((player.currentPosition - 60_000L).coerceAtLeast(0L))
                            else -> if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo((player.mediaItemCount - 1).coerceAtLeast(0), 0L)
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                        val chUpAction = prefs.getString("wheel_channel_up_action", "NextChannel") ?: "NextChannel"
                        when (chUpAction) {
                            "PrevChannel" -> if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo((player.mediaItemCount - 1).coerceAtLeast(0), 0L)
                            "Skip10" -> player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L)))
                            else -> if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(0, 0L)
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                        val chDownAction = prefs.getString("wheel_channel_down_action", "PrevChannel") ?: "PrevChannel"
                        when (chDownAction) {
                            "NextChannel" -> if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(0, 0L)
                            "Rewind10" -> player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                            else -> if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo((player.mediaItemCount - 1).coerceAtLeast(0), 0L)
                        }
                        return true
                    }
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val extras = Bundle().apply {
                putBoolean(CONTENT_STYLE_SUPPORTED, true)
                putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
                putBoolean(EXTRA_MEDIA_SEARCH_SUPPORTED, true)
            }

            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(getString(R.string.app_name_audio))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setExtras(extras)
                        .build()
                )
                .build()

            val rootParams = LibraryParams.Builder()
                .setExtras(extras)
                .build()

            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, rootParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val prefs = getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
            favoriteUrls = (prefs.getStringSet("favorites", emptySet()) ?: emptySet()).toMutableSet()

            val items = mutableListOf<MediaItem>()

            when {
                parentId == ROOT_ID -> {
                    items.add(createBrowsableTab(TAB_FAVORITES, "Избранное", android.R.drawable.btn_star_big_on))
                    items.add(createBrowsableTab(TAB_RECENT, "Недавние", android.R.drawable.ic_menu_recent_history))
                    items.add(createBrowsableTab(TAB_FOLDERS, "Папки", android.R.drawable.ic_menu_sort_by_size))
                    items.add(createBrowsableTab(TAB_ALL, "Все каналы", R.mipmap.ic_launcher))
                }

                parentId == TAB_FAVORITES -> {
                    val favChannels = cachedChannels.filter { favoriteUrls.contains(it.url) }
                    lastBrowsedList = favChannels
                    favChannels.forEach { items.add(createPlayableItem(it)) }
                }

                parentId == TAB_RECENT -> {
                    val recents = recentUrls()
                    val recentChannels = cachedChannels
                        .filter { recents.contains(it.url) }
                        .sortedBy { recents.indexOf(it.url) }
                    lastBrowsedList = recentChannels
                    recentChannels.forEach { items.add(createPlayableItem(it)) }
                }

                parentId == TAB_ALL -> {
                    lastBrowsedList = cachedChannels
                    cachedChannels.forEach { items.add(createPlayableItem(it)) }
                }

                parentId == TAB_FOLDERS -> {
                    val categories = cachedChannels
                        .map { it.category?.takeIf { c -> c.isNotBlank() } ?: "Общие" }
                        .distinct()
                    categories.forEach { category ->
                        val count = cachedChannels.count {
                            (it.category?.takeIf { c -> c.isNotBlank() } ?: "Общие") == category
                        }
                        items.add(createBrowsableFolder("$CAT_PREFIX$category", category, "$count каналов"))
                    }
                }

                parentId.startsWith(CAT_PREFIX) -> {
                    val category = parentId.removePrefix(CAT_PREFIX)
                    val folderChannels = cachedChannels.filter {
                        (it.category?.takeIf { c -> c.isNotBlank() } ?: "Общие") == category
                    }
                    lastBrowsedList = folderChannels
                    folderChannels.forEach { items.add(createPlayableItem(it)) }
                }
            }

            val fromIndex = (page * pageSize).coerceAtMost(items.size)
            val toIndex = ((page + 1) * pageSize).coerceAtMost(items.size)
            val paged = if (fromIndex < toIndex) items.subList(fromIndex, toIndex) else emptyList()

            return Futures.immediateFuture(LibraryResult.ofItemList(paged, params))
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val q = query.trim().lowercase()
            val count = cachedChannels.count {
                it.name.lowercase().contains(q) || (it.tvgName ?: "").lowercase().contains(q)
            }
            session.notifySearchResultChanged(browser, query, count, params)
            return Futures.immediateFuture(LibraryResult.ofVoid(params))
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val q = query.trim().lowercase()
            val matched = cachedChannels.filter {
                it.name.lowercase().contains(q) || (it.tvgName ?: "").lowercase().contains(q)
            }
            lastBrowsedList = matched
            val result = matched.map { createPlayableItem(it) }
            return Futures.immediateFuture(LibraryResult.ofItemList(result, params))
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val selectedItem = mediaItems.firstOrNull()
                ?: return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)

            val selectedUrl = selectedItem.mediaId
            val queueSource = if (lastBrowsedList.any { it.url == selectedUrl }) {
                lastBrowsedList
            } else {
                cachedChannels
            }

            if (queueSource.isNotEmpty()) {
                val queue = queueSource.map { createPlayableItem(it) }
                val targetIndex = queueSource.indexOfFirst { it.url == selectedUrl }.coerceAtLeast(0)
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(queue, targetIndex, C.TIME_UNSET)
                )
            }

            val fallback = SharedPlayerManager.buildMediaItem(selectedUrl, null)
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(listOf(fallback), 0, C.TIME_UNSET)
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updated = mediaItems.map { item ->
                if (item.localConfiguration == null) {
                    MediaItem.Builder()
                        .setMediaId(item.mediaId)
                        .setUri(item.mediaId)
                        .setMediaMetadata(item.mediaMetadata)
                        .build()
                } else item
            }.toMutableList()
            return Futures.immediateFuture(updated)
        }

        private fun createBrowsableTab(id: String, title: String, iconRes: Int): MediaItem {
            val iconUri = Uri.parse("android.resource://$packageName/$iconRes")
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtworkUri(iconUri)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                        .build()
                )
                .build()
        }

        private fun createBrowsableFolder(id: String, title: String, subtitle: String): MediaItem {
            val extras = Bundle().apply {
                putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
            }
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setExtras(extras)
                        .build()
                )
                .build()
        }

        private fun createPlayableItem(channel: IptvChannel): MediaItem =
            SharedPlayerManager.buildMediaItem(channel.url, channel)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        SharedPlayerManager.releasePlayer()
        executor.shutdown()
        super.onDestroy()
    }
}