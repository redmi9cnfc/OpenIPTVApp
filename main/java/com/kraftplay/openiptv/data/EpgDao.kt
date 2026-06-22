package com.kraftplay.openiptv.data

import androidx.room.*
import com.kraftplay.openiptv.model.EpgProgram
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<EpgProgram>)

    @Query("DELETE FROM epg_programs")
    suspend fun deleteAll()

    @Query("SELECT * FROM epg_programs WHERE LOWER(channelId) = LOWER(:channelId) AND endTime > :now ORDER BY startTime ASC")
    fun getUpcomingPrograms(channelId: String, now: Long): Flow<List<EpgProgram>>

    @Query("SELECT * FROM epg_programs WHERE LOWER(channelId) = LOWER(:channelId) AND startTime <= :now AND endTime > :now LIMIT 1")
    suspend fun getCurrentProgram(channelId: String, now: Long): EpgProgram?

    @Query("SELECT * FROM epg_programs WHERE LOWER(channelId) = LOWER(:channelId) AND startTime > :now ORDER BY startTime ASC LIMIT 1")
    suspend fun getNextProgram(channelId: String, now: Long): EpgProgram?
}
