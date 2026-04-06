package com.example.rustcamera.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rustcamera.*

@Composable
fun ControlsTab(controls: List<ControlInfo>) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Camera Controls", controls.size)

        if (controls.isEmpty()) {
            InfoCard {
                Text("No camera controls available", style = MaterialTheme.typography.body2)
            }
            return@Column
        }

        controls.forEach { ctrl ->
            InfoCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Badge(ctrl.controlType, AppColors.accent)
                    Badge(
                        if (ctrl.active) "Active" else "Inactive",
                        if (ctrl.active) AppColors.green else AppColors.textMuted,
                    )
                    if (ctrl.flags.isNotBlank()) {
                        Badge(ctrl.flags, AppColors.orange)
                    }
                }

                MetricRow("Name", ctrl.name)
                MetricRow("Current Value", ctrl.currentValue)
                MetricRow("Value Type", ctrl.descriptionTag)

                if (ctrl.valueDetails != "N/A") {
                    MiniLabel(ctrl.valueDetails, AppColors.textSecondary)
                }
            }
        }
    }
}
