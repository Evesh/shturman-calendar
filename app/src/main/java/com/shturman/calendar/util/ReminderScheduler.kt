package com.shturman.calendar.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shturman.calendar.data.Reminder
import com.shturman.calendar.data.ReminderPeriod
import com.shturman.calendar.receiver.ReminderReceiver
import java.util.*

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(reminder: Reminder) {
        AppLog.d("Scheduler: scheduling reminder #${reminder.id}, title=${reminder.title}, melodyUri=${reminder.melodyUri}")
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
            putExtra("TITLE", reminder.title)
            putExtra("MELODY_URI", reminder.melodyUri)
            putExtra("VIBRATE", reminder.vibrate)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, reminder.year)
            set(Calendar.MONTH, reminder.month - 1)
            set(Calendar.DAY_OF_MONTH, reminder.dayOfMonth)
            if (reminder.isAllDay) {
                // Для "весь день" по умолчанию ставим 9:00 утра
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
            } else {
                set(Calendar.HOUR_OF_DAY, reminder.hour)
                set(Calendar.MINUTE, reminder.minute)
            }
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Move to the next occurrence if time is in the past
        while (calendar.timeInMillis <= System.currentTimeMillis()) {
            when (reminder.period) {
                ReminderPeriod.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
                ReminderPeriod.WEEKLY -> {
                    val nextDay = getNextWeeklyDay(calendar, reminder.weeklyDays)
                    if (nextDay != null) {
                        calendar.set(Calendar.DAY_OF_WEEK, nextDay)
                        if (calendar.timeInMillis <= System.currentTimeMillis()) {
                            calendar.add(Calendar.WEEK_OF_YEAR, 1)
                        }
                    } else {
                        calendar.add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }
                ReminderPeriod.MONTHLY -> calendar.add(Calendar.MONTH, 1)
                ReminderPeriod.YEARLY -> calendar.add(Calendar.YEAR, 1)
                ReminderPeriod.ONCE -> break
            }
            if (reminder.period == ReminderPeriod.ONCE) break
        }

        if (calendar.timeInMillis <= System.currentTimeMillis() && reminder.period == ReminderPeriod.ONCE) {
            return
        }

        // Применяем смещение "напомнить за..."
        val triggerTime = calendar.timeInMillis - (reminder.remindBeforeMinutes * 60 * 1000L)
        val finalTime = if (triggerTime > System.currentTimeMillis()) triggerTime else calendar.timeInMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, finalTime, pendingIntent)
                } else {
                    // Если разрешение не дано, используем обычный метод (может сработать с задержкой)
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, finalTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, finalTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            AppLog.e("SecurityException while scheduling alarm", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, finalTime, pendingIntent)
        }
    }

    private fun getNextWeeklyDay(current: Calendar, weeklyDays: String): Int? {
        val days = weeklyDays.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (days.isEmpty()) return null
        
        // В Calendar: SUNDAY=1, MONDAY=2... SATURDAY=7
        val currentDay = current.get(Calendar.DAY_OF_WEEK)
        val sortedDays = days.sorted()
        
        // Ищем следующий день в текущей неделе
        for (day in sortedDays) {
            if (day > currentDay) return day
        }
        // Если в этой неделе дней больше нет, берем первый день следующей недели
        return sortedDays.first()
    }

    fun cancel(reminder: Reminder) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
