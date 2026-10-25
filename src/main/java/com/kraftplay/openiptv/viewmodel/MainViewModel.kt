package com.kraftplay.openiptv.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kraftplay.openiptv.data.EpgDatabase
import com.kraftplay.openiptv.model.CustomThemeConfig
import com.kraftplay.openiptv.model.EpgProgram
import com.kraftplay.openiptv.model.EpgSource
import com.kraftplay.openiptv.model.IptvChannel
import com.kraftplay.openiptv.model.Playlist
import com.kraftplay.openiptv.model.isKitTvPlaylist
import com.kraftplay.openiptv.util.EpgMatcher
import com.kraftplay.openiptv.parser.M3uParser
import com.kraftplay.openiptv.parser.XmlTvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
        
    private val prefs = application.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
    private val cacheFile = File(application.cacheDir, "channels_cache.json")
    private val epgDao = EpgDatabase.getDatabase(application).epgDao()
    
    private val defaultKitTvUrl = ""
    private val defaultKitTvPlaylist = Playlist(
        id = "kittv-default",
        name = "KitTV",
        url = defaultKitTvUrl,
        isSelected = true,
        autoUpdateInterval = "On launch"
    )
    private val _playlists = MutableStateFlow(
        loadPlaylistsFromPrefs().ifEmpty { listOf(defaultKitTvPlaylist) }.map { playlist ->
            // Repair the built-in playlist even when an older app version saved a stale URL/name.
            if (playlist.id == "kittv-default" || playlist.name.equals("KitTV", true)) {
                playlist.copy(id = if (playlist.id.isBlank()) "kittv-default" else playlist.id, name = "KitTV", url = defaultKitTvUrl)
            } else playlist
        }
    )
    val playlists: StateFlow<List<Playlist>> = _playlists

    private val _rawChannels = MutableStateFlow<List<IptvChannel>>(loadChannelsFromCache())
    
    private val _playlistError = MutableStateFlow<String?>(null)
    val playlistError: StateFlow<String?> = _playlistError

    private val _favorites = MutableStateFlow<Set<String>>(loadFavoritesFromPrefs())
    private val _pinnedUrls = MutableStateFlow<Set<String>>(loadPinnedFromPrefs())
    private val _lockedChannels = MutableStateFlow<Map<String, String>>(loadLockedPasswordsFromPrefs())
    
    val allChannels: StateFlow<List<IptvChannel>> = combine(_rawChannels, _favorites, _pinnedUrls, _lockedChannels) { channels, favs, pinned, locked ->
        channels.map { channel ->
            channel.copy(
                isFavorite = favs.contains(channel.url),
                isPinned = pinned.contains(channel.url),
                isLocked = locked.containsKey(channel.url)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory

    val categoryDisplayMode = MutableStateFlow(prefs.getString("category_display_mode", "Chips") ?: "Chips")
    val selectedFolder = MutableStateFlow<String?>(null)
    val customThemeJson = MutableStateFlow(prefs.getString("custom_theme_json", "") ?: "")
    
    val parsedCustomTheme: StateFlow<CustomThemeConfig> = customThemeJson.map { json ->
        if (json.isBlank()) CustomThemeConfig.DEFAULT
        else try { Json.decodeFromString<CustomThemeConfig>(json) } catch (e: Exception) { CustomThemeConfig.DEFAULT }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CustomThemeConfig.DEFAULT)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _hiddenCategories = MutableStateFlow<Set<String>>(loadHiddenCategoriesFromPrefs())
    val hiddenCategories: StateFlow<Set<String>> = _hiddenCategories
    private val _hiddenChannels = MutableStateFlow<Set<String>>(prefs.getStringSet("hidden_channels", emptySet()) ?: emptySet())
    val hiddenChannels: StateFlow<Set<String>> = _hiddenChannels
    val channelManagementList: StateFlow<List<IptvChannel>> = _rawChannels

    private val _epgSources = MutableStateFlow<List<EpgSource>>(loadEpgSourcesFromPrefs())
    val epgSources: StateFlow<List<EpgSource>> = _epgSources

    private var epgDataMap: Map<String, List<EpgProgram>> = emptyMap()
    private var epgCleanNameMap: Map<String, List<EpgProgram>> = emptyMap()
    private var epgTranslitNameMap: Map<String, List<EpgProgram>> = emptyMap()

    private val _epgPrograms = MutableStateFlow<List<EpgProgram>>(emptyList())
    val epgPrograms: StateFlow<List<EpgProgram>> = _epgPrograms
    
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

    val parentalPin = MutableStateFlow(prefs.getString("parental_pin", "") ?: "")
    val isParentalUnlocked = MutableStateFlow(false)

    val fontSize = MutableStateFlow(prefs.getFloat("font_size", 14f))
    val showClock = MutableStateFlow(prefs.getBoolean("show_clock", true))
    val clockPosition = MutableStateFlow(prefs.getString("clock_position", "Top right") ?: "Top right")
    val language = MutableStateFlow(prefs.getString("language", if (Locale.getDefault().language == "ru") "ru" else "en") ?: if (Locale.getDefault().language == "ru") "ru" else "en")
    val hidePlaylistUrl = MutableStateFlow(prefs.getBoolean("hide_playlist_url", false))
    val listSize = MutableStateFlow(prefs.getString("list_size", "Standard") ?: "Standard")
    val themeMode = MutableStateFlow(prefs.getString("theme_mode", "Dark") ?: "Dark")
    val accentColor = MutableStateFlow(prefs.getString("accent_color", "Blue") ?: "Blue")
    val groupSortOrder = MutableStateFlow(prefs.getString("group_sort_order", "Provider") ?: "Provider")
    val uiStyle = MutableStateFlow(prefs.getString("ui_style", "Material3") ?: "Material3")

    val showSource = MutableStateFlow(prefs.getBoolean("show_source", false))
    val showResolution = MutableStateFlow(prefs.getBoolean("show_resolution", false))
    val showFps = MutableStateFlow(prefs.getBoolean("show_fps", false))
    val showCodecs = MutableStateFlow(prefs.getBoolean("show_codecs", false))
    val showBitrate = MutableStateFlow(prefs.getBoolean("show_bitrate", false))
    val resolutionFormat = MutableStateFlow(prefs.getString("resolution_format", "Dimensions") ?: "Dimensions")
    val badgeShape = MutableStateFlow(prefs.getString("badge_shape", "Cookie") ?: "Cookie")
    val badgeColor = MutableStateFlow(prefs.getString("badge_color", "Auto") ?: "Auto")
    val panelColor = MutableStateFlow(prefs.getString("panel_color", "Auto") ?: "Auto")

    val expressiveGlow = MutableStateFlow(prefs.getBoolean("expressive_glow", true))
    val waveAmplitude = MutableStateFlow(prefs.getFloat("wave_amplitude", 0.08f))
    val waveSpeed = MutableStateFlow(prefs.getFloat("wave_speed", 1.0f))
    val cardCornerRadius = MutableStateFlow(prefs.getInt("card_corner_radius", 22))
    val springScale = MutableStateFlow(prefs.getFloat("spring_scale", 1.08f))
    val ambientCanvas = MutableStateFlow(prefs.getBoolean("ambient_canvas", true))
    val epgProgressStyle = MutableStateFlow(prefs.getString("epg_progress_style", "Wavy") ?: "Wavy")
    val playerGlassTint = MutableStateFlow(prefs.getString("player_glass_tint", "Dark Glass") ?: "Dark Glass")
    val cardBadgeStyle = MutableStateFlow(prefs.getString("card_badge_style", "Floating Pill") ?: "Floating Pill")

    val autoStartLast = MutableStateFlow(prefs.getBoolean("auto_start_last", false))
    val lastChannelUrl = MutableStateFlow(prefs.getString("last_channel_url", "") ?: "")
    val forceLandscape = MutableStateFlow(prefs.getBoolean("force_landscape", false))
    val backgroundPlayback = MutableStateFlow(prefs.getBoolean("background_playback", false))
    val autoHideTimeout = MutableStateFlow(prefs.getInt("auto_hide_timeout", 3))
    val bufferingLevel = MutableStateFlow(prefs.getString("buffering_level", "Standard") ?: "Standard")
    val backBehavior = MutableStateFlow(prefs.getString("back_behavior", "Back to list") ?: "Back to list")
    val sleepTimer = MutableStateFlow(0)
    val reloadStreamOnToggle = MutableStateFlow(prefs.getBoolean("reload_stream_toggle", false))
    val fullScreenByDefault = MutableStateFlow(prefs.getBoolean("full_screen_default", true))
    val aspectRatioMode = MutableStateFlow(prefs.getString("aspect_ratio_mode", "16:9") ?: "16:9")
    val catchupMode = MutableStateFlow(prefs.getString("catchup_mode", "Auto") ?: "Auto")
    val hardwareDecoder = MutableStateFlow(prefs.getString("hardware_decoder", "Auto") ?: "Auto")
    val showChannelNumbers = MutableStateFlow(prefs.getBoolean("show_channel_numbers", true))
    val volumeGestureEnabled = MutableStateFlow(prefs.getBoolean("volume_gesture_enabled", true))
    val epgUpdateInterval = MutableStateFlow(prefs.getString("epg_update_interval", "On launch") ?: "On launch")
    
    val playbackSpeed = MutableStateFlow(1.0f)
    val audioVolumeBoost = MutableStateFlow(1.0f)

    val iconRotationEnabled = MutableStateFlow(prefs.getBoolean("icon_rotation_enabled", true))
    val lockedFolders = MutableStateFlow<Map<String, String>>(loadLockedFoldersFromPrefs())
    val customCategoryOrder = MutableStateFlow<List<String>>(loadCustomCategoryOrderFromPrefs())
    val customChannelOrder = MutableStateFlow<List<String>>(loadCustomChannelOrderFromPrefs())

    val categories: StateFlow<List<String>> = combine(_rawChannels, groupSortOrder, _hiddenCategories, customCategoryOrder) { channels, sortOrder, hidden, customOrder ->
        val cats = channels.mapNotNull { it.category }.filter { it.isNotEmpty() }.distinct()
        val orderMap = customOrder.withIndex().associate { it.value to it.index }
        val sortedCats = if (sortOrder == "Alphabetical") cats.sorted() else cats.sortedBy { orderMap[it] ?: Int.MAX_VALUE }
        listOf("All", "Favorites", "Recent") + sortedCats
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All", "Favorites", "Recent"))

    val categoryChannelCounts: StateFlow<Map<String, Int>> = combine(_rawChannels, _favorites, _recentChannels) { channels, favs, recents ->
        val counts = mutableMapOf<String, Int>()
        counts["All"] = channels.size
        counts["Favorites"] = channels.count { favs.contains(it.url) }
        counts["Recent"] = recents.size
        channels.forEach { channel ->
            val cat = channel.category
            if (!cat.isNullOrEmpty()) {
                counts[cat] = (counts[cat] ?: 0) + 1
            }
        }
        counts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val filteredChannels: StateFlow<List<IptvChannel>> = combine(
        allChannels, _selectedCategory, _searchQuery, _recentChannels, selectedFolder, categoryDisplayMode, _hiddenChannels, customChannelOrder
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val channels = array[0] as List<IptvChannel>
        val category = array[1] as String
        val query = array[2] as String
        @Suppress("UNCHECKED_CAST")
        val recents = array[3] as List<IptvChannel>
        val folder = array[4] as? String
        val mode = array[5] as String
        @Suppress("UNCHECKED_CAST")
        val hiddenChannels = array[6] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val channelOrder = array[7] as List<String>

        val activeCategory = if (mode == "Folders" && folder != null) folder else category
        val baseList = (if (activeCategory == "Recent") recents else channels).filterNot { hiddenChannels.contains(it.url) }
        val filtered = baseList.filter { channel ->
            val matchesCategory = when(activeCategory) {
                "All" -> true
                "Favorites" -> channel.isFavorite
                "Recent" -> true
                else -> (channel.category ?: "") == activeCategory
            }
            val matchesSearch = channel.name.contains(query, ignoreCase = true)
            matchesCategory && matchesSearch
        }
        val orderMap = channelOrder.withIndex().associate { it.value to it.index }
        filtered.sortedWith(
            compareByDescending<IptvChannel> { it.isPinned }
            .thenByDescending { it.isFavorite }
            .thenBy { orderMap[it.url] ?: Int.MAX_VALUE }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    val epgDbSize = MutableStateFlow(0.0)

    fun updateEpgDbSize() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbFile = getApplication<Application>().getDatabasePath("epg_database")
                val walFile = getApplication<Application>().getDatabasePath("epg_database-wal")
                var totalBytes = if (dbFile.exists()) dbFile.length() else 0L
                if (walFile.exists()) totalBytes += walFile.length()
                val mb = totalBytes / (1024.0 * 1024.0)
                withContext(Dispatchers.Main) {
                    epgDbSize.value = mb
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun shouldUpdatePlaylist(playlist: Playlist, isAppLaunch: Boolean = false): Boolean {
        if (!playlist.enabled) return false
        if (playlist.lastUpdatedTime == 0L) return true
        val now = System.currentTimeMillis()
        val elapsed = now - playlist.lastUpdatedTime
        return when (playlist.autoUpdateInterval) {
            "On launch" -> isAppLaunch
            "15 min" -> elapsed >= 15 * 60 * 1000L
            "30 min" -> elapsed >= 30 * 60 * 1000L
            "1 hour" -> elapsed >= 60 * 60 * 1000L
            "12 hours" -> elapsed >= 12 * 60 * 60 * 1000L
            "24 hours", "1 day" -> elapsed >= 24 * 60 * 60 * 1000L
            "2 days" -> elapsed >= 2 * 24 * 60 * 60 * 1000L
            "7 days" -> elapsed >= 7 * 24 * 60 * 60 * 1000L
            "Off", "Disabled", "Никогда" -> false
            else -> false
        }
    }

    private fun shouldUpdateEpgSource(source: EpgSource, isAppLaunch: Boolean = false): Boolean {
        if (!source.enabled) return false
        if (source.lastUpdatedTime == 0L) return true
        val now = System.currentTimeMillis()
        val elapsed = now - source.lastUpdatedTime
        return when (source.autoUpdateInterval) {
            "On launch" -> isAppLaunch
            "15 min" -> elapsed >= 15 * 60 * 1000L
            "30 min" -> elapsed >= 30 * 60 * 1000L
            "1 hour" -> elapsed >= 60 * 60 * 1000L
            "12 hours" -> elapsed >= 12 * 60 * 60 * 1000L
            "24 hours", "1 day" -> elapsed >= 24 * 60 * 60 * 1000L
            "2 days" -> elapsed >= 2 * 24 * 60 * 60 * 1000L
            "7 days" -> elapsed >= 7 * 24 * 60 * 60 * 1000L
            "Off", "Disabled", "Никогда" -> false
            else -> false
        }
    }

    init {
        val today = Calendar.getInstance()
        val seasonalTheme = when {
            today.get(Calendar.MONTH) == Calendar.OCTOBER && today.get(Calendar.DAY_OF_MONTH) == 31 -> "Halloween"
            today.get(Calendar.MONTH) == Calendar.DECEMBER && today.get(Calendar.DAY_OF_MONTH) == 31 -> "Winter"
            else -> null
        }
        if (seasonalTheme != null) {
            themeMode.value = seasonalTheme
            prefs.edit().putString("theme_mode", seasonalTheme).apply()
        }
        // Seed KitTV as the default source on a fresh install; retain any user playlists.
        // Persist normalized defaults/migrations so stale saved URLs cannot reappear on next launch.
        if (prefs.getString("playlists_json", null).isNullOrBlank() ||
            loadPlaylistsFromPrefs().isEmpty() ||
            loadPlaylistsFromPrefs() != _playlists.value) {
            savePlaylistsToPrefs()
        }
        val selectedPlaylist = _playlists.value.find { it.isSelected }
        if (selectedPlaylist != null) {
            val cached = loadChannelsFromCache()
            if (cached.isNotEmpty()) {
                _rawChannels.value = cached
            }
            if (shouldUpdatePlaylist(selectedPlaylist, isAppLaunch = true) || cached.isEmpty()) {
                loadPlaylist(selectedPlaylist.url)
            }
        } else {
            _rawChannels.value = emptyList()
            if (cacheFile.exists()) cacheFile.delete()
        }
        loadEpgFromDatabase()
        refreshAllEpg(force = false, isAppLaunch = true)
        updateEpgDbSize()

        viewModelScope.launch {
            _rawChannels.collect { channels ->
                if (channels.isNotEmpty()) {
                    updateCurrentPrograms()
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                updateCurrentPrograms()
                checkAutoUpdates()
                if (sleepTimer.value > 0) sleepTimer.value -= 1
                delay(60000)
            }
        }
    }

    fun importPlaylistFile(uri: Uri, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _playlistError.value = null
            try {
                val app = getApplication<Application>()
                val resolver = app.contentResolver
                // Copy the picked document into app-private storage immediately. This avoids
                // depending on a Downloads provider URI/temporary grant after the picker closes,
                // and makes imported playlists portable within this device.
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw java.io.IOException("Could not open the selected file. Please select it again using the system file picker.")
                if (bytes.isEmpty()) throw java.io.IOException("The selected file is empty")
                val playlistDir = File(app.filesDir, "imported_playlists").apply { mkdirs() }
                val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "playlist.m3u" }
                val localFile = File(playlistDir, "${UUID.randomUUID()}-$safeName")
                localFile.writeBytes(bytes)
                val rawStream = ByteArrayInputStream(bytes)
                rawStream.mark(4)
                val b1 = rawStream.read()
                val b2 = rawStream.read()
                rawStream.reset()
                val finalStream = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(rawStream) else rawStream
                val parseResult = finalStream.use { M3uParser.parseWithEpg(it) }
                val parsed = parseResult.channels
                if (parsed.isEmpty()) {
                    throw java.io.IOException("No channel URLs found. For TXT files, put one stream URL on each line.")
                }

                if (parseResult.epgUrls.isNotEmpty()) {
                    val currentSources = _epgSources.value.toMutableList()
                    var addedAny = false
                    parseResult.epgUrls.forEach { epgUrl ->
                        if (currentSources.none { it.url == epgUrl }) {
                            currentSources.add(EpgSource(UUID.randomUUID().toString(), "Плейлист EPG", epgUrl, enabled = true))
                            addedAny = true
                        }
                    }
                    if (addedAny) {
                        _epgSources.value = currentSources
                        saveEpgSourcesToPrefs()
                        refreshAllEpg()
                    }
                }

                withContext(Dispatchers.Main) {
                    _playlistError.value = null
                    _rawChannels.value = parsed
                    saveChannelsToCache(parsed)
                    _selectedCategory.value = "All"
                    selectedFolder.value = null
                    _searchQuery.value = ""
                    val newPlaylist = Playlist(
                        UUID.randomUUID().toString(),
                        fileName.ifBlank { "playlist.m3u" },
                        localFile.absolutePath,
                        isSelected = true,
                        channelCount = parsed.size
                    )
                    _playlists.value = _playlists.value.map { it.copy(isSelected = false) } + newPlaylist
                    savePlaylistsToPrefs()
                    updateCurrentPrograms()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _playlistError.value = "File read error: ${e.localizedMessage ?: "unknown error"}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateCustomTheme(jsonString: String): Boolean {
        return try {
            if (jsonString.isBlank()) {
                resetCustomTheme()
                true
            } else {
                Json.decodeFromString<CustomThemeConfig>(jsonString)
                customThemeJson.value = jsonString
                prefs.edit().putString("custom_theme_json", jsonString).apply()
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun resetCustomTheme() {
        customThemeJson.value = ""
        prefs.edit().remove("custom_theme_json").apply()
    }

    fun selectFolder(folderName: String?) {
        selectedFolder.value = folderName
    }

    fun setCategoryDisplayMode(mode: String) {
        categoryDisplayMode.value = mode
        selectedFolder.value = null
        prefs.edit().putString("category_display_mode", mode).apply()
    }

    private fun loadEpgFromDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                epgDao.pruneWindow(now - 30L * 24L * 60L * 60L * 1000L, now + 30L * 24L * 60L * 60L * 1000L)
                val cached = epgDao.getAllValidPrograms(now)
                if (cached.isNotEmpty()) {
                    processLoadedEpg(cached)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val xmlTvChannelNamesMap = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    private suspend fun processLoadedEpg(programs: List<EpgProgram>) = withContext(Dispatchers.Default) {
        val exactMap = mutableMapOf<String, MutableList<EpgProgram>>()
        val cleanMap = mutableMapOf<String, MutableList<EpgProgram>>()
        val translitMap = mutableMapOf<String, MutableList<EpgProgram>>()

        programs.forEach { prog ->
            val exactKey = prog.channelId.lowercase().trim()
            if (exactKey.isNotEmpty()) {
                exactMap.getOrPut(exactKey) { mutableListOf() }.add(prog)
            }
            val cleanKey = EpgMatcher.cleanName(prog.channelId)
            if (cleanKey.isNotEmpty()) {
                cleanMap.getOrPut(cleanKey) { mutableListOf() }.add(prog)
                val translitKey = EpgMatcher.transliterate(cleanKey)
                if (translitKey.isNotEmpty()) {
                    translitMap.getOrPut(translitKey) { mutableListOf() }.add(prog)
                }
            }

            val displayNames = xmlTvChannelNamesMap[exactKey]
            displayNames?.forEach { name ->
                val cKey = EpgMatcher.cleanName(name)
                if (cKey.isNotEmpty()) {
                    cleanMap.getOrPut(cKey) { mutableListOf() }.add(prog)
                    val tKey = EpgMatcher.transliterate(cKey)
                    if (tKey.isNotEmpty()) {
                        translitMap.getOrPut(tKey) { mutableListOf() }.add(prog)
                    }
                }
            }
        }

        epgDataMap = exactMap.mapValues { entry -> entry.value.sortedBy { it.startTime } }
        epgCleanNameMap = cleanMap.mapValues { entry -> entry.value.sortedBy { it.startTime } }
        epgTranslitNameMap = translitMap.mapValues { entry -> entry.value.sortedBy { it.startTime } }
        
        withContext(Dispatchers.Main) {
            _epgPrograms.value = programs
            updateCurrentPrograms()
        }
    }

    private suspend fun updateCurrentPrograms() = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val channels = _rawChannels.value
        val currentMap = mutableMapOf<String, EpgProgram>()
        val nextMap = mutableMapOf<String, EpgProgram>()
        
        channels.forEach { channel ->
            val match = EpgMatcher.findProgramForChannel(
                channel = channel,
                epgDataMap = epgDataMap,
                epgCleanNameMap = epgCleanNameMap,
                epgTranslitNameMap = epgTranslitNameMap,
                now = now
            )
            if (match != null) {
                currentMap[channel.url] = match
                val next = EpgMatcher.findNextProgramForChannel(
                    channel = channel,
                    currentMatch = match,
                    epgDataMap = epgDataMap,
                    epgCleanNameMap = epgCleanNameMap,
                    epgTranslitNameMap = epgTranslitNameMap,
                    now = now
                )
                if (next != null) nextMap[channel.url] = next
            }
        }
        withContext(Dispatchers.Main) {
            _currentPrograms.value = currentMap
            _nextPrograms.value = nextMap
        }
    }

    fun togglePinChannel(url: String) {
        val currentPinned = _pinnedUrls.value.toMutableSet()
        if (currentPinned.contains(url)) currentPinned.remove(url) else currentPinned.add(url)
        _pinnedUrls.value = currentPinned
        prefs.edit().putStringSet("pinned_channels", currentPinned).apply()
    }

    private fun loadPinnedFromPrefs(): Set<String> = prefs.getStringSet("pinned_channels", emptySet()) ?: emptySet()

    fun toggleHideChannel(url: String) {
        val updated = _hiddenChannels.value.toMutableSet()
        if (!updated.add(url)) updated.remove(url)
        _hiddenChannels.value = updated
        prefs.edit().putStringSet("hidden_channels", updated).apply()
    }

    fun toggleHideCategory(category: String) {
        val currentHidden = _hiddenCategories.value.toMutableSet()
        if (currentHidden.contains(category)) currentHidden.remove(category) else currentHidden.add(category)
        _hiddenCategories.value = currentHidden
        prefs.edit().putStringSet("hidden_categories", currentHidden).apply()
    }

    private fun loadHiddenCategoriesFromPrefs(): Set<String> = prefs.getStringSet("hidden_categories", emptySet()) ?: emptySet()

    fun setParentalPin(pin: String) {
        parentalPin.value = pin
        prefs.edit().putString("parental_pin", pin).apply()
    }

    fun unlockParentalPin(pin: String): Boolean {
        if (parentalPin.value.isEmpty() || parentalPin.value == pin) {
            isParentalUnlocked.value = true
            return true
        }
        return false
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
        val playlists = loadPlaylistsFromPrefs()
        if (playlists.isEmpty()) {
            if (cacheFile.exists()) cacheFile.delete()
            return emptyList()
        }
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
        val source = url.trim().removePrefix("\uFEFF")
        if (source.isBlank()) {
            _playlistError.value = "Enter a playlist URL"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _playlistError.value = null
            try {
                val bytes: ByteArray = when {
                    source.startsWith("content://", ignoreCase = true) -> {
                        val uri = Uri.parse(source)
                        getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw java.io.IOException("Could not open the local file")
                    }
                    source.startsWith("file://", ignoreCase = true) || source.startsWith("/") -> {
                        val path = if (source.startsWith("file://", ignoreCase = true)) Uri.parse(source).path
                            ?: throw java.io.IOException("Invalid file path") else source
                        File(path).inputStream().use { it.readBytes() }
                    }
                    source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true) -> {
                        val request = Request.Builder()
                            .url(source)
                            .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
                            .header("Accept", "*/*")
                            .header("Connection", "keep-alive")
                            .get()
                            .build()
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                throw java.io.IOException("Сервер вернул HTTP ${response.code}")
                            }
                            val body = response.body ?: throw java.io.IOException("The server returned an empty response")
                            body.bytes()
                        }
                    }
                    else -> throw java.io.IOException("The URL must start with http:// or https://")
                }

                if (bytes.isEmpty()) throw java.io.IOException("The playlist file is empty")
                val rawStream = ByteArrayInputStream(bytes)
                rawStream.mark(4)
                val b1 = rawStream.read()
                val b2 = rawStream.read()
                rawStream.reset()
                val input = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(rawStream) else rawStream
                val parseResult = input.use { M3uParser.parseWithEpg(it) }
                val parsed = parseResult.channels

                if (parsed.isEmpty()) {
                    val sample = bytes.toString(Charsets.UTF_8).take(120).trim()
                    val message = if (sample.contains("<html", true) || sample.contains("<!doctype", true)) {
                        "The server returned an HTML page instead of a playlist. Check the direct M3U link."
                    } else {
                        "No channels found. Supported formats are M3U/M3U8 and TXT files containing stream URLs."
                    }
                    throw java.io.IOException(message)
                }

                if (parseResult.epgUrls.isNotEmpty()) {
                    val currentSources = _epgSources.value.toMutableList()
                    var addedAny = false
                    parseResult.epgUrls.forEach { epgUrl ->
                        if (currentSources.none { it.url == epgUrl }) {
                            currentSources.add(EpgSource(UUID.randomUUID().toString(), "Плейлист EPG", epgUrl, enabled = true))
                            addedAny = true
                        }
                    }
                    if (addedAny) {
                        _epgSources.value = currentSources
                        saveEpgSourcesToPrefs()
                        refreshAllEpg()
                    }
                }

                withContext(Dispatchers.Main) {
                    _playlistError.value = null
                    _rawChannels.value = parsed
                    saveChannelsToCache(parsed)
                    _selectedCategory.value = "All"
                    selectedFolder.value = null
                    _searchQuery.value = ""
                    _playlists.value = _playlists.value.map {
                        if (it.url.trim() == source) it.copy(channelCount = parsed.size) else it
                    }
                    savePlaylistsToPrefs()
                    updateCurrentPrograms()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _playlistError.value = e.message?.takeIf { it.isNotBlank() }
                        ?: "Could not load playlist. Check the URL and your internet connection."
                }
            } finally {
                _isLoading.value = false
            }
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

    fun refreshAllEpg(force: Boolean = false, isAppLaunch: Boolean = false) {
        val enabledSources = _epgSources.value.filter { source ->
            if (force) source.enabled else shouldUpdateEpgSource(source, isAppLaunch)
        }
        if (enabledSources.isEmpty()) return
        
        _isEpgLoading.value = true
        _epgStatus.value = "LOADING"
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val epgNow = System.currentTimeMillis()
                val cutoffTime = epgNow - 30L * 24L * 60L * 60L * 1000L
                val futureLimit = epgNow + 30L * 24L * 60L * 60L * 1000L
                
                try {
                    epgDao.pruneWindow(cutoffTime, futureLimit)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                for (source in enabledSources) {
                    try {
                        val request = Request.Builder()
                            .url(source.url)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .build()
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body?.let { body ->
                                    val contentLength = body.contentLength()
                                    val rawStream = body.byteStream()

                                    var bytesReadTotal = 0L
                                    val countingStream = object : java.io.FilterInputStream(rawStream) {
                                        override fun read(): Int {
                                            val b = super.read()
                                            if (b != -1) {
                                                bytesReadTotal++
                                                updateProgress(bytesReadTotal, contentLength)
                                            }
                                            return b
                                        }
                                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                                            val r = super.read(b, off, len)
                                            if (r > 0) {
                                                bytesReadTotal += r
                                                updateProgress(bytesReadTotal, contentLength)
                                            }
                                            return r
                                        }
                                        private fun updateProgress(current: Long, total: Long) {
                                            val currentMB = current / (1024.0 * 1024.0)
                                            val progressStr = if (total > 0) {
                                                val totalMB = total / (1024.0 * 1024.0)
                                                val percent = (current * 100 / total).toInt().coerceIn(0, 100)
                                                String.format(Locale.US, "%.1f / %.1f MB (%d%%)", currentMB, totalMB, percent)
                                            } else {
                                                String.format(Locale.US, "%.1f MB", currentMB)
                                            }
                                            _epgStatus.value = progressStr
                                        }
                                    }

                                    val bis = BufferedInputStream(countingStream)
                                    bis.mark(10)
                                    val b1 = bis.read()
                                    val b2 = bis.read()
                                    bis.reset()
                                    
                                    val isGzipped = (b1 == 0x1f && b2 == 0x8b)

                                    var parsedCount = 0
                                    XmlTvParser.parseStreaming(
                                        bis, isGzipped, cutoffTime, futureLimit,
                                        onChannelNamesParsed = { namesMap ->
                                            xmlTvChannelNamesMap.putAll(namesMap)
                                        }
                                    ) { batch ->
                                        try {
                                            epgDao.insertAll(batch)
                                            parsedCount += batch.size
                                            _epgStatus.value = "PARSING: $parsedCount"
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) { 
                        e.printStackTrace()
                    }
                }

                val now = System.currentTimeMillis()
                runCatching { epgDao.pruneWindow(now - 30L * 24L * 60L * 60L * 1000L, now + 30L * 24L * 60L * 60L * 1000L) }
                val updatedSources = _epgSources.value.map { source ->
                    if (enabledSources.any { it.id == source.id }) source.copy(lastUpdatedTime = now) else source
                }
                _epgSources.value = updatedSources
                saveEpgSourcesToPrefs()

                val validPrograms = epgDao.getAllValidPrograms(now)
                processLoadedEpg(validPrograms)
                updateEpgDbSize()

                withContext(Dispatchers.Main) {
                    _epgStatus.value = if (validPrograms.isNotEmpty()) "LOADED:${validPrograms.size}:100%" else "EPG_UPDATED"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    _isEpgLoading.value = false
                }
            }
        }
    }

    fun clearAllEpg() {
        viewModelScope.launch(Dispatchers.IO) {
            _isEpgLoading.value = true
            _epgStatus.value = "CLEARING"
            try {
                epgDao.deleteAll()
                epgDataMap = emptyMap()
                epgCleanNameMap = emptyMap()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            withContext(Dispatchers.Main) {
                _epgPrograms.value = emptyList()
                _currentPrograms.value = emptyMap()
                _nextPrograms.value = emptyMap()
                _isEpgLoading.value = false
                _epgStatus.value = "CLEARED"
                updateEpgDbSize()
            }
        }
    }

    fun searchEpgPrograms(query: String): Flow<List<EpgProgram>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
        } else {
            val results = epgDao.searchPrograms(query.trim().lowercase(Locale.ROOT), System.currentTimeMillis())
            emit(results)
        }
    }.flowOn(Dispatchers.IO)

    /** Export only shareable media-list data; never include channel-lock passwords in Wi-Fi transfers. */
    fun exportWifiTransferJson(): String {
        val resolver = getApplication<Application>().contentResolver
        val portablePlaylists = _playlists.value.filterNot { isKitTvPlaylist(it.name, it.url, it.id) }.map { playlist ->
            val localBytes = runCatching {
                when {
                    playlist.url.startsWith("content://", ignoreCase = true) ->
                        resolver.openInputStream(Uri.parse(playlist.url))?.use { it.readBytes() }
                    playlist.url.startsWith("/") -> File(playlist.url).takeIf { it.isFile }?.readBytes()
                    playlist.url.startsWith("file://", ignoreCase = true) ->
                        Uri.parse(playlist.url).path?.let { File(it).takeIf { file -> file.isFile }?.readBytes() }
                    else -> null
                }
            }.getOrNull()
            val encoded = localBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
            if (encoded != null) playlist.copy(url = "data:text/plain;base64,$encoded") else playlist
        }
        val map = mapOf(
            "playlists" to Json.encodeToString(portablePlaylists),
            "epgSources" to Json.encodeToString(_epgSources.value),
            "favorites" to Json.encodeToString(_favorites.value),
            "pinned" to Json.encodeToString(_pinnedUrls.value),
            "locked" to Json.encodeToString(_lockedChannels.value),
            "lockedFolders" to Json.encodeToString(lockedFolders.value),
            "hiddenChannels" to Json.encodeToString(_hiddenChannels.value),
            "hiddenCategories" to Json.encodeToString(_hiddenCategories.value),
            "customCategoryOrder" to Json.encodeToString(customCategoryOrder.value),
            "customChannelOrder" to Json.encodeToString(customChannelOrder.value)
        )
        return Json.encodeToString(map)
    }

    fun exportBackupJson(): String {
        val map = mapOf(
            "playlists" to Json.encodeToString(_playlists.value.filterNot { isKitTvPlaylist(it.name, it.url, it.id) }),
            "epgSources" to Json.encodeToString(_epgSources.value),
            "favorites" to Json.encodeToString(_favorites.value),
            "pinned" to Json.encodeToString(_pinnedUrls.value),
            "locked" to Json.encodeToString(_lockedChannels.value),
            "lockedFolders" to Json.encodeToString(lockedFolders.value),
            "hiddenChannels" to Json.encodeToString(_hiddenChannels.value),
            "hiddenCategories" to Json.encodeToString(_hiddenCategories.value),
            "customCategoryOrder" to Json.encodeToString(customCategoryOrder.value),
            "customChannelOrder" to Json.encodeToString(customChannelOrder.value)
        )
        return Json.encodeToString(map)
    }

    fun importBackupJson(jsonString: String): Boolean {
        return try {
            val map: Map<String, String> = Json.decodeFromString(jsonString)
            map["playlists"]?.let {
                val imported: List<Playlist> = Json.decodeFromString<List<Playlist>>(it).filterNot { p -> isKitTvPlaylist(p.name, p.url, p.id) }
                val playlistDir = File(getApplication<Application>().filesDir, "imported_playlists").apply { mkdirs() }
                _playlists.value = imported.map { playlist ->
                    val prefix = "data:text/plain;base64,"
                    if (playlist.url.startsWith(prefix, ignoreCase = true)) {
                        val bytes = android.util.Base64.decode(playlist.url.substring(prefix.length), android.util.Base64.DEFAULT)
                        val safeName = playlist.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifBlank { "playlist" }
                        val target = File(playlistDir, "${playlist.id.replace(Regex("[^A-Za-z0-9_-]"), "_")}-$safeName.m3u8")
                        target.writeBytes(bytes)
                        playlist.copy(url = target.absolutePath)
                    } else playlist
                }
                savePlaylistsToPrefs()
            }
            map["epgSources"]?.let { _epgSources.value = Json.decodeFromString(it); saveEpgSourcesToPrefs() }
            map["favorites"]?.let { _favorites.value = Json.decodeFromString(it); prefs.edit().putStringSet("favorites", _favorites.value).apply() }
            map["pinned"]?.let { _pinnedUrls.value = Json.decodeFromString(it); prefs.edit().putStringSet("pinned_channels", _pinnedUrls.value).apply() }
            map["locked"]?.let { _lockedChannels.value = Json.decodeFromString(it); saveLockedPasswordsToPrefs(_lockedChannels.value) }
            map["lockedFolders"]?.let { lockedFolders.value = Json.decodeFromString<Map<String, String>>(it); saveLockedFoldersToPrefs(lockedFolders.value) }
            map["hiddenChannels"]?.let { _hiddenChannels.value = Json.decodeFromString<Set<String>>(it); prefs.edit().putStringSet("hidden_channels", _hiddenChannels.value).apply() }
            map["hiddenCategories"]?.let { _hiddenCategories.value = Json.decodeFromString<Set<String>>(it); prefs.edit().putStringSet("hidden_categories", _hiddenCategories.value).apply() }
            map["customCategoryOrder"]?.let { customCategoryOrder.value = Json.decodeFromString<List<String>>(it); saveCustomCategoryOrderToPrefs(customCategoryOrder.value) }
            map["customChannelOrder"]?.let { customChannelOrder.value = Json.decodeFromString<List<String>>(it); saveCustomChannelOrderToPrefs(customChannelOrder.value) }
            _playlists.value.firstOrNull { it.isSelected && it.enabled }?.let { selected -> loadPlaylist(selected.url) }
            refreshAllEpg(force = true)
            true
        } catch (e: Exception) {
            false
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
                    "show_codecs" -> showCodecs.value = value
                    "show_bitrate" -> showBitrate.value = value
                    "show_channel_numbers" -> showChannelNumbers.value = value
                    "volume_gesture_enabled" -> volumeGestureEnabled.value = value
                    "expressive_glow" -> expressiveGlow.value = value
                    "ambient_canvas" -> ambientCanvas.value = value
                    "icon_rotation_enabled" -> iconRotationEnabled.value = value
                }
            }
            is Float -> {
                editor.putFloat(key, value)
                when(key) {
                    "font_size" -> fontSize.value = value
                    "playback_speed" -> playbackSpeed.value = value
                    "audio_volume_boost" -> audioVolumeBoost.value = value
                    "wave_amplitude" -> waveAmplitude.value = value
                    "wave_speed" -> waveSpeed.value = value
                    "spring_scale" -> springScale.value = value
                }
            }
            is Int -> {
                editor.putInt(key, value)
                when(key) {
                    "auto_hide_timeout" -> autoHideTimeout.value = value
                    "card_corner_radius" -> cardCornerRadius.value = value
                }
            }
            is String -> {
                editor.putString(key, value)
                when(key) {
                    "language" -> language.value = value
                    "list_size" -> listSize.value = value
                    "theme_mode" -> themeMode.value = value
                    "accent_color" -> accentColor.value = value
                    "last_channel_url" -> lastChannelUrl.value = value
                    "back_behavior" -> backBehavior.value = value
                    "clock_position" -> clockPosition.value = value
                    "buffering_level" -> bufferingLevel.value = value
                    "group_sort_order" -> groupSortOrder.value = value
                    "resolution_format" -> resolutionFormat.value = value
                    "badge_shape" -> badgeShape.value = value
                    "badge_color" -> badgeColor.value = value
                    "panel_color" -> panelColor.value = value
                    "ui_style" -> uiStyle.value = value
                    "category_display_mode" -> categoryDisplayMode.value = value
                    "catchup_mode" -> catchupMode.value = value
                    "aspect_ratio_mode" -> aspectRatioMode.value = value
                    "hardware_decoder" -> hardwareDecoder.value = value
                    "epg_update_interval" -> epgUpdateInterval.value = value
                    "epg_progress_style" -> epgProgressStyle.value = value
                    "player_glass_tint" -> playerGlassTint.value = value
                    "card_badge_style" -> cardBadgeStyle.value = value
                }
            }
            is Int -> {
                editor.putInt(key, value)
                when(key) {
                    "auto_hide_timeout" -> autoHideTimeout.value = value
                    "card_corner_radius" -> cardCornerRadius.value = value
                }
            }
        }
        editor.apply()
    }

    fun addPlaylist(name: String, url: String) {
        _selectedCategory.value = "All"
        selectedFolder.value = null
        _searchQuery.value = ""
        val finalName = name.ifBlank { "Playlist ${_playlists.value.size + 1}" }
        
        val newPlaylists = _playlists.value.map { it.copy(isSelected = false) }
        val newPlaylist = Playlist(UUID.randomUUID().toString(), finalName, url, isSelected = true)
        
        _playlists.value = newPlaylists + newPlaylist
        savePlaylistsToPrefs()
        loadPlaylist(url)
    }

    fun deletePlaylist(id: String) {
        val updated = _playlists.value.filter { it.id != id }
        _playlists.value = updated
        savePlaylistsToPrefs()
        if (updated.isEmpty()) {
            _rawChannels.value = emptyList()
            if (cacheFile.exists()) cacheFile.delete()
        } else if (!updated.any { it.isSelected }) {
            selectPlaylist(updated.first().id)
        }
    }

    fun updatePlaylist(updatedPlaylist: Playlist) {
        val previousSelected = _playlists.value.any { it.id == updatedPlaylist.id && it.isSelected }
        var list = _playlists.value.map { current ->
            if (current.id == updatedPlaylist.id) updatedPlaylist else current
        }
        if (!updatedPlaylist.enabled && previousSelected) {
            // A disabled playlist must not remain selected; switch to another enabled source.
            val fallback = list.firstOrNull { it.enabled && it.id != updatedPlaylist.id }
            list = list.map { it.copy(isSelected = fallback?.id == it.id) }
            _playlists.value = list
            savePlaylistsToPrefs()
            if (fallback != null) loadPlaylist(fallback.url) else {
                _rawChannels.value = emptyList()
                if (cacheFile.exists()) cacheFile.delete()
            }
            return
        }
        if (updatedPlaylist.enabled && updatedPlaylist.isSelected) {
            list = list.map { it.copy(isSelected = it.id == updatedPlaylist.id) }
        }
        _playlists.value = list
        savePlaylistsToPrefs()
        if (updatedPlaylist.isSelected && updatedPlaylist.enabled) loadPlaylist(updatedPlaylist.url)
    }

    fun updateEpgSource(updatedEpgSource: EpgSource) {
        val list = _epgSources.value.map {
            if (it.id == updatedEpgSource.id) updatedEpgSource else it
        }
        _epgSources.value = list
        saveEpgSourcesToPrefs()
        if (updatedEpgSource.enabled) {
            refreshAllEpg()
        }
    }

    private fun checkAutoUpdates() {
        _playlists.value.filter { it.isSelected && it.enabled }.forEach { playlist ->
            if (shouldUpdatePlaylist(playlist, isAppLaunch = false)) {
                loadPlaylist(playlist.url)
            }
        }

        val epgNeedsUpdate = _epgSources.value.filter { it.enabled }.any { source ->
            shouldUpdateEpgSource(source, isAppLaunch = false)
        }
        if (epgNeedsUpdate) {
            refreshAllEpg()
        }
    }

    fun selectPlaylist(id: String) {
        val target = _playlists.value.find { it.id == id } ?: return
        if (!target.enabled) return
        _selectedCategory.value = "All"
        selectedFolder.value = null
        _searchQuery.value = ""
        _playlists.value = _playlists.value.map { it.copy(isSelected = it.id == id) }
        savePlaylistsToPrefs()
        loadPlaylist(target.url)
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
        _pinnedUrls.value = emptySet()
        _channelViewingTimes.value = emptyMap()
        _lockedChannels.value = emptyMap()
        _recentChannels.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) { epgDao.deleteAll() }
        if (cacheFile.exists()) cacheFile.delete()
    }

    private fun savePlaylistsToPrefs() = prefs.edit().putString("playlists_json", Json.encodeToString(_playlists.value)).apply()
    private fun loadPlaylistsFromPrefs(): List<Playlist> = try { Json.decodeFromString(prefs.getString("playlists_json", "[]")!!) } catch (e: Exception) { emptyList() }
    private fun saveEpgSourcesToPrefs() = prefs.edit().putString("epg_sources_json", Json.encodeToString(_epgSources.value)).apply()
    private fun loadEpgSourcesFromPrefs(): List<EpgSource> = try { Json.decodeFromString(prefs.getString("epg_sources_json", "[]")!!) } catch (e: Exception) { emptyList() }

    private fun loadLockedFoldersFromPrefs(): Map<String, String> {
        val json = prefs.getString("locked_folders_json", null) ?: return emptyMap()
        return try { Json.decodeFromString(json) } catch (e: Exception) { emptyMap() }
    }

    private fun saveLockedFoldersToPrefs(map: Map<String, String>) {
        prefs.edit().putString("locked_folders_json", Json.encodeToString(map)).apply()
    }

    private fun loadCustomCategoryOrderFromPrefs(): List<String> {
        val json = prefs.getString("custom_category_order_json", null) ?: return emptyList()
        return try { Json.decodeFromString(json) } catch (e: Exception) { emptyList() }
    }

    private fun saveCustomCategoryOrderToPrefs(list: List<String>) {
        prefs.edit().putString("custom_category_order_json", Json.encodeToString(list)).apply()
    }

    private fun loadCustomChannelOrderFromPrefs(): List<String> {
        val json = prefs.getString("custom_channel_order_json", null) ?: return emptyList()
        return try { Json.decodeFromString(json) } catch (e: Exception) { emptyList() }
    }

    private fun saveCustomChannelOrderToPrefs(list: List<String>) {
        prefs.edit().putString("custom_channel_order_json", Json.encodeToString(list)).apply()
    }

    fun lockFolder(folder: String, pass: String) {
        val map = lockedFolders.value.toMutableMap()
        map[folder] = pass
        lockedFolders.value = map
        saveLockedFoldersToPrefs(map)
    }

    fun unlockFolder(folder: String) {
        val map = lockedFolders.value.toMutableMap()
        map.remove(folder)
        lockedFolders.value = map
        saveLockedFoldersToPrefs(map)
    }

    fun isFolderLocked(folder: String): Boolean = lockedFolders.value.containsKey(folder)
    fun getFolderPassword(folder: String): String? = lockedFolders.value[folder]

    fun moveCategory(folder: String, targetPosition: Int) {
        val current = categories.value.filterNot { it in listOf("All", "Favorites", "Recent") }.toMutableList()
        current.remove(folder)
        val clamped = targetPosition.coerceIn(0, current.size)
        current.add(clamped, folder)
        customCategoryOrder.value = current
        saveCustomCategoryOrderToPrefs(current)
    }

    fun moveChannel(channelUrl: String, targetPosition: Int) {
        val currentChannels = filteredChannels.value.map { it.url }.toMutableList()
        currentChannels.remove(channelUrl)
        val clamped = targetPosition.coerceIn(0, currentChannels.size)
        currentChannels.add(clamped, channelUrl)
        customChannelOrder.value = currentChannels
        saveCustomChannelOrderToPrefs(currentChannels)
    }

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
