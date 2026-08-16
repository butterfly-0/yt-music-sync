package com.ytmusic.downloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ytmusic.downloader.YTMusicApp
import com.ytmusic.downloader.data.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YTMusicApp
    private val youtubeRepository = app.youtubeRepository

    val playlists: StateFlow<List<Playlist>> = youtubeRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _addPlaylistError = MutableStateFlow<String?>(null)
    val addPlaylistError: StateFlow<String?> = _addPlaylistError.asStateFlow()

    private val _isAddingPlaylist = MutableStateFlow(false)
    val isAddingPlaylist: StateFlow<Boolean> = _isAddingPlaylist.asStateFlow()

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
        }
    }
}
