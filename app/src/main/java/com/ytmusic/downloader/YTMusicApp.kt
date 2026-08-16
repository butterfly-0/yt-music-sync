package com.ytmusic.downloader

import android.app.Application
import com.ytmusic.downloader.audio.AudioPreviewPlayer
import com.ytmusic.downloader.audio.AudioTagger
import com.ytmusic.downloader.audio.MediaStoreHelper
import com.ytmusic.downloader.data.local.AppDatabase
import com.ytmusic.downloader.data.preferences.UserPreferences
import com.ytmusic.downloader.data.repository.DownloadRepository
import com.ytmusic.downloader.data.repository.YouTubeRepository
import com.ytmusic.downloader.worker.NotificationHelper
import com.ytmusic.downloader.worker.SyncWorker
import com.ytmusic.downloader.youtube.YouTubeClient
import com.ytmusic.downloader.youtube.YouTubeExtractor

class YTMusicApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var userPreferences: UserPreferences
        private set

    lateinit var youtubeClient: YouTubeClient
        private set

    lateinit var youtubeExtractor: YouTubeExtractor
        private set

    lateinit var audioTagger: AudioTagger
        private set

    lateinit var lyricsHelper: com.ytmusic.downloader.audio.LyricsHelper
        private set

    lateinit var mediaStoreHelper: MediaStoreHelper
        private set

    lateinit var downloadRepository: DownloadRepository
        private set

    lateinit var youtubeRepository: YouTubeRepository
        private set

    lateinit var notificationHelper: NotificationHelper
        private set

    lateinit var audioPreviewPlayer: AudioPreviewPlayer
        private set

    lateinit var musicPlayerManager: com.ytmusic.downloader.player.MusicPlayerManager
        private set

    lateinit var appUpdateManager: com.ytmusic.downloader.update.AppUpdateManager
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        userPreferences = UserPreferences(this)
        notificationHelper = NotificationHelper(this)
        audioPreviewPlayer = AudioPreviewPlayer(this)
        appUpdateManager = com.ytmusic.downloader.update.AppUpdateManager(this)

        youtubeClient = YouTubeClient(getCookies = { userPreferences.cookies })
        youtubeExtractor = YouTubeExtractor(youtubeClient)
        lyricsHelper = com.ytmusic.downloader.audio.LyricsHelper(youtubeClient.getHttpClient())
        audioTagger = AudioTagger()
        mediaStoreHelper = MediaStoreHelper(this)
        musicPlayerManager = com.ytmusic.downloader.player.MusicPlayerManager(this, lyricsHelper)

        downloadRepository = DownloadRepository(
            context = this,
            trackDao = database.trackDao(),
            extractor = youtubeExtractor,
            audioTagger = audioTagger,
            lyricsHelper = lyricsHelper,
            mediaStoreHelper = mediaStoreHelper,
            userPreferences = userPreferences
        )

        youtubeRepository = YouTubeRepository(
            extractor = youtubeExtractor,
            playlistDao = database.playlistDao(),
            trackDao = database.trackDao(),
            userPreferences = userPreferences
        )

        // Schedule periodic sync if configured
        SyncWorker.schedulePeriodicSync(this, userPreferences)
    }

    override fun onTerminate() {
        super.onTerminate()
        audioPreviewPlayer.release()
    }
}
