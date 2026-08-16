package com.ytmusic.downloader.audio

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ytmusic.downloader.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MediaStoreHelper(private val context: Context) {

    /**
     * Saves audio file to system Music directory and indexes it via MediaStore.
     */
    suspend fun saveToMediaStore(
        sourceFile: File,
        track: Track,
        subFolder: String = "YouTubeSync"
    ): String? = withContext(Dispatchers.IO) {
        val fileName = sanitizeFileName("${track.artist} - ${track.title}.${track.format.extension}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ Scoped Storage using MediaStore ContentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.TITLE, track.title)
                put(MediaStore.Audio.Media.ARTIST, track.artist)
                put(MediaStore.Audio.Media.ALBUM, track.album)
                put(MediaStore.Audio.Media.MIME_TYPE, track.format.mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$subFolder")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val audioUri: Uri? = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (audioUri != null) {
                try {
                    resolver.openOutputStream(audioUri)?.use { outputStream ->
                        FileInputStream(sourceFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(audioUri, contentValues, null, null)

                    audioUri.toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } else {
                null
            }
        } else {
            // Android 9 and lower: direct file write to Environment.DIRECTORY_MUSIC
            try {
                val musicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    subFolder
                )
                if (!musicDir.exists()) {
                    musicDir.mkdirs()
                }
                val destFile = File(musicDir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)

                // Scan file to make it immediately visible
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf(track.format.mimeType),
                    null
                )

                destFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
    }
}
