package com.example.rusttrayicon.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rusttrayicon.*

@Composable
fun TrayInfoTab(manager: TrayIconManager, onEvent: (TrayEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("Tray Icon Info")

        InfoCard {
            Text("Platform Notes", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary)
            Spacer(Modifier.height(8.dp))

            MiniLabel("macOS", color = AppColors.accent)
            Text(
                "Supports tooltip, title, template icons. Title appears next to the icon in the menu bar. " +
                    "Calls are dispatched to the main thread automatically via dispatch_sync.",
                fontSize = 12.sp,
                color = AppColors.textSecondary,
            )

            Spacer(Modifier.height(8.dp))
            MiniLabel("Windows", color = AppColors.green)
            Text(
                "Supports tooltip and icon. Title is not displayed. Tray icons appear in the notification area.",
                fontSize = 12.sp,
                color = AppColors.textSecondary,
            )

            Spacer(Modifier.height(8.dp))
            MiniLabel("Linux", color = AppColors.orange)
            Text(
                "Tooltip is not supported. Title is displayed. Requires gtk and libappindicator.",
                fontSize = 12.sp,
                color = AppColors.textSecondary,
            )
        }

        InfoCard {
            Text("API Surface", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary)
            Spacer(Modifier.height(8.dp))

            MetricRow("Crate", "tray-icon 0.19 (local wrapper)")
            MetricRow("Bridge", "FFM (Java Foreign Function & Memory API)")
            MetricRow("Thread safety", "dispatch_sync to main queue (macOS)")
            MetricRow("Lifecycle", "Managed via Rust thread_local + Kotlin proxy")
        }

        Spacer(Modifier.height(8.dp))
    }
}
