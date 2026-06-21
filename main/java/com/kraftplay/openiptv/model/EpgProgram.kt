package com.kraftplay.openiptv.model

import java.util.Date

data class EpgProgram(
    val channelId: String,
    val title: String,
    val start: Date,
    val stop: Date,
    val description: String? = null
)
