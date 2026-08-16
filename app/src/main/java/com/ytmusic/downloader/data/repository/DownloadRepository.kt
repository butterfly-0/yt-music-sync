package com.ytmusic.downloader.data.repository

import android.content.Context
import com.ytmusic.downloader.audio.AudioTagger
import com.ytmusic.downloader.audio.LyricsHelper
import com.ytmusic.downloader.audio.MediaStoreHelper
import com.ytmusic.downloader.data.local.TrackDao
import com.ytmusic.downloader.data.local.TrackEntity
import com.ytmusic.downloader.data.model.AudioFormat
import com.ytmusic.downloader.data.model.DownloadStatus
import com.ytmusic.downloader.data.model.Track
import com.ytmusic.downloader.youtube.YouTubeExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DownloadRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val extractor: YouTubeExtractor,
    private val audioTagger: AudioTagger,
    private val lyricsHelper: LyricsHelper,
    private val mediaStoreHelper: MediaStoreHelper,
    private val userPreferences: com.ytmusic.downloader.data.preferences.UserPreferences
) {

    /**
     * Downloads, fetches lyrics, tags and registers a track in the system music library.
     */
    suspend fun downloadAndSaveTrack(
        track: Track,
        preferredFormat: AudioFormat,
        onProgress: (percent: Int) -> Unit = {}
    ): Result<Track> = withContext(Dispatchers.IO) {
        try {
            // 1. Get direct audio stream URL
            val streamInfo = extractor.getAudioStreamUrl(track.id, preferredFormat)
                ?: return@withContext Result.failure(Exception("Не вдалося отримати аудіопотік для: ${track.title}"))

            // 2. Prepare temporary file
            val tempDir = File(context.cacheDir, "downloads").apply { if (!exists()) mkdirs() }
            val tempRawFile = File(tempDir, "temp_raw_${track.id}.${preferredFormat.extension}")
            val tempTaggedFile = File(tempDir, "temp_tagged_${track.id}.${preferredFormat.extension}")

            // 3. Download stream
            val downloadOk = audioTagger.downloadStreamToFile(streamInfo.url, tempRawFile) { percent ->
                onProgress((percent * 0.65).toInt())
            }

            if (!downloadOk || !tempRawFile.exists()) {
                return@withContext Result.failure(Exception("Помилка завантаження аудіофайлу"))
            }

            onProgress(70)

            // 4. Download Cover art image
            val coverBytes = audioTagger.downloadCoverImage(track.thumbnailUrl)
            onProgress(80)

            // 5. Fetch Lyrics (Synced LRC and Plain Text)
            val lyricsResult = try {
                lyricsHelper.fetchLyrics(track)
            } catch (e: Exception) {
                null
            }
            onProgress(85)

            // 6. Embed ID3 tags / Cover art / Lyrics
            val currentTrack = track.copy(
                format = preferredFormat,
                durationSeconds = streamInfo.durationMs / 1000
            )

            audioTagger.processAndTagAudio(
                tempAudioFile = tempRawFile,
                finalFile = tempTaggedFile,
                track = currentTrack,
                format = preferredFormat,
                coverBytes = coverBytes,
                lyrics = lyricsResult?.plainLyrics
            )
            onProgress(90)

            // 7. Save to public MediaStore Music folder or Custom SAF Directory
            val savedUriOrPath = mediaStoreHelper.saveToMediaStore(
                sourceFile = if (tempTaggedFile.exists()) tempTaggedFile else tempRawFile,
                track = currentTrack,
                customTreeUri = userPreferences.customDownloadUri
            )

            // 8. Save .lrc lyrics file alongside audio if lyrics were found
            val lyricsContent = lyricsResult?.syncedLyrics ?: lyricsResult?.plainLyrics
            if (!lyricsContent.isNullOrBlank()) {
                try {
                    mediaStoreHelper.saveLyricsFile(
                        track = currentTrack,
                        lyricsContent = lyricsContent,
                        customTreeUri = userPreferences.customDownloadUri
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Clean temporary files
            tempRawFile.delete()
            tempTaggedFile.delete()

            if (savedUriOrPath != null) {
                val completedTrack = currentTrack.copy(
                    localFilePath = savedUriOrPath,
                    status = DownloadStatus.COMPLETED,
                    downloadedAt = System.currentTimeMillis()
                )

                // 9. Save to Room database
                trackDao.insertTrack(TrackEntity.fromDomain(completedTrack))
                onProgress(100)

                Result.success(completedTrack)
            } else {
                Result.failure(Exception("Помилка збереження файлу в системне сховище"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Deletes a downloaded track from storage and the local database.
     */
    suspend fun deleteTrack(track: Track): Boolean = withContext(Dispatchers.IO) {
        try {
            val path = track.localFilePath
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
            trackDao.deleteTrackById(track.id)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
