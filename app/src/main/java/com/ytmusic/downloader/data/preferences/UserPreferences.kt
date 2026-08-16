package com.ytmusic.downloader.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ytmusic.downloader.data.model.AudioFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "ytmusic_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for edge cases where Android Keystore has temporary issues
        context.getSharedPreferences("ytmusic_prefs_fallback", Context.MODE_PRIVATE)
    }

    private val _isLoggedInFlow = MutableStateFlow(isLoggedIn)
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    var cookies: String
        get() = prefs.getString(KEY_COOKIES, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_COOKIES, value).apply()
            _isLoggedInFlow.value = value.isNotBlank()
        }

    var accountName: String
        get() = prefs.getString(KEY_ACCOUNT_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACCOUNT_NAME, value).apply()

    var accountEmail: String
        get() = prefs.getString(KEY_ACCOUNT_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACCOUNT_EMAIL, value).apply()

    val isLoggedIn: Boolean
        get() = cookies.isNotBlank() && (cookies.contains("SAPISID") || cookies.contains("SID") || cookies.contains("LOGIN_INFO"))

    var audioFormat: AudioFormat
        get() {
            val formatName = prefs.getString(KEY_AUDIO_FORMAT, AudioFormat.M4A.name) ?: AudioFormat.M4A.name
            return try {
                AudioFormat.valueOf(formatName)
            } catch (e: Exception) {
                AudioFormat.M4A
            }
        }
        set(value) = prefs.edit().putString(KEY_AUDIO_FORMAT, value.name).apply()

    var syncIntervalHours: Int
        get() = prefs.getInt(KEY_SYNC_INTERVAL_HOURS, 2)
        set(value) = prefs.edit().putInt(KEY_SYNC_INTERVAL_HOURS, value).apply()

    var isWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    var isChargingOnly: Boolean
        get() = prefs.getBoolean(KEY_CHARGING_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_CHARGING_ONLY, value).apply()

    var isSyncOnlyNew: Boolean
        get() = prefs.getBoolean(KEY_SYNC_ONLY_NEW, true)
        set(value) = prefs.edit().putBoolean(KEY_SYNC_ONLY_NEW, value).apply()

    var firstInstalledTime: Long
        get() {
            val time = prefs.getLong(KEY_FIRST_INSTALLED_TIME, 0L)
            return if (time == 0L) {
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_FIRST_INSTALLED_TIME, now).apply()
                now
            } else {
                time
            }
        }
        set(value) = prefs.edit().putLong(KEY_FIRST_INSTALLED_TIME, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIME, value).apply()

    var lastSyncCount: Int
        get() = prefs.getInt(KEY_LAST_SYNC_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_SYNC_COUNT, value).apply()

    fun clearAuth() {
        cookies = ""
        accountName = ""
        accountEmail = ""
        _isLoggedInFlow.value = false
    }

    companion object {
        private const val KEY_COOKIES = "key_cookies"
        private const val KEY_ACCOUNT_NAME = "key_account_name"
        private const val KEY_ACCOUNT_EMAIL = "key_account_email"
        private const val KEY_AUDIO_FORMAT = "key_audio_format"
        private const val KEY_SYNC_INTERVAL_HOURS = "key_sync_interval_hours"
        private const val KEY_WIFI_ONLY = "key_wifi_only"
        private const val KEY_CHARGING_ONLY = "key_charging_only"
        private const val KEY_SYNC_ONLY_NEW = "key_sync_only_new"
        private const val KEY_FIRST_INSTALLED_TIME = "key_first_installed_time"
        private const val KEY_LAST_SYNC_TIME = "key_last_sync_time"
        private const val KEY_LAST_SYNC_COUNT = "key_last_sync_count"
    }
}
