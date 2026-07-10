package com.shturman.calendar.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import com.shturman.calendar.util.AppLog
import com.shturman.calendar.util.WeatherApi
import com.shturman.calendar.util.WeatherData

@SuppressLint("MissingPermission")
@Composable
fun WeatherWidget() {
    val context = LocalContext.current
    var weather by remember { mutableStateOf<WeatherData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermission = granted
        if (!granted) {
            errorMsg = "Нет доступа к геолокации"
            isLoading = false
        }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect

        try {
            isLoading = true
            val location = try {
                fusedLocationClient.lastLocation.await() ?: fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                ).await()
            } catch (e: Exception) {
                AppLog.e("WeatherWidget: location fetch failed", e)
                null
            }

            if (location != null) {
                val result = WeatherApi.getWeather(context, location.latitude, location.longitude)
                if (result != null) {
                    weather = result
                } else {
                    errorMsg = "Не удалось загрузить погоду"
                }
            } else {
                errorMsg = "Геолокация недоступна"
            }
        } catch (e: Exception) {
            AppLog.e("WeatherWidget error", e)
            errorMsg = "Ошибка: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            hasPermission = true
        }
    }

    Column(modifier = Modifier.padding(top = 12.dp)) {
        when {
            isLoading -> {
                WeatherCardContainer {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Загрузка погоды...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            weather != null -> {
                val w = weather!!
                WeatherCardContainer {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(w.icon, style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${String.format("%.0f", w.temp)}°C",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    w.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            WeatherDetailItem(
                                icon = Icons.Default.WaterDrop,
                                label = "Влажность",
                                value = "${w.humidity}%"
                            )
                            WeatherDetailItem(
                                icon = Icons.Default.Air,
                                label = "Ветер",
                                value = "${String.format("%.1f", w.windSpeed)} м/с"
                            )
                            WeatherDetailItem(
                                icon = null,
                                label = "Ощущается",
                                value = "${String.format("%.0f", w.feelsLike)}°C",
                                emoji = "🌡️"
                            )
                        }
                    }
                }
            }
            errorMsg != null -> {
                WeatherCardContainer(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)) {
                    Text(
                        errorMsg!!,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherCardContainer(
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = color,
        content = content
    )
}

@Composable
fun WeatherDetailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    value: String,
    emoji: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        } else if (emoji != null) {
            Text(emoji, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(4.dp))
        Column {
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
