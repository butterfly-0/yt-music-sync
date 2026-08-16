package com.ytmusic.downloader.data.model

data class Playlist(
    val id: String, // Playlist ID, e.g., "LL" for Liked, or "PL..."
    val title: String,
    val url: String,
    val isLikedMusic: Boolean = false,
    val isEnabled: Boolean = true,
    val syncOnlyNew: Boolean = true,
    val lastSyncedAt: Long = 0,
    val trackCount: Int = 0,
    val thumbnailUrl: String? = null
)
