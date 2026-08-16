package com.ytmusic.downloader.youtube

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ytmusic.downloader.data.model.AudioFormat
import com.ytmusic.downloader.data.model.Playlist
import com.ytmusic.downloader.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class YouTubeExtractor(private val client: YouTubeClient) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Fetches only real music playlists created or saved by the user.
     */
    suspend fun getUserPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val playlists = mutableListOf<Playlist>()

        // 1. Add Default Pinned "Liked Music" Playlist
        playlists.add(
            Playlist(
                id = "LM",
                title = "Вподобана музика (YouTube Music)",
                url = "https://music.youtube.com/playlist?list=LM",
                isLikedMusic = true,
                isEnabled = true,
                syncOnlyNew = true,
                trackCount = 0,
                lastSyncedAt = 0,
                thumbnailUrl = null
            )
        )

        // 2. Query YouTube Music Liked/Saved Playlists Endpoints (Strictly user's playlists, never artists/channels)
        val candidateEndpoints = listOf(
            "FEmusic_liked_playlists",
            "FEplaylist_aggregated"
        )

        for (browseId in candidateEndpoints) {
            val requestBody = JsonObject().apply {
                add("context", getMusicContext())
                addProperty("browseId", browseId)
            }

            val response = client.postInnertube("browse", requestBody)
            if (response != null) {
                extractPlaylistsRecursively(response, playlists)
            }
        }

        // 3. YouTube Web Playlists Aggregated Library
        val webLibrary = client.postWebInnertube("browse", JsonObject().apply {
            add("context", getWebContext())
            addProperty("browseId", "FEplaylist_aggregated")
        })
        if (webLibrary != null) {
            extractPlaylistsRecursively(webLibrary, playlists)
        }

        playlists.filter { isValidPlaylist(it) }.distinctBy { it.id }
    }

    /**
     * Fetches tracks from a YouTube Music / YouTube playlist.
     */
    suspend fun getPlaylistTracks(
        playlistId: String,
        maxTracks: Int = 200
    ): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()

        val candidateBrowseIds = when (playlistId) {
            "LM", "FEmusic_liked_videos" -> listOf("FEmusic_liked_videos", "LM", "VLLM")
            else -> {
                val cleanId = playlistId.removePrefix("VL")
                listOf("VL$cleanId", cleanId)
            }
        }

        // Try YouTube Music client
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

        // Fallback to standard YouTube Web client
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
     * Extracts direct audio stream URL with multi-client fallbacks (Android VR, Android, Cobalt, Piped).
     */
    suspend fun getAudioStreamUrl(videoId: String, preferredFormat: AudioFormat = AudioFormat.M4A): AudioStreamInfo? = withContext(Dispatchers.IO) {
        // 1. Try Android VR Context (Verified direct googlevideo unthrottled streams)
        try {
            val vrRequestBody = JsonObject().apply {
                add("context", getAndroidVrContext())
                addProperty("videoId", videoId)
                addProperty("cpn", generateCpn())
                addProperty("contentCheckOk", true)
                addProperty("racyCheckOk", true)
            }
            val vrResponse = client.postInnertubeWithClient("player", vrRequestBody, "ANDROID_VR", "1.43.32")
            val vrStream = extractStreamFromPlayerResponse(vrResponse, preferredFormat)
            if (vrStream != null) return@withContext vrStream
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Try Standard Android Context
        try {
            val androidRequestBody = JsonObject().apply {
                add("context", getAndroidContext())
                addProperty("videoId", videoId)
                addProperty("cpn", generateCpn())
                addProperty("contentCheckOk", true)
                addProperty("racyCheckOk", true)
            }
            val androidResponse = client.postInnertubeWithClient("player", androidRequestBody, "ANDROID", "19.29.37")
            val androidStream = extractStreamFromPlayerResponse(androidResponse, preferredFormat)
            if (androidStream != null) return@withContext androidStream
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Try Cobalt API
        val cobaltInstances = listOf(
            "https://api.cobalt.tools",
            "https://cobalt.api.timelessoses.moe",
            "https://co.wuk.sh/api/json"
        )
        for (cobaltUrl in cobaltInstances) {
            try {
                val jsonPayload = JsonObject().apply {
                    addProperty("url", "https://www.youtube.com/watch?v=$videoId")
                    addProperty("downloadMode", "audio")
                    addProperty("audioFormat", "best")
                }
                val req = Request.Builder()
                    .url(if (cobaltUrl.endsWith("/api/json")) cobaltUrl else "$cobaltUrl/")
                    .post(jsonPayload.toString().toRequestBody(jsonMediaType))
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val resp = client.getHttpClient().newCall(req).execute()
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JsonParser.parseString(body).asJsonObject
                    val streamUrl = json.get("url")?.asString
                    if (!streamUrl.isNullOrBlank()) {
                        return@withContext AudioStreamInfo(
                            url = streamUrl,
                            mimeType = "audio/mp4",
                            bitrate = 256000,
                            durationMs = 0L
                        )
                    }
                }
            } catch (e: Exception) {
                // Continue
            }
        }

        // 5. Try Piped API Instances Fallback
        val pipedInstances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.private.coffee",
            "https://pipedapi.tokhmi.xyz"
        )
        for (instance in pipedInstances) {
            try {
                val pipedUrl = "$instance/streams/$videoId"
                val req = Request.Builder().url(pipedUrl).build()
                val resp = client.getHttpClient().newCall(req).execute()
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JsonParser.parseString(body).asJsonObject
                    val audioStreams = json.getAsJsonArray("audioStreams") ?: JsonArray()
                    for (i in 0 until audioStreams.size()) {
                        val stream = audioStreams[i].asJsonObject
                        val url = stream.get("url")?.asString
                        val mimeType = stream.get("mimeType")?.asString ?: "audio/mp4"
                        val bitrate = stream.get("bitrate")?.asInt ?: 128000
                        if (url != null) {
                            return@withContext AudioStreamInfo(
                                url = url,
                                mimeType = mimeType,
                                bitrate = bitrate,
                                durationMs = json.get("duration")?.asLong?.times(1000) ?: 0L
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Continue
            }
        }

        null
    }

    private fun extractStreamFromPlayerResponse(response: JsonObject?, preferredFormat: AudioFormat): AudioStreamInfo? {
        if (response == null) return null
        try {
            val streamingData = response.getAsJsonObject("streamingData") ?: return null
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

            if (bestAudioUrl == null && adaptiveFormats.size() > 0) {
                for (i in 0 until adaptiveFormats.size()) {
                    val format = adaptiveFormats.get(i).asJsonObject
                    val currentMime = format.get("mimeType")?.asString ?: ""
                    val url = format.get("url")?.asString
                    if (url != null && currentMime.startsWith("audio/")) {
                        bestAudioUrl = url
                        mimeType = currentMime.split(";")[0]
                        bestBitrate = format.get("bitrate")?.asInt ?: 128000
                        break
                    }
                }
            }

            if (bestAudioUrl != null) {
                val durationMs = response.getAsJsonObject("videoDetails")?.get("lengthSeconds")?.asLong?.times(1000) ?: 0L
                return AudioStreamInfo(
                    url = bestAudioUrl,
                    mimeType = mimeType,
                    bitrate = bestBitrate,
                    durationMs = durationMs
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

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

    private fun extractPlaylistsRecursively(element: JsonElement, playlists: MutableList<Playlist>) {
        if (element.isJsonObject) {
            val obj = element.asJsonObject

            // 1. YouTube Music Standard Two-Row Item
            if (obj.has("musicTwoRowItemRenderer")) {
                val renderer = obj.getAsJsonObject("musicTwoRowItemRenderer")
                parseMusicTwoRowItemPlaylist(renderer)?.let {
                    if (isValidPlaylist(it) && playlists.none { p -> p.id == it.id }) {
                        playlists.add(it)
                    }
                }
                return
            }

            // 2. YouTube Music List Item Renderer
            if (obj.has("musicResponsiveListItemRenderer")) {
                val renderer = obj.getAsJsonObject("musicResponsiveListItemRenderer")
                parseMusicResponsiveListItemPlaylist(renderer)?.let {
                    if (isValidPlaylist(it) && playlists.none { p -> p.id == it.id }) {
                        playlists.add(it)
                    }
                }
            }

            // 3. YouTube Grid & General Playlist Renderers
            if (obj.has("gridPlaylistRenderer")) {
                val renderer = obj.getAsJsonObject("gridPlaylistRenderer")
                parseGridPlaylist(renderer)?.let {
                    if (isValidPlaylist(it) && playlists.none { p -> p.id == it.id }) {
                        playlists.add(it)
                    }
                }
                return
            }

            if (obj.has("playlistRenderer")) {
                val renderer = obj.getAsJsonObject("playlistRenderer")
                parseGeneralPlaylist(renderer)?.let {
                    if (isValidPlaylist(it) && playlists.none { p -> p.id == it.id }) {
                        playlists.add(it)
                    }
                }
                return
            }

            if (obj.has("compactPlaylistRenderer")) {
                val renderer = obj.getAsJsonObject("compactPlaylistRenderer")
                parseCompactPlaylist(renderer)?.let {
                    if (isValidPlaylist(it) && playlists.none { p -> p.id == it.id }) {
                        playlists.add(it)
                    }
                }
                return
            }

            for (entry in obj.entrySet()) {
                extractPlaylistsRecursively(entry.value, playlists)
            }
        } else if (element.isJsonArray) {
            val array = element.asJsonArray
            for (i in 0 until array.size()) {
                extractPlaylistsRecursively(array.get(i), playlists)
            }
        }
    }

    private fun parseMusicTwoRowItemPlaylist(item: JsonObject): Playlist? {
        try {
            val navEndpoint = item.getAsJsonObject("navigationEndpoint")
                ?: item.getAsJsonObject("onTap")?.getAsJsonObject("innertubeCommand")
                ?: return null

            val browseEndpoint = navEndpoint.getAsJsonObject("browseEndpoint")
            var playlistId = browseEndpoint?.get("browseId")?.asString
                ?: navEndpoint.getAsJsonObject("watchPlaylistEndpoint")?.get("playlistId")?.asString
                ?: navEndpoint.getAsJsonObject("watchEndpoint")?.get("playlistId")?.asString
                ?: return null

            if (playlistId.startsWith("VL")) {
                playlistId = playlistId.removePrefix("VL")
            }

            if (!isValidPlaylistId(playlistId)) return null

            val title = item.getAsJsonObject("title")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                ?: item.getAsJsonObject("title")?.get("simpleText")?.asString
                ?: "Плейлист"

            val thumbnails = item.getAsJsonObject("thumbnailRenderer")
                ?.getAsJsonObject("musicThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
                ?: item.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")

            val thumbUrl = thumbnails?.lastOrNull()?.asJsonObject?.get("url")?.asString

            var trackCount = 0
            val subtitleRuns = item.getAsJsonObject("subtitle")?.getAsJsonArray("runs")
            if (subtitleRuns != null) {
                val subtitleText = (0 until subtitleRuns.size()).joinToString("") { subtitleRuns[it].asJsonObject.get("text")?.asString ?: "" }
                if (subtitleText.contains("підписник", ignoreCase = true) || subtitleText.contains("subscribers", ignoreCase = true) || subtitleText.contains("виконавець", ignoreCase = true)) {
                    return null // Channel/Artist, not playlist!
                }
                for (i in 0 until subtitleRuns.size()) {
                    val text = subtitleRuns[i].asJsonObject.get("text")?.asString ?: ""
                    val digits = text.filter { it.isDigit() }.toIntOrNull()
                    if (digits != null && digits > 0) {
                        trackCount = digits
                        break
                    }
                }
            }

            return Playlist(
                id = playlistId,
                title = title,
                url = "https://music.youtube.com/playlist?list=$playlistId",
                isLikedMusic = false,
                isEnabled = true,
                syncOnlyNew = false,
                trackCount = trackCount,
                lastSyncedAt = 0L,
                thumbnailUrl = thumbUrl
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseMusicResponsiveListItemPlaylist(item: JsonObject): Playlist? {
        try {
            val flexColumns = item.getAsJsonArray("flexColumns") ?: return null
            if (flexColumns.size() == 0) return null

            val col1 = flexColumns.get(0).asJsonObject
                .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                ?.getAsJsonObject("text")
                ?.getAsJsonArray("runs")
                ?.get(0)?.asJsonObject

            val navEndpoint = col1?.getAsJsonObject("navigationEndpoint") ?: return null
            val browseEndpoint = navEndpoint.getAsJsonObject("browseEndpoint") ?: return null
            val browseId = browseEndpoint.get("browseId")?.asString ?: return null

            val playlistId = browseId.removePrefix("VL")
            if (!isValidPlaylistId(playlistId)) return null

            val title = col1.get("text")?.asString ?: "Плейлист"
            val thumbnails = item.getAsJsonObject("thumbnail")
                ?.getAsJsonObject("musicThumbnailRenderer")
                ?.getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
            val thumbUrl = thumbnails?.lastOrNull()?.asJsonObject?.get("url")?.asString

            return Playlist(
                id = playlistId,
                title = title,
                url = "https://music.youtube.com/playlist?list=$playlistId",
                isLikedMusic = false,
                isEnabled = true,
                syncOnlyNew = false,
                trackCount = 0,
                lastSyncedAt = 0L,
                thumbnailUrl = thumbUrl
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseGridPlaylist(item: JsonObject): Playlist? {
        try {
            val playlistId = item.get("playlistId")?.asString ?: return null
            if (!isValidPlaylistId(playlistId)) return null

            val title = item.getAsJsonObject("title")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                ?: item.getAsJsonObject("title")?.get("simpleText")?.asString
                ?: "Плейлист"

            val thumbnails = item.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
            val thumbUrl = thumbnails?.lastOrNull()?.asJsonObject?.get("url")?.asString

            val trackCountText = item.getAsJsonObject("videoCountShortText")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                ?: item.getAsJsonObject("videoCountText")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
            val trackCount = trackCountText?.filter { it.isDigit() }?.toIntOrNull() ?: 0

            return Playlist(
                id = playlistId,
                title = title,
                url = "https://music.youtube.com/playlist?list=$playlistId",
                isLikedMusic = false,
                isEnabled = true,
                syncOnlyNew = false,
                trackCount = trackCount,
                lastSyncedAt = 0L,
                thumbnailUrl = thumbUrl
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseGeneralPlaylist(item: JsonObject): Playlist? {
        try {
            val playlistId = item.get("playlistId")?.asString ?: return null
            if (!isValidPlaylistId(playlistId)) return null

            val title = item.getAsJsonObject("title")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                ?: item.getAsJsonObject("title")?.get("simpleText")?.asString
                ?: "Плейлист"

            val thumbnails = item.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
            val thumbUrl = thumbnails?.lastOrNull()?.asJsonObject?.get("url")?.asString

            val trackCountText = item.getAsJsonObject("videoCountText")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
            val trackCount = trackCountText?.filter { it.isDigit() }?.toIntOrNull() ?: 0

            return Playlist(
                id = playlistId,
                title = title,
                url = "https://music.youtube.com/playlist?list=$playlistId",
                isLikedMusic = false,
                isEnabled = true,
                syncOnlyNew = false,
                trackCount = trackCount,
                lastSyncedAt = 0L,
                thumbnailUrl = thumbUrl
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseCompactPlaylist(item: JsonObject): Playlist? {
        try {
            val playlistId = item.get("playlistId")?.asString ?: return null
            if (!isValidPlaylistId(playlistId)) return null

            val title = item.getAsJsonObject("title")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString
                ?: "Плейлист"
            return Playlist(
                id = playlistId,
                title = title,
                url = "https://music.youtube.com/playlist?list=$playlistId",
                isLikedMusic = false,
                isEnabled = true,
                syncOnlyNew = false,
                trackCount = 0,
                lastSyncedAt = 0L,
                thumbnailUrl = null
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun isValidPlaylistId(id: String): Boolean {
        if (id.isBlank()) return false
        if (id == "LM") return true
        if (id == "LL") return false // Reject LL
        val clean = id.removePrefix("VL")
        if (clean.startsWith("UC") || clean.startsWith("FE") || clean.startsWith("MPRE") || clean.contains("channel/") || clean.contains("artist/")) {
            return false
        }
        return clean.startsWith("PL") || clean.startsWith("RD") || clean.startsWith("OLAK5uy_")
    }

    private fun isValidPlaylist(playlist: Playlist): Boolean {
        return isValidPlaylistId(playlist.id)
    }

    private fun extractTracksRecursively(
        element: JsonElement,
        playlistId: String,
        tracks: MutableList<Track>,
        maxTracks: Int
    ) {
        if (tracks.size >= maxTracks) return

        if (element.isJsonObject) {
            val obj = element.asJsonObject

            if (obj.has("musicResponsiveListItemRenderer")) {
                val renderer = obj.getAsJsonObject("musicResponsiveListItemRenderer")
                parseMusicResponsiveItem(renderer, playlistId)?.let { track ->
                    if (tracks.none { it.id == track.id }) {
                        tracks.add(track)
                    }
                }
                return
            }

            if (obj.has("playlistVideoRenderer")) {
                val renderer = obj.getAsJsonObject("playlistVideoRenderer")
                parsePlaylistVideoRenderer(renderer, playlistId)?.let { track ->
                    if (tracks.none { it.id == track.id }) {
                        tracks.add(track)
                    }
                }
                return
            }

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

    private fun getIosContext(): JsonObject {
        return JsonObject().apply {
            val clientObj = JsonObject().apply {
                addProperty("clientName", "IOS")
                addProperty("clientVersion", "19.29.1")
                addProperty("deviceModel", "iPhone14,3")
                addProperty("osVersion", "17.5.1")
                addProperty("hl", "uk")
                addProperty("gl", "UA")
            }
            add("client", clientObj)
        }
    }

    private fun getAndroidVrContext(): JsonObject {
        return JsonObject().apply {
            val clientObj = JsonObject().apply {
                addProperty("clientName", "ANDROID_VR")
                addProperty("clientVersion", "1.43.32")
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
