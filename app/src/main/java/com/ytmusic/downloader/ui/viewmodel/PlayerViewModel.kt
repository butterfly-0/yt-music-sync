package com.ytmusic.downloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ytmusic.downloader.YTMusicApp
import com.ytmusic.downloader.data.model.Track
import com.ytmusic.downloader.player.LyricLine
import com.ytmusic.downloader.player.MusicPlayerManager
import com.ytmusic.downloader.player.PlayerRepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter {
    ALL,
    ARTISTS,
    PLAYLISTS,
    RECENT
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YTMusicApp
    private val trackDao = app.database.trackDao()
    private val playlistDao = app.database.playlistDao()
    val playerManager: MusicPlayerManager = app.musicPlayerManager

    val currentTrack: StateFlow<Track?> = playerManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPositionMs: StateFlow<Long> = playerManager.currentPositionMs
    val durationMs: StateFlow<Long> = playerManager.durationMs
    val progress: StateFlow<Float> = playerManager.progress
    val isShuffle: StateFlow<Boolean> = playerManager.isShuffle
    val repeatMode: StateFlow<PlayerRepeatMode> = playerManager.repeatMode
    val lyrics: StateFlow<List<LyricLine>> = playerManager.lyrics
    val currentLyricIndex: StateFlow<Int> = playerManager.currentLyricIndex
    val isLoadingLyrics: StateFlow<Boolean> = playerManager.isLoadingLyrics

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow(LibraryFilter.ALL)
    val activeFilter: StateFlow<LibraryFilter> = _activeFilter.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    private val allDownloadedTracksFlow = trackDao.getAllTracks().map { list -> list.map { it.toDomain() } }

    val filteredTracks: StateFlow<List<Track>> = combine(
        allDownloadedTracksFlow,
        _searchQuery,
        _activeFilter
    ) { tracks, query, filter ->
        var list = if (query.isBlank()) {
            tracks
        } else {
            tracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }

        when (filter) {
            LibraryFilter.ALL -> list
            LibraryFilter.RECENT -> list.sortedByDescending { it.downloadedAt }
            LibraryFilter.ARTISTS -> list.sortedBy { it.artist }
            LibraryFilter.PLAYLISTS -> list.sortedBy { it.album }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalTracksCount: StateFlow<Int> = allDownloadedTracksFlow
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun setFilter(filter: LibraryFilter) {
        _activeFilter.value = filter
    }

    fun expandNowPlaying() {
        if (currentTrack.value != null || filteredTracks.value.isNotEmpty()) {
            _isNowPlayingExpanded.value = true
        }
    }

    fun collapseNowPlaying() {
        _isNowPlayingExpanded.value = false
    }

    fun playTrack(track: Track) {
        val currentList = filteredTracks.value
        playerManager.playTrack(track, currentList)
    }

    fun playAll(shuffle: Boolean = false) {
        val currentList = filteredTracks.value
        if (currentList.isNotEmpty()) {
            playerManager.playQueue(currentList, startIndex = 0, shuffle = shuffle)
        }
    }

    fun playOrPause() {
        playerManager.playOrPause()
    }

    fun next() {
        playerManager.next()
    }

    fun previous() {
        playerManager.previous()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun toggleRepeat() {
        playerManager.toggleRepeat()
    }

    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            app.downloadRepository.deleteTrack(track)
            if (currentTrack.value?.id == track.id) {
                playerManager.next()
            }
        }
    }
}
