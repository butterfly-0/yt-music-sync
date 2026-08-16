package com.ytmusic.downloader.youtube

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.ytmusic.downloader.data.model.AudioFormat
import com.ytmusic.downloader.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeExtractor(private val client: YouTubeClient) {

    /**
     * Fetches tracks from a YouTube Music / YouTube playlist (e.g. "LM", "LL", or "PL...").
     * Uses multi-endpoint fallback and robust recursive JSON parsing.
     */
    suspend fun getPlaylistTracks(
        playlistId: String,
        maxTracks: Int = 100
    ): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()

        // 1. Determine candidate browseIds
        val candidateBrowseIds = when (playlistId) {
            "LM", "FEmusic_liked_videos" -> listOf("FEmusic_liked_videos", "LM", "VLLM", "VLLL")
            "LL", "VLLL" -> listOf("VLLL", "LL", "FEmusic_liked_videos")
            else -> {
                val cleanId = playlistId.removePrefix("VL")
                listOf("VL$cleanId", cleanId)
            }
        }

        // Try YouTube Music client first
        for (browseId in candidateBrowseIds) {
            val requestBody = JsonObject().apply {
                add("context", getMusicContext())
                addProperty("browseId", browseId)
            }

            val response = client.postInnertube("browse", requestBody)
            if (response != null) {
                extractTracksRecursively(response, playlistId, tracks, maxTracks)
                if (tracks.isNotEmpty()) {
                    return@withContext tracks
                }
            }
        }

        // If YouTube Music returned 0 tracks, fallback to standard YouTube Web client
        for (browseId in candidateBrowseIds) {
            val requestBody = JsonObject().apply {
                add("context", getWebContext())
                addProperty("browseId", browseId)
            }

            val response = client.postWebInnertube("browse", requestBody)
            if (response != null) {
                extractTracksRecursively(response, playlistId, tracks, maxTracks)
                if (tracks.isNotEmpty()) {
                    return@withContext tracks
                }
            }
        }

        tracks
    }

    /**
     * Extracts direct audio stream URL for a specific video ID.
     */
    suspend fun getAudioStreamUrl(videoId: String, preferredFormat: AudioFormat = AudioFormat.M4A): AudioStreamInfo? = withContext(Dispatchers.IO) {
        // Request player data using Android client context (gives direct stream URLs without cipher)
        val playerRequestBody = JsonObject().apply {
            add("context", getAndroidContext())
            addProperty("videoId", videoId)
            addProperty("cpn", generateCpn())
            addProperty("contentCheckOk", true)
            addProperty("racyCheckOk", true)
        }

        val response = client.postAndroidInnertube("player", playerRequestBody) ?: return@withContext null

        try {
            val streamingData = response.getAsJsonObject("streamingData") ?: return@withContext null
            val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats") ?: JsonArray()

            var bestAudioUrl: String? = null
            var bestBitrate = 0
            var mimeType = "audio/mp4"

            for (i in 0 until adaptiveFormats.size()) {
                val format = adaptiveFormats.get(i).asJsonObject
                val currentMime = format.get("mimeType")?.asString ?: ""
                val bitrate = format.get("bitrate")?.asInt ?: 0
                val url = format.get("url")?.asString

                if (url != null && currentMime.startsWith("audio/")) {
                    if (preferredFormat == AudioFormat.M4A && currentMime.contains("mp4a")) {
                        if (bitrate > bestBitrate) {
                            bestBitrate = bitrate
                            bestAudioUrl = url
                            mimeType = "audio/mp4"
                        }
                    } else if (bitrate > bestBitrate) {
                        bestBitrate = bitrate
                        bestAudioUrl = url
                        mimeType = currentMime.split(";")[0]
                    }
                }
            }

            // Fallback if no matching preferred format found
            if (bestAudioUrl == null && adaptiveFormats.size() > 0) {
                for (i in 0 until adaptiveFormats.size()) {
                    val format = adaptiveFormats.get(i).asJsonObject
                    val currentMime = format.get("mimeType")?.asString ?: ""
                    val url = format.get("url")?.asString
                    if (url != null && currentMime.startsWith("audio/")) {
                        bestAudioUrl = url
                        mimeType = currentMime.split(";")[0]
                        break
                    }
                }
            }

            if (bestAudioUrl != null) {
                val durationMs = response.getAsJsonObject("videoDetails")?.get("lengthSeconds")?.asLong?.times(1000) ?: 0L
                AudioStreamInfo(
                    url = bestAudioUrl,
                    mimeType = mimeType,
                    bitrate = bestBitrate,
                    durationMs = durationMs
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetches user profile info (name, email/handle) from Innertube account menu.
     */
    suspend fun getUserProfile(): UserProfile? = withContext(Dispatchers.IO) {
        val requestBody = JsonObject().apply {
            add("context", getMusicContext())
        }
        val response = client.postInnertube("account/account_menu", requestBody) ?: return@withContext null
        try {
            val actions = response.getAsJsonArray("actions") ?: return@withContext null
            val accountSection = actions[0].asJsonObject
                .getAsJsonObject("openPopupAction")
                ?.getAsJsonObject("popup")
                ?.getAsJsonObject("multiPageMenuRenderer")
                ?.getAsJsonObject("header")
                ?.getAsJsonObject("activeAccountHeaderRenderer") ?: return@withContext null

            val name = accountSection.getAsJsonObject("accountName")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: "YouTube User"
            val email = accountSection.getAsJsonObject("accountEmail")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: ""
            UserProfile(name = name, email = email)
        } catch (e: Exception) {
            UserProfile(name = "YouTube User", email = "")
        }
    }

    /**
     * Robust recursive JSON tree walker to find track renderers regardless of nesting structure.
     */
    private fun extractTracksRecursively(
        element: JsonElement,
        playlistId: String,
        tracks: MutableList<Track>,
        maxTracks: Int
    ) {
        if (tracks.size >= maxTracks) return

        if (element.isJsonObject) {
            val obj = element.asJsonObject

            // Check if it's a YouTube Music track item
            if (obj.has("musicResponsiveListItemRenderer")) {
                val renderer = obj.getAsJsonObject("musicResponsiveListItemRenderer")
                parseMusicResponsiveItem(renderer, playlistId)?.let { track ->
                    if (tracks.none { it.id == track.id }) {
                        tracks.add(track)
                    }
                }
                return
            }

            // Check if it's a standard YouTube video item
            if (obj.has("playlistVideoRenderer")) {
                val renderer = obj.getAsJsonObject("playlistVideoRenderer")
                parsePlaylistVideoRenderer(renderer, playlistId)?.let { track ->
                    if (tracks.none { it.id == track.id }) {
                        tracks.add(track)
                    }
                }
                return
            }

            // Recurse into all object fields
            for (entry in obj.entrySet()) {
                extractTracksRecursively(entry.value, playlistId, tracks, maxTracks)
            }
        } else if (element.isJsonArray) {
            val array = element.asJsonArray
            for (i in 0 until array.size()) {
                extractTracksRecursively(array.get(i), playlistId, tracks, maxTracks)
            }
        }
    }

    private fun parseMusicResponsiveItem(item: JsonObject, playlistId: String): Track? {
        try {
            val flexColumns = item.getAsJsonArray("flexColumns") ?: return null
            if (flexColumns.size() < 2) return null

            // Column 1: Title & VideoId
            val col1 = flexColumns.get(0).asJsonObject
                .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                ?.getAsJsonObject("text")
                ?.getAsJsonArray("runs")
                ?.get(0)?.asJsonObject

            val title = col1?.get("text")?.asString ?: "Unknown Title"
            val navEndpoint = col1?.getAsJsonObject("navigationEndpoint")
                ?.getAsJsonObject("watchEndpoint")
                ?: item.getAsJsonObject("playlistItemData")
            val videoId = navEndpoint?.get("videoId")?.asString ?: return null

            // Column 2: Artist & Album
            val col2Runs = flexColumns.get(1).asJsonObject
                .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                ?.getAsJsonObject("text")
                ?.getAsJsonArray("runs") ?: JsonArray()

            var artist = "Unknown Artist"
            var album = "YouTube Music"

            if (col2Runs.size() > 0) {
                artist = col2Runs.get(0).asJsonObject.get("text")?.asString ?: "Unknown Artist"
            }
            if (col2Runs.size() > 2) {
                album = col2Runs.get(2).asJsonObject.get("text")?.asString ?: "YouTube Music"
            }

            // Thumbnail
            val thumbnails = item.getAsJsonObject("thumbnail")
                ?.getAsJsonObject("musicThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")

            var thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            if (thumbnails != null && thumbnails.size() > 0) {
                val lastThumb = thumbnails.get(thumbnails.size() - 1).asJsonObject
                thumbnailUrl = lastThumb.get("url")?.asString ?: thumbnailUrl
            }

            thumbnailUrl = getHighResThumbnailUrl(videoId, thumbnailUrl)

            return Track(
                id = videoId,
                title = title,
                artist = artist,
                album = album,
                thumbnailUrl = thumbnailUrl,
                sourcePlaylistId = playlistId,
                downloadedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parsePlaylistVideoRenderer(item: JsonObject, playlistId: String): Track? {
        try {
            val videoId = item.get("videoId")?.asString ?: return null
            val title = item.getAsJsonObject("title")
                ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                ?: item.getAsJsonObject("title")?.get("simpleText")?.asString
                ?: "Unknown Title"

            val artist = item.getAsJsonObject("shortBylineText")
                ?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                ?: "YouTube Artist"

            val thumbnails = item.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
            var thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            if (thumbnails != null && thumbnails.size() > 0) {
                thumbnailUrl = thumbnails.get(thumbnails.size() - 1).asJsonObject.get("url")?.asString ?: thumbnailUrl
            }

            return Track(
                id = videoId,
                title = title,
                artist = artist,
                album = "YouTube",
                thumbnailUrl = getHighResThumbnailUrl(videoId, thumbnailUrl),
                sourcePlaylistId = playlistId,
                downloadedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun getHighResThumbnailUrl(videoId: String, originalUrl: String): String {
        return if (originalUrl.contains("googleusercontent.com")) {
            originalUrl.replace(Regex("=w\\d+-h\\d+.*"), "=w600-h600-l90-rj")
        } else {
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        }
    }

    private fun getMusicContext(): JsonObject {
        return JsonObject().apply {
            val clientObj = JsonObject().apply {
                addProperty("clientName", "WEB_REMIX")
                addProperty("clientVersion", "1.20240506.01.00")
                addProperty("hl", "uk")
                addProperty("gl", "UA")
            }
            add("client", clientObj)
        }
    }

    private fun getWebContext(): JsonObject {
        return JsonObject().apply {
            val clientObj = JsonObject().apply {
                addProperty("clientName", "WEB")
                addProperty("clientVersion", "2.20240506.00.00")
                addProperty("hl", "uk")
                addProperty("gl", "UA")
            }
            add("client", clientObj)
        }
    }

    private fun getAndroidContext(): JsonObject {
        return JsonObject().apply {
            val clientObj = JsonObject().apply {
                addProperty("clientName", "ANDROID")
                addProperty("clientVersion", "19.16.39")
                addProperty("androidSdkVersion", 34)
                addProperty("hl", "uk")
                addProperty("gl", "UA")
            }
            add("client", clientObj)
        }
    }

    private fun generateCpn(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        return (1..16).map { chars.random() }.joinToString("")
    }
}

data class AudioStreamInfo(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val durationMs: Long
)

data class UserProfile(
    val name: String,
    val email: String
)
