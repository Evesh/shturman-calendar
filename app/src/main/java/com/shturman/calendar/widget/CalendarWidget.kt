package com.shturman.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.shturman.calendar.MainActivity
import com.shturman.calendar.R
import com.shturman.calendar.data.ReminderDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CalendarWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.shturman.calendar.UPDATE_WIDGET") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, CalendarWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.calendar_widget)
        
        val dateStr = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
        views.setTextViewText(R.id.widget_date, dateStr)

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        CoroutineScope(Dispatchers.IO).launch {
            val db = ReminderDatabase.getDatabase(context)
            val now = Calendar.getInstance()
            val year = now.get(Calendar.YEAR)
            val month = now.get(Calendar.MONTH) + 1
            val day = now.get(Calendar.DAY_OF_MONTH)
            
            val reminders = db.reminderDao().getRemindersForDay(day, month, year)
            
            if (reminders.isEmpty()) {
                views.setTextViewText(R.id.widget_event, context.getString(R.string.empty_day_title))
            } else {
                val firstEvent = reminders.first()
                val timeStr = if (firstEvent.isAllDay) "" else String.format("%02d:%02d ", firstEvent.hour, firstEvent.minute)
                views.setTextViewText(R.id.widget_event, "$timeStr${firstEvent.title}")
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
