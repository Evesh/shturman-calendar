package com.shturman.calendar.receiver

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shturman.calendar.R

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("NOTIFICATION_ID", 0)
        val title = intent.getStringExtra("TITLE") ?: context.getString(R.string.notification_default_title)
        val melodyUri = intent.getStringExtra("MELODY_URI")
        val reminderId = intent.getIntExtra("REMINDER_ID", -1)
        val snoozeMinutes = intent.getIntExtra("SNOOZE_MINUTES", 15)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminderId)
            putExtra("TITLE", title)
            putExtra("MELODY_URI", melodyUri)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId + 10000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + snoozeMinutes * 60 * 1000L,
            pendingIntent
        )
    }
}
