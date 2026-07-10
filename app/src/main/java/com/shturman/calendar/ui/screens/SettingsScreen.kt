package com.shturman.calendar.ui.screens

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shturman.calendar.R
import com.shturman.calendar.ui.ReminderViewModel
import com.shturman.calendar.ui.theme.ThemeMode
import com.shturman.calendar.ui.theme.getThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ReminderViewModel,
    onBack: () -> Unit,
    onThemeChanged: () -> Unit = {}
) {
    val reminders by viewModel.reminders.collectAsState()
    SettingsContent(
        reminders = reminders,
        viewModel = viewModel,
        onBack = onBack,
        onThemeChanged = onThemeChanged
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    reminders: List<com.shturman.calendar.data.Reminder>,
    viewModel: ReminderViewModel? = null,
    onBack: () -> Unit,
    onThemeChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val defaultMelodyLabel = stringResource(R.string.default_melody)
    var defaultMelodyUri by remember {
        mutableStateOf(sharedPrefs.getString("default_melody", null)?.let { Uri.parse(it) })
    }
    var melodyName by remember {
        mutableStateOf(defaultMelodyUri?.let {
            try { RingtoneManager.getRingtone(context, it)?.getTitle(context) }
            catch (_: Exception) { null }
        } ?: defaultMelodyLabel)
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }
            defaultMelodyUri = uri
            sharedPrefs.edit().putString("default_melody", uri?.toString()).apply()
            melodyName = uri?.let {
                try { RingtoneManager.getRingtone(context, it)?.getTitle(context) }
                catch (_: Exception) { null }
            } ?: defaultMelodyLabel
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.select_melody_picker_title))
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, defaultMelodyUri)
            }
            ringtoneLauncher.launch(intent)
        } else {
            android.widget.Toast.makeText(context, "Для выбора звука нужно разрешение", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var themeMode by remember { mutableStateOf(getThemeMode(context)) }
    var soundEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("sound_enabled", true)) }
    var vibrateEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("vibrate_enabled", true)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_description))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Theme
            SectionHeader(stringResource(R.string.theme_section_title))
            CardSection {
                data class ThemeOption(val mode: ThemeMode, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)
                val options = listOf(
                    ThemeOption(ThemeMode.LIGHT, Icons.Default.LightMode, stringResource(R.string.theme_light)),
                    ThemeOption(ThemeMode.DARK, Icons.Default.DarkMode, stringResource(R.string.theme_dark)),
                    ThemeOption(ThemeMode.SYSTEM, Icons.Default.PhoneAndroid, stringResource(R.string.theme_system))
                )
                options.forEach { option ->
                    val selected = themeMode == option.mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                themeMode = option.mode
                                sharedPrefs.edit().putString("theme_mode", option.mode.name).apply()
                                onThemeChanged()
                            }
                            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) else Modifier)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(option.icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            SectionHeader(stringResource(R.string.sound_section_title))
            CardSection {
                OutlinedCard(
                    onClick = {
                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            android.Manifest.permission.READ_MEDIA_AUDIO
                        } else {
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                        }

                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, permission
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.select_melody_picker_title))
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, defaultMelodyUri)
                            }
                            ringtoneLauncher.launch(intent)
                        } else {
                            mediaPermissionLauncher.launch(permission)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.default_melody_status_format, melodyName), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(stringResource(R.string.change_melody_button), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.notification_sound), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = soundEnabled, onCheckedChange = { soundEnabled = it; sharedPrefs.edit().putBoolean("sound_enabled", it).apply() })
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.Vibration, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.notification_vibration), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = vibrateEnabled, onCheckedChange = { vibrateEnabled = it; sharedPrefs.edit().putBoolean("vibrate_enabled", it).apply() })
                }
            }

            // Sync settings
            SectionHeader("Синхронизация")
            CardSection {
                var calendarSync by remember { mutableStateOf(sharedPrefs.getBoolean("sync_system_calendar", true)) }
                var holidaysSync by remember { mutableStateOf(sharedPrefs.getBoolean("sync_holidays", false)) }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Системный календарь", style = MaterialTheme.typography.bodyMedium)
                        Text("Импорт событий при запуске", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = calendarSync, onCheckedChange = {
                        calendarSync = it
                        sharedPrefs.edit().putBoolean("sync_system_calendar", it).apply()
                    })
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.Celebration, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Праздники", style = MaterialTheme.typography.bodyMedium)
                        Text("Государственные и церковные", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = holidaysSync, onCheckedChange = {
                        holidaysSync = it
                        sharedPrefs.edit().putBoolean("sync_holidays", it).apply()
                    })
                }
            }

            // Weather settings
            SectionHeader("Погода")
            CardSection {
                var weatherEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("weather_enabled", false)) }
                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        android.widget.Toast.makeText(context, "Геолокация включена", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        weatherEnabled = false
                        sharedPrefs.edit().putBoolean("weather_enabled", false).apply()
                        android.widget.Toast.makeText(context, "Для погоды нужна геолокация", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.WbSunny, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Показывать погоду", style = MaterialTheme.typography.bodyMedium)
                        Text("Текущая погода по геолокации", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = weatherEnabled, onCheckedChange = { enabled ->
                        if (enabled) {
                            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                        weatherEnabled = enabled
                        sharedPrefs.edit().putBoolean("weather_enabled", enabled).apply()
                    })
                }
            }

            // Data
            SectionHeader(stringResource(R.string.data_section_title))
            CardSection {
                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        val importedReminders = com.shturman.calendar.util.IcsImport.import(context, it)
                        if (importedReminders.isNotEmpty()) {
                            viewModel?.importReminders(importedReminders)
                            android.widget.Toast.makeText(context, context.getString(R.string.import_success, importedReminders.size), android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, R.string.import_error, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Export Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                val success = com.shturman.calendar.util.IcsExport.export(context, reminders)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    if (!success) {
                                        android.widget.Toast.makeText(context, "Ошибка экспорта", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FileUpload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.export_button), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("Сохранить все события в файл", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Import Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            importLauncher.launch(arrayOf("text/calendar", "application/ics"))
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FileDownload, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.import_button), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("Восстановить события из файла", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }

            // About
            SectionHeader(stringResource(R.string.about_section_title))
            var cardClicks by remember { mutableIntStateOf(0) }
            var reportSent by remember { mutableStateOf(false) }
            var firebaseError by remember { mutableStateOf<String?>(null) }

            CardSection(
                modifier = Modifier.clickable {
                    cardClicks++
                    if (cardClicks >= 5 && !reportSent) {
                        try {
                            val crashlytics = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                            val logFile = com.shturman.calendar.util.AppLog.getLogFile()
                            val logText = logFile?.readText() ?: "No logs"
                            crashlytics.setCustomKey("card_click_report", true)
                            crashlytics.log(logText.take(4000))
                            crashlytics.recordException(Exception("Card click report"))
                            crashlytics.sendUnsentReports()
                            reportSent = true
                            firebaseError = null
                            android.widget.Toast.makeText(context, "Отчёт отправлен", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            firebaseError = "Ошибка: ${e.message}"
                            android.widget.Toast.makeText(context, "Не удалось отправить", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else if (cardClicks >= 5 && reportSent) {
                        android.widget.Toast.makeText(context, "Отчёт уже отправлен", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(stringResource(R.string.app_version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (firebaseError != null) {
                    Text(firebaseError!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.app_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun CardSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    com.shturman.calendar.ui.theme.КалендарьШтурманаTheme {
        SettingsContent(
            reminders = emptyList(),
            onBack = {}
        )
    }
}
