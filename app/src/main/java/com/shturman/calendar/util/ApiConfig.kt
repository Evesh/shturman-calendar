package com.shturman.calendar.util

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ApiConfig {
    const val WEATHER_CACHE_KEY = "weather_cache"
    const val WEATHER_CACHE_TIME_KEY = "weather_cache_time"
    const val WEATHER_CACHE_MAX_AGE = 60 * 60 * 1000L // 1 час

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
