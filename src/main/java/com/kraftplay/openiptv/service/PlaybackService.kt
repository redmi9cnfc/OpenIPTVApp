package com.kraftplay.openiptv.service

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import com.kraftplay.openiptv.model.IptvChannel
import com.kraftplay.openiptv.model.Playlist
import com.kraftplay.openiptv.parser.M3uParser
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient()
    private var cachedChannels = listOf<IptvChannel>()
    private var favoriteUrls = setOf<String>()

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
        loadData()
    }

    private fun loadData() {
        executor.execute {
            val prefs = getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
            favoriteUrls = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
            
            val jsonStr = prefs.getString("playlists_json", "[]") ?: "[]"
            val playlists = try { Json.decodeFromString<List<Playlist>>(jsonStr) } catch (e: Exception) { emptyList() }
            val selected = playlists.find { it.isSelected } ?: playlists.firstOrNull()
            
            selected?.let { playlist ->
                try {
                    val request = Request.Builder().url(playlist.url).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.let { body ->
                                cachedChannels = M3uParser.parse(body.byteStream())
                            }
                        }
                    }
                } catch (e: Exception) { }
            }
        }
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId("ROOT")
                .setMediaMetadata(MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build())
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = mutableListOf<MediaItem>()
            when {
                parentId == "ROOT" -> {
                    // Add Favorites category
                    items.add(createBrowsableItem("CAT_Favorites", "Favorites"))
                    // Add Recent category (mock for now or load from prefs)
                    items.add(createBrowsableItem("CAT_Recent", "Recent"))
                    
                    val categories = cachedChannels.map { it.category ?: "Other" }.distinct().sorted()
                    categories.forEach { category ->
                        items.add(createBrowsableItem("CAT_$category", category))
                    }
                }
                parentId == "CAT_Favorites" -> {
                    cachedChannels.filter { favoriteUrls.contains(it.url) }.forEach { channel ->
                        items.add(createPlayableItem(channel))
                    }
                }
                parentId.startsWith("CAT_") -> {
                    val category = parentId.substring(4)
                    cachedChannels.filter { (it.category ?: "Other") == category }.forEach { channel ->
                        items.add(createPlayableItem(channel))
                    }
                }
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
        }

        private fun createBrowsableItem(id: String, title: String): MediaItem {
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build())
                .build()
        }

        private fun createPlayableItem(channel: IptvChannel): MediaItem {
            return MediaItem.Builder()
                .setMediaId(channel.url)
                .setUri(channel.url)
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(channel.name)
                    .setArtworkUri(channel.logoUrl?.let { Uri.parse(it) })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build())
                .build()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updatedItems = mediaItems.map { item ->
                if (item.localConfiguration == null) {
                    MediaItem.Builder()
                        .setUri(item.mediaId)
                        .setMediaMetadata(item.mediaMetadata)
                        .build()
                } else item
            }.toMutableList()
            return Futures.immediateFuture(updatedItems)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        executor.shutdown()
        super.onDestroy()
    }
}
