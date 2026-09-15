package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.BuildConfig

@Database(
    entities = [
        SessionEntity::class,
        CourtEntity::class,
        PlayerEntity::class,
        CompletedMatchEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PickleballDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: PickleballDatabase? = null

        fun getInstance(context: Context): PickleballDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PickleballDatabase::class.java,
                    "pickleball_sessions.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }

        /** Test-only: close and clear the singleton so a test can start from a clean database. */
        @androidx.annotation.VisibleForTesting
        fun resetInstanceForTest() {
            if (!BuildConfig.DEBUG) return // inert no-op in release builds
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
