package com.shturman.calendar.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shturman.calendar.R
import com.shturman.calendar.data.Reminder
import com.shturman.calendar.ui.ReminderViewModel
import com.shturman.calendar.ui.theme.reminderColor
import com.shturman.calendar.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen(
    viewModel: ReminderViewModel,
    onAddReminder: (Int, Int, Int) -> Unit,
    onEditReminder: (Int) -> Unit,
    onSettings: () -> Unit,
    onShowYearly: () -> Unit
) {
    val reminders by viewModel.reminders.collectAsState()
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var reminderToDelete by remember { mutableStateOf<Reminder?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Show skeleton while loading
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        isLoading = false
    }

    // Holidays
    val holidayRepo = remember { com.shturman.calendar.data.HolidayRepository(context) }
    var holidays by remember { mutableStateOf(listOf<com.shturman.calendar.data.Holiday>()) }
    var holidaysEnabled by remember { mutableStateOf(context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("sync_holidays", false)) }

    // Re-read holidays setting when screen becomes visible
    LaunchedEffect(Unit) {
        holidaysEnabled = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("sync_holidays", false)
        if (holidaysEnabled) {
            holidayRepo.syncIfNeeded()
        }
    }

    // Pre-compute event days with colors for current month view
    var currentMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var currentYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }

    val eventDaysWithColor = remember(reminders, currentMonth, currentYear) {
        computeEventDaysWithColor(reminders, currentMonth, currentYear)
    }

    // Load holidays for current month (only if enabled)
    LaunchedEffect(currentMonth, currentYear, holidaysEnabled) {
        holidays = if (holidaysEnabled) {
            holidayRepo.getHolidaysForMonthList(currentYear, currentMonth + 1)
        } else {
            emptyList()
        }
    }

    // Holiday for selected date
    val selectedHoliday = holidays.find {
        it.day == selectedDate.get(Calendar.DAY_OF_MONTH) &&
        it.month == selectedDate.get(Calendar.MONTH) + 1 &&
        it.year == selectedDate.get(Calendar.YEAR)
    }

    reminderToDelete?.let { reminder ->
        AlertDialog(
            onDismissRequest = { reminderToDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_confirmation, reminder.title)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteReminder(reminder); reminderToDelete = null }) {
                    Text(stringResource(R.string.delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { reminderToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent)
                        )
                    } else {
                        Text(
                            "Календарь Штурмана",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.7).sp
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(if (isSearchActive) Icons.Default.SearchOff else Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    IconButton(onClick = onShowYearly) {
                        Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    onAddReminder(
                        selectedDate.get(Calendar.YEAR),
                        selectedDate.get(Calendar.MONTH) + 1,
                        selectedDate.get(Calendar.DAY_OF_MONTH)
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(24.dp))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f))))
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val isSearching = searchQuery.isNotBlank()

                // Show skeleton while loading
                if (isLoading) {
                    CalendarSkeleton()
                    repeat(3) { EventCardSkeleton() }
                } else {
                // Calendar — hidden during search
                AnimatedVisibility(
                    visible = !isSearching,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    CustomCalendar(
                        eventDaysWithColor = eventDaysWithColor,
                        holidays = holidays,
                        selectedDate = selectedDate,
                        onDateSelected = { selectedDate = it },
                        onMonthChanged = { month, year ->
                            currentMonth = month
                            currentYear = year
                        }
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Events list — filter with global search support
                val selectedDow = selectedDate.get(Calendar.DAY_OF_WEEK)

                val filteredReminders = reminders.filter { reminder ->
                    val matchesSearch = searchQuery.isBlank() ||
                            reminder.title.contains(searchQuery, ignoreCase = true) ||
                            reminder.description.contains(searchQuery, ignoreCase = true)

                    if (isSearching) {
                        matchesSearch
                    } else {
                        val matchesDate = when (reminder.period) {
                            com.shturman.calendar.data.ReminderPeriod.ONCE -> {
                                reminder.year == selectedDate.get(Calendar.YEAR) &&
                                        reminder.month == (selectedDate.get(Calendar.MONTH) + 1) &&
                                        reminder.dayOfMonth == selectedDate.get(Calendar.DAY_OF_MONTH)
                            }
                            com.shturman.calendar.data.ReminderPeriod.DAILY -> true
                            com.shturman.calendar.data.ReminderPeriod.WEEKLY -> {
                                val days = reminder.weeklyDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                                selectedDow in days
                            }
                            com.shturman.calendar.data.ReminderPeriod.MONTHLY -> {
                                reminder.dayOfMonth == selectedDate.get(Calendar.DAY_OF_MONTH)
                            }
                            com.shturman.calendar.data.ReminderPeriod.YEARLY -> {
                                reminder.month == (selectedDate.get(Calendar.MONTH) + 1) &&
                                        reminder.dayOfMonth == selectedDate.get(Calendar.DAY_OF_MONTH)
                            }
                        }
                        matchesDate && matchesSearch
                    }
                }

                var isRefreshing by remember { mutableStateOf(false) }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            kotlinx.coroutines.delay(500)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Weather — under calendar if no events, under events if there are
                    val weatherEnabled = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("weather_enabled", false)
                    if (weatherEnabled && filteredReminders.isEmpty() && selectedHoliday == null) {
                        item { WeatherWidget() }
                    }

                    // Holiday widget
                    if (selectedHoliday != null) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                androidx.compose.ui.graphics.Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.secondary
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🎉", style = MaterialTheme.typography.bodyLarge)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            selectedHoliday!!.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val holidayType = when (selectedHoliday!!.type) {
                                            1 -> "Государственный праздник"
                                            2 -> "Церковный праздник"
                                            3 -> "Профессиональный праздник"
                                            else -> "Праздник"
                                        }
                                        Text(
                                            holidayType,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // User events
                    if (filteredReminders.isNotEmpty()) {
                        items(filteredReminders, key = { it.id }) { reminder ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.EndToStart -> { reminderToDelete = reminder; false }
                                        SwipeToDismissBoxValue.StartToEnd -> { viewModel.toggleReminder(reminder); false }
                                        else -> false
                                    }
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val direction = dismissState.dismissDirection
                                    val color = when (direction) {
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.tertiary
                                        else -> Color.Transparent
                                    }
                                    val icon = when (direction) {
                                        SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                                        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.ToggleOn
                                        else -> Icons.Default.Edit
                                    }
                                    Box(
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(color).padding(horizontal = 20.dp),
                                        contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
                                    ) {
                                        Icon(icon, null, tint = Color.White)
                                    }
                                },
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true
                            ) {
                                ModernReminderItem(
                                    reminder = reminder,
                                    showDate = isSearching,
                                    onDelete = { reminderToDelete = reminder },
                                    onToggle = { viewModel.toggleReminder(reminder) },
                                    onEdit = { onEditReminder(reminder.id) }
                                )
                            }
                        }

                        // Weather under events
                        if (weatherEnabled) {
                            item { WeatherWidget() }
                        }
                    } else if (selectedHoliday == null) {
                        item { EmptyDayView() }
                    }
                }
                } // PullToRefreshBox
                } // if (isLoading) else
            }
        }
    }
}

@Composable
fun CustomCalendar(
    eventDaysWithColor: Map<String, List<Color>>,
    holidays: List<com.shturman.calendar.data.Holiday>,
    selectedDate: Calendar,
    onDateSelected: (Calendar) -> Unit,
    onMonthChanged: (Int, Int) -> Unit = { _, _ -> }
) {
    // Build holiday map for fast lookup: day -> holiday name
    val holidayMap = remember(holidays) {
        holidays.associateBy { it.day }
    }

    var calendarMonth by remember { mutableStateOf(selectedDate.clone() as Calendar) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    // Notify parent when month changes
    LaunchedEffect(calendarMonth) {
        onMonthChanged(calendarMonth.get(Calendar.MONTH), calendarMonth.get(Calendar.YEAR))
    }

    // Sync month when selectedDate changes from parent (e.g. "today" button)
    LaunchedEffect(selectedDate) {
        if (selectedDate.get(Calendar.MONTH) != calendarMonth.get(Calendar.MONTH) ||
            selectedDate.get(Calendar.YEAR) != calendarMonth.get(Calendar.YEAR)) {
            calendarMonth = selectedDate.clone() as Calendar
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragAccumulator < -50) {
                            calendarMonth = (calendarMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                        } else if (dragAccumulator > 50) {
                            calendarMonth = (calendarMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                        }
                        dragAccumulator = 0f
                    },
                    onDragCancel = { dragAccumulator = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                    }
                )
            },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Month header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(calendarMonth.time).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Row {
                    IconButton(onClick = { calendarMonth = (calendarMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) } }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { calendarMonth = (calendarMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) } }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Day names
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                val daysOfWeek = listOf(stringResource(R.string.mon), stringResource(R.string.tue), stringResource(R.string.wed), stringResource(R.string.thu), stringResource(R.string.fri), stringResource(R.string.sat), stringResource(R.string.sun))
                daysOfWeek.forEach { day ->
                    Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }

            // Days grid — always 6 rows for stable layout
            val days = getDaysOfMonth(calendarMonth)
            val paddedDays = days + List(42 - days.size) { null }
            val rows = paddedDays.chunked(7)

            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    row.forEach { day ->
                        if (day == null) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight())
                        } else {
                            val isSelected = isSameDay(day, selectedDate)
                            val isToday = isSameDay(day, Calendar.getInstance())
                            val key = "${day.get(Calendar.YEAR)}-${day.get(Calendar.MONTH) + 1}-${day.get(Calendar.DAY_OF_MONTH)}"
                            val eventColors = eventDaysWithColor[key] ?: emptyList()
                            val hasHoliday = holidayMap.containsKey(day.get(Calendar.DAY_OF_MONTH))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(1.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { onDateSelected(day) },
                                contentAlignment = Alignment.Center
                            ) {
                                // Text is ALWAYS at the center of the cell
                                Text(
                                    day.get(Calendar.DAY_OF_MONTH).toString(),
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        hasHoliday -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.align(Alignment.Center)
                                )

                                // Indicators are at the bottom
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (eventColors.isNotEmpty()) {
                                        // Show up to 4 dots
                                        eventColors.take(4).forEach { color ->
                                            Box(
                                                Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else color)
                                            )
                                        }
                                    } else if (hasHoliday && !isSelected) {
                                        Box(
                                            Modifier
                                                .size(4.dp)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


fun getDaysOfMonth(calendar: Calendar): List<Calendar?> {
    val monthCalendar = calendar.clone() as Calendar
    monthCalendar.set(Calendar.DAY_OF_MONTH, 1)
    var firstDayOfWeek = monthCalendar.get(Calendar.DAY_OF_WEEK) - 2
    if (firstDayOfWeek < 0) firstDayOfWeek = 6
    val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val days = mutableListOf<Calendar?>()
    for (i in 0 until firstDayOfWeek) days.add(null)
    for (i in 1..daysInMonth) {
        val day = monthCalendar.clone() as Calendar
        day.set(Calendar.DAY_OF_MONTH, i)
        days.add(day)
    }
    return days
}

fun computeEventDaysWithColor(reminders: List<Reminder>, month: Int, year: Int): Map<String, List<Color>> {
    val result = mutableMapOf<String, MutableList<Color>>()
    val cal = Calendar.getInstance()
    val defaultColor = Color(0xFF2C4A7C) // Navy

    for (day in 1..31) {
        cal.set(year, month, day)
        if (cal.get(Calendar.MONTH) != month) break

        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        for (r in reminders) {
            if (!r.isEnabled) continue

            val matches = when (r.period) {
                com.shturman.calendar.data.ReminderPeriod.ONCE -> {
                    r.year == year && r.month == month + 1 && r.dayOfMonth == day
                }
                com.shturman.calendar.data.ReminderPeriod.DAILY -> true
                com.shturman.calendar.data.ReminderPeriod.WEEKLY -> {
                    val days = r.weeklyDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                    dayOfWeek in days
                }
                com.shturman.calendar.data.ReminderPeriod.MONTHLY -> {
                    r.dayOfMonth == day
                }
                com.shturman.calendar.data.ReminderPeriod.YEARLY -> {
                    r.month == month + 1 && r.dayOfMonth == day
                }
            }

            if (matches) {
                val key = "$year-${month + 1}-$day"
                val color = reminderColor(r.color)
                val finalColor = if (color != Color.Unspecified) color else defaultColor
                
                val list = result.getOrPut(key) { mutableListOf() }
                if (!list.contains(finalColor)) {
                    list.add(finalColor)
                }
            }
        }
    }
    return result
}


fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun EmptyDayView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.EventBusy,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Мероприятий нет",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
    }
}

@Composable
fun ModernReminderItem(
    reminder: Reminder,
    showDate: Boolean = false,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val colorAccent = reminderColor(reminder.color)

    // Countdown timer
    val eventTime = remember(reminder) {
        Calendar.getInstance().apply {
            set(reminder.year, reminder.month - 1, reminder.dayOfMonth, reminder.hour, reminder.minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000) // Update every minute
        }
    }
    val diff = eventTime - now
    val countdownText = if (diff > 0 && !showDate) {
        val days = diff / (24 * 60 * 60 * 1000)
        val hours = (diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
        val mins = (diff % (60 * 60 * 1000)) / (60 * 1000)
        when {
            days > 0 -> "Через $days д $hours ч"
            hours > 0 -> "Через $hours ч $mins мин"
            mins > 0 -> "Через $mins мин"
            else -> "Сейчас"
        }
    } else null

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (reminder.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        onClick = onEdit
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (colorAccent != Color.Unspecified) {
                Box(Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(colorAccent))
                Spacer(Modifier.width(10.dp))
            }

            Switch(checked = reminder.isEnabled, onCheckedChange = { onToggle() }, modifier = Modifier.scale(0.75f))

            Spacer(Modifier.width(10.dp))

            if (reminder.isAllDay) {
                Text(
                    "Весь день",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (reminder.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    String.format("%02d:%02d", reminder.hour, reminder.minute),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (reminder.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = if (reminder.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                if (reminder.description.isNotBlank()) {
                    Text(reminder.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                
                val infoText = when (reminder.period) {
                    com.shturman.calendar.data.ReminderPeriod.ONCE -> if (showDate) "${reminder.dayOfMonth}.${reminder.month}.${reminder.year}" else ""
                    com.shturman.calendar.data.ReminderPeriod.WEEKLY -> {
                        val dayNames = mapOf(1 to "Вс", 2 to "Пн", 3 to "Вт", 4 to "Ср", 5 to "Чт", 6 to "Пт", 7 to "Сб")
                        val days = reminder.weeklyDays.split(",").mapNotNull { it.trim().toIntOrNull() }.mapNotNull { dayNames[it] }.joinToString(", ")
                        "Каждую неделю: $days"
                    }
                    com.shturman.calendar.data.ReminderPeriod.DAILY -> "Каждый день"
                    com.shturman.calendar.data.ReminderPeriod.MONTHLY -> "Каждый месяц"
                    com.shturman.calendar.data.ReminderPeriod.YEARLY -> "Каждый год"
                }
                
                if (infoText.isNotEmpty()) {
                    Text(infoText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                }

                if (countdownText != null && reminder.isEnabled) {
                    Text(countdownText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
            }
        }
    }
}
