package com.ytmusic.downloader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ytmusic.downloader.data.model.Playlist

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val isLikedMusic: Boolean,
    val isEnabled: Boolean,
    val syncOnlyNew: Boolean,
    val lastSyncedAt: Long,
    val trackCount: Int,
    val thumbnailUrl: String?
) {
    fun toDomain(): Playlist {
        return Playlist(
            id = id,
            title = title,
            url = url,
            isLikedMusic = isLikedMusic,
            isEnabled = isEnabled,
            syncOnlyNew = syncOnlyNew,
            lastSyncedAt = lastSyncedAt,
            trackCount = trackCount,
            thumbnailUrl = thumbnailUrl
        )
    }

    companion object {
        fun fromDomain(playlist: Playlist): PlaylistEntity {
            return PlaylistEntity(
                id = playlist.id,
                title = playlist.title,
                url = playlist.url,
                isLikedMusic = playlist.isLikedMusic,
                isEnabled = playlist.isEnabled,
                syncOnlyNew = playlist.syncOnlyNew,
                lastSyncedAt = playlist.lastSyncedAt,
                trackCount = playlist.trackCount,
                thumbnailUrl = playlist.thumbnailUrl
            )
        }
    }
}
