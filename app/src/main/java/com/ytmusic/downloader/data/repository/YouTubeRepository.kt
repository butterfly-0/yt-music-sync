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
     * Discovers new tracks to download across all active playlists.
     */
    suspend fun findNewTracksToDownload(): List<Track> = withContext(Dispatchers.IO) {
        val newTracks = mutableListOf<Track>()
        val alreadyDownloadedIds = trackDao.getAllDownloadedTrackIds().toSet()
        val enabledPlaylists = playlistDao.getEnabledPlaylists()

        for (playlistEntity in enabledPlaylists) {
            try {
                val fetchedTracks = extractor.getPlaylistTracks(playlistEntity.id, maxTracks = 50)
                
                // Update playlist track count
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
            val title = customTitle?.takeIf { it.isNotBlank() } ?: "Плейлист $playlistId"

            val playlistEntity = PlaylistEntity(
                id = playlistId,
                title = title,
                url = if (urlOrId.startsWith("http")) urlOrId else "https://music.youtube.com/playlist?list=$playlistId",
                isLikedMusic = playlistId == "LM" || playlistId == "LL",
                isEnabled = true,
                syncOnlyNew = userPreferences.isSyncOnlyNew,
                lastSyncedAt = 0,
                trackCount = tracks.size,
                thumbnailUrl = tracks.firstOrNull()?.thumbnailUrl
            )

            playlistDao.insertPlaylist(playlistEntity)
            Result.success(playlistEntity.toDomain())
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun updatePlaylistEnabled(playlistId: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withContext
        playlistDao.updatePlaylist(playlist.copy(isEnabled = isEnabled))
    }

    suspend fun updatePlaylistSyncOnlyNew(playlistId: String, syncOnlyNew: Boolean) = withContext(Dispatchers.IO) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withContext
        playlistDao.updatePlaylist(playlist.copy(syncOnlyNew = syncOnlyNew))
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylistById(playlistId)
    }

    private fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed == "LM" || trimmed == "LL") return trimmed
        if (!trimmed.startsWith("http")) return trimmed

        val regex = Regex("[?&]list=([a-zA-Z0-9_-]+)")
        val match = regex.find(trimmed)
        return match?.groupValues?.get(1)
    }
}
