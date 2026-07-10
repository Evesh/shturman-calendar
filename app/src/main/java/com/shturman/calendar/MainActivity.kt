package com.shturman.calendar

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shturman.calendar.ui.ReminderViewModel
import com.shturman.calendar.ui.screens.AddReminderScreen
import com.shturman.calendar.ui.screens.ReminderListScreen
import com.shturman.calendar.ui.screens.SettingsScreen
import com.shturman.calendar.ui.screens.YearlyEventsScreen
import com.shturman.calendar.ui.theme.ThemeMode
import com.shturman.calendar.ui.theme.КалендарьШтурманаTheme
import com.shturman.calendar.ui.theme.getThemeMode
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        com.shturman.calendar.util.AppLog.d("App started")

        val app = application
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.shturman.calendar.data.ReminderDatabase.getDatabase(app).reminderDao().getAllReminders()
            } catch (e: Exception) {
                Firebase.crashlytics.recordException(e)
            }
        }
        setContent {
            var themeMode by remember { mutableStateOf(getThemeMode(this@MainActivity)) }

            КалендарьШтурманаTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val viewModel: ReminderViewModel = viewModel()

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                    val calendarSyncEnabled = prefs.getBoolean("sync_system_calendar", true)
                    val calendarGranted = permissions[Manifest.permission.READ_CALENDAR] == true
                    com.shturman.calendar.util.AppLog.d("Permissions: calendar=$calendarGranted, sync=$calendarSyncEnabled")
                    com.shturman.calendar.util.CrashlyticsHelper.logEvent("Permissions", "calendar=$calendarGranted, sync=$calendarSyncEnabled")
                    if (calendarGranted && calendarSyncEnabled) {
                        viewModel.syncFromSystemCalendar()
                    }
                }

                LaunchedEffect(Unit) {
                    withContext(Dispatchers.Main) {
                        val permissions = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                        } else {
                            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        permissions.add(Manifest.permission.READ_CALENDAR)
                        permissions.add(Manifest.permission.WRITE_CALENDAR)
                        if (permissions.isNotEmpty()) {
                            permissionLauncher.launch(permissions.toTypedArray())
                        }
                    }

                    // Check for app updates
                    val updateInfo = com.shturman.calendar.util.UpdateChecker.checkForUpdate(this@MainActivity)
                    if (updateInfo != null) {
                        showUpdateDialog(updateInfo)
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "list",
                    enterTransition = {
                        fadeIn(animationSpec = spring(dampingRatio = 0.9f, stiffness = 300f)) +
                        slideInHorizontally(animationSpec = spring(dampingRatio = 0.85f, stiffness = 200f)) { it / 5 }
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                        slideOutHorizontally(animationSpec = tween(200, easing = FastOutSlowInEasing)) { -it / 8 }
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = spring(dampingRatio = 0.9f, stiffness = 300f)) +
                        slideInHorizontally(animationSpec = spring(dampingRatio = 0.85f, stiffness = 200f)) { -it / 5 }
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                        slideOutHorizontally(animationSpec = tween(200, easing = FastOutSlowInEasing)) { it / 8 }
                    }
                ) {
                    composable("list") {
                        ReminderListScreen(
                            viewModel = viewModel,
                            onAddReminder = { y, m, d -> navController.navigate("add?year=$y&month=$m&day=$d") },
                            onEditReminder = { id -> navController.navigate("edit/$id") },
                            onSettings = { navController.navigate("settings") },
                            onShowYearly = { navController.navigate("yearly") }
                        )
                    }
                    composable(
                        "yearly",
                        enterTransition = {
                            fadeIn(animationSpec = spring(dampingRatio = 0.9f, stiffness = 280f)) +
                            slideInHorizontally(animationSpec = spring(dampingRatio = 0.85f, stiffness = 200f)) { it / 4 }
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                            slideOutHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing)) { it / 6 }
                        }
                    ) {
                        YearlyEventsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onEditReminder = { id -> navController.navigate("edit/$id") }
                        )
                    }
                    composable(
                        "add?year={year}&month={month}&day={day}",
                        arguments = listOf(
                            navArgument("year") { type = NavType.IntType; defaultValue = -1 },
                            navArgument("month") { type = NavType.IntType; defaultValue = -1 },
                            navArgument("day") { type = NavType.IntType; defaultValue = -1 }
                        ),
                        enterTransition = {
                            fadeIn(animationSpec = spring(dampingRatio = 0.85f, stiffness = 250f)) +
                            slideInVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 180f)) { it / 4 }
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                            slideOutVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) { it / 6 }
                        }
                    ) { backStackEntry ->
                        val year = backStackEntry.arguments?.getInt("year")?.takeIf { it != -1 }
                        val month = backStackEntry.arguments?.getInt("month")?.takeIf { it != -1 }
                        val day = backStackEntry.arguments?.getInt("day")?.takeIf { it != -1 }
                        AddReminderScreen(
                            viewModel = viewModel,
                            initialYear = year,
                            initialMonth = month,
                            initialDay = day,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        "edit/{reminderId}",
                        arguments = listOf(navArgument("reminderId") { type = NavType.StringType }),
                        enterTransition = {
                            fadeIn(animationSpec = spring(dampingRatio = 0.85f, stiffness = 250f)) +
                            slideInVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 180f)) { it / 4 }
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                            slideOutVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) { it / 6 }
                        }
                    ) { backStackEntry ->
                        val reminderId = backStackEntry.arguments?.getString("reminderId")?.toIntOrNull()
                        AddReminderScreen(viewModel = viewModel, reminderId = reminderId, onBack = { navController.popBackStack() })
                    }
                    composable(
                        "settings",
                        enterTransition = {
                            fadeIn(animationSpec = spring(dampingRatio = 0.9f, stiffness = 280f)) +
                            slideInHorizontally(animationSpec = spring(dampingRatio = 0.85f, stiffness = 200f)) { it / 4 }
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                            slideOutHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing)) { it / 6 }
                        }
                    ) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onThemeChanged = { themeMode = getThemeMode(this@MainActivity) }
                        )
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(updateInfo: com.shturman.calendar.util.UpdateInfo) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Доступно обновление")
            .setMessage("${updateInfo.message}\n\nТекущая версия: ${BuildConfig.VERSION_NAME}\nНовая версия: ${updateInfo.version}")
            .setPositiveButton("Обновить") { _, _ ->
                com.shturman.calendar.util.UpdateChecker.openDownloadUrl(this, updateInfo.downloadUrl)
            }
            .setNegativeButton("Позже", null)
            .show()
    }
}
