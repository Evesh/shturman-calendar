package com.shturman.calendar.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.shturman.calendar.BuildConfig
import org.json.JSONObject
import java.net.URL

object UpdateChecker {
    // GitHub repository URL
    private const val GITHUB_REPO = "Evesh/shturman-calendar"

    suspend fun checkForUpdate(context: Context): UpdateInfo? {
        return try {
            val url = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
            AppLog.d("UpdateChecker: checking $url")

            val json = URL(url).readText()
            val release = JSONObject(json)

            val tagName = release.getString("tag_name").removePrefix("v")
            val body = release.getString("body")
            val downloadUrl = "https://github.com/$GITHUB_REPO/releases/download/v$tagName/app-release.apk"

            val currentVersion = BuildConfig.VERSION_NAME

            if (tagName.isNotBlank() && tagName > currentVersion) {
                AppLog.d("Update available: $tagName (current: $currentVersion)")
                UpdateInfo(
                    version = tagName,
                    downloadUrl = downloadUrl,
                    message = body.ifBlank { "Доступна новая версия $tagName" }
                )
            } else {
                AppLog.d("No update available. Current: $currentVersion, Latest: $tagName")
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
