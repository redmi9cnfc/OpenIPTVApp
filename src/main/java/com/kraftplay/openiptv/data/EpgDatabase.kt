package com.kraftplay.openiptv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kraftplay.openiptv.model.EpgProgram

@Database(entities = [EpgProgram::class], version = 1, exportSchema = false)
abstract class EpgDatabase : RoomDatabase() {
    abstract fun epgDao(): EpgDao

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
