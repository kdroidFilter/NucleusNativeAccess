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
fun FormatsTab(formats: List<CompatibleFormat>, currentFormat: FormatInfo?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Compatible Formats", formats.size)

        if (formats.isEmpty()) {
            InfoCard {
                Text("No compatible formats reported", style = MaterialTheme.typography.body2)
            }
            return@Column
        }

        // Group by resolution
        val byResolution = formats.groupBy { formatResolution(it.width, it.height) }

        byResolution.forEach { (resolution, fmts) ->
            SectionHeader(resolution, fmts.size)

            fmts.forEach { fmt ->
                val isCurrent = currentFormat != null &&
                    fmt.width == currentFormat.width &&
                    fmt.height == currentFormat.height &&
                    fmt.frameRate == currentFormat.frameRate &&
                    fmt.format == currentFormat.format

                InfoCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Badge(fmt.format, AppColors.purple)
                            Badge("${fmt.frameRate} fps", AppColors.cyan)
                            Badge(formatAspectRatio(fmt.width, fmt.height), AppColors.textSecondary)
                            if (isCurrent) {
                                Badge("ACTIVE", AppColors.green)
                            }
                        }
                        Text(
                            formatPixelCount(fmt.width, fmt.height),
                            style = MaterialTheme.typography.caption,
                        )
                    }
                }
            }
        }
    }
}
