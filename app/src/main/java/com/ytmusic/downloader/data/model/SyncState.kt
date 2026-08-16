package com.ytmusic.downloader.data.model

sealed interface SyncState {
    data object Idle : SyncState
    data class Checking(val message: String = "Перевірка нових треків…") : SyncState
    data class Downloading(
        val currentTrackTitle: String,
        val currentTrackArtist: String,
        val currentIndex: Int,
        val totalTracks: Int,
        val progressPercent: Int = 0
    ) : SyncState
    data class Completed(val newTracksCount: Int, val timestamp: Long) : SyncState
    data class Error(val errorMessage: String) : SyncState
}
