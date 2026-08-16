package com.ytmusic.downloader.data.model

enum class AudioFormat(val extension: String, val mimeType: String, val displayName: String) {
    M4A("m4a", "audio/mp4", "M4A (256 kbps AAC)"),
    MP3("mp3", "audio/mpeg", "MP3 (320 kbps)")
}

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class Track(
    val id: String, // YouTube Video ID
    val title: String,
    val artist: String,
    val album: String = "YouTube Music",
    val durationSeconds: Long = 0,
    val thumbnailUrl: String,
    val localFilePath: String? = null,
    val format: AudioFormat = AudioFormat.M4A,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val downloadedAt: Long = System.currentTimeMillis(),
    val sourcePlaylistId: String = "LL"
)
