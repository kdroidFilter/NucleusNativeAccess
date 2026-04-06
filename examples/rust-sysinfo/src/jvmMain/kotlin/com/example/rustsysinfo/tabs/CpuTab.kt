package com.example.rustsysinfo.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rustsysinfo.*

@Composable
fun CpuTab(cpus: List<CpuInfo>, globalUsage: Float) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("Overview") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("Total Usage", "%.1f%%".format(globalUsage), Modifier.weight(1f), AppColors.accent)
                if (cpus.isNotEmpty()) {
                    StatBox("Cores", "${cpus.size}", Modifier.weight(1f), AppColors.cyan)
                    StatBox("Frequency", "${cpus[0].frequency} MHz", Modifier.weight(1f), AppColors.purple)
                }
            }
        }

        if (cpus.isNotEmpty()) {
            item {
                InfoCard {
                    MetricRow("Brand", cpus[0].brand)
                    MetricRow("Vendor", cpus[0].vendorId)
                }
            }
        }

        item { SectionHeader("Per-Core Usage", cpus.size) }

        items(cpus) { cpu ->
            InfoCard {
                GaugeBar(
                    label = cpu.name,
                    fraction = cpu.usage / 100f,
                    detail = "%.1f%% @ %d MHz".format(cpu.usage, cpu.frequency),
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
