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
