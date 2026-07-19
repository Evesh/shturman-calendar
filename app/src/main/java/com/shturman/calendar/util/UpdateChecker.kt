package com.shturman.calendar.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.shturman.calendar.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

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

                if (downloadUrl == null) {
                    downloadUrl = "https://github.com/$GITHUB_REPO/releases/latest"
                }

                val currentVersion = BuildConfig.VERSION_NAME

                if (isNewerVersion(currentVersion, tagName)) {
                    UpdateInfo(
                        version = tagName,
                        downloadUrl = downloadUrl,
                        message = body.ifBlank { "Доступна новая версия $tagName" }
                    )
                } else null
            } catch (e: Exception) {
                AppLog.e("Update check failed", e)
                null
            }
        }
    }

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
        } catch (e: Exception) { latest > current }
    }

    suspend fun downloadAndInstall(context: Context, url: String, onProgress: (Float) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = ApiConfig.okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("Failed to download file")
                
                val body = response.body ?: throw Exception("Empty response body")
                val totalBytes = body.contentLength()
                val apkFile = File(context.cacheDir, "update.apk")
                
                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var downloadedBytes = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) onProgress(downloadedBytes.toFloat() / totalBytes)
                        }
                    }
                }
                installApk(context, apkFile)
            } catch (e: Exception) {
                AppLog.e("Download failed", e)
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openDownloadUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) { AppLog.e("Failed to open download URL", e) }
    }
}

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val message: String
)
