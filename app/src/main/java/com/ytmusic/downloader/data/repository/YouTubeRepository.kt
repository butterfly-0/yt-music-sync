package com.ytmusic.downloader.data.repository

import com.ytmusic.downloader.data.local.PlaylistDao
import com.ytmusic.downloader.data.local.PlaylistEntity
import com.ytmusic.downloader.data.local.TrackDao
import com.ytmusic.downloader.data.model.Playlist
import com.ytmusic.downloader.data.model.Track
import com.ytmusic.downloader.data.preferences.UserPreferences
import com.ytmusic.downloader.youtube.YouTubeExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class YouTubeRepository(
    private val extractor: YouTubeExtractor,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val userPreferences: UserPreferences
) {

    fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getEnabledPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        playlistDao.getEnabledPlaylists().map { it.toDomain() }
    }

    /**
     * Automatically syncs and updates user's library playlists from their YouTube account.
     */
    suspend fun syncUserPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val fetchedPlaylists = extractor.getUserPlaylists()
        for (playlist in fetchedPlaylists) {
            val existing = playlistDao.getPlaylistById(playlist.id)
            if (existing == null) {
                playlistDao.insertPlaylist(PlaylistEntity.fromDomain(playlist))
            } else {
                playlistDao.updatePlaylist(
                    existing.copy(
                        title = playlist.title.ifBlank { existing.title },
                        trackCount = if (playlist.trackCount > 0) playlist.trackCount else existing.trackCount,
                        thumbnailUrl = playlist.thumbnailUrl ?: existing.thumbnailUrl
                    )
                )
            }
        }
        playlistDao.getAllPlaylistsSync().map { it.toDomain() }
    }

    /**
     * Discovers new tracks to download across all active playlists.
     */
    suspend fun findNewTracksToDownload(): List<Track> = withContext(Dispatchers.IO) {
        val newTracks = mutableListOf<Track>()
        val alreadyDownloadedIds = trackDao.getAllDownloadedTrackIds().toSet()

        // Sync and refresh playlists first
        syncUserPlaylists()

        val enabledPlaylists = playlistDao.getEnabledPlaylists()

        for (playlistEntity in enabledPlaylists) {
            try {
                val fetchedTracks = extractor.getPlaylistTracks(playlistEntity.id, maxTracks = 100)

                // Update playlist track count & thumbnail
                playlistDao.updatePlaylist(
                    playlistEntity.copy(
                        trackCount = fetchedTracks.size,
                        lastSyncedAt = System.currentTimeMillis(),
                        thumbnailUrl = fetchedTracks.firstOrNull()?.thumbnailUrl ?: playlistEntity.thumbnailUrl
                    )
                )

                // Filter out already downloaded tracks
                val pendingForPlaylist = fetchedTracks.filter { track ->
                    !alreadyDownloadedIds.contains(track.id) && newTracks.none { it.id == track.id }
                }

                newTracks.addAll(pendingForPlaylist)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        newTracks
    }

    suspend fun addPlaylist(urlOrId: String, customTitle: String? = null): Result<Playlist> = withContext(Dispatchers.IO) {
        try {
            val playlistId = extractPlaylistId(urlOrId)
                ?: return@withContext Result.failure(Exception("Невірне посилання на плейлист YouTube"))

            val existing = playlistDao.getPlaylistById(playlistId)
            if (existing != null) {
                return@withContext Result.success(existing.toDomain())
            }

            val tracks = extractor.getPlaylistTracks(playlistId, maxTracks = 10)
            val title = customTitle?.ifBlank { null } ?: "Плейлист ($playlistId)"
            val thumb = tracks.firstOrNull()?.thumbnailUrl

            val entity = PlaylistEntity(
                id = playlistId,
                title = title,
                url = "https://music.youtube.com/playlist?list=$playlistId",
                isLikedMusic = playlistId == "LM" || playlistId == "LL",
                isEnabled = true,
                syncOnlyNew = false,
                lastSyncedAt = System.currentTimeMillis(),
                trackCount = tracks.size,
                thumbnailUrl = thumb
            )

            playlistDao.insertPlaylist(entity)
            Result.success(entity.toDomain())
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun updatePlaylistEnabled(playlist: Playlist, enabled: Boolean) = withContext(Dispatchers.IO) {
        val entity = playlistDao.getPlaylistById(playlist.id) ?: return@withContext
        playlistDao.updatePlaylist(entity.copy(isEnabled = enabled))
    }

    suspend fun updatePlaylistSyncOnlyNew(playlist: Playlist, syncOnlyNew: Boolean) = withContext(Dispatchers.IO) {
        val entity = playlistDao.getPlaylistById(playlist.id) ?: return@withContext
        playlistDao.updatePlaylist(entity.copy(syncOnlyNew = syncOnlyNew))
    }

    suspend fun deletePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        if (!playlist.isLikedMusic) {
            playlistDao.deletePlaylistById(playlist.id)
        }
    }

    private fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.startsWith("PL") || trimmed.startsWith("RD") || trimmed.startsWith("OLAK5uy_") || trimmed == "LM" || trimmed == "LL") {
            return trimmed
        }
        val regex = Regex("[?&]list=([a-zA-Z0-9_-]+)")
        return regex.find(trimmed)?.groupValues?.get(1)
    }
}
