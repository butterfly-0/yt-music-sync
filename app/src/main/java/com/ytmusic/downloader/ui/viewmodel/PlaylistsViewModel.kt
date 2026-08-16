package com.ytmusic.downloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ytmusic.downloader.YTMusicApp
import com.ytmusic.downloader.data.model.Playlist
import com.ytmusic.downloader.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YTMusicApp
    private val youtubeRepository = app.youtubeRepository
    private val trackDao = app.database.trackDao()
    private val extractor = app.youtubeExtractor
    private val downloadRepo = app.downloadRepository
    private val previewPlayer = app.audioPreviewPlayer
    private val userPrefs = app.userPreferences

    val playlists: StateFlow<List<Playlist>> = youtubeRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _addPlaylistError = MutableStateFlow<String?>(null)
    val addPlaylistError: StateFlow<String?> = _addPlaylistError.asStateFlow()

    private val _isAddingPlaylist = MutableStateFlow(false)
    val isAddingPlaylist: StateFlow<Boolean> = _isAddingPlaylist.asStateFlow()

    // Playlist Details Modal / Screen
    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _playlistTracks = MutableStateFlow<List<Track>>(emptyList())
    val playlistTracks: StateFlow<List<Track>> = _playlistTracks.asStateFlow()

    private val _isLoadingTracks = MutableStateFlow(false)
    val isLoadingTracks: StateFlow<Boolean> = _isLoadingTracks.asStateFlow()

    private val _downloadingTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingTrackIds: StateFlow<Set<String>> = _downloadingTrackIds.asStateFlow()

    private val _downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTrackIds: StateFlow<Set<String>> = _downloadedTrackIds.asStateFlow()

    val currentPlayingTrackId: StateFlow<String?> = app.musicPlayerManager.currentTrack
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPlaying: StateFlow<Boolean> = app.musicPlayerManager.isPlaying

    fun togglePlayPreview(track: Track) {
        if (app.musicPlayerManager.currentTrack.value?.id == track.id) {
            app.musicPlayerManager.playOrPause()
        } else {
            app.musicPlayerManager.playTrack(track, _playlistTracks.value)
        }
    }

    init {
        syncPlaylists()
    }

    fun syncPlaylists() {
        viewModelScope.launch {
            try {
                youtubeRepository.syncUserPlaylists()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectPlaylist(playlist: Playlist) {
        _selectedPlaylist.value = playlist
        _playlistTracks.value = emptyList()
        _isLoadingTracks.value = true

        viewModelScope.launch {
            try {
                val downloadedIds = trackDao.getAllDownloadedTrackIds().toSet()
                _downloadedTrackIds.value = downloadedIds

                val tracks = extractor.getPlaylistTracks(playlist.id, maxTracks = 150)
                _playlistTracks.value = tracks
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingTracks.value = false
            }
        }
    }

    fun closePlaylistDetail() {
        _selectedPlaylist.value = null
        _playlistTracks.value = emptyList()
        _isLoadingTracks.value = false
    }

    fun downloadSingleTrack(track: Track) {
        viewModelScope.launch {
            _downloadingTrackIds.value = _downloadingTrackIds.value + track.id
            try {
                val result = downloadRepo.downloadAndSaveTrack(track, userPrefs.audioFormat)
                if (result.isSuccess) {
                    _downloadedTrackIds.value = _downloadedTrackIds.value + track.id
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _downloadingTrackIds.value = _downloadingTrackIds.value - track.id
            }
        }
    }

    fun togglePlayPreview(track: Track) {
        val path = track.localFilePath
        if (!path.isNullOrBlank()) {
            previewPlayer.playOrPause(track.id, path)
        }
    }

    fun addPlaylist(urlOrId: String, title: String? = null) {
        viewModelScope.launch {
            _isAddingPlaylist.value = true
            _addPlaylistError.value = null
            val result = youtubeRepository.addPlaylist(urlOrId, title)
            if (result.isFailure) {
                _addPlaylistError.value = result.exceptionOrNull()?.localizedMessage ?: "Не вдалося додати плейлист"
            }
            _isAddingPlaylist.value = false
        }
    }

    fun clearError() {
        _addPlaylistError.value = null
    }

    fun togglePlaylistEnabled(playlist: Playlist, isEnabled: Boolean) {
        viewModelScope.launch {
            youtubeRepository.updatePlaylistEnabled(playlist, isEnabled)
        }
    }

    fun toggleSyncOnlyNew(playlist: Playlist, syncOnlyNew: Boolean) {
        viewModelScope.launch {
            youtubeRepository.updatePlaylistSyncOnlyNew(playlist, syncOnlyNew)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            youtubeRepository.deletePlaylist(playlist)
            if (_selectedPlaylist.value?.id == playlist.id) {
                closePlaylistDetail()
            }
        }
    }
}
