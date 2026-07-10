package com.shturman.calendar.util

import com.google.firebase.crashlytics.FirebaseCrashlytics

object CrashlyticsHelper {
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("error_tag", tag)
            crashlytics.setCustomKey("error_message", message)
            crashlytics.log("[$tag] $message")
            if (throwable != null) {
                crashlytics.recordException(throwable)
            }
        } catch (_: Exception) {
            // Crashlytics not available
        }
    }

    fun logEvent(tag: String, message: String) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("event_tag", tag)
            crashlytics.log("[$tag] $message")
        } catch (_: Exception) {
            // Crashlytics not available
        }
    }
}
