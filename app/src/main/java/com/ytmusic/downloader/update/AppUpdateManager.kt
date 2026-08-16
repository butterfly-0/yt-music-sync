package com.ytmusic.downloader.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import com.ytmusic.downloader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progressPercent: Int) : UpdateState
    data class ReadyToInstall(val apkFile: File) : UpdateState
    data object UpToDate : UpdateState
    data class Error(val message: String) : UpdateState
}

data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val publishedAt: String
)

class AppUpdateManager(
    private val context: Context,
    private val repoOwner: String = "butterfly-0",
    private val repoName: String = "yt-music-sync"
) {
    private val okHttpClient = OkHttpClient()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Checking
        val apiUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Accept", "application/vnd.github.v3+json")
            .addHeader("User-Agent", "YTMusicSync-App")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                _updateState.value = UpdateState.UpToDate
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val json = JsonParser.parseString(body).asJsonObject

            val tagName = json.get("tag_name")?.asString ?: ""
            val releaseNotes = json.get("body")?.asString ?: "Оновлення додатку"
            val publishedAt = json.get("published_at")?.asString ?: ""

            val latestVersion = tagName.removePrefix("v").trim()
            val currentVersion = BuildConfig.VERSION_NAME

            // Find .apk asset
            val assets = json.getAsJsonArray("assets") ?: com.google.gson.JsonArray()
            var apkDownloadUrl: String? = null

            for (i in 0 until assets.size()) {
                val asset = assets.get(i).asJsonObject
                val name = asset.get("name")?.asString ?: ""
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkDownloadUrl = asset.get("browser_download_url")?.asString
                    break
                }
            }

            if (isNewerVersion(latestVersion, currentVersion) && !apkDownloadUrl.isNullOrBlank()) {
                val updateInfo = UpdateInfo(
                    versionName = latestVersion,
                    releaseNotes = releaseNotes,
                    downloadUrl = apkDownloadUrl,
                    publishedAt = publishedAt
                )
                _updateState.value = UpdateState.Available(updateInfo)
                updateInfo
            } else {
                _updateState.value = UpdateState.UpToDate
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _updateState.value = UpdateState.Error(e.localizedMessage ?: "Помилка перевірки оновлень")
            null
        }
    }

    suspend fun downloadAndInstallUpdate(info: UpdateInfo) = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Downloading(0)

        val updatesDir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
        val apkFile = File(updatesDir, "update_${info.versionName}.apk")

        val request = Request.Builder().url(info.downloadUrl).build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body ?: throw Exception("Порожнє тіло відповіді")
            val contentLength = body.contentLength()

            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (contentLength > 0) {
                    val percent = ((totalBytesRead * 100) / contentLength).toInt()
                    _updateState.value = UpdateState.Downloading(percent)
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            _updateState.value = UpdateState.ReadyToInstall(apkFile)
            withContext(Dispatchers.Main) {
                triggerInstall(apkFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _updateState.value = UpdateState.Error(e.localizedMessage ?: "Помилка завантаження файлу оновлення")
        }
    }

    fun triggerInstall(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
