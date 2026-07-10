package com.shturman.calendar.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.shturman.calendar.R
import com.shturman.calendar.data.Reminder
import java.util.*

object CalendarIntegration {
    private fun getDefaultCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}",
            null,
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    fun addToSystemCalendar(context: Context, reminder: Reminder): Long? {
        val calendarId = getDefaultCalendarId(context) ?: return null
        val calendar = Calendar.getInstance().apply {
            set(reminder.year, reminder.month - 1, reminder.dayOfMonth, reminder.hour, reminder.minute)
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, if (reminder.isAllDay) {
                Calendar.getInstance().apply {
                    set(reminder.year, reminder.month - 1, reminder.dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                    timeZone = TimeZone.getTimeZone("UTC")
                }.timeInMillis
            } else calendar.timeInMillis)
            put(CalendarContract.Events.DTEND, if (reminder.isAllDay) {
                Calendar.getInstance().apply {
                    set(reminder.year, reminder.month - 1, reminder.dayOfMonth, 0, 0, 0)
                    add(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.MILLISECOND, 0)
                    timeZone = TimeZone.getTimeZone("UTC")
                }.timeInMillis
            } else calendar.timeInMillis + 60 * 60 * 1000)
            put(CalendarContract.Events.ALL_DAY, if (reminder.isAllDay) 1 else 0)
            put(CalendarContract.Events.TITLE, reminder.title)
            put(CalendarContract.Events.DESCRIPTION, reminder.description.ifBlank { context.getString(R.string.default_event_description) })
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (reminder.isAllDay) "UTC" else TimeZone.getDefault().id)

            val rrule = when (reminder.period) {
                com.shturman.calendar.data.ReminderPeriod.DAILY -> "FREQ=DAILY"
                com.shturman.calendar.data.ReminderPeriod.WEEKLY -> {
                    val rruleDays = reminder.weeklyDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                        .map { day -> when (day) { 1 -> "SU"; 2 -> "MO"; 3 -> "TU"; 4 -> "WE"; 5 -> "TH"; 6 -> "FR"; 7 -> "SA"; else -> "" } }
                        .filter { it.isNotEmpty() }
                    if (rruleDays.isNotEmpty()) "FREQ=WEEKLY;BYDAY=${rruleDays.joinToString(",")}" else "FREQ=WEEKLY"
                }
                com.shturman.calendar.data.ReminderPeriod.MONTHLY -> "FREQ=MONTHLY"
                com.shturman.calendar.data.ReminderPeriod.YEARLY -> "FREQ=YEARLY"
                com.shturman.calendar.data.ReminderPeriod.ONCE -> null
            }
            rrule?.let { put(CalendarContract.Events.RRULE, it) }
        }

        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLong()
        } catch (e: Exception) {
            AppLog.e("CalendarIntegration: addToSystemCalendar failed", e)
            CrashlyticsHelper.logError("CalendarIntegration", "addToSystemCalendar: ${e.message}", e)
            null
        }
    }

    fun deleteFromSystemCalendar(context: Context, eventId: Long) {
        try {
            val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(deleteUri, null, null)
        } catch (e: Exception) {
            AppLog.e("CalendarIntegration: deleteFromSystemCalendar failed", e)
            CrashlyticsHelper.logError("CalendarIntegration", "deleteFromSystemCalendar: ${e.message}", e)
        }
    }

    fun updateSystemCalendar(context: Context, reminder: Reminder) {
        val eventId = reminder.systemEventId ?: return
        val calendar = Calendar.getInstance().apply {
            set(reminder.year, reminder.month - 1, reminder.dayOfMonth, reminder.hour, reminder.minute)
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, if (reminder.isAllDay) {
                Calendar.getInstance().apply {
                    set(reminder.year, reminder.month - 1, reminder.dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                    timeZone = TimeZone.getTimeZone("UTC")
                }.timeInMillis
            } else calendar.timeInMillis)
            put(CalendarContract.Events.DTEND, if (reminder.isAllDay) {
                Calendar.getInstance().apply {
                    set(reminder.year, reminder.month - 1, reminder.dayOfMonth, 0, 0, 0)
                    add(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.MILLISECOND, 0)
                    timeZone = TimeZone.getTimeZone("UTC")
                }.timeInMillis
            } else calendar.timeInMillis + 60 * 60 * 1000)
            put(CalendarContract.Events.ALL_DAY, if (reminder.isAllDay) 1 else 0)
            put(CalendarContract.Events.TITLE, reminder.title)
            put(CalendarContract.Events.DESCRIPTION, reminder.description.ifBlank { context.getString(R.string.default_event_description) })
            put(CalendarContract.Events.EVENT_TIMEZONE, if (reminder.isAllDay) "UTC" else TimeZone.getDefault().id)

            val rrule = when (reminder.period) {
                com.shturman.calendar.data.ReminderPeriod.DAILY -> "FREQ=DAILY"
                com.shturman.calendar.data.ReminderPeriod.WEEKLY -> {
                    val rruleDays = reminder.weeklyDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                        .map { day -> when (day) { 1 -> "SU"; 2 -> "MO"; 3 -> "TU"; 4 -> "WE"; 5 -> "TH"; 6 -> "FR"; 7 -> "SA"; else -> "" } }
                        .filter { it.isNotEmpty() }
                    if (rruleDays.isNotEmpty()) "FREQ=WEEKLY;BYDAY=${rruleDays.joinToString(",")}" else "FREQ=WEEKLY"
                }
                com.shturman.calendar.data.ReminderPeriod.MONTHLY -> "FREQ=MONTHLY"
                com.shturman.calendar.data.ReminderPeriod.YEARLY -> "FREQ=YEARLY"
                com.shturman.calendar.data.ReminderPeriod.ONCE -> null
            }
            if (rrule != null) {
                put(CalendarContract.Events.RRULE, rrule)
            } else {
                putNull(CalendarContract.Events.RRULE)
            }
        }

        try {
            val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.update(updateUri, values, null, null)
        } catch (e: Exception) {
            AppLog.e("CalendarIntegration: updateSystemCalendar failed", e)
            CrashlyticsHelper.logError("CalendarIntegration", "updateSystemCalendar: ${e.message}", e)
        }
    }

    fun importFromSystemCalendar(context: Context): List<Reminder> {
        val reminders = mutableListOf<Reminder>()
        val seenIds = mutableSetOf<Long>()

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_ID
        )

        AppLog.d("CalendarSync: querying events (last 2 years + next 2 years)")

        // Ограничиваем 2 года назад и 2 года вперёд для производительности
        val start = System.currentTimeMillis() - 2L * 365 * 24 * 60 * 60 * 1000
        val end = System.currentTimeMillis() + 2L * 365 * 24 * 60 * 60 * 1000

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
            arrayOf(start.toString(), end.toString()),
            "${CalendarContract.Events.DTSTART} ASC"
        )

        cursor?.use {
            AppLog.d("CalendarSync: found ${it.count} events")
            while (it.moveToNext()) {
                val id = it.getLong(0)
                if (id in seenIds) continue
                seenIds.add(id)

                val title = it.getString(1) ?: continue
                val description = it.getString(2) ?: ""
                val dtStart = it.getLong(3)
                val rrule = it.getString(5)
                val isAllDay = it.getInt(6) == 1

                val cal = Calendar.getInstance().apply { timeInMillis = dtStart }

                val period = when {
                    rrule == null -> com.shturman.calendar.data.ReminderPeriod.ONCE
                    rrule.contains("FREQ=DAILY") -> com.shturman.calendar.data.ReminderPeriod.DAILY
                    rrule.contains("FREQ=WEEKLY") -> com.shturman.calendar.data.ReminderPeriod.WEEKLY
                    rrule.contains("FREQ=MONTHLY") -> com.shturman.calendar.data.ReminderPeriod.MONTHLY
                    rrule.contains("FREQ=YEARLY") -> com.shturman.calendar.data.ReminderPeriod.YEARLY
                    else -> com.shturman.calendar.data.ReminderPeriod.ONCE
                }

                reminders.add(
                    Reminder(
                        title = title,
                        description = description,
                        hour = cal.get(Calendar.HOUR_OF_DAY),
                        minute = cal.get(Calendar.MINUTE),
                        dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                        month = cal.get(Calendar.MONTH) + 1,
                        year = cal.get(Calendar.YEAR),
                        period = period,
                        melodyUri = null,
                        systemEventId = id,
                        isSyncedWithSystem = true,
                        isAllDay = isAllDay
                    )
                )
            }
        }

        AppLog.d("CalendarSync: imported ${reminders.size} unique events")
        return reminders
    }
}
