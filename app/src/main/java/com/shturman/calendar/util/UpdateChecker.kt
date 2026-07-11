package com.shturman.calendar.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.shturman.calendar.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

object UpdateChecker {
    private const val GITHUB_REPO = "Evesh/shturman-calendar"

    suspend fun checkForUpdate(context: Context): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
                AppLog.d("UpdateChecker: checking $url")

                val request = Request.Builder().url(url).build()
                val response = ApiConfig.okHttpClient.newCall(request).execute()
                val json = response.body?.string() ?: return@withContext null
                val release = JSONObject(json)

                val tagName = release.getString("tag_name").removePrefix("v")
                val body = release.optString("body", "")
                
                // Ищем первый доступный APK в ассетах релиза
                var downloadUrl: String? = null
                val assets = release.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                }

                // Если в ассетах не нашли, формируем стандартную ссылку (запасной вариант)
                if (downloadUrl == null) {
                    downloadUrl = "https://github.com/$GITHUB_REPO/releases/latest"
                }

                val currentVersion = BuildConfig.VERSION_NAME

                if (isNewerVersion(currentVersion, tagName)) {
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
    }

    // Надежное сравнение версий (например, 1.0.9 и 1.0.10)
    private fun isNewerVersion(current: String, latest: String): Boolean {
        return try {
            val currParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val lateParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLength = maxOf(currParts.size, lateParts.size)
            
            for (i in 0 until maxLength) {
                val curr = currParts.getOrNull(i) ?: 0
                val late = lateParts.getOrNull(i) ?: 0
                if (late > curr) return true
                if (late < curr) return false
            }
            false
        } catch (e: Exception) {
            latest > current // Запасной вариант - обычное сравнение строк
        }
    }

    fun openDownloadUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
