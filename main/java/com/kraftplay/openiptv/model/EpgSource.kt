package com.kraftplay.openiptv.model

import kotlinx.serialization.Serializable

@Serializable
data class EpgSource(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true
)
