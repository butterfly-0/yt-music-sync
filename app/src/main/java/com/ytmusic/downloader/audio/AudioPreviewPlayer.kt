package com.ytmusic.downloader.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPreviewPlayer(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null

    private val _currentPlayingTrackId = MutableStateFlow<String?>(null)
    val currentPlayingTrackId: StateFlow<String?> = _currentPlayingTrackId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        initPlayer()
    }

    private fun initPlayer() {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _isPlaying.value = false
                        _currentPlayingTrackId.value = null
                    }
                }
            })
        }
    }

    fun playOrPause(trackId: String, mediaUriOrPath: String) {
        val player = exoPlayer ?: return

        if (_currentPlayingTrackId.value == trackId) {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        } else {
            player.stop()
            player.clearMediaItems()

            val uri = if (mediaUriOrPath.startsWith("content://") || mediaUriOrPath.startsWith("http://") || mediaUriOrPath.startsWith("https://")) {
                Uri.parse(mediaUriOrPath)
            } else {
                Uri.fromFile(java.io.File(mediaUriOrPath))
            }

            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()

            _currentPlayingTrackId.value = trackId
        }
    }

    fun stop() {
        exoPlayer?.stop()
        _currentPlayingTrackId.value = null
        _isPlaying.value = false
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
