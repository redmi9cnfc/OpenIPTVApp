package com.kraftplay.openiptv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kraftplay.openiptv.model.EpgProgram

@Database(entities = [EpgProgram::class], version = 1, exportSchema = false)
abstract class EpgDatabase : RoomDatabase() {
    abstract fun epgDao(): EpgDao

    /** Trims the WAL and reclaims pages after pruning the rolling EPG window. */
    fun compactStorage() {
        val db = openHelper.writableDatabase
        runCatching { db.query("PRAGMA wal_checkpoint(TRUNCATE)").close() }
        runCatching { db.execSQL("VACUUM") }
    }

    companion object {
        @Volatile
        private var INSTANCE: EpgDatabase? = null

        fun getDatabase(context: Context): EpgDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EpgDatabase::class.java,
                    "epg_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
