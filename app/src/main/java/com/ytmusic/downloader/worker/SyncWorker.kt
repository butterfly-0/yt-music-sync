package com.ytmusic.downloader.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ytmusic.downloader.YTMusicApp
import com.ytmusic.downloader.data.model.SyncState
import com.ytmusic.downloader.data.preferences.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class SyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val app = appContext.applicationContext as YTMusicApp
    private val notificationHelper = app.notificationHelper
    private val youtubeRepository = app.youtubeRepository
    private val downloadRepository = app.downloadRepository
    private val userPreferences = app.userPreferences

    override suspend fun doWork(): Result {
        _syncStateFlow.value = SyncState.Checking()

        try {
            val newTracks = youtubeRepository.findNewTracksToDownload()
            if (newTracks.isEmpty()) {
                userPreferences.lastSyncTimestamp = System.currentTimeMillis()
                userPreferences.lastSyncCount = 0
                _syncStateFlow.value = SyncState.Completed(0, System.currentTimeMillis())
                return Result.success(workDataOf("downloaded_count" to 0))
            }

            val total = newTracks.size
            var downloadedSuccessCount = 0

            for ((index, track) in newTracks.withIndex()) {
                val currentIndex = index + 1

                // Show foreground notification
                val notification = notificationHelper.buildProgressNotification(
                    title = track.title,
                    artist = track.artist,
                    currentIndex = currentIndex,
                    totalTracks = total,
                    percent = 0
                )

                val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ForegroundInfo(
                        NotificationHelper.NOTIFICATION_SYNC_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    ForegroundInfo(NotificationHelper.NOTIFICATION_SYNC_ID, notification)
                }

                try {
                    setForeground(foregroundInfo)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                _syncStateFlow.value = SyncState.Downloading(
                    currentTrackTitle = track.title,
                    currentTrackArtist = track.artist,
                    currentIndex = currentIndex,
                    totalTracks = total,
                    progressPercent = 0
                )

                val result = downloadRepository.downloadAndSaveTrack(
                    track = track,
                    preferredFormat = userPreferences.audioFormat,
                    onProgress = { percent ->
                        _syncStateFlow.value = SyncState.Downloading(
                            currentTrackTitle = track.title,
                            currentTrackArtist = track.artist,
                            currentIndex = currentIndex,
                            totalTracks = total,
                            progressPercent = percent
                        )
                    }
                )

                if (result.isSuccess) {
                    downloadedSuccessCount++
                }
            }

            userPreferences.lastSyncTimestamp = System.currentTimeMillis()
            userPreferences.lastSyncCount = downloadedSuccessCount

            _syncStateFlow.value = SyncState.Completed(downloadedSuccessCount, System.currentTimeMillis())
            notificationHelper.showCompletionNotification(downloadedSuccessCount)

            return Result.success(workDataOf("downloaded_count" to downloadedSuccessCount))
        } catch (e: Exception) {
            e.printStackTrace()
            _syncStateFlow.value = SyncState.Error(e.localizedMessage ?: "Помилка синхронізації")
            return Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC_WORK_NAME = "yt_music_periodic_sync"
        private const val UNIQUE_ONE_TIME_WORK_NAME = "yt_music_one_time_sync"

        private val _syncStateFlow = MutableStateFlow<SyncState>(SyncState.Idle)
        val syncStateFlow: StateFlow<SyncState> = _syncStateFlow.asStateFlow()

        fun setIdle() {
            _syncStateFlow.value = SyncState.Idle
        }

        fun enqueueOneTimeSync(context: Context) {
            val app = context.applicationContext as YTMusicApp
            val prefs = app.userPreferences

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (prefs.isWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresCharging(prefs.isChargingOnly)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun schedulePeriodicSync(context: Context, prefs: UserPreferences) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (prefs.isWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresCharging(prefs.isChargingOnly)
                .build()

            val intervalHours = prefs.syncIntervalHours.toLong().coerceAtLeast(1)

            val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalHours, TimeUnit.HOURS,
                15, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
        }
    }
}
