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

    @Query("SELECT COUNT(*) FROM epg_programs")
    suspend fun getProgramCount(): Int

    @Query("DELETE FROM epg_programs WHERE endTime < :cutoffTime")
    suspend fun deleteExpired(cutoffTime: Long)

    @Query("SELECT * FROM epg_programs WHERE endTime > (:now - 1209600000) ORDER BY startTime ASC")
    suspend fun getAllValidPrograms(now: Long): List<EpgProgram>

    @Query("SELECT * FROM epg_programs WHERE LOWER(channelId) = LOWER(:channelId) AND endTime > :now ORDER BY startTime ASC")
    fun getUpcomingPrograms(channelId: String, now: Long): Flow<List<EpgProgram>>

    @Query("SELECT * FROM epg_programs WHERE LOWER(channelId) = LOWER(:channelId) AND startTime <= :now AND endTime > :now LIMIT 1")
    suspend fun getCurrentProgram(channelId: String, now: Long): EpgProgram?

    @Query("SELECT * FROM epg_programs WHERE LOWER(channelId) = LOWER(:channelId) AND startTime > :now ORDER BY startTime ASC LIMIT 1")
    suspend fun getNextProgram(channelId: String, now: Long): EpgProgram?

    @Query("SELECT * FROM epg_programs WHERE (LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(description) LIKE '%' || LOWER(:query) || '%') AND endTime > (:now - 1209600000) ORDER BY startTime ASC LIMIT 300")
    suspend fun searchPrograms(query: String, now: Long): List<EpgProgram>
}
