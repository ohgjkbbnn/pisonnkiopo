package com.savoo.scclient.data.local

import com.savoo.scclient.compat.*
import com.savoo.scclient.compat.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.ConstructedBy
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import com.savoo.scclient.data.model.ExcludedMixArtist
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.FavoritePlaylist
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.LocalPlaylist
import com.savoo.scclient.data.model.LocalPlaylistTrack
import com.savoo.scclient.data.model.LyricsCacheEntity
import com.savoo.scclient.data.model.LyricsSyncEntity
import com.savoo.scclient.data.model.OfflineTrack
import com.savoo.scclient.data.model.PlayEvent
import com.savoo.scclient.data.model.TelegramImportRecord
import com.savoo.scclient.data.model.UnavailableTrackEntity

@Database(
    entities = [FavoriteTrack::class, FavoriteArtist::class, FavoritePlaylist::class, OfflineTrack::class, TelegramImportRecord::class, PlayEvent::class, ExcludedMixArtist::class, LyricsCacheEntity::class, UnavailableTrackEntity::class, LyricsSyncEntity::class, LocalPlaylist::class, LocalPlaylistTrack::class],
    version = 17,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun offlineDao(): OfflineDao
    abstract fun telegramImportDao(): TelegramImportDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun excludedArtistDao(): ExcludedArtistDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
    abstract fun lyricsSyncDao(): LyricsSyncDao
    abstract fun unavailableTrackDao(): UnavailableTrackDao
    abstract fun localPlaylistDao(): LocalPlaylistDao

    companion object {
        const val DATABASE_NAME = "scclient.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder<AppDatabase>(name = "${context.filesDir.absolutePath}/$DATABASE_NAME")
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
