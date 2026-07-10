package com.shturman.calendar.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shturman.calendar.data.Reminder
import com.shturman.calendar.data.ReminderDatabase
import com.shturman.calendar.util.CalendarIntegration
import com.shturman.calendar.util.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val database by lazy { ReminderDatabase.getDatabase(application) }
    private val dao by lazy { database.reminderDao() }
    private val scheduler by lazy { ReminderScheduler(application) }

    val reminders: StateFlow<List<Reminder>> by lazy {
        dao.getAllReminders()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun addReminder(reminder: Reminder, addToSystem: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            var finalReminder = reminder.copy(isSyncedWithSystem = addToSystem)
            if (addToSystem) {
                val systemId = CalendarIntegration.addToSystemCalendar(getApplication(), reminder)
                finalReminder = finalReminder.copy(systemEventId = systemId)
            }
            val id = dao.insertReminder(finalReminder)
            scheduler.schedule(finalReminder.copy(id = id.toInt()))
            updateWidget()
        }
    }

    fun updateReminder(reminder: Reminder, syncWithSystem: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            var updatedReminder = reminder.copy(isSyncedWithSystem = syncWithSystem)

            if (syncWithSystem && updatedReminder.isEnabled) {
                if (updatedReminder.systemEventId != null) {
                    CalendarIntegration.updateSystemCalendar(getApplication(), updatedReminder)
                } else {
                    val newSystemId = CalendarIntegration.addToSystemCalendar(getApplication(), updatedReminder)
                    updatedReminder = updatedReminder.copy(systemEventId = newSystemId)
                }
            } else {
                updatedReminder.systemEventId?.let {
                    CalendarIntegration.deleteFromSystemCalendar(getApplication(), it)
                    updatedReminder = updatedReminder.copy(systemEventId = null)
                }
            }

            dao.insertReminder(updatedReminder)

            if (updatedReminder.isEnabled) {
                scheduler.schedule(updatedReminder)
            } else {
                scheduler.cancel(updatedReminder)
            }
            updateWidget()
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteReminder(reminder)
            scheduler.cancel(reminder)
            reminder.systemEventId?.let {
                CalendarIntegration.deleteFromSystemCalendar(getApplication(), it)
            }
            updateWidget()
        }
    }

    fun toggleReminder(reminder: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = reminder.copy(isEnabled = !reminder.isEnabled)

            var systemId = updated.systemEventId
            if (!updated.isEnabled && systemId != null) {
                CalendarIntegration.deleteFromSystemCalendar(getApplication(), systemId)
                systemId = null
            } else if (updated.isEnabled && updated.isSyncedWithSystem && systemId == null) {
                systemId = CalendarIntegration.addToSystemCalendar(getApplication(), updated)
            }

            val finalReminder = updated.copy(systemEventId = systemId)
            dao.insertReminder(finalReminder)

            if (finalReminder.isEnabled) {
                scheduler.schedule(finalReminder)
            } else {
                scheduler.cancel(finalReminder)
            }
        }
    }

    fun importReminders(reminders: List<Reminder>) {
        viewModelScope.launch(Dispatchers.IO) {
            reminders.forEach { reminder ->
                val id = dao.insertReminder(reminder.copy(id = 0))
                val newReminder = reminder.copy(id = id.toInt())
                if (newReminder.isEnabled) {
                    scheduler.schedule(newReminder)
                }
            }
        }
    }

    fun syncFromSystemCalendar() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.shturman.calendar.util.AppLog.d("Sync: starting system calendar sync")
                val systemEvents = CalendarIntegration.importFromSystemCalendar(getApplication())
                com.shturman.calendar.util.AppLog.d("Sync: found ${systemEvents.size} events from system calendar")

                // Get ALL existing systemEventIds as Long set
                val existingIds = dao.getAllRemindersList()
                    .mapNotNull { it.systemEventId }
                    .toSet()
                com.shturman.calendar.util.AppLog.d("Sync: existing systemEventIds=$existingIds")

                var imported = 0
                systemEvents.forEach { event ->
                    if (event.systemEventId != null && event.systemEventId !in existingIds) {
                        val id = dao.insertReminder(event)
                        val newEvent = event.copy(id = id.toInt())
                        if (newEvent.isEnabled) {
                            scheduler.schedule(newEvent)
                        }
                        imported++
                    }
                }
                com.shturman.calendar.util.AppLog.d("Sync: imported $imported new events")
                if (imported > 0) updateWidget()
            } catch (e: Exception) {
                com.shturman.calendar.util.AppLog.e("Sync failed", e)
                com.shturman.calendar.util.CrashlyticsHelper.logError("ReminderViewModel", "syncFromSystemCalendar: ${e.message}", e)
            }
        }
    }

    private fun updateWidget() {
        val intent = android.content.Intent("com.shturman.calendar.UPDATE_WIDGET")
        getApplication<Application>().sendBroadcast(intent)
    }
}
