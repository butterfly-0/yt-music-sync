package com.ytmusic.downloader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [TrackEntity::class, PlaylistEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ytmusic_downloader.db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default "Liked Music" playlist
                        CoroutineScope(Dispatchers.IO).launch {
                            getInstance(context).playlistDao().insertPlaylist(
                                PlaylistEntity(
                                    id = "LM",
                                    title = "Вподобана музика (YouTube Music)",
                                    url = "https://music.youtube.com/playlist?list=LM",
                                    isLikedMusic = true,
                                    isEnabled = true,
                                    syncOnlyNew = true,
                                    lastSyncedAt = 0,
                                    trackCount = 0,
                                    thumbnailUrl = null
                                )
                            )
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
