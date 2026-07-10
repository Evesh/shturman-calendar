package com.shturman.calendar.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object AppLog {
    private const val TAG = "ShturmanCalendar"
    private var logFile: File? = null

    fun init(context: Context) {
        val file = File(context.filesDir, "shturman_log.txt")
        logFile = file
        if (!file.exists()) file.createNewFile()
        Log.d(TAG, "Log initialized: ${file.absolutePath}")
    }

    fun d(message: String) {
        Log.d(TAG, message)
        appendToFile("D", message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        appendToFile("E", "$message ${throwable?.message ?: ""}")
    }

    private fun appendToFile(level: String, message: String) {
        try {
            val file = logFile ?: return
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val line = "$timestamp [$level] $message\n"
            file.appendText(line)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    fun getLog(): String {
        return try {
            val file = logFile ?: return "Логгер не инициализирован"
            if (file.exists()) file.readText() else "Лог пуст"
        } catch (e: Exception) {
            "Ошибка чтения лога: ${e.message}"
        }
    }

    fun getLogFile(): File? = logFile

    fun clearLog() {
        try { logFile?.delete() } catch (_: Exception) {}
    }
}
