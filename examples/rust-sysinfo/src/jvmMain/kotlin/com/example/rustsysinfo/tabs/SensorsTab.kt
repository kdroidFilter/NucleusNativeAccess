package com.example.rustsysinfo.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rustsysinfo.*

@Composable
fun SensorsTab(sensors: List<SensorInfo>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("Hardware Sensors", sensors.size) }

        if (sensors.isEmpty()) {
            item {
                InfoCard { MiniLabel("No sensors detected on this system") }
            }
        }

        items(sensors) { sensor ->
            InfoCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Badge(sensor.label, color = AppColors.textPrimary)
                    if (sensor.id != null) {
                        Badge(sensor.id, color = AppColors.textMuted)
                    }
                }
                Spacer(Modifier.height(4.dp))

                if (sensor.temperature != null) {
                    val critical = sensor.critical
                    val max = sensor.max
                    val fraction = when {
                        critical != null && critical > 0 -> (sensor.temperature / critical).coerceIn(0f, 1f)
                        max != null && max > 0 -> (sensor.temperature / max).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    val tempColors = when {
                        fraction > 0.85f -> AppColors.red to AppColors.red
                        fraction > 0.6f -> AppColors.orange to AppColors.red
                        else -> AppColors.green to AppColors.cyan
                    }
                    GaugeBar(
                        label = "Temperature",
                        fraction = fraction,
                        detail = buildString {
                            append("%.1f\u00B0C".format(sensor.temperature))
                            if (max != null) append("  max %.0f\u00B0C".format(max))
                            if (critical != null) append("  crit %.0f\u00B0C".format(critical))
                        },
                        colors = tempColors,
                    )
                } else {
                    MiniLabel("Temperature: N/A")
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
