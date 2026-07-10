package com.shturman.calendar.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shturman.calendar.ui.ReminderViewModel
import com.shturman.calendar.ui.theme.reminderColor
import java.text.DateFormatSymbols
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearlyEventsScreen(
    viewModel: ReminderViewModel,
    onBack: () -> Unit,
    onEditReminder: (Int) -> Unit
) {
    val reminders by viewModel.reminders.collectAsState()
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    
    val yearlyReminders = remember(reminders) {
        reminders.filter { it.year == currentYear || it.period != com.shturman.calendar.data.ReminderPeriod.ONCE }
            .sortedWith(compareBy({ it.month }, { it.dayOfMonth }, { it.hour }, { it.minute }))
            .groupBy { it.month }
    }

    val monthNames = remember {
        listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", 
               "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
    }
    val monthNamesShort = remember {
        listOf("янв", "фев", "мар", "апр", "май", "июн", 
               "июл", "авг", "сен", "окт", "ноя", "дек")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "События года",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                        Text(
                            currentYear.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (yearlyReminders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Событий пока нет", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                yearlyReminders.forEach { (month, monthEvents) ->
                    item {
                        Text(
                            text = monthNames[month - 1],
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    items(monthEvents) { reminder ->
                        EventCard(
                            title = reminder.title,
                            time = if (reminder.isAllDay) "Весь день" else String.format(Locale.getDefault(), "%02d:%02d", reminder.hour, reminder.minute),
                            description = "${reminder.dayOfMonth} ${monthNamesShort[month - 1]}",
                            color = reminderColor(reminder.color),
                            isEnabled = reminder.isEnabled,
                            onClick = { onEditReminder(reminder.id) }
                        )
                    }
                }
            }
        }
    }
}
