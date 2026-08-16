package com.ytmusic.downloader.audio

import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import com.ytmusic.downloader.data.model.AudioFormat
import com.ytmusic.downloader.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class AudioTagger {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Downloads direct audio stream from Googlevideo / Cobalt CDN to local file.
     */
    suspend fun downloadStreamToFile(
        streamUrl: String,
        targetFile: File,
        onProgress: (Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(streamUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Quest 2) AppleWebKit/537.36")
            .addHeader("Accept-Encoding", "identity")
            .addHeader("Range", "bytes=0-")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()

            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(16 * 1024)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (contentLength > 0) {
                    val percent = ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                    onProgress(percent)
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            targetFile.exists() && targetFile.length() > 1024
        } catch (e: Exception) {
            e.printStackTrace()
            targetFile.delete()
            false
        }
    }

    /**
     * Downloads album cover image as byte array.
     */
    suspend fun downloadCoverImage(imageUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(imageUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Embeds ID3 tags, lyrics, and HD Album Art into an MP3 file.
     */
    suspend fun embedMp3Tags(
        sourceFile: File,
        destinationFile: File,
        track: Track,
        coverBytes: ByteArray?,
        lyrics: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val mp3file = Mp3File(sourceFile)
            val id3v2Tag = if (mp3file.hasId3v2Tag()) {
                mp3file.id3v2Tag
            } else {
                val tag = ID3v24Tag()
                mp3file.id3v2Tag = tag
                tag
            }

            id3v2Tag.title = track.title
            id3v2Tag.artist = track.artist
            id3v2Tag.album = track.album

            if (!lyrics.isNullOrBlank()) {
                id3v2Tag.lyrics = lyrics
                id3v2Tag.comment = lyrics
            }

            if (coverBytes != null && coverBytes.isNotEmpty()) {
                id3v2Tag.setAlbumImage(coverBytes, "image/jpeg")
            }

            mp3file.save(destinationFile.absolutePath)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                sourceFile.copyTo(destinationFile, overwrite = true)
                true
            } catch (ex: Exception) {
                false
            }
        }
    }

    /**
     * Prepares track file by applying formatting, metadata, lyrics and tags.
     */
    suspend fun processAndTagAudio(
        tempAudioFile: File,
        finalFile: File,
        track: Track,
        format: AudioFormat,
        coverBytes: ByteArray?,
        lyrics: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (format == AudioFormat.MP3) {
            embedMp3Tags(tempAudioFile, finalFile, track, coverBytes, lyrics)
        } else {
            try {
                tempAudioFile.copyTo(finalFile, overwrite = true)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
