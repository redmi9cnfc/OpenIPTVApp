package com.kraftplay.openiptv.model

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val url: String,
    val isSelected: Boolean = false,
    val channelCount: Int = 0,
    val enabled: Boolean = true,
    val password: String? = null,
    val autoUpdateInterval: String = "On launch",
    val lastUpdatedTime: Long = 0L
)

fun isKitTvPlaylist(name: String, url: String, id: String = ""): Boolean {
    return id == "kittv-default" ||
           name.contains("KitTV", ignoreCase = true) ||
           name.contains("kItTv", ignoreCase = true) ||
           url.contains("KitTV", ignoreCase = true) ||
           url.contains("kItTv", ignoreCase = true)
}
