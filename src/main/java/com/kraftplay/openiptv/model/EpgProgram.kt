package com.kraftplay.openiptv.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "epg_programs")
data class EpgProgram(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val title: String,
    val startTime: Long, // Using Long for Room
    val endTime: Long,
    val description: String? = null,
    val category: String? = null,
    val iconUrl: String? = null
) {
    // Helper to get Date objects if needed
    val start: Date get() = Date(startTime)
    val stop: Date get() = Date(endTime)
}
