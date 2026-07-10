package com.shturman.calendar.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.shturman.calendar.BuildConfig
import kotlinx.coroutines.tasks.await

object UpdateChecker {
    private const val REMOTE_VERSION_KEY = "latest_version"
    private const val REMOTE_DOWNLOAD_URL_KEY = "download_url"
    private const val REMOTE_UPDATE_MESSAGE_KEY = "update_message"

    suspend fun checkForUpdate(context: Context): UpdateInfo? {
        return try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings).await()
            remoteConfig.fetchAndActivate().await()

            val latestVersion: String = remoteConfig.getString(REMOTE_VERSION_KEY)
            val downloadUrl: String = remoteConfig.getString(REMOTE_DOWNLOAD_URL_KEY)
            val updateMessage: String = remoteConfig.getString(REMOTE_UPDATE_MESSAGE_KEY)

            val currentVersion = BuildConfig.VERSION_NAME

            if (latestVersion.isNotBlank() && latestVersion > currentVersion) {
                AppLog.d("Update available: $latestVersion (current: $currentVersion)")
                UpdateInfo(
                    version = latestVersion,
                    downloadUrl = downloadUrl,
                    message = updateMessage.ifBlank { "Доступна новая версия $latestVersion" }
                )
            } else {
                AppLog.d("No update available. Current: $currentVersion")
                null
            }
        } catch (e: Exception) {
            AppLog.e("Update check failed", e)
            null
        }
    }

    fun openDownloadUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLog.e("Failed to open download URL", e)
        }
    }
}

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val message: String
)
