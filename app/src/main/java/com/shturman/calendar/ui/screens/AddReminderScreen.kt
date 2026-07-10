package com.shturman.calendar.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shturman.calendar.R
import com.shturman.calendar.data.Reminder
import com.shturman.calendar.data.ReminderColor
import com.shturman.calendar.data.ReminderPeriod
import com.shturman.calendar.ui.ReminderViewModel
import com.shturman.calendar.ui.theme.reminderColor
import com.shturman.calendar.util.AppLog
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.media.RingtoneManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.tooling.preview.Preview
import com.shturman.calendar.ui.theme.КалендарьШтурманаTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderScreen(
    viewModel: ReminderViewModel,
    reminderId: Int? = null,
    initialYear: Int? = null,
    initialMonth: Int? = null,
    initialDay: Int? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val defaultMelodyName = stringResource(R.string.default_melody)

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isAllDay by remember { mutableStateOf(false) }
    var selectedDate by remember {
        mutableStateOf(
            if (initialYear != null && initialMonth != null && initialDay != null) {
                Calendar.getInstance().apply {
                    set(initialYear, initialMonth - 1, initialDay)
                }
            } else {
                Calendar.getInstance()
            }
        )
    }
    var selectedTime by remember { mutableStateOf(Calendar.getInstance()) }
    var period by remember { mutableStateOf(ReminderPeriod.ONCE) }

    // Load defaults from shared prefs — initialize immediately
    val sharedPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val defaultRemindBefore = sharedPrefs.getInt("remind_before_minutes", 0)
    val defaultVibrate = sharedPrefs.getBoolean("vibrate_enabled", true)
    val defaultMelodyUriString = sharedPrefs.getString("default_melody", null)
    AppLog.d("AddReminder: default_melody from prefs=$defaultMelodyUriString")
    val defaultMelodyUriParsed = defaultMelodyUriString?.let { try { Uri.parse(it) } catch (_: Exception) { null } }
    AppLog.d("AddReminder: parsed melodyUri=$defaultMelodyUriParsed")
    val defaultMelodyResolvedName = defaultMelodyUriParsed?.let {
        try { RingtoneManager.getRingtone(context, it).getTitle(context) } catch (_: Exception) { null }
    } ?: defaultMelodyName

    var melodyUri by remember { mutableStateOf(defaultMelodyUriParsed) }
    var melodyName by remember { mutableStateOf(defaultMelodyResolvedName) }
    var addToSystemCalendar by remember { mutableStateOf(false) }
    var isEnabled by remember { mutableStateOf(true) }
    var selectedColor by remember { mutableStateOf(ReminderColor.DEFAULT) }
    var weeklyDays by remember { mutableStateOf(setOf(2)) }
    var remindBefore by remember { mutableIntStateOf(defaultRemindBefore) }
    var vibrate by remember { mutableStateOf(defaultVibrate) }

    val handleBack = {
        if (title.isNotEmpty() || description.isNotEmpty()) {
            Toast.makeText(context, R.string.event_not_saved, Toast.LENGTH_SHORT).show()
        }
        onBack()
    }

    BackHandler(onBack = handleBack)

    LaunchedEffect(reminderId) {
        if (reminderId != null) {
            viewModel.reminders.value.find { it.id == reminderId }?.let { r ->
                AppLog.d("Loading reminder #$reminderId: melodyUri=${r.melodyUri}")
                title = r.title
                description = r.description
                selectedDate = Calendar.getInstance().apply {
                    set(r.year, r.month - 1, r.dayOfMonth)
                }
                selectedTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, r.hour)
                    set(Calendar.MINUTE, r.minute)
                }
                period = r.period
                isAllDay = r.isAllDay
                melodyUri = r.melodyUri?.let { Uri.parse(it) }
                AppLog.d("Parsed melodyUri: $melodyUri")
                melodyName = melodyUri?.let {
                    try { RingtoneManager.getRingtone(context, it).getTitle(context) }
                    catch (e: Exception) { AppLog.e("Failed to get ringtone name", e); defaultMelodyName }
                } ?: defaultMelodyName
                AppLog.d("Loaded melodyName: $melodyName")
                addToSystemCalendar = r.isSyncedWithSystem
                isEnabled = r.isEnabled
                selectedColor = r.color
                weeklyDays = r.weeklyDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                    .ifEmpty { setOf(2) }
                remindBefore = r.remindBeforeMinutes
                vibrate = r.vibrate
            }
        }
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        AppLog.d("Ringtone picker result: resultCode=${result.resultCode}")
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            AppLog.d("Ringtone picked URI: $uri")
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }
            melodyUri = uri
            melodyName = uri?.let {
                try { RingtoneManager.getRingtone(context, it)?.getTitle(context) }
                catch (_: Exception) { null }
            } ?: defaultMelodyName
            AppLog.d("Ringtone name resolved: $melodyName")
        } else {
            AppLog.d("Ringtone picker cancelled or failed")
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.select_melody_picker_title))
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, melodyUri)
            }
            ringtoneLauncher.launch(intent)
        } else {
            android.widget.Toast.makeText(context, "Для выбора звука нужно разрешение", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.timeInMillis
    )
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = Calendar.getInstance().apply { timeInMillis = it }
                    }
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AddReminderContent(
        reminderId = reminderId,
        title = title,
        onTitleChange = { if (it.length <= 128) title = it },
        description = description,
        onDescriptionChange = { description = it },
        isAllDay = isAllDay,
        onAllDayChange = { isAllDay = it },
        selectedDate = selectedDate,
        onDateClick = { showDatePicker = true },
        selectedTime = selectedTime,
        onTimeClick = {
            TimePickerDialog(
                context,
                { _, h, m ->
                    selectedTime = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, h)
                        set(Calendar.MINUTE, m)
                    }
                },
                selectedTime.get(Calendar.HOUR_OF_DAY),
                selectedTime.get(Calendar.MINUTE),
                true
            ).show()
        },
        period = period,
        onPeriodChange = { period = it },
        selectedColor = selectedColor,
        onColorChange = { selectedColor = it },
        weeklyDays = weeklyDays,
        onWeeklyDaysChange = { weeklyDays = it },
        remindBefore = remindBefore,
        onRemindBeforeChange = { remindBefore = it },
        vibrate = vibrate,
        onVibrateChange = { vibrate = it },
        melodyName = melodyName,
        onMelodyClick = {
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
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, melodyUri)
                }
                ringtoneLauncher.launch(intent)
            } else {
                mediaPermissionLauncher.launch(permission)
            }
        },
        addToSystemCalendar = addToSystemCalendar,
        onAddToSystemCalendarChange = { addToSystemCalendar = it },
        onSave = {
            val savedUri = melodyUri?.toString()
            val noTitle = context.getString(R.string.no_title)
            val reminder = Reminder(
                id = reminderId ?: 0,
                title = if (title.isBlank()) noTitle else title,
                description = description,
                hour = selectedTime.get(Calendar.HOUR_OF_DAY),
                minute = selectedTime.get(Calendar.MINUTE),
                dayOfMonth = selectedDate.get(Calendar.DAY_OF_MONTH),
                month = selectedDate.get(Calendar.MONTH) + 1,
                year = selectedDate.get(Calendar.YEAR),
                period = period,
                melodyUri = savedUri,
                isEnabled = isEnabled,
                systemEventId = if (reminderId != null) {
                    viewModel.reminders.value.find { it.id == reminderId }?.systemEventId
                } else null,
                color = selectedColor,
                weeklyDays = weeklyDays.joinToString(","),
                remindBeforeMinutes = remindBefore,
                vibrate = vibrate,
                isAllDay = isAllDay
            )
            if (reminderId == null) {
                viewModel.addReminder(reminder, addToSystemCalendar)
            } else {
                viewModel.updateReminder(reminder, addToSystemCalendar)
            }
            onBack()
        },
        onBack = handleBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderContent(
    reminderId: Int?,
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    isAllDay: Boolean,
    onAllDayChange: (Boolean) -> Unit,
    selectedDate: Calendar,
    onDateClick: () -> Unit,
    selectedTime: Calendar,
    onTimeClick: () -> Unit,
    period: ReminderPeriod,
    onPeriodChange: (ReminderPeriod) -> Unit,
    selectedColor: ReminderColor,
    onColorChange: (ReminderColor) -> Unit,
    weeklyDays: Set<Int>,
    onWeeklyDaysChange: (Set<Int>) -> Unit,
    remindBefore: Int,
    onRemindBeforeChange: (Int) -> Unit,
    vibrate: Boolean,
    onVibrateChange: (Boolean) -> Unit,
    melodyName: String,
    onMelodyClick: () -> Unit,
    addToSystemCalendar: Boolean,
    onAddToSystemCalendarChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (reminderId == null) stringResource(R.string.new_reminder_title) else stringResource(R.string.edit_reminder_title)) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.event_title_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text(
                        text = "${title.length} / 128",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )

            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.description_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // All Day switch
            Surface(
                onClick = { onAllDayChange(!isAllDay) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.EventAvailable, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Весь день", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isAllDay, onCheckedChange = onAllDayChange)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedCard(
                    onClick = onDateClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                R.string.date_format,
                                selectedDate.get(Calendar.DAY_OF_MONTH),
                                SimpleDateFormat("MMM", Locale.getDefault()).format(selectedDate.time)
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (!isAllDay) {
                    OutlinedCard(
                        onClick = onTimeClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.time_format,
                                    selectedTime.get(Calendar.HOUR_OF_DAY),
                                    selectedTime.get(Calendar.MINUTE)
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Period selection — dropdown
            var periodExpanded by remember { mutableStateOf(false) }
            val periodLabels = mapOf(
                ReminderPeriod.ONCE to stringResource(R.string.period_once),
                ReminderPeriod.DAILY to stringResource(R.string.period_daily),
                ReminderPeriod.WEEKLY to stringResource(R.string.period_weekly),
                ReminderPeriod.MONTHLY to stringResource(R.string.period_monthly),
                ReminderPeriod.YEARLY to stringResource(R.string.period_yearly)
            )
            Text(stringResource(R.string.repeat_label), style = MaterialTheme.typography.labelLarge)
            ExposedDropdownMenuBox(expanded = periodExpanded, onExpandedChange = { periodExpanded = it }) {
                OutlinedTextField(
                    value = periodLabels[period] ?: "",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                ExposedDropdownMenu(expanded = periodExpanded, onDismissRequest = { periodExpanded = false }) {
                    ReminderPeriod.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(periodLabels[p] ?: "") },
                            onClick = { onPeriodChange(p); periodExpanded = false },
                            leadingIcon = {
                                Icon(
                                    when (p) {
                                        ReminderPeriod.ONCE -> Icons.Default.Schedule
                                        ReminderPeriod.DAILY -> Icons.Default.Repeat
                                        ReminderPeriod.WEEKLY -> Icons.Default.DateRange
                                        ReminderPeriod.MONTHLY -> Icons.Default.CalendarMonth
                                        ReminderPeriod.YEARLY -> Icons.Default.EventRepeat
                                    },
                                    null, tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }
            }

            // Weekly day selection
            if (period == ReminderPeriod.WEEKLY) {
                Text(stringResource(R.string.repeat_label), style = MaterialTheme.typography.labelSmall)
                val dayLabels = listOf(
                    2 to stringResource(R.string.week_mon),
                    3 to stringResource(R.string.week_tue),
                    4 to stringResource(R.string.week_wed),
                    5 to stringResource(R.string.week_thu),
                    6 to stringResource(R.string.week_fri),
                    7 to stringResource(R.string.week_sat),
                    1 to stringResource(R.string.week_sun)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dayLabels.forEach { (day, label) ->
                        val isSelected = day in weeklyDays
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    CircleShape
                                )
                                .clickable {
                                    onWeeklyDaysChange(if (isSelected) weeklyDays - day else weeklyDays + day)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Color picker — dropdown
            var colorExpanded by remember { mutableStateOf(false) }
            val colorLabels = mapOf(
                ReminderColor.DEFAULT to stringResource(R.string.color_default),
                ReminderColor.RED to "Красный",
                ReminderColor.ORANGE to "Оранжевый",
                ReminderColor.YELLOW to "Жёлтый",
                ReminderColor.GREEN to "Зелёный",
                ReminderColor.BLUE to "Синий",
                ReminderColor.PURPLE to "Фиолетовый",
                ReminderColor.PINK to "Розовый"
            )
            Text(stringResource(R.string.color_label), style = MaterialTheme.typography.labelLarge)
            ExposedDropdownMenuBox(expanded = colorExpanded, onExpandedChange = { colorExpanded = it }) {
                OutlinedTextField(
                    value = colorLabels[selectedColor] ?: "",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = colorExpanded) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selectedColor == ReminderColor.DEFAULT) MaterialTheme.colorScheme.surfaceVariant
                                    else reminderColor(selectedColor)
                                )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                ExposedDropdownMenu(expanded = colorExpanded, onDismissRequest = { colorExpanded = false }) {
                    ReminderColor.entries.forEach { color ->
                        DropdownMenuItem(
                            text = { Text(colorLabels[color] ?: "") },
                            onClick = { onColorChange(color); colorExpanded = false },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (color == ReminderColor.DEFAULT) MaterialTheme.colorScheme.surfaceVariant
                                            else reminderColor(color)
                                        )
                                )
                            }
                        )
                    }
                }
            }

            // Remind — dropdown
            var remindExpanded by remember { mutableStateOf(false) }
            val remindOptions = listOf(0, 5, 15, 30, 60, 1440)
            val remindLabels = listOf("Сразу", "За 5 минут", "За 15 минут", "За 30 минут", "За 1 час", "За сутки")
            val currentRemindLabel = remindLabels.getOrElse(remindOptions.indexOf(remindBefore)) { "Сразу" }
            Text(stringResource(R.string.reminder_label), style = MaterialTheme.typography.labelLarge)
            ExposedDropdownMenuBox(expanded = remindExpanded, onExpandedChange = { remindExpanded = it }) {
                OutlinedTextField(
                    value = currentRemindLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = remindExpanded) },
                    leadingIcon = { Icon(Icons.Default.Alarm, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                ExposedDropdownMenu(expanded = remindExpanded, onDismissRequest = { remindExpanded = false }) {
                    remindLabels.forEachIndexed { index, label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { onRemindBeforeChange(remindOptions[index]); remindExpanded = false },
                            leadingIcon = { Icon(Icons.Default.Alarm, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) }
                        )
                    }
                }
            }

            // Vibration toggle
            Surface(
                onClick = { onVibrateChange(!vibrate) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.notification_vibration), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = vibrate, onCheckedChange = onVibrateChange)
                }
            }

            // Melody
            OutlinedCard(
                onClick = onMelodyClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Notifications, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.change_melody_button), style = MaterialTheme.typography.labelSmall)
                        Text(melodyName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
            }

            // System calendar sync
            Surface(
                onClick = { onAddToSystemCalendarChange(!addToSystemCalendar) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.system_calendar_sync), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = addToSystemCalendar, onCheckedChange = onAddToSystemCalendarChange)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.save_button), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AddReminderPreview() {
    КалендарьШтурманаTheme {
        AddReminderContent(
            reminderId = null,
            title = "Важное событие",
            onTitleChange = {},
            description = "Заметки о событии",
            onDescriptionChange = {},
            isAllDay = false,
            onAllDayChange = {},
            selectedDate = Calendar.getInstance(),
            onDateClick = {},
            selectedTime = Calendar.getInstance(),
            onTimeClick = {},
            period = ReminderPeriod.ONCE,
            onPeriodChange = {},
            selectedColor = ReminderColor.BLUE,
            onColorChange = {},
            weeklyDays = setOf(2, 4, 6),
            onWeeklyDaysChange = {},
            remindBefore = 15,
            onRemindBeforeChange = {},
            vibrate = true,
            onVibrateChange = {},
            melodyName = "Стандартная мелодия",
            onMelodyClick = {},
            addToSystemCalendar = false,
            onAddToSystemCalendarChange = {},
            onSave = {},
            onBack = {}
        )
    }
}

