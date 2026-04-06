package com.example.rustcamera.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.rustcamera.*

@Composable
fun PreviewTab(state: CameraState?) {
    if (state == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Opening camera...", style = MaterialTheme.typography.body1)
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Live Preview")

        // Stats row
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("FPS", "${state.fps}", Modifier.weight(1f), accentColor = AppColors.green)
            val fmt = state.currentFormat
            if (fmt != null) {
                StatBox("Resolution", formatResolution(fmt.width, fmt.height), Modifier.weight(1f))
                StatBox("Frame Rate", "${fmt.frameRate} fps", Modifier.weight(1f), accentColor = AppColors.cyan)
                StatBox("Format", fmt.format, Modifier.weight(1f), accentColor = AppColors.purple)
            }
        }

        // Live feed
        val frame = state.frame
        if (frame != null) {
            InfoCard {
                Image(
                    bitmap = frame.toComposeImageBitmap(),
                    contentDescription = "Camera Feed",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        // Stream status
        InfoCard {
            MetricRow("Stream", if (state.isOpen) "Active" else "Closed")
            val fmt = state.currentFormat
            if (fmt != null) {
                MetricRow("Pixel Count", formatPixelCount(fmt.width, fmt.height))
                MetricRow("Aspect Ratio", formatAspectRatio(fmt.width, fmt.height))
            }
        }
    }
}
