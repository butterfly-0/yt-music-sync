package com.ytmusic.downloader.youtube

import java.security.MessageDigest

object CookieManager {

    /**
     * Extracts individual cookie value by name from a raw cookie string.
     */
    fun getCookieValue(cookieString: String, name: String): String? {
        val cookies = cookieString.split(";")
        for (cookie in cookies) {
            val parts = cookie.trim().split("=", limit = 2)
            if (parts.size == 2 && parts[0].trim().equals(name, ignoreCase = true)) {
                return parts[1].trim()
            }
        }
        return null
    }

    /**
     * Generates the SAPISIDHASH Authorization header value required for authenticated
     * YouTube Innertube API requests.
     * Formula: SHA1(timestamp + " " + sapisid + " " + origin)
     */
    fun generateSapisidHash(sapisid: String, origin: String = "https://music.youtube.com"): String {
        val timestamp = System.currentTimeMillis() / 1000
        val payload = "$timestamp $sapisid $origin"
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(payload.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hex"
    }

    /**
     * Checks if cookies contain valid authentication tokens.
     */
    fun hasValidAuth(cookieString: String): Boolean {
        val sapisid = getCookieValue(cookieString, "SAPISID")
        val sid = getCookieValue(cookieString, "SID")
        val loginInfo = getCookieValue(cookieString, "LOGIN_INFO")
        return !sapisid.isNullOrBlank() || !sid.isNullOrBlank() || !loginInfo.isNullOrBlank()
    }
}
