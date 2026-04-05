package com.example.rustsysinfo.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rustsysinfo.*

@Composable
fun DisksTab(disks: List<DiskInfo>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("Disks", disks.size) }

        items(disks) { disk ->
            InfoCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Badge(
                        disk.name.ifEmpty { disk.mountPoint },
                        color = AppColors.textPrimary,
                    )
                    Badge(
                        disk.kind,
                        color = when (disk.kind) {
                            "SSD" -> AppColors.green
                            "HDD" -> AppColors.orange
                            else -> AppColors.textMuted
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))

                val usedSpace = disk.totalSpace - disk.availableSpace
                val usedPct = if (disk.totalSpace > 0) usedSpace.toFloat() / disk.totalSpace else 0f
                GaugeBar("Space", usedPct, "${formatBytes(usedSpace)} / ${formatBytes(disk.totalSpace)}")

                MetricRow("Mount", disk.mountPoint)
                MetricRow("Filesystem", disk.fileSystem)

                val flags = buildList {
                    if (disk.isRemovable) add("Removable")
                    if (disk.isReadOnly) add("Read-only")
                }
                if (flags.isNotEmpty()) {
                    MetricRow("Flags", flags.joinToString(", "))
                }

                if (disk.readBytes > 0 || disk.writtenBytes > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox("Read", formatBytes(disk.readBytes), Modifier.weight(1f), AppColors.cyan)
                        StatBox("Written", formatBytes(disk.writtenBytes), Modifier.weight(1f), AppColors.orange)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
