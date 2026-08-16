package com.ytmusic.downloader.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ytmusic.downloader.YTMusicApp
import com.ytmusic.downloader.data.model.AudioFormat
import com.ytmusic.downloader.worker.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YTMusicApp
    val userPrefs = app.userPreferences
    private val youtubeExtractor = app.youtubeExtractor
    val appUpdateManager = app.appUpdateManager
    private val mediaStoreHelper = app.mediaStoreHelper
    val updateState = appUpdateManager.updateState

    val isLoggedIn: StateFlow<Boolean> = userPrefs.isLoggedInFlow

    private val _accountName = MutableStateFlow(userPrefs.accountName)
    val accountName: StateFlow<String> = _accountName.asStateFlow()

    private val _accountEmail = MutableStateFlow(userPrefs.accountEmail)
    val accountEmail: StateFlow<String> = _accountEmail.asStateFlow()

    private val _audioFormat = MutableStateFlow(userPrefs.audioFormat)
    val audioFormat: StateFlow<AudioFormat> = _audioFormat.asStateFlow()

    private val _syncIntervalHours = MutableStateFlow(userPrefs.syncIntervalHours)
    val syncIntervalHours: StateFlow<Int> = _syncIntervalHours.asStateFlow()

    private val _isWifiOnly = MutableStateFlow(userPrefs.isWifiOnly)
    val isWifiOnly: StateFlow<Boolean> = _isWifiOnly.asStateFlow()

    private val _isChargingOnly = MutableStateFlow(userPrefs.isChargingOnly)
    val isChargingOnly: StateFlow<Boolean> = _isChargingOnly.asStateFlow()

    private val _isSyncOnlyNew = MutableStateFlow(userPrefs.isSyncOnlyNew)
    val isSyncOnlyNew: StateFlow<Boolean> = _isSyncOnlyNew.asStateFlow()

    private val _customDownloadDisplayName = MutableStateFlow(userPrefs.customDownloadDisplayName)
    val customDownloadDisplayName: StateFlow<String> = _customDownloadDisplayName.asStateFlow()

    init {
        refreshProfile()
    }

    fun setAudioFormat(format: AudioFormat) {
        userPrefs.audioFormat = format
        _audioFormat.value = format
    }

    fun setSyncIntervalHours(hours: Int) {
        userPrefs.syncIntervalHours = hours
        _syncIntervalHours.value = hours
        SyncWorker.schedulePeriodicSync(getApplication(), userPrefs)
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        userPrefs.isWifiOnly = wifiOnly
        _isWifiOnly.value = wifiOnly
        SyncWorker.schedulePeriodicSync(getApplication(), userPrefs)
    }

    fun setChargingOnly(chargingOnly: Boolean) {
        userPrefs.isChargingOnly = chargingOnly
        _isChargingOnly.value = chargingOnly
        SyncWorker.schedulePeriodicSync(getApplication(), userPrefs)
    }

    fun setSyncOnlyNew(syncOnlyNew: Boolean) {
        userPrefs.isSyncOnlyNew = syncOnlyNew
        _isSyncOnlyNew.value = syncOnlyNew
    }

    fun setCustomDownloadFolder(uri: Uri, displayName: String) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        userPrefs.customDownloadUri = uri.toString()
        userPrefs.customDownloadDisplayName = displayName
        _customDownloadDisplayName.value = displayName
    }

    fun resetToDefaultMusicFolder() {
        userPrefs.customDownloadUri = ""
        userPrefs.customDownloadDisplayName = "Music/YouTubeSync"
        _customDownloadDisplayName.value = "Music/YouTubeSync"
    }

    fun openMusicFolder() {
        mediaStoreHelper.openMusicFolder(userPrefs.customDownloadUri)
    }

    fun refreshProfile() {
        if (userPrefs.isLoggedIn) {
            viewModelScope.launch {
                val profile = youtubeExtractor.getUserProfile()
                if (profile != null) {
                    userPrefs.accountName = profile.name
                    userPrefs.accountEmail = profile.email
                    _accountName.value = profile.name
                    _accountEmail.value = profile.email
                }
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            appUpdateManager.checkForUpdates()
        }
    }

    fun downloadAndInstallUpdate(info: com.ytmusic.downloader.update.UpdateInfo) {
        viewModelScope.launch {
            appUpdateManager.downloadAndInstallUpdate(info)
        }
    }

    fun logout() {
        userPrefs.clearAuth()
        _accountName.value = ""
        _accountEmail.value = ""
    }
}
