package com.shturman.calendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shturman.calendar.data.Reminder
import com.shturman.calendar.ui.theme.reminderColor

@Composable
fun EventCard(
    title: String,
    time: String? = null,
    description: String? = null,
    periodText: String? = null,
    color: Color = Color.Unspecified,
    isEnabled: Boolean = true,
    isHoliday: Boolean = false,
    alpha: Float = 1f,
    showToggle: Boolean = false,
    isChecked: Boolean = false,
    onToggle: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        onClick = { onClick?.invoke() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator
            if (color != Color.Unspecified) {
                Box(Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Spacer(Modifier.width(10.dp))
            }

            // Toggle or icon
            if (showToggle && onToggle != null) {
                Switch(
                    checked = isChecked,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.scale(0.75f)
                )
            } else if (isHoliday) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎉", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.width(10.dp))

            // Time
            if (time != null) {
                Text(
                    time,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(14.dp))
            }

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                if (description != null && description.isNotBlank()) {
                    Text(description, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                if (periodText != null) {
                    Text(periodText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                }
            }

            // Trailing content
            trailing?.invoke()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EventCardPreview() {
    MaterialTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EventCard(
                title = "Встреча с командой",
                time = "14:30",
                description = "Обсудить проект",
                periodText = "Каждый понедельник"
            )
            EventCard(
                title = "Праздник",
                isHoliday = true,
                isEnabled = false,
                alpha = 0.7f
            )
            EventCard(
                title = "День рождения",
                time = "10:00",
                color = androidx.compose.ui.graphics.Color(0xFFE53935)
            )
        }
    }
}
