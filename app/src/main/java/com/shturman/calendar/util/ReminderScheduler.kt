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
        AppLog.d("Scheduler: scheduling reminder #${reminder.id}, title=${reminder.title}")
        
        val intent = createIntent(reminder)
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
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
            } else {
                set(Calendar.HOUR_OF_DAY, reminder.hour)
                set(Calendar.MINUTE, reminder.minute)
            }
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Находим следующее актуальное время срабатывания
        val now = System.currentTimeMillis()
        val finalTime: Long
        while (true) {
            val triggerTime = calendar.timeInMillis - (reminder.remindBeforeMinutes * 60 * 1000L)

            if (triggerTime > now) {
                finalTime = triggerTime
                break
            }

            // Если триггер в прошлом, но само событие еще не наступило (окно уведомления)
            if (calendar.timeInMillis > now) {
                val diff = now - triggerTime
                if (diff < 15000L) {
                    // Считаем, что это тот самый триггер, который сейчас обрабатывается
                    if (reminder.period == ReminderPeriod.ONCE) return
                    // Для повторяющихся - переходим к следующему циклу, чтобы продвинуть календарь
                } else {
                    // Это "пропущенное" уведомление (например, телефон был выключен) - уведомляем сейчас
                    finalTime = now + 1000L
                    break
                }
            } else if (reminder.period == ReminderPeriod.ONCE) {
                // Событие уже полностью в прошлом
                return
            }

            // Переходим к следующему повтору
            when (reminder.period) {
                ReminderPeriod.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
                ReminderPeriod.WEEKLY -> {
                    val nextDay = getNextWeeklyDay(calendar, reminder.weeklyDays)
                    if (nextDay != null) {
                        if (nextDay <= calendar.get(Calendar.DAY_OF_WEEK)) {
                            calendar.add(Calendar.WEEK_OF_YEAR, 1)
                        }
                        calendar.set(Calendar.DAY_OF_WEEK, nextDay)
                    } else {
                        calendar.add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }
                ReminderPeriod.MONTHLY -> calendar.add(Calendar.MONTH, 1)
                ReminderPeriod.YEARLY -> calendar.add(Calendar.YEAR, 1)
                ReminderPeriod.ONCE -> return
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, finalTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, finalTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, finalTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            AppLog.e("SecurityException in AlarmManager", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, finalTime, pendingIntent)
        }
    }

    private fun createIntent(reminder: Reminder): Intent {
        return Intent(context, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
            putExtra("TITLE", reminder.title)
            putExtra("MELODY_URI", reminder.melodyUri)
            putExtra("VIBRATE", reminder.vibrate)
            // Добавляем action, чтобы интенты были уникальными для фильтрации в системе
            action = "com.shturman.calendar.ACTION_REMIND_${reminder.id}"
        }
    }

    private fun getNextWeeklyDay(current: Calendar, weeklyDays: String): Int? {
        val days = weeklyDays.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
        if (days.isEmpty()) return null
        
        val currentDay = current.get(Calendar.DAY_OF_WEEK)
        val sortedDays = days.sorted()
        
        // Ищем первый день, который больше текущего
        return sortedDays.firstOrNull { it > currentDay } ?: sortedDays.first()
    }

    fun cancel(reminder: Reminder) {
        val intent = createIntent(reminder)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
