package com.kraftplay.openiptv.model

import kotlinx.serialization.Serializable

@Serializable
data class IptvChannel(
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val category: String? = null,
    val epgId: String? = null,
    var isFavorite: Boolean = false
)
