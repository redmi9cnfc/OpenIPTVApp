package com.kraftplay.openiptv.model

data class IptvChannel(
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val category: String? = null,
    val epgId: String? = null
)
