package com.shturman.calendar.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shturman.calendar.data.ReminderDatabase
import com.shturman.calendar.util.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            val database = ReminderDatabase.getDatabase(context)
            val scheduler = ReminderScheduler(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val reminders = database.reminderDao().getAllReminders().first()
                    reminders.forEach { reminder ->
                        if (reminder.isEnabled) {
                            scheduler.schedule(reminder)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
