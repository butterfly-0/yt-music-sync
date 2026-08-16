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

    val currentPlayingTrackId: StateFlow<String?> = previewPlayer.currentPlayingTrackId
    val isPlaying: StateFlow<Boolean> = previewPlayer.isPlaying

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
            downloadRepo.downloadAndSaveTrack(track, userPrefs.audioFormat)
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
