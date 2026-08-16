package com.ytmusic.downloader.audio

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.ytmusic.downloader.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MediaStoreHelper(private val context: Context) {

    /**
     * Saves audio file to system Music directory or custom SAF directory and indexes it.
     */
    suspend fun saveToMediaStore(
        sourceFile: File,
        track: Track,
        customTreeUri: String? = null,
        subFolder: String = "YouTubeSync"
    ): String? = withContext(Dispatchers.IO) {
        val fileName = sanitizeFileName("${track.artist} - ${track.title}.${track.format.extension}")

        // 1. Custom User-Selected SAF Directory
        if (!customTreeUri.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(customTreeUri)
                val targetDir = DocumentFile.fromTreeUri(context, treeUri)
                if (targetDir != null && targetDir.canWrite()) {
                    // Check if file already exists in custom dir
                    targetDir.findFile(fileName)?.delete()
                    val newFile = targetDir.createFile(track.format.mimeType, fileName)
                    if (newFile != null) {
                        context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                            FileInputStream(sourceFile).use { input ->
                                input.copyTo(out)
                            }
                        }
                        return@withContext newFile.uri.toString()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Default Public MediaStore Music Folder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            // Android 9 and lower
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

    /**
     * Saves lyrics (.lrc file) alongside the audio file.
     */
    suspend fun saveLyricsFile(
        track: Track,
        lyricsContent: String,
        customTreeUri: String? = null,
        subFolder: String = "YouTubeSync"
    ) = withContext(Dispatchers.IO) {
        if (lyricsContent.isBlank()) return@withContext
        val fileName = sanitizeFileName("${track.artist} - ${track.title}.lrc")

        // 1. Custom SAF Directory
        if (!customTreeUri.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(customTreeUri)
                val targetDir = DocumentFile.fromTreeUri(context, treeUri)
                if (targetDir != null && targetDir.canWrite()) {
                    targetDir.findFile(fileName)?.delete()
                    val newFile = targetDir.createFile("text/plain", fileName)
                    if (newFile != null) {
                        context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                            out.write(lyricsContent.toByteArray(Charsets.UTF_8))
                        }
                    }
                    return@withContext
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Local File in Music Directory (Android 9 or direct storage)
        try {
            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                subFolder
            ).apply { if (!exists()) mkdirs() }
            val lrcFile = File(musicDir, fileName)
            FileOutputStream(lrcFile).use { out ->
                out.write(lyricsContent.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Opens the music storage folder in the system file manager.
     */
    fun openMusicFolder(customTreeUri: String? = null) {
        if (!customTreeUri.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(customTreeUri)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(treeUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(Intent.createChooser(intent, "Відкрити папку з музикою"))
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback: Open system Downloads / Music file manager
        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "vnd.android.cursor.dir/audio")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(intent, "Відкрити аудіофайли"))
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
    }
}
