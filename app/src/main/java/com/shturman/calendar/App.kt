package com.shturman.calendar

import android.app.Application
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        com.google.firebase.FirebaseApp.initializeApp(this)

        // Enable Crashlytics
        Firebase.crashlytics.setCrashlyticsCollectionEnabled(true)

        // Debug: verify initialization
        Log.d("CrashlyticsInit", "Crashlytics enabled: ${Firebase.crashlytics.isCrashlyticsCollectionEnabled}")
        Firebase.crashlytics.log("App initialized")
        Firebase.crashlytics.setCustomKey("app_version", "1.0")

        com.shturman.calendar.util.AppLog.init(this)
        com.shturman.calendar.util.AppLog.d("App initialized with Crashlytics")
    }
}
