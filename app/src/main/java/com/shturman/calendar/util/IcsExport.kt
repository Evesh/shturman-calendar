package com.shturman.calendar.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.shturman.calendar.data.Reminder
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object IcsExport {
    fun export(context: Context, reminders: List<Reminder>): Boolean {
        return try {
            val ics = buildString {
                appendLine("BEGIN:VCALENDAR")
                appendLine("VERSION:2.0")
                appendLine("PRODID:-//ShturmanCalendar//RU")
                appendLine("CALSCALE:GREGORIAN")

                for (r in reminders) {
                    val dtStart: String
                    val dtEnd: String
                    if (r.isAllDay) {
                        dtStart = String.format("VALUE=DATE:%04d%02d%02d", r.year, r.month, r.dayOfMonth)
                        // Конец события на следующий день для "All Day" в ICS
                        val cal = Calendar.getInstance().apply {
                            set(r.year, r.month - 1, r.dayOfMonth)
                            add(Calendar.DAY_OF_MONTH, 1)
                        }
                        dtEnd = String.format("VALUE=DATE:%04d%02d%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                    } else {
                        dtStart = formatIcsDate(r.year, r.month - 1, r.dayOfMonth, r.hour, r.minute)
                        dtEnd = formatIcsDate(r.year, r.month - 1, r.dayOfMonth, r.hour + 1, r.minute)
                    }

                    appendLine("BEGIN:VEVENT")
                    appendLine("DTSTART;$dtStart")
                    appendLine("DTEND;$dtEnd")
                    appendLine("SUMMARY:${escapeIcs(r.title)}")
                    if (r.description.isNotBlank()) {
                        appendLine("DESCRIPTION:${escapeIcs(r.description)}")
                    }
                    if (r.color != com.shturman.calendar.data.ReminderColor.DEFAULT) {
                        appendLine("COLOR:${r.color.name}")
                    }

                    val rrule = when (r.period) {
                        com.shturman.calendar.data.ReminderPeriod.DAILY -> "RRULE:FREQ=DAILY"
                        com.shturman.calendar.data.ReminderPeriod.WEEKLY -> {
                            val days = r.weeklyDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                                .map { d -> when (d) { 1 -> "SU"; 2 -> "MO"; 3 -> "TU"; 4 -> "WE"; 5 -> "TH"; 6 -> "FR"; 7 -> "SA"; else -> "" } }
                                .filter { it.isNotEmpty() }
                            if (days.isNotEmpty()) "RRULE:FREQ=WEEKLY;BYDAY=${days.joinToString(",")}" else "RRULE:FREQ=WEEKLY"
                        }
                        com.shturman.calendar.data.ReminderPeriod.MONTHLY -> "RRULE:FREQ=MONTHLY"
                        com.shturman.calendar.data.ReminderPeriod.YEARLY -> "RRULE:FREQ=YEARLY"
                        else -> null
                    }
                    rrule?.let { appendLine(it) }

                    appendLine("UID:${r.id}@shturman.calendar")
                    appendLine("END:VEVENT")
                }

                appendLine("END:VCALENDAR")
            }

            val file = File(context.cacheDir, "shturman_export.ics")
            file.writeText(ics)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Экспорт событий")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Экспорт в .ics"))
            true
        } catch (e: Exception) {
            AppLog.e("ICS export failed", e)
            false
        }
    }

    private fun formatIcsDate(year: Int, month: Int, day: Int, hour: Int, minute: Int): String {
        return String.format("%04d%02d%02dT%02d%02d00", year, month + 1, day, hour, minute)
    }

    private fun escapeIcs(text: String): String {
        return text.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")
    }
}
