package com.shturman.calendar.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.shturman.calendar.R
import com.shturman.calendar.data.ReminderDatabase
import com.shturman.calendar.util.AppLog
import com.shturman.calendar.util.CrashlyticsHelper
import com.shturman.calendar.util.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        val reminderId = intent.getIntExtra("REMINDER_ID", -1)
        val title = intent.getStringExtra("TITLE") ?: context.getString(R.string.notification_default_title)
        var melodyUriString = intent.getStringExtra("MELODY_URI")
        val vibrate = intent.getBooleanExtra("VIBRATE", true)

        // Если в напоминании нет мелодии, берем из общих настроек
        if (melodyUriString == null) {
            val sharedPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            melodyUriString = sharedPrefs.getString("default_melody", null)
        }

        try {
            showNotification(context, reminderId, title, melodyUriString, vibrate)
        } catch (e: Exception) {
            AppLog.e("ReminderReceiver: showNotification failed", e)
            CrashlyticsHelper.logError("ReminderReceiver", "showNotification: ${e.message}", e)
            try {
                showNotification(context, reminderId, title, null, vibrate)
            } catch (_: Exception) {}
        }

        if (reminderId != -1) {
            val database = ReminderDatabase.getDatabase(context)
            val scheduler = ReminderScheduler(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val reminder = database.reminderDao().getReminderById(reminderId)
                    if (reminder != null && reminder.isEnabled) {
                        scheduler.schedule(reminder)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            pendingResult.finish()
        }
    }

    private fun showNotification(context: Context, reminderId: Int, title: String, melodyUriString: String?, vibrate: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sharedPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        
        // Проверяем, включен ли звук вообще
        val isSoundEnabled = sharedPrefs.getBoolean("sound_enabled", true)
        
        // 1. Определяем итоговый URI мелодии
        val finalMelodyUriString = melodyUriString ?: sharedPrefs.getString("default_melody", null)
        val finalSoundUri = if (isSoundEnabled && !finalMelodyUriString.isNullOrEmpty()) {
            Uri.parse(finalMelodyUriString)
        } else if (isSoundEnabled) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            null
        }

        // 2. Создаем уникальный ID канала для этой комбинации звука и вибрации
        val soundId = finalSoundUri?.toString()?.hashCode() ?: 0
        val vibrationId = if (vibrate) 1 else 0
        val channelId = "reminders_${soundId}_v$vibrationId"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val channelName = context.getString(R.string.notification_channel_name)
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_description)
                    enableVibration(vibrate)
                    if (vibrate) {
                        vibrationPattern = longArrayOf(0, 400, 200, 400)
                    }
                    setSound(
                        finalSoundUri,
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        // Используем reminderId вместо title.hashCode() для предотвращения конфликтов
        val notificationId = if (reminderId != -1) reminderId else title.hashCode()

        val doneIntent = Intent(context, DismissReceiver::class.java).apply {
            putExtra("NOTIFICATION_ID", notificationId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context, notificationId + 200000, doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze15Intent = Intent(context, SnoozeReceiver::class.java).apply {
            putExtra("NOTIFICATION_ID", notificationId)
            putExtra("TITLE", title)
            putExtra("MELODY_URI", melodyUriString)
            putExtra("REMINDER_ID", reminderId)
            putExtra("SNOOZE_MINUTES", 15)
        }
        val snooze15PendingIntent = PendingIntent.getBroadcast(
            context, notificationId + 300000, snooze15Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze60Intent = Intent(context, SnoozeReceiver::class.java).apply {
            putExtra("NOTIFICATION_ID", notificationId)
            putExtra("TITLE", title)
            putExtra("MELODY_URI", melodyUriString)
            putExtra("REMINDER_ID", reminderId)
            putExtra("SNOOZE_MINUTES", 60)
        }
        val snooze60PendingIntent = PendingIntent.getBroadcast(
            context, notificationId + 400000, snooze60Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Используем существующую иконку
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.snooze_15), snooze15PendingIntent)
            .addAction(0, context.getString(R.string.done_action), donePendingIntent)

        // Для версий ниже Android 8.0
        if (finalSoundUri != null) {
            builder.setSound(finalSoundUri)
        }
        
        if (vibrate) {
            builder.setVibrate(longArrayOf(0, 400, 200, 400))
        } else {
            builder.setVibrate(longArrayOf(0))
        }

        notificationManager.notify(notificationId, builder.build())
    }
}
