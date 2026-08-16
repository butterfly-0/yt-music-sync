package com.ytmusic.downloader.youtube

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.ytmusic.downloader.data.model.AudioFormat
import com.ytmusic.downloader.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

class YouTubeExtractor(private val client: YouTubeClient) {

    /**
     * Fetches tracks from a YouTube Music playlist (e.g. "LM" for Liked Music, "LL", or "VLPL...").
     */
    suspend fun getPlaylistTracks(
        playlistId: String,
        maxTracks: Int = 100
    ): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val browseId = if (playlistId.startsWith("VL") || playlistId == "LM" || playlistId == "LL") {
            playlistId
        } else {
            "VL$playlistId"
        }

        val requestBody = JsonObject().apply {
            add("context", getMusicContext())
            addProperty("browseId", browseId)
        }

        val response = client.postInnertube("browse", requestBody) ?: return@withContext emptyList()

        try {
            parsePlaylistResponse(response, playlistId, tracks, maxTracks)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        tracks
    }

    /**
     * Extracts audio stream URL for a specific video ID.
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

    private fun parsePlaylistResponse(
        root: JsonObject,
        playlistId: String,
        tracks: MutableList<Track>,
        maxTracks: Int
    ) {
        val contents = root.getAsJsonObject("contents")
            ?.getAsJsonObject("singleColumnBrowseResultsRenderer")
            ?.getAsJsonArray("tabs")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("tabRenderer")
            ?.getAsJsonObject("content")
            ?.getAsJsonObject("sectionListRenderer")
            ?.getAsJsonArray("contents") ?: return

        for (i in 0 until contents.size()) {
            val section = contents.get(i).asJsonObject
            val musicShelf = section.getAsJsonObject("musicShelfRenderer")
                ?: section.getAsJsonObject("musicPlaylistShelfRenderer")
                ?: continue

            val items = musicShelf.getAsJsonArray("contents") ?: continue
            for (j in 0 until items.size()) {
                if (tracks.size >= maxTracks) break

                val item = items.get(j).asJsonObject
                val trackRenderer = item.getAsJsonObject("musicResponsiveListItemRenderer") ?: continue
                val track = parseTrackItem(trackRenderer, playlistId)
                if (track != null) {
                    tracks.add(track)
                }
            }
        }
    }

    private fun parseTrackItem(item: JsonObject, playlistId: String): Track? {
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

            // Clean thumbnail url (upgrade to HD)
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

    private fun getHighResThumbnailUrl(videoId: String, originalUrl: String): String {
        return if (originalUrl.contains("googleusercontent.com")) {
            // Upgrade Google CDN image dimensions (e.g. =w120-h120 -> =w544-h544)
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
