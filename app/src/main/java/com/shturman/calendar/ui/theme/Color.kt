package com.shturman.calendar.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Nautical palette
val Navy80 = Color(0xFFB8C9E8)
val NavyGrey80 = Color(0xFFB0B8C8)
val Teal80 = Color(0xFF80CBC4)

val Navy40 = Color(0xFF2C4A7C)
val NavyGrey40 = Color(0xFF546E7A)
val Teal40 = Color(0xFF00796B)

// Reminder colors
val ReminderRed = Color(0xFFE53935)
val ReminderOrange = Color(0xFFFB8C00)
val ReminderYellow = Color(0xFFFDD835)
val ReminderGreen = Color(0xFF43A047)
val ReminderBlue = Color(0xFF1E88E5)
val ReminderPurple = Color(0xFF8E24AA)
val ReminderPink = Color(0xFFD81B60)

fun reminderColor(color: com.shturman.calendar.data.ReminderColor): Color {
    return when (color) {
        com.shturman.calendar.data.ReminderColor.DEFAULT -> Color.Unspecified
        com.shturman.calendar.data.ReminderColor.RED -> ReminderRed
        com.shturman.calendar.data.ReminderColor.ORANGE -> ReminderOrange
        com.shturman.calendar.data.ReminderColor.YELLOW -> ReminderYellow
        com.shturman.calendar.data.ReminderColor.GREEN -> ReminderGreen
        com.shturman.calendar.data.ReminderColor.BLUE -> ReminderBlue
        com.shturman.calendar.data.ReminderColor.PURPLE -> ReminderPurple
        com.shturman.calendar.data.ReminderColor.PINK -> ReminderPink
    }
}

// Gradient brushes for calendar indicators
fun selectedDayGradient(): Brush = Brush.linearGradient(
    colors = listOf(Navy40, Teal40)
)

fun todayGradient(isDark: Boolean): Brush = Brush.linearGradient(
    colors = if (isDark) listOf(Navy80.copy(alpha = 0.3f), Teal80.copy(alpha = 0.2f))
             else listOf(Navy40.copy(alpha = 0.15f), Teal40.copy(alpha = 0.1f))
)

// Glassmorphism modifier
fun Modifier.glassEffect(
    cornerRadius: Dp = 20.dp,
    blurRadius: Dp = 0.dp,
    alpha: Float = 0.12f
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha),
                Color.White.copy(alpha = alpha * 0.5f)
            )
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.3f),
                Color.White.copy(alpha = 0.1f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )
