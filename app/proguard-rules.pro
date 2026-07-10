# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.shturman.calendar.data.Reminder { *; }
-keep class com.shturman.calendar.data.Holiday { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements com.google.gson.TypeAdapterFactory

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Compose
-dontwarn androidx.compose.**

# Firebase
-keep class com.google.firebase.** { *; }
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
