package com.shturman.calendar.data

import android.content.Context
import com.shturman.calendar.util.AppLog
import com.shturman.calendar.util.HolidayApi
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class HolidayRepository(private val context: Context) {
    private val db = HolidayDatabase.getDatabase(context)
    private val dao = db.holidayDao()
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun getHolidaysForMonth(year: Int, month: Int): Flow<List<Holiday>> {
        return dao.getHolidaysForMonth(year, month)
    }

    suspend fun getHolidaysForMonthList(year: Int, month: Int): List<Holiday> {
        return dao.getHolidaysForMonthList(year, month)
    }

    suspend fun syncIfNeeded() {
        val lastSync = prefs.getLong("holiday_last_sync", 0)
        val now = System.currentTimeMillis()
        val oneMonth = 30L * 24 * 60 * 60 * 1000

        if (now - lastSync > oneMonth || lastSync == 0L) {
            AppLog.d("HolidayRepo: syncing holidays for ${Calendar.getInstance().get(Calendar.YEAR)}")
            syncHolidays(Calendar.getInstance().get(Calendar.YEAR))
            prefs.edit().putLong("holiday_last_sync", now).apply()
        }
    }

    suspend fun syncHolidays(year: Int) {
        val apiHolidays = HolidayApi.fetchHolidays(year)
        if (apiHolidays.isEmpty()) {
            AppLog.d("HolidayRepo: no holidays from API")
            return
        }

        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
        val holidays = apiHolidays.mapNotNull { api ->
            try {
                val dateStr = api.date ?: return@mapNotNull null
                val date = dateFormat.parse(dateStr) ?: return@mapNotNull null
                val cal = Calendar.getInstance().apply { time = date }

                Holiday(
                    id = api.id,
                    name = api.name,
                    date = dateStr,
                    type = api.type,
                    year = cal.get(Calendar.YEAR),
                    month = cal.get(Calendar.MONTH) + 1,
                    day = cal.get(Calendar.DAY_OF_MONTH)
                )
            } catch (e: Exception) {
                AppLog.e("HolidayRepo: parse error for ${api.name}", e)
                null
            }
        }

        dao.deleteYear(year)
        dao.insertAll(holidays)
        AppLog.d("HolidayRepo: saved ${holidays.size} holidays for $year")
    }
}
