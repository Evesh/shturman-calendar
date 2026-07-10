package com.shturman.calendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReminderPeriod {
    ONCE, DAILY, WEEKLY, MONTHLY, YEARLY
}

enum class ReminderColor(val value: Int) {
    DEFAULT(0),
    RED(1),
    ORANGE(2),
    YELLOW(3),
    GREEN(4),
    BLUE(5),
    PURPLE(6),
    PINK(7)
}

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val hour: Int,
    val minute: Int,
    val dayOfMonth: Int,
    val month: Int, // 1-12
    val year: Int,
    val period: ReminderPeriod,
    val melodyUri: String?,
    val isEnabled: Boolean = true,
    val systemEventId: Long? = null,
    val isSyncedWithSystem: Boolean = false,
    val color: ReminderColor = ReminderColor.DEFAULT,
    val weeklyDays: String = "",
    val remindBeforeMinutes: Int = 0, // 0 = at time, 5/15/30/60
    val vibrate: Boolean = true,
    val isAllDay: Boolean = false
)
