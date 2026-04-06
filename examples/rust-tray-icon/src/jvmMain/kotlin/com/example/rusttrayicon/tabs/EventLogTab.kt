package com.example.rusttrayicon.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rusttrayicon.*
import java.text.SimpleDateFormat
import java.util.*

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

@Composable
fun EventLogTab(events: List<TrayEvent>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("Event Log", count = events.size)

        if (events.isEmpty()) {
            InfoCard {
                Text(
                    "No events yet. Create a tray icon and interact with it.",
                    fontSize = 13.sp,
                    color = AppColors.textMuted,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(events) { _, event ->
                    EventRow(event)
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: TrayEvent) {
    val typeColor = when (event.type) {
        "Created" -> AppColors.green
        "Destroyed" -> AppColors.red
        "Tooltip", "Title" -> AppColors.accent
        "Visibility" -> AppColors.orange
        "Icon" -> AppColors.purple
        "TrayEvent" -> AppColors.cyan
        "MenuEvent" -> AppColors.green
        "Error" -> AppColors.red
        else -> AppColors.textSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.card)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            timeFormat.format(Date(event.timestamp)),
            fontSize = 11.sp,
            color = AppColors.textMuted,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(10.dp))
        Badge(event.type, typeColor)
        Spacer(Modifier.width(10.dp))
        Text(
            event.details,
            fontSize = 12.sp,
            color = AppColors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}
