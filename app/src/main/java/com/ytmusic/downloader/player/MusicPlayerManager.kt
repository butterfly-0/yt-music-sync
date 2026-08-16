package com.ytmusic.downloader.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ytmusic.downloader.audio.LyricsHelper
import com.ytmusic.downloader.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class PlayerRepeatMode {
    OFF,
    ALL,
    ONE
}

class MusicPlayerManager(
    private val context: Context,
    private val lyricsHelper: LyricsHelper
) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
        shuffleModeEnabled = false
    }

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(PlayerRepeatMode.OFF)
    val repeatMode: StateFlow<PlayerRepeatMode> = _repeatMode.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    _durationMs.value = dur
                } else if (playbackState == Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }
        })
    }

    fun playTrack(track: Track, newQueue: List<Track> = listOf(track)) {
        val targetQueue = if (newQueue.isEmpty()) listOf(track) else newQueue
        _queue.value = targetQueue
        _currentIndex.value = targetQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        startTrackPlayback(track)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val queueList = if (shuffle) tracks.shuffled() else tracks
        _isShuffle.value = shuffle
        _queue.value = queueList
        val validIndex = startIndex.coerceIn(0, queueList.size - 1)
        _currentIndex.value = validIndex
        startTrackPlayback(queueList[validIndex])
    }

    private fun startTrackPlayback(track: Track) {
        _currentTrack.value = track
        _currentPositionMs.value = 0L
        _progress.value = 0f
        _durationMs.value = if (track.durationSeconds > 0) track.durationSeconds * 1000L else 0L

        // Prepare MediaItem from local path or URI
        val uri = getTrackUri(track)
        if (uri != null) {
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }

        // Load synced lyrics
        loadLyrics(track)
    }

    fun playOrPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (_currentTrack.value != null) {
                exoPlayer.play()
            } else if (_queue.value.isNotEmpty()) {
                val index = _currentIndex.value.coerceIn(0, _queue.value.size - 1)
                startTrackPlayback(_queue.value[index])
            }
        }
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun next() {
        val currentQ = _queue.value
        if (currentQ.isEmpty()) return

        var nextIdx = _currentIndex.value + 1
        if (nextIdx >= currentQ.size) {
            if (_repeatMode.value == PlayerRepeatMode.ALL) {
                nextIdx = 0
            } else {
                return
            }
        }
        _currentIndex.value = nextIdx
        startTrackPlayback(currentQ[nextIdx])
    }

    fun previous() {
        if (exoPlayer.currentPosition > 3000L) {
            exoPlayer.seekTo(0L)
            _currentPositionMs.value = 0L
            return
        }

        val currentQ = _queue.value
        if (currentQ.isEmpty()) return

        var prevIdx = _currentIndex.value - 1
        if (prevIdx < 0) {
            prevIdx = if (_repeatMode.value == PlayerRepeatMode.ALL) currentQ.size - 1 else 0
        }
        _currentIndex.value = prevIdx
        startTrackPlayback(currentQ[prevIdx])
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _currentPositionMs.value = positionMs
        val dur = _durationMs.value
        if (dur > 0) {
            _progress.value = (positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        }
        _currentLyricIndex.value = LrcParser.findActiveLyricIndex(_lyrics.value, positionMs)
    }

    fun toggleShuffle() {
        val newShuffle = !_isShuffle.value
        _isShuffle.value = newShuffle
        val curTrack = _currentTrack.value
        val curList = _queue.value
        if (curList.isNotEmpty() && curTrack != null) {
            val rearranged = if (newShuffle) {
                listOf(curTrack) + (curList.filter { it.id != curTrack.id }).shuffled()
            } else {
                curList.sortedBy { it.downloadedAt }
            }
            _queue.value = rearranged
            _currentIndex.value = rearranged.indexOfFirst { it.id == curTrack.id }.coerceAtLeast(0)
        }
    }

    fun toggleRepeat() {
        val nextMode = when (_repeatMode.value) {
            PlayerRepeatMode.OFF -> PlayerRepeatMode.ALL
            PlayerRepeatMode.ALL -> PlayerRepeatMode.ONE
            PlayerRepeatMode.ONE -> PlayerRepeatMode.OFF
        }
        _repeatMode.value = nextMode
        exoPlayer.repeatMode = when (nextMode) {
            PlayerRepeatMode.ONE -> Player.REPEAT_MODE_ONE
            PlayerRepeatMode.ALL -> Player.REPEAT_MODE_ALL
            PlayerRepeatMode.OFF -> Player.REPEAT_MODE_OFF
        }
    }

    private fun handleTrackEnded() {
        if (_repeatMode.value == PlayerRepeatMode.ONE) {
            seekTo(0L)
            exoPlayer.play()
        } else {
            next()
        }
    }

    private fun loadLyrics(track: Track) {
        _lyrics.value = emptyList()
        _currentLyricIndex.value = -1
        _isLoadingLyrics.value = true

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Try reading .lrc file next to local file if available
                var foundLyrics: String? = null
                val localPath = track.localFilePath
                if (!localPath.isNullOrBlank() && !localPath.startsWith("content://")) {
                    val audioFile = File(localPath)
                    val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
                    if (lrcFile.exists()) {
                        foundLyrics = lrcFile.readText()
                    }
                }

                // 2. Fetch from LRCLIB if not found locally
                if (foundLyrics.isNullOrBlank()) {
                    val result = lyricsHelper.fetchLyrics(track)
                    foundLyrics = result?.syncedLyrics ?: result?.plainLyrics
                }

                val parsed = LrcParser.parse(foundLyrics)
                _lyrics.value = parsed
                _currentLyricIndex.value = LrcParser.findActiveLyricIndex(parsed, exoPlayer.currentPosition)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingLyrics.value = false
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration.coerceAtLeast(0L)
                _currentPositionMs.value = pos
                if (dur > 0L) {
                    _durationMs.value = dur
                    _progress.value = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                }
                _currentLyricIndex.value = LrcParser.findActiveLyricIndex(_lyrics.value, pos)
                delay(200)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun getTrackUri(track: Track): Uri? {
        val path = track.localFilePath ?: return null
        return if (path.startsWith("content://") || path.startsWith("file://")) {
            Uri.parse(path)
        } else {
            Uri.fromFile(File(path))
        }
    }

    fun release() {
        stopProgressTracker()
        exoPlayer.release()
    }
}
