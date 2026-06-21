package com.kraftplay.openiptv.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kraftplay.openiptv.model.EpgProgram
import com.kraftplay.openiptv.model.EpgSource
import com.kraftplay.openiptv.model.IptvChannel
import com.kraftplay.openiptv.model.Playlist
import com.kraftplay.openiptv.parser.M3uParser
import com.kraftplay.openiptv.parser.XmlTvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val client = OkHttpClient()
    private val prefs = application.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
    
    private val _channels = MutableStateFlow<List<IptvChannel>>(emptyList())
    private val _favorites = MutableStateFlow<Set<String>>(loadFavoritesFromPrefs())
    
    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _playlists = MutableStateFlow<List<Playlist>>(loadPlaylistsFromPrefs())
    val playlists: StateFlow<List<Playlist>> = _playlists

    private val _epgSources = MutableStateFlow<List<EpgSource>>(loadEpgSourcesFromPrefs())
    val epgSources: StateFlow<List<EpgSource>> = _epgSources

    private val _epgPrograms = MutableStateFlow<List<EpgProgram>>(emptyList())
    private val _currentPrograms = MutableStateFlow<Map<String, EpgProgram>>(emptyMap())
    val currentPrograms: StateFlow<Map<String, EpgProgram>> = _currentPrograms

    private val _isEpgLoading = MutableStateFlow(false)
    val isEpgLoading: StateFlow<Boolean> = _isEpgLoading

    private val _epgStatus = MutableStateFlow("")
    val epgStatus: StateFlow<String> = _epgStatus

    // Viewing time per channel (URL -> Seconds)
    private val _channelViewingTimes = MutableStateFlow<Map<String, Long>>(loadChannelTimesFromPrefs())
    val channelViewingTimes: StateFlow<Map<String, Long>> = _channelViewingTimes

    // UI Settings
    val fontSize = MutableStateFlow(prefs.getFloat("font_size", 14f))
    val showClock = MutableStateFlow(prefs.getBoolean("show_clock", true))
    val language = MutableStateFlow(prefs.getString("language", Locale.getDefault().language) ?: "en")
    val hidePlaylistUrl = MutableStateFlow(prefs.getBoolean("hide_playlist_url", false))
    val listSize = MutableStateFlow(prefs.getString("list_size", "Standard") ?: "Standard")
    val themeMode = MutableStateFlow(prefs.getString("theme_mode", "Dark") ?: "Dark")
    val accentColor = MutableStateFlow(prefs.getString("accent_color", "Blue") ?: "Blue")

    // Player Settings
    val autoStartLast = MutableStateFlow(prefs.getBoolean("auto_start_last", false))
    val lastChannelUrl = MutableStateFlow(prefs.getString("last_channel_url", "") ?: "")
    val fullScreenByDefault = MutableStateFlow(prefs.getBoolean("full_screen_default", false))
    val forceLandscape = MutableStateFlow(prefs.getBoolean("force_landscape", false))
    val backgroundPlayback = MutableStateFlow(prefs.getBoolean("background_playback", false))
    val autoHideTimeout = MutableStateFlow(prefs.getInt("auto_hide_timeout", 3))
    val bufferingLevel = MutableStateFlow(prefs.getString("buffering_level", "Standard") ?: "Standard")
    val backBehavior = MutableStateFlow(prefs.getString("back_behavior", "Back to list") ?: "Back to list")
    val sleepTimer = MutableStateFlow(0)
    val reloadStreamOnToggle = MutableStateFlow(prefs.getBoolean("reload_stream_toggle", false))

    val categories: StateFlow<List<String>> = _channels.map { channels ->
        val cats = channels.mapNotNull { it.category }.filter { it.isNotEmpty() }.distinct().sorted()
        listOf("All", "Favorites") + cats
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All", "Favorites"))

    val filteredChannels: StateFlow<List<IptvChannel>> = combine(_channels, _selectedCategory, _searchQuery, _favorites) { channels, category, query, favs ->
        channels.map { it.copy(isFavorite = favs.contains(it.url)) }.filter { channel ->
            val matchesCategory = when(category) {
                "All" -> true
                "Favorites" -> channel.isFavorite
                else -> (channel.category ?: "") == category
            }
            val matchesSearch = channel.name.contains(query, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        val selected = _playlists.value.find { it.isSelected }
        selected?.let { loadPlaylist(it.url) }
        refreshAllEpg()
        viewModelScope.launch {
            while (true) {
                updateCurrentPrograms()
                if (sleepTimer.value > 0) sleepTimer.value -= 1
                delay(60000)
            }
        }
    }

    private fun updateCurrentPrograms() {
        val now = Date()
        val allPrograms = _epgPrograms.value.filter { it.start.before(now) && it.stop.after(now) }
        val currentMap = mutableMapOf<String, EpgProgram>()
        _channels.value.forEach { channel ->
            val match = allPrograms.find { it.channelId == channel.epgId || it.channelId == channel.name }
            if (match != null) {
                channel.epgId?.let { currentMap[it] = match }
                currentMap[channel.name] = match
            }
        }
        _currentPrograms.value = currentMap
    }

    fun updateChannelViewingTime(url: String, seconds: Long) {
        val currentMap = _channelViewingTimes.value.toMutableMap()
        val currentTime = currentMap[url] ?: 0L
        currentMap[url] = currentTime + seconds
        _channelViewingTimes.value = currentMap
        saveChannelTimesToPrefs(currentMap)
    }

    private fun saveChannelTimesToPrefs(map: Map<String, Long>) {
        val json = Json.encodeToString(map)
        prefs.edit().putString("channel_viewing_times", json).apply()
    }

    private fun loadChannelTimesFromPrefs(): Map<String, Long> {
        val json = prefs.getString("channel_viewing_times", null) ?: return emptyMap()
        return try { Json.decodeFromString(json) } catch (e: Exception) { emptyMap() }
    }

    fun loadPlaylist(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.let { body ->
                            val parsedChannels = M3uParser.parse(body.byteStream())
                            _channels.value = parsedChannels
                            
                            // Update channel count for the playlist
                            _playlists.value = _playlists.value.map {
                                if (it.url == url) it.copy(channelCount = parsedChannels.size) else it
                            }
                            savePlaylistsToPrefs()
                            updateCurrentPrograms()
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() } finally { _isLoading.value = false }
        }
    }

    fun toggleFavorite(url: String) {
        val newFavs = _favorites.value.toMutableSet()
        if (newFavs.contains(url)) newFavs.remove(url) else newFavs.add(url)
        _favorites.value = newFavs
        prefs.edit().putStringSet("favorites", newFavs).apply()
    }

    private fun loadFavoritesFromPrefs(): Set<String> = prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    fun refreshAllEpg() {
        val enabledSources = _epgSources.value.filter { it.enabled }
        if (enabledSources.isEmpty()) return
        _isEpgLoading.value = true
        _epgStatus.value = "0%"
        viewModelScope.launch(Dispatchers.IO) {
            var loadedCount = 0
            enabledSources.forEach { source ->
                try {
                    val request = Request.Builder().url(source.url).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.let { body ->
                                val programs = XmlTvParser.parse(body.byteStream())
                                _epgPrograms.value = (_epgPrograms.value + programs).distinctBy { "${it.channelId}_${it.start.time}" }
                                loadedCount++
                                _epgStatus.value = "${(loadedCount * 100) / enabledSources.size}%"
                                updateCurrentPrograms()
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            _isEpgLoading.value = false
            _epgStatus.value = "100%"
        }
    }

    fun updateSetting(key: String, value: Any) {
        val editor = prefs.edit()
        when (value) {
            is Boolean -> {
                editor.putBoolean(key, value)
                when(key) {
                    "show_clock" -> showClock.value = value
                    "hide_playlist_url" -> hidePlaylistUrl.value = value
                    "auto_start_last" -> autoStartLast.value = value
                    "full_screen_default" -> {}
                    "reload_stream_toggle" -> reloadStreamOnToggle.value = value
                    "force_landscape" -> forceLandscape.value = value
                    "background_playback" -> backgroundPlayback.value = value
                }
            }
            is Float -> { editor.putFloat(key, value); if (key == "font_size") fontSize.value = value }
            is String -> {
                editor.putString(key, value)
                when(key) {
                    "language" -> language.value = value
                    "list_size" -> listSize.value = value
                    "theme_mode" -> themeMode.value = value
                    "accent_color" -> accentColor.value = value
                    "last_channel_url" -> lastChannelUrl.value = value
                    "back_behavior" -> backBehavior.value = value
                    "buffering_level" -> bufferingLevel.value = value
                }
            }
            is Int -> { editor.putInt(key, value); if (key == "auto_hide_timeout") autoHideTimeout.value = value }
        }
        editor.apply()
    }

    private val _recentChannels = MutableStateFlow<List<IptvChannel>>(emptyList())
    val recentChannels: StateFlow<List<IptvChannel>> = _recentChannels

    fun addToRecent(channel: IptvChannel) {
        val newList = listOf(channel) + _recentChannels.value.filter { it.url != channel.url }
        _recentChannels.value = newList.take(10)
    }

    fun addPlaylist(name: String, url: String) {
        val new = Playlist(UUID.randomUUID().toString(), name, url, _playlists.value.isEmpty())
        _playlists.value = _playlists.value + new
        savePlaylistsToPrefs()
        if (new.isSelected) loadPlaylist(url)
    }

    fun deletePlaylist(id: String) { _playlists.value = _playlists.value.filter { it.id != id }; savePlaylistsToPrefs() }
    fun selectPlaylist(id: String) {
        _playlists.value = _playlists.value.map { it.copy(isSelected = it.id == id) }
        savePlaylistsToPrefs()
        _playlists.value.find { it.isSelected }?.let { loadPlaylist(it.url) }
    }

    fun addEpgSource(name: String, url: String) {
        val new = EpgSource(UUID.randomUUID().toString(), name, url)
        _epgSources.value = _epgSources.value + new
        saveEpgSourcesToPrefs()
        refreshAllEpg()
    }

    fun deleteEpgSource(id: String) { _epgSources.value = _epgSources.value.filter { it.id != id }; saveEpgSourcesToPrefs(); refreshAllEpg() }

    fun clearData() {
        prefs.edit().clear().apply()
        _playlists.value = emptyList()
        _epgSources.value = emptyList()
        _channels.value = emptyList()
        _epgPrograms.value = emptyList()
        _currentPrograms.value = emptyMap()
        _favorites.value = emptySet()
        _channelViewingTimes.value = emptyMap()
    }

    private fun savePlaylistsToPrefs() = prefs.edit().putString("playlists_json", Json.encodeToString(_playlists.value)).apply()
    private fun loadPlaylistsFromPrefs(): List<Playlist> = try { Json.decodeFromString(prefs.getString("playlists_json", "[]")!!) } catch (e: Exception) { emptyList() }
    private fun saveEpgSourcesToPrefs() = prefs.edit().putString("epg_sources_json", Json.encodeToString(_epgSources.value)).apply()
    private fun loadEpgSourcesFromPrefs(): List<EpgSource> = try { Json.decodeFromString(prefs.getString("epg_sources_json", "[]")!!) } catch (e: Exception) { emptyList() }

    fun selectCategory(category: String) { _selectedCategory.value = category }
    fun updateSearchQuery(query: String) { _searchQuery.value = query }
}
