package com.shturman.calendar.util

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*

data class HolidayResponse(
    val status: Int,
    val holidays: List<ApiHoliday>?
)

data class ApiHoliday(
    val id: Int,
    val name: String,
    val date: String?,       // DD.MM.YYYY
    val holiday: String?,    // MMDD for recurring
    val type: Int
)

object HolidayApi {
    private const val API_BASE = "https://htmlweb.ru/json/calendar/list"

    suspend fun fetchHolidays(year: Int): List<ApiHoliday> {
        return withContext(Dispatchers.IO) {
            try {
                // Запрашиваем на весь год
                val dFrom = "01.01.$year"
                val dTo = "31.12.$year"
                val url = "$API_BASE?d_from=$dFrom&d_to=$dTo&perpage=500"
                AppLog.d("HolidayApi: fetching holidays for $year")
                
                val request = Request.Builder().url(url).build()
                val response = ApiConfig.okHttpClient.newCall(request).execute()
                val json = response.body?.string() ?: throw Exception("Empty response body")
                
                val responseData = Gson().fromJson(json, HolidayResponse::class.java)
                val allHolidays = responseData.holidays ?: emptyList()
                
                // Фильтруем: 1-Гос, 2-Церк, 3-Проф.
                val filtered = allHolidays.filter { holiday ->
                    val isHolidayType = holiday.type in 1..3
                    val name = holiday.name.trim()
                    val lowerName = name.lowercase()
                    
                    // Исключаем обычные выходные и технические записи о переносах
                    val isGenericWeekend = lowerName == "суббота" || 
                                          lowerName == "воскресенье" || 
                                          lowerName == "выходной день" ||
                                          lowerName == "выходной" ||
                                          (lowerName.startsWith("перенос") && !lowerName.contains("праздник"))
                    
                    isHolidayType && name.isNotEmpty() && !isGenericWeekend
                }
                
                AppLog.d("HolidayApi: total ${allHolidays.size}, after filtering (holidays only): ${filtered.size}")
                filtered
            } catch (e: Exception) {
                AppLog.e("HolidayApi error", e)
                CrashlyticsHelper.logError("HolidayApi", e.message ?: "Unknown error", e)
                emptyList()
            }
        }
    }
}
