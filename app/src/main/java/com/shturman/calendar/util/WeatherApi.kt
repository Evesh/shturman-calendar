package com.shturman.calendar.util

import android.content.Context
import com.shturman.calendar.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class WeatherData(
    val temp: Double,
    val feelsLike: Double,
    val humidity: Int,
    val description: String,
    val icon: String,
    val windSpeed: Double
)

object WeatherApi {
    private var cachedWeather: WeatherData? = null
    private var cachedLat: Double = 0.0
    private var cachedLon: Double = 0.0

    suspend fun getWeather(context: Context, lat: Double, lon: Double): WeatherData? {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val cacheTime = prefs.getLong(ApiConfig.WEATHER_CACHE_TIME_KEY, 0)
        val now = System.currentTimeMillis()

        // Check if location is significantly different (more than ~5km)
        val isLocationChanged = cachedWeather != null && 
                (Math.abs(cachedLat - lat) > 0.05 || Math.abs(cachedLon - lon) > 0.05)

        if (!isLocationChanged && cachedWeather != null && now - cacheTime < ApiConfig.WEATHER_CACHE_MAX_AGE) {
            return cachedWeather
        }

        if (!isLocationChanged && cachedWeather == null) {
            val cachedJson = prefs.getString(ApiConfig.WEATHER_CACHE_KEY, null)
            val savedLat = prefs.getFloat("weather_cache_lat", 0f).toDouble()
            val savedLon = prefs.getFloat("weather_cache_lon", 0f).toDouble()
            
            val savedLocationChanged = Math.abs(savedLat - lat) > 0.05 || Math.abs(savedLon - lon) > 0.05

            if (!savedLocationChanged && cachedJson != null && now - cacheTime < ApiConfig.WEATHER_CACHE_MAX_AGE) {
                cachedWeather = try {
                    val parts = cachedJson.split("|")
                    WeatherData(parts[0].toDouble(), parts[1].toDouble(), parts[2].toInt(), parts[3], parts[4], parts[5].toDouble())
                } catch (_: Exception) { null }
                
                if (cachedWeather != null) {
                    cachedLat = savedLat
                    cachedLon = savedLon
                    return cachedWeather
                }
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.WEATHER_API_KEY
                if (apiKey.isEmpty() || apiKey == "null") {
                    AppLog.e("WeatherApi: API Key is missing in local.properties")
                    return@withContext null
                }

                val url = "https://api.weatherapi.com/v1/current.json?key=$apiKey&q=$lat,$lon&lang=ru"
                AppLog.d("WeatherApi: fetching weatherapi.com for $lat,$lon")
                
                val request = Request.Builder().url(url).build()
                val response = try {
                    ApiConfig.okHttpClient.newCall(request).execute()
                } catch (e: Exception) {
                    throw Exception("Сеть недоступна: ${e.message}")
                }
                
                if (!response.isSuccessful) {
                    val code = response.code
                    if (code == 401 || code == 403) throw Exception("Ошибка API (неверный ключ)")
                    throw Exception("Ошибка сервера: $code")
                }

                val json = response.body?.string() ?: throw Exception("Пустой ответ от сервера")

                AppLog.d("WeatherApi: raw response = $json")
                
                val root = JSONObject(json)
                if (root.has("error")) {
                    val errorMsg = root.getJSONObject("error").getString("message")
                    throw Exception("WeatherAPI Error: $errorMsg")
                }

                val current = root.getJSONObject("current")
                val condition = current.getJSONObject("condition")
                val code = condition.getInt("code")

                val weather = WeatherData(
                    temp = current.getDouble("temp_c"),
                    feelsLike = current.getDouble("feelslike_c"),
                    humidity = current.getInt("humidity"),
                    description = condition.getString("text"),
                    icon = getWeatherIcon(code),
                    windSpeed = current.getDouble("wind_kph") / 3.6 // Convert km/h to m/s
                )

                cachedWeather = weather
                cachedLat = lat
                cachedLon = lon
                
                prefs.edit()
                    .putString(ApiConfig.WEATHER_CACHE_KEY, "${weather.temp}|${weather.feelsLike}|${weather.humidity}|${weather.description}|${weather.icon}|${weather.windSpeed}")
                    .putLong(ApiConfig.WEATHER_CACHE_TIME_KEY, now)
                    .putFloat("weather_cache_lat", lat.toFloat())
                    .putFloat("weather_cache_lon", lon.toFloat())
                    .apply()
                
                AppLog.d("WeatherApi: success - ${weather.temp}°C, ${weather.description}")
                weather
            } catch (e: Exception) {
                AppLog.e("WeatherApi error", e)
                CrashlyticsHelper.logError("WeatherApi", e.message ?: "Unknown error", e)
                null
            }
        }
    }

    private fun getWeatherIcon(code: Int): String {
        return when (code) {
            1000 -> "☀️" // Sunny / Clear
            1003 -> "⛅" // Partly cloudy
            1006, 1009 -> "☁️" // Cloudy / Overcast
            1030, 1135, 1147 -> "🌫️" // Mist / Fog
            1063, 1180, 1183, 1186, 1189, 1192, 1195, 1240, 1243, 1246 -> "🌧️" // Patchy rain / Rain
            1066, 1069, 1072, 1114, 1117, 1210, 1213, 1216, 1219, 1222, 1225, 1255, 1258 -> "❄️" // Snow / Sleet
            1087, 1273, 1276, 1279, 1282 -> "⛈️" // Thunder
            else -> "🌤️"
        }
    }
}
