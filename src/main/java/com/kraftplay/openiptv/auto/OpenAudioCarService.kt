package com.kraftplay.openiptv.auto

import android.content.Context
import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import com.kraftplay.openiptv.R
import com.kraftplay.openiptv.ui.tr
import com.kraftplay.openiptv.model.IptvChannel
import com.kraftplay.openiptv.model.Playlist
import com.kraftplay.openiptv.parser.M3uParser
import com.kraftplay.openiptv.service.SharedPlayerManager
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.concurrent.Executors

class OpenAudioCarService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    fun getMinCarApiLevel(): Int {
        return 1
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return object : Session() {
            override fun onCreateScreen(intent: Intent): Screen {
                return OpenAudioHomeScreen(carContext)
            }
        }
    }
}

class OpenAudioHomeScreen(carContext: CarContext) : Screen(carContext) {

    private var cachedChannels = listOf<IptvChannel>()
    private var favoriteUrls = mutableSetOf<String>()
    private val executor = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient()

    init {
        loadChannels()
    }

    private fun loadChannels() {
        val prefs = carContext.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        favoriteUrls = (prefs.getStringSet("favorites", emptySet()) ?: emptySet()).toMutableSet()

        val cacheFile = File(carContext.cacheDir, "channels_cache.json")
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
                                    invalidate()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        val favChannels = cachedChannels.filter { favoriteUrls.contains(it.url) }
        if (favChannels.isNotEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(tr("★ Избранные каналы ({0})", favChannels.size))
                    .setOnClickListener {
                        screenManager.push(OpenAudioChannelListScreen(carContext, tr("Избранное"), favChannels))
                    }
                    .build()
            )
        }

        if (cachedChannels.isNotEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(tr("Все каналы ({0})", cachedChannels.size))
                    .setOnClickListener {
                        screenManager.push(OpenAudioChannelListScreen(carContext, tr("Все каналы"), cachedChannels))
                    }
                    .build()
            )

            val categories = cachedChannels
                .map { it.category?.takeIf { c -> c.isNotBlank() } ?: tr("Общие") }
                .distinct()

            categories.take(10).forEach { category ->
                val catChannels = cachedChannels.filter { (it.category?.takeIf { c -> c.isNotBlank() } ?: tr("Общие")) == category }
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(category)
                        .addText(tr("{0} каналов", catChannels.size))
                        .setOnClickListener {
                            screenManager.push(OpenAudioChannelListScreen(carContext, category, catChannels))
                        }
                        .build()
                )
            }
        } else {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(tr("Загрузка списка каналов..."))
                    .addText(tr("Пожалуйста, подождите"))
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.app_name_audio))
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }
}

class OpenAudioChannelListScreen(
    carContext: CarContext,
    private val title: String,
    private val channels: List<IptvChannel>
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        channels.take(100).forEach { channel ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(channel.name)
                    .addText(channel.category ?: "IPTV")
                    .setOnClickListener {
                        SharedPlayerManager.playStream(
                            carContext,
                            channel.url,
                            channel,
                            channels
                        )
                        carContext.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("last_channel_url", channel.url)
                            .apply()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
