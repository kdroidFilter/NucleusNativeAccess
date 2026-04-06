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
fun FormatTab(state: CameraState?) {
    if (state?.currentFormat == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading format info...", style = MaterialTheme.typography.body1)
        }
        return
    }

    val fmt = state.currentFormat

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Active Format")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("Width", "${fmt.width} px", Modifier.weight(1f))
            StatBox("Height", "${fmt.height} px", Modifier.weight(1f))
            StatBox("Frame Rate", "${fmt.frameRate} fps", Modifier.weight(1f), accentColor = AppColors.cyan)
            StatBox("Pixel Format", fmt.format, Modifier.weight(1f), accentColor = AppColors.purple)
        }

        InfoCard {
            MetricRow("Resolution", formatResolution(fmt.width, fmt.height))
            MetricRow("Pixel Count", formatPixelCount(fmt.width, fmt.height))
            MetricRow("Aspect Ratio", formatAspectRatio(fmt.width, fmt.height))
            MetricRow("Effective FPS", "${state.fps}")
        }

        SectionHeader("Supported FourCC Codes", state.supportedFourcc.size)

        if (state.supportedFourcc.isNotEmpty()) {
            InfoCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.supportedFourcc.forEach { fourcc ->
                        Badge(fourcc, AppColors.purple)
                    }
                }
            }
        } else {
            InfoCard {
                Text("No FourCC codes reported", style = MaterialTheme.typography.body2)
            }
        }
    }
}
