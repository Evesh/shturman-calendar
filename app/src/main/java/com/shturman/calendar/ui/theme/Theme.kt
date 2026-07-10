package com.shturman.calendar.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Navy80,
    secondary = NavyGrey80,
    tertiary = Teal80,
    surface = Color(0xFF1A1C20),
    background = Color(0xFF111318),
    surfaceVariant = Color(0xFF252830),
    onSurface = Color(0xFFE2E2E6)
)

private val LightColorScheme = lightColorScheme(
    primary = Navy40,
    secondary = NavyGrey40,
    tertiary = Teal40,
    surface = Color(0xFFFFFBFE),
    background = Color(0xFFF8F9FC),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurface = Color(0xFF1C1B1F)
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

fun getThemeMode(context: Context): ThemeMode {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    return when (prefs.getString("theme_mode", "SYSTEM")) {
        "LIGHT" -> ThemeMode.LIGHT
        "DARK" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }
}

@Composable
fun КалендарьШтурманаTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> darkTheme
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
