package com.ytmusic.downloader.audio

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ytmusic.downloader.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class LyricsHelper(private val okHttpClient: OkHttpClient) {

    /**
     * Fetches lyrics for a track using LRCLIB API and YouTube Music Innertube.
     * Returns paired Synced LRC (if available) and Plain Lyrics text.
     */
    suspend fun fetchLyrics(track: Track): LyricsResult? = withContext(Dispatchers.IO) {
        // 1. Try LRCLIB (free open lyrics database with synced timestamps)
        try {
            val trackClean = cleanTitle(track.title)
            val artistClean = cleanArtist(track.artist)
            val encodedTrack = URLEncoder.encode(trackClean, "UTF-8")
            val encodedArtist = URLEncoder.encode(artistClean, "UTF-8")

            val url = "https://lrclib.net/api/get?track_name=$encodedTrack&artist_name=$encodedArtist"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "YTMusicSync/1.0.4 (https://github.com/butterfly-0/yt-music-sync)")
                .build()

            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JsonParser.parseString(body).asJsonObject
                    val syncedLyrics = json.get("syncedLyrics")?.let { if (it.isJsonNull) null else it.asString }
                    val plainLyrics = json.get("plainLyrics")?.let { if (it.isJsonNull) null else it.asString }

                    if (!syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank()) {
                        return@withContext LyricsResult(
                            syncedLyrics = syncedLyrics,
                            plainLyrics = plainLyrics ?: syncedLyrics
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Search fallback on LRCLIB
        try {
            val query = URLEncoder.encode("${track.artist} ${track.title}", "UTF-8")
            val url = "https://lrclib.net/api/search?q=$query"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "YTMusicSync/1.0.4")
                .build()

            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val arr = JsonParser.parseString(body).asJsonArray
                    if (arr.size() > 0) {
                        val first = arr[0].asJsonObject
                        val syncedLyrics = first.get("syncedLyrics")?.let { if (it.isJsonNull) null else it.asString }
                        val plainLyrics = first.get("plainLyrics")?.let { if (it.isJsonNull) null else it.asString }
                        if (!syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank()) {
                            return@withContext LyricsResult(
                                syncedLyrics = syncedLyrics,
                                plainLyrics = plainLyrics ?: syncedLyrics
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        null
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\(official\\s*(video|audio|music\\s*video|lyric\\s*video|hd|4k)?\\)"), "")
            .replace(Regex("(?i)\\[official\\s*(video|audio|music\\s*video|lyric\\s*video|hd|4k)?\\]"), "")
            .replace(Regex("(?i)\\(lyrics?\\)"), "")
            .replace(Regex("(?i)\\[lyrics?\\]"), "")
            .replace(Regex("(?i)\\(audio\\)"), "")
            .replace(Regex("(?i)\\[audio\\]"), "")
            .trim()
    }

    private fun cleanArtist(artist: String): String {
        return artist
            .replace(Regex("(?i) - Topic$"), "")
            .replace(Regex("(?i)VEVO$"), "")
            .trim()
    }
}

data class LyricsResult(
    val syncedLyrics: String?,
    val plainLyrics: String?
)
