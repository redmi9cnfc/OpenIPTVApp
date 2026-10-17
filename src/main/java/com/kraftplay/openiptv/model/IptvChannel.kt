package com.kraftplay.openiptv.model

import kotlinx.serialization.Serializable

@Serializable
data class IptvChannel(
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val category: String? = null,
    val epgId: String? = null,
    val tvgName: String? = null,
    val catchup: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int? = null,
    var isFavorite: Boolean = false,
    var isLocked: Boolean = false,
    var isPinned: Boolean = false,
    var customOrder: Int = 0
)
