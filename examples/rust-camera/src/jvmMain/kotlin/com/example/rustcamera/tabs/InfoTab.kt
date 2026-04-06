package com.example.rustcamera.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rustcamera.*

@Composable
fun InfoTab(state: CameraState?) {
    if (state?.deviceInfo == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading camera info...", style = MaterialTheme.typography.body1)
        }
        return
    }

    val info = state.deviceInfo

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Camera Device")

        InfoCard {
            MetricRow("Name", info.humanName)
            MetricRow("Description", info.description.ifBlank { "N/A" })
            MetricRow("Misc", info.misc.ifBlank { "N/A" })
            MetricRow("Index", info.indexDescription)
        }

        SectionHeader("Stream Status")

        InfoCard {
            MetricRow("Stream", if (state.isOpen) "Active" else "Closed")
            MetricRow("FPS", "${state.fps}")
        }

        val fmt = state.currentFormat
        if (fmt != null) {
            SectionHeader("Current Configuration")

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("Width", "${fmt.width} px", Modifier.weight(1f))
                StatBox("Height", "${fmt.height} px", Modifier.weight(1f))
                StatBox("Frame Rate", "${fmt.frameRate} fps", Modifier.weight(1f), accentColor = AppColors.cyan)
                StatBox("Format", fmt.format, Modifier.weight(1f), accentColor = AppColors.purple)
            }

            InfoCard {
                MetricRow("Resolution", formatResolution(fmt.width, fmt.height))
                MetricRow("Pixel Count", formatPixelCount(fmt.width, fmt.height))
                MetricRow("Aspect Ratio", formatAspectRatio(fmt.width, fmt.height))
            }
        }

        SectionHeader("Capabilities Summary")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("Formats", "${state.compatibleFormats.size}", Modifier.weight(1f), accentColor = AppColors.green)
            StatBox("FourCC Codes", "${state.supportedFourcc.size}", Modifier.weight(1f), accentColor = AppColors.orange)
            StatBox("Controls", "${state.controls.size}", Modifier.weight(1f), accentColor = AppColors.cyan)
        }
    }
}
