package com.ytmusic.downloader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ytmusic.downloader.data.model.AudioFormat
import com.ytmusic.downloader.data.model.DownloadStatus
import com.ytmusic.downloader.data.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String, // YouTube Video ID
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val localFilePath: String?,
    val format: String, // "M4A" or "MP3"
    val status: String, // "COMPLETED", "FAILED", etc.
    val downloadedAt: Long,
    val sourcePlaylistId: String
) {
    fun toDomain(): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds,
            thumbnailUrl = thumbnailUrl,
            localFilePath = localFilePath,
            format = try { AudioFormat.valueOf(format) } catch (e: Exception) { AudioFormat.M4A },
            status = try { DownloadStatus.valueOf(status) } catch (e: Exception) { DownloadStatus.COMPLETED },
            downloadedAt = downloadedAt,
            sourcePlaylistId = sourcePlaylistId
        )
    }

    companion object {
        fun fromDomain(track: Track): TrackEntity {
            return TrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationSeconds = track.durationSeconds,
                thumbnailUrl = track.thumbnailUrl,
                localFilePath = track.localFilePath,
                format = track.format.name,
                status = track.status.name,
                downloadedAt = track.downloadedAt,
                sourcePlaylistId = track.sourcePlaylistId
            )
        }
    }
}
