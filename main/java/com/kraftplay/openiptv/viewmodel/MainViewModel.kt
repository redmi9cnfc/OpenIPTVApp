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
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
        
    private val prefs = application.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
    private val cacheFile = File(application.cacheDir, "channels_cache.json")
    
    private val _rawChannels = MutableStateFlow<List<IptvChannel>>(loadChannelsFromCache())
    
    private val _favorites = MutableStateFlow<Set<String>>(loadFavoritesFromPrefs())
    private val _lockedChannels = MutableStateFlow<Map<String, String>>(loadLockedPasswordsFromPrefs())
    
    val allChannels: StateFlow<List<IptvChannel>> = combine(_rawChannels, _favorites, _lockedChannels) { channels, favs, locked ->
        channels.map { it.copy(isFavorite = favs.contains(it.url), isLocked = locked.containsKey(it.url)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _playlists = MutableStateFlow<List<Playlist>>(loadPlaylistsFromPrefs())
    val playlists: StateFlow<List<Playlist>> = _playlists

    private val _epgSources = MutableStateFlow<List<EpgSource>>(loadEpgSourcesFromPrefs())
    val epgSources: StateFlow<List<EpgSource>> = _epgSources

    private var epgDataMap: Map<String, List<EpgProgram>> = emptyMap()
    private var epgCleanNameMap: Map<String, List<EpgProgram>> = emptyMap()

    private val _epgPrograms = MutableStateFlow<List<EpgProgram>>(emptyList())
    
    private val _currentPrograms = MutableStateFlow<Map<String, EpgProgram>>(emptyMap())
    val currentPrograms: StateFlow<Map<String, EpgProgram>> = _currentPrograms

    private val _nextPrograms = MutableStateFlow<Map<String, EpgProgram>>(emptyMap())
    val nextPrograms: StateFlow<Map<String, EpgProgram>> = _nextPrograms

    private val _isEpgLoading = MutableStateFlow(false)
    val isEpgLoading: StateFlow<Boolean> = _isEpgLoading

    private val _epgStatus = MutableStateFlow("")
    val epgStatus: StateFlow<String> = _epgStatus

    private val _channelViewingTimes = MutableStateFlow<Map<String, Long>>(loadChannelTimesFromPrefs())
    val channelViewingTimes: StateFlow<Map<String, Long>> = _channelViewingTimes

    private val _recentChannels = MutableStateFlow<List<IptvChannel>>(loadRecentFromPrefs())
    val recentChannels: StateFlow<List<IptvChannel>> = _recentChannels

    val fontSize = MutableStateFlow(prefs.getFloat("font_size", 14f))
    val showClock = MutableStateFlow(prefs.getBoolean("show_clock", true))
    val language = MutableStateFlow(prefs.getString("language", Locale.getDefault().language) ?: "en")
    val hidePlaylistUrl = MutableStateFlow(prefs.getBoolean("hide_playlist_url", false))
    val listSize = MutableStateFlow(prefs.getString("list_size", "Standard") ?: "Standard")
    val themeMode = MutableStateFlow(prefs.getString("theme_mode", "Dark") ?: "Dark")
    val accentColor = MutableStateFlow(prefs.getString("accent_color", "Blue") ?: "Blue")
    val groupSortOrder = MutableStateFlow(prefs.getString("group_sort_order", "Provider") ?: "Provider")

    val showSource = MutableStateFlow(prefs.getBoolean("show_source", true))
    val showResolution = MutableStateFlow(prefs.getBoolean("show_resolution", true))
    val showFps = MutableStateFlow(prefs.getBoolean("show_fps", true))
    val resolutionFormat = MutableStateFlow(prefs.getString("resolution_format", "Dimensions") ?: "Dimensions")

    val autoStartLast = MutableStateFlow(prefs.getBoolean("auto_start_last", false))
    val lastChannelUrl = MutableStateFlow(prefs.getString("last_channel_url", "") ?: "")
    val forceLandscape = MutableStateFlow(prefs.getBoolean("force_landscape", false))
    val backgroundPlayback = MutableStateFlow(prefs.getBoolean("background_playback", false))
    val autoHideTimeout = MutableStateFlow(prefs.getInt("auto_hide_timeout", 3))
    val bufferingLevel = MutableStateFlow(prefs.getString("buffering_level", "Standard") ?: "Standard")
    val backBehavior = MutableStateFlow(prefs.getString("back_behavior", "Back to list") ?: "Back to list")
    val sleepTimer = MutableStateFlow(0)
    val reloadStreamOnToggle = MutableStateFlow(prefs.getBoolean("reload_stream_toggle", false))
    val fullScreenByDefault = MutableStateFlow(prefs.getBoolean("full_screen_default", false))

    val categories: StateFlow<List<String>> = combine(_rawChannels, groupSortOrder) { channels, sortOrder ->
        val cats = channels.mapNotNull { it.category }.filter { it.isNotEmpty() }.distinct()
        val sortedCats = if (sortOrder == "Alphabetical") cats.sorted() else cats
        listOf("All", "Favorites", "Recent") + sortedCats
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All", "Favorites", "Recent"))

    val filteredChannels: StateFlow<List<IptvChannel>> = combine(
        allChannels, _selectedCategory, _searchQuery, _recentChannels
    ) { channels, category, query, recents ->
        val baseList = if (category == "Recent") recents else channels
        baseList.filter { channel ->
            val matchesCategory = when(category) {
                "All" -> true
                "Favorites" -> channel.isFavorite
                "Recent" -> true
                else -> (channel.category ?: "") == category
            }
            val matchesSearch = channel.name.contains(query, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        refreshAllEpg()
        viewModelScope.launch {
            while (true) {
                updateCurrentPrograms()
                if (sleepTimer.value > 0) sleepTimer.value -= 1
                delay(60000)
            }
        }
    }

    private suspend fun updateCurrentPrograms() = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val channels = _rawChannels.value
        val currentMap = mutableMapOf<String, EpgProgram>()
        val nextMap = mutableMapOf<String, EpgProgram>()
        
        channels.forEach { channel ->
            val match = findEpgMatch(channel, now)
            if (match != null) {
                currentMap[channel.url] = match
                val channelProgs = epgDataMap[match.channelId.lowercase()] ?: emptyList()
                val next = channelProgs.find { it.startTime >= match.endTime }
                if (next != null) nextMap[channel.url] = next
            }
        }
        withContext(Dispatchers.Main) {
            _currentPrograms.value = currentMap
            _nextPrograms.value = nextMap
        }
    }

    private fun findEpgMatch(channel: IptvChannel, now: Long): EpgProgram? {
        val idsToTry = mutableListOf<String>()
        channel.epgId?.let { idsToTry.add(it.lowercase().trim()) }
        channel.tvgName?.let { idsToTry.add(it.lowercase().trim()) }
        idsToTry.add(channel.name.lowercase().trim())

        for (id in idsToTry) {
            if (id.isEmpty()) continue
            val match = epgDataMap[id]?.find { it.startTime <= now && it.endTime > now }
            if (match != null) return match
        }
        
        val cleanName = channel.name.lowercase()
            .replace(" fhd", "").replace(" hd", "").replace(" sd", "").replace(" 4k", "")
            .replace(Regex("[^a-z0-9а-я]"), "").trim()
            
        if (cleanName.isNotEmpty()) {
            return epgCleanNameMap[cleanName]?.find { it.startTime <= now && it.endTime > now }
        }

        return null
    }

    fun addToRecent(channel: IptvChannel) {
        val newList = (listOf(channel) + _recentChannels.value.filter { it.url != channel.url }).take(15)
        _recentChannels.value = newList
        saveRecentToPrefs()
    }

    private fun saveRecentToPrefs() {
        val json = Json.encodeToString(_recentChannels.value)
        prefs.edit().putString("recent_channels_json", json).apply()
    }

    private fun loadRecentFromPrefs(): List<IptvChannel> {
        val json = prefs.getString("recent_channels_json", null) ?: return emptyList()
        return try { Json.decodeFromString(json) } catch (e: Exception) { emptyList() }
    }

    private fun saveChannelsToCache(channels: List<IptvChannel>) {
        try { cacheFile.writeText(Json.encodeToString(channels)) } catch (e: Exception) { }
    }

    private fun loadChannelsFromCache(): List<IptvChannel> {
        if (!cacheFile.exists()) return emptyList()
        return try { Json.decodeFromString(cacheFile.readText()) } catch (e: Exception) { emptyList() }
    }

    fun updateChannelViewingTime(url: String, seconds: Long) {
        val currentMap = _channelViewingTimes.value.toMutableMap()
        currentMap[url] = (currentMap[url] ?: 0L) + seconds
        _channelViewingTimes.value = currentMap
        prefs.edit().putString("channel_viewing_times", Json.encodeToString(currentMap)).apply()
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
                            val parsed = M3uParser.parse(body.byteStream())
                            _rawChannels.value = parsed
                            saveChannelsToCache(parsed)
                            _playlists.value = _playlists.value.map {
                                if (it.url == url) it.copy(channelCount = parsed.size) else it
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

    fun lockChannel(url: String, password: String) {
        val newLocked = _lockedChannels.value.toMutableMap()
        newLocked[url] = password
        _lockedChannels.value = newLocked
        saveLockedPasswordsToPrefs(newLocked)
    }

    fun unlockChannel(url: String, password: String): Boolean {
        val currentPwd = _lockedChannels.value[url]
        if (currentPwd == password) {
            val newLocked = _lockedChannels.value.toMutableMap()
            newLocked.remove(url)
            _lockedChannels.value = newLocked
            saveLockedPasswordsToPrefs(newLocked)
            return true
        }
        return false
    }

    fun changeChannelPassword(url: String, oldPwd: String, newPwd: String): Boolean {
        val currentPwd = _lockedChannels.value[url]
        if (currentPwd == oldPwd) {
            lockChannel(url, newPwd)
            return true
        }
        return false
    }

    fun getChannelPassword(url: String): String? = _lockedChannels.value[url]

    private fun saveLockedPasswordsToPrefs(map: Map<String, String>) {
        prefs.edit().putString("locked_passwords_json", Json.encodeToString(map)).apply()
    }

    private fun loadLockedPasswordsFromPrefs(): Map<String, String> {
        val json = prefs.getString("locked_passwords_json", null) ?: return emptyMap()
        return try { Json.decodeFromString(json) } catch (e: Exception) { emptyMap() }
    }

    fun refreshAllEpg() {
        val enabledSources = _epgSources.value.filter { it.enabled }
        if (enabledSources.isEmpty()) return
        
        _isEpgLoading.value = true
        _epgStatus.value = "0%"
        
        viewModelScope.launch(Dispatchers.IO) {
            val allProgs = mutableListOf<EpgProgram>()
            var loadedCount = 0
            
            for (source in enabledSources) {
                try {
                    val request = Request.Builder().url(source.url).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.let { body ->
                                val bis = BufferedInputStream(body.byteStream())
                                bis.mark(10)
                                val b1 = bis.read()
                                val b2 = bis.read()
                                bis.reset()
                                
                                val isGzipped = (b1 == 0x1f && b2 == 0x8b)
                                val parsed = XmlTvParser.parse(bis, isGzipped)
                                allProgs.addAll(parsed)
                                
                                loadedCount++
                                withContext(Dispatchers.Main) {
                                    _epgStatus.value = "${(loadedCount * 100) / enabledSources.size}%"
                                }
                            }
                        }
                    }
                } catch (e: Exception) { 
                    e.printStackTrace()
                }
            }
            
            withContext(Dispatchers.Default) {
                val distinctProgs = allProgs.distinctBy { "${it.channelId}_${it.startTime}" }
                
                epgDataMap = distinctProgs.groupBy { it.channelId.lowercase().trim() }
                    .mapValues { entry -> entry.value.sortedBy { it.startTime } }
                
                epgCleanNameMap = distinctProgs.groupBy { 
                    it.channelId.lowercase()
                        .replace(" fhd", "").replace(" hd", "").replace(" sd", "").replace(" 4k", "")
                        .replace(Regex("[^a-z0-9а-я]"), "").trim()
                }
                
                withContext(Dispatchers.Main) {
                    _epgPrograms.value = distinctProgs
                    _isEpgLoading.value = false
                    _epgStatus.value = if (distinctProgs.isNotEmpty()) "100%" else "Failed"
                    updateCurrentPrograms()
                }
            }
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
                    "full_screen_default" -> fullScreenByDefault.value = value
                    "reload_stream_toggle" -> reloadStreamOnToggle.value = value
                    "force_landscape" -> forceLandscape.value = value
                    "background_playback" -> backgroundPlayback.value = value
                    "show_source" -> showSource.value = value
                    "show_resolution" -> showResolution.value = value
                    "show_fps" -> showFps.value = value
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
                    "group_sort_order" -> groupSortOrder.value = value
                    "resolution_format" -> resolutionFormat.value = value
                }
            }
            is Int -> { editor.putInt(key, value); if (key == "auto_hide_timeout") autoHideTimeout.value = value }
        }
        editor.apply()
    }

    fun addPlaylist(name: String, url: String) {
        val new = Playlist(UUID.randomUUID().toString(), name, url, _playlists.value.isEmpty())
        _playlists.value = _playlists.value + new
        savePlaylistsToPrefs()
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
        _rawChannels.value = emptyList()
        _epgPrograms.value = emptyList()
        epgDataMap = emptyMap()
        epgCleanNameMap = emptyMap()
        _currentPrograms.value = emptyMap()
        _favorites.value = emptySet()
        _channelViewingTimes.value = emptyMap()
        _lockedChannels.value = emptyMap()
        _recentChannels.value = emptyList()
        if (cacheFile.exists()) cacheFile.delete()
    }

    private fun savePlaylistsToPrefs() = prefs.edit().putString("playlists_json", Json.encodeToString(_playlists.value)).apply()
    private fun loadPlaylistsFromPrefs(): List<Playlist> = try { Json.decodeFromString(prefs.getString("playlists_json", "[]")!!) } catch (e: Exception) { emptyList() }
    private fun saveEpgSourcesToPrefs() = prefs.edit().putString("epg_sources_json", Json.encodeToString(_epgSources.value)).apply()
    private fun loadEpgSourcesFromPrefs(): List<EpgSource> = try { Json.decodeFromString(prefs.getString("epg_sources_json", "[]")!!) } catch (e: Exception) { emptyList() }

    fun selectCategory(category: String) { _selectedCategory.value = category }
    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    fun getChannelSchedule(url: String): Flow<List<EpgProgram>> = flow {
        val channels = allChannels.value
        val channel = channels.find { it.url == url }
        if (channel != null) {
            val idsToTry = listOfNotNull(channel.epgId, channel.tvgName, channel.name)
            for (id in idsToTry) {
                val progs = (epgDataMap[id.lowercase().trim()] ?: emptyList())
                    .filter { it.endTime > System.currentTimeMillis() }
                if (progs.isNotEmpty()) {
                    emit(progs)
                    return@flow
                }
            }
            emit(emptyList())
        } else {
            emit(emptyList())
        }
    }
}
