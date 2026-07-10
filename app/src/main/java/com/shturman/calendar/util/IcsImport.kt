package com.shturman.calendar.util

import android.content.Context
import android.net.Uri
import com.shturman.calendar.data.Reminder
import com.shturman.calendar.data.ReminderPeriod
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*

object IcsImport {
    fun import(context: Context, uri: Uri): List<Reminder> {
        val reminders = mutableListOf<Reminder>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    var currentReminder: MutableMap<String, String>? = null

                    while (reader.readLine().also { line = it } != null) {
                        val trimmedLine = line!!.trim()
                        when {
                            trimmedLine == "BEGIN:VEVENT" -> {
                                currentReminder = mutableMapOf()
                            }
                            trimmedLine == "END:VEVENT" -> {
                                currentReminder?.let { map ->
                                    val reminder = parseMapToReminder(map)
                                    if (reminder != null) reminders.add(reminder)
                                }
                                currentReminder = null
                            }
                            currentReminder != null -> {
                                val lineParts = trimmedLine.split(":", limit = 2)
                                if (lineParts.size == 2) {
                                    val key = lineParts[0]
                                    val value = lineParts[1]
                                    
                                    // Handle parameters like DTSTART;VALUE=DATE
                                    val cleanKey = if (key.contains(";")) key.split(";")[0] else key
                                    currentReminder[cleanKey] = value
                                    if (key.contains("VALUE=DATE")) {
                                        currentReminder["IS_ALL_DAY"] = "true"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e("ICS import failed", e)
        }
        return reminders
    }

    private fun parseMapToReminder(map: Map<String, String>): Reminder? {
        val summary = map["SUMMARY"] ?: return null
        val description = map["DESCRIPTION"] ?: ""
        val dtStart = map["DTSTART"] ?: return null
        val rrule = map["RRULE"]
        val isAllDay = map["IS_ALL_DAY"] == "true"

        return try {
            val year = dtStart.substring(0, 4).toInt()
            val month = dtStart.substring(4, 6).toInt()
            val day = dtStart.substring(6, 8).toInt()
            
            var hour = 0
            var minute = 0
            
            if (!isAllDay && dtStart.length >= 13) {
                // Expected format with time: YYYYMMDDTHHMMSS
                val tIndex = dtStart.indexOf('T')
                if (tIndex != -1 && dtStart.length >= tIndex + 5) {
                    hour = dtStart.substring(tIndex + 1, tIndex + 3).toInt()
                    minute = dtStart.substring(tIndex + 3, tIndex + 5).toInt()
                }
            }

            val period = when {
                rrule == null -> ReminderPeriod.ONCE
                rrule.contains("FREQ=DAILY") -> ReminderPeriod.DAILY
                rrule.contains("FREQ=WEEKLY") -> ReminderPeriod.WEEKLY
                rrule.contains("FREQ=MONTHLY") -> ReminderPeriod.MONTHLY
                rrule.contains("FREQ=YEARLY") -> ReminderPeriod.YEARLY
                else -> ReminderPeriod.ONCE
            }

            val color = try {
                map["COLOR"]?.let { com.shturman.calendar.data.ReminderColor.valueOf(it) } ?: com.shturman.calendar.data.ReminderColor.DEFAULT
            } catch (_: Exception) { com.shturman.calendar.data.ReminderColor.DEFAULT }

            Reminder(
                title = unescapeIcs(summary),
                description = unescapeIcs(description),
                year = year,
                month = month,
                dayOfMonth = day,
                hour = hour,
                minute = minute,
                period = period,
                melodyUri = null,
                isAllDay = isAllDay,
                color = color
            )
        } catch (e: Exception) {
            AppLog.e("Failed to parse event: $dtStart", e)
            null
        }
    }

    private fun unescapeIcs(text: String): String {
        return text.replace("\\\\", "\\").replace("\\,", ",").replace("\\;", ";").replace("\\n", "\n")
    }
}
