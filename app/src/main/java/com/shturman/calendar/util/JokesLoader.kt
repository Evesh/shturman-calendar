package com.shturman.calendar.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object JokesLoader {
    private var lineCount = 0
    private var initialized = false

    private fun init(context: Context) {
        if (initialized) return
        try {
            BufferedReader(InputStreamReader(context.assets.open("jokes.txt"))).use { reader ->
                lineCount = reader.readLines().size
            }
            initialized = true
        } catch (_: Exception) {}
    }

    suspend fun getRandomJoke(context: Context): String? {
        return withContext(Dispatchers.IO) {
            try {
                init(context)
                if (lineCount == 0) return@withContext null

                val targetLine = (0 until lineCount).random()
                var currentLine = 0

                BufferedReader(InputStreamReader(context.assets.open("jokes.txt"))).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (currentLine == targetLine) {
                            return@withContext line?.trim()?.takeIf { it.isNotBlank() }
                        }
                        currentLine++
                    }
                }
                null
            } catch (e: Exception) {
                AppLog.e("JokesLoader error", e)
                null
            }
        }
    }
}
