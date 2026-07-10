package com.shturman.calendar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Holiday::class], version = 1, exportSchema = false)
abstract class HolidayDatabase : RoomDatabase() {
    abstract fun holidayDao(): HolidayDao

    companion object {
        @Volatile
        private var INSTANCE: HolidayDatabase? = null

        fun getDatabase(context: Context): HolidayDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HolidayDatabase::class.java,
                    "holiday_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
