package com.ytmusic.downloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ytmusic.downloader.YTMusicApp
import com.ytmusic.downloader.data.model.SyncState
import com.ytmusic.downloader.data.model.Track
import com.ytmusic.downloader.worker.SyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YTMusicApp
    private val trackDao = app.database.trackDao()
    private val downloadRepo = app.downloadRepository
    private val previewPlayer = app.audioPreviewPlayer
    val userPrefs = app.userPreferences

    val syncState: StateFlow<SyncState> = SyncWorker.syncStateFlow

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val tracks: StateFlow<List<Track>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                trackDao.getAllTracks().map { list -> list.map { it.toDomain() } }
            } else {
                trackDao.searchTracks(query).map { list -> list.map { it.toDomain() } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalTrackCount: StateFlow<Int> = trackDao.getTrackCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val currentPlayingTrackId: StateFlow<String?> = app.musicPlayerManager.currentTrack
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPlaying: StateFlow<Boolean> = app.musicPlayerManager.isPlaying

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun startSync() {
        SyncWorker.enqueueOneTimeSync(getApplication())
    }

    fun togglePlayPreview(track: Track) {
        if (app.musicPlayerManager.currentTrack.value?.id == track.id) {
            app.musicPlayerManager.playOrPause()
        } else {
            app.musicPlayerManager.playTrack(track, tracks.value)
        }
    }

    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            downloadRepo.deleteTrack(track)
            if (app.musicPlayerManager.currentTrack.value?.id == track.id) {
                app.musicPlayerManager.next()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
