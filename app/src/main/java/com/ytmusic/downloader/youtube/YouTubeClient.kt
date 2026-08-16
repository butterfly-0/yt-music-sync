package com.ytmusic.downloader.youtube

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class YouTubeClient(private val getCookies: () -> String) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Executes YouTube Music Innertube API request.
     */
    suspend fun postInnertube(endpoint: String, requestBody: JsonObject): JsonObject? = withContext(Dispatchers.IO) {
        val cookies = getCookies()
        val sapisid = CookieManager.getCookieValue(cookies, "SAPISID")
            ?: CookieManager.getCookieValue(cookies, "__Secure-3PAPISID")
            ?: CookieManager.getCookieValue(cookies, "__Secure-1PAPISID")

        val url = "https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false"
        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .addHeader("X-YouTube-Client-Name", "67") // WEB_REMIX
            .addHeader("X-YouTube-Client-Version", "1.20240506.01.00")
            .addHeader("X-Origin", "https://music.youtube.com")
            .addHeader("Origin", "https://music.youtube.com")
            .addHeader("Referer", "https://music.youtube.com/")
            .addHeader("X-Goog-AuthUser", "0")

        if (cookies.isNotBlank()) {
            requestBuilder.addHeader("Cookie", cookies)
        }

        if (!sapisid.isNullOrBlank()) {
            val authHeader = CookieManager.generateSapisidHash(sapisid, "https://music.youtube.com")
            requestBuilder.addHeader("Authorization", authHeader)
        }

        try {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val bodyString = response.body?.string() ?: return@withContext null
            JsonParser.parseString(bodyString).asJsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Executes Innertube request with customized Client (e.g. iOS or Android VR).
     */
    suspend fun postInnertubeWithClient(
        endpoint: String,
        requestBody: JsonObject,
        clientName: String,
        clientVersion: String
    ): JsonObject? = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false"
        val cookies = getCookies()

        val userAgent = when (clientName) {
            "IOS" -> "com.google.ios.youtube/$clientVersion (iPhone14,3; U; CPU iOS 17_5_1 like Mac OS X)"
            "ANDROID_VR" -> "Mozilla/5.0 (Quest 2) AppleWebKit/537.36 (KHTML, like Gecko) OculusBrowser/32.0.0.0"
            else -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", userAgent)
            .addHeader("X-YouTube-Client-Name", if (clientName == "IOS") "5" else "28")
            .addHeader("X-YouTube-Client-Version", clientVersion)

        if (cookies.isNotBlank()) {
            requestBuilder.addHeader("Cookie", cookies)
        }

        try {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val bodyString = response.body?.string() ?: return@withContext null
            JsonParser.parseString(bodyString).asJsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Executes standard YouTube Web Innertube API request.
     */
    suspend fun postWebInnertube(endpoint: String, requestBody: JsonObject): JsonObject? = withContext(Dispatchers.IO) {
        val cookies = getCookies()
        val sapisid = CookieManager.getCookieValue(cookies, "SAPISID")
            ?: CookieManager.getCookieValue(cookies, "__Secure-3PAPISID")
            ?: CookieManager.getCookieValue(cookies, "__Secure-1PAPISID")

        val url = "https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false"
        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .addHeader("X-YouTube-Client-Name", "1") // WEB
            .addHeader("X-YouTube-Client-Version", "2.20240506.00.00")
            .addHeader("X-Origin", "https://www.youtube.com")
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("Referer", "https://www.youtube.com/")
            .addHeader("X-Goog-AuthUser", "0")

        if (cookies.isNotBlank()) {
            requestBuilder.addHeader("Cookie", cookies)
        }

        if (!sapisid.isNullOrBlank()) {
            val authHeader = CookieManager.generateSapisidHash(sapisid, "https://www.youtube.com")
            requestBuilder.addHeader("Authorization", authHeader)
        }

        try {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val bodyString = response.body?.string() ?: return@withContext null
            JsonParser.parseString(bodyString).asJsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Executes Android Innertube API request.
     */
    suspend fun postAndroidInnertube(endpoint: String, requestBody: JsonObject): JsonObject? = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false"
        val cookies = getCookies()

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "com.google.android.youtube/19.16.39 (Linux; U; Android 14) gzip")
            .addHeader("X-YouTube-Client-Name", "3") // ANDROID
            .addHeader("X-YouTube-Client-Version", "19.16.39")

        if (cookies.isNotBlank()) {
            requestBuilder.addHeader("Cookie", cookies)
        }

        try {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val bodyString = response.body?.string() ?: return@withContext null
            JsonParser.parseString(bodyString).asJsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getHttpClient(): OkHttpClient = okHttpClient
}
