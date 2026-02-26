package com.droidlink.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.droidlink.app.data.local.dao.ConnectionDao
import com.droidlink.app.data.local.entity.ConnectionEntity

/**
 * Room database for DroidLink.
 */
@Database(
    entities = [ConnectionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun connectionDao(): ConnectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "droidlink_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
