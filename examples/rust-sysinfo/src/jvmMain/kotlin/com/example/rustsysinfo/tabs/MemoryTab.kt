package com.example.rustsysinfo.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rustsysinfo.*

@Composable
fun MemoryTab(info: MemoryInfo?) {
    if (info == null) {
        CircularProgressIndicator(color = AppColors.accent)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("RAM") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("Total", formatBytes(info.totalMemory), Modifier.weight(1f), AppColors.cyan)
                StatBox("Used", formatBytes(info.usedMemory), Modifier.weight(1f), AppColors.accent)
                StatBox("Available", formatBytes(info.availableMemory), Modifier.weight(1f), AppColors.green)
            }
        }
        item {
            InfoCard {
                val usedPct = if (info.totalMemory > 0) info.usedMemory.toFloat() / info.totalMemory else 0f
                GaugeBar("Usage", usedPct, "${formatBytes(info.usedMemory)} / ${formatBytes(info.totalMemory)}")
                MetricRow("Free", formatBytes(info.freeMemory))
            }
        }

        item { SectionHeader("Swap") }
        if (info.totalSwap > 0) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("Total", formatBytes(info.totalSwap), Modifier.weight(1f), AppColors.purple)
                    StatBox("Used", formatBytes(info.usedSwap), Modifier.weight(1f), AppColors.orange)
                    StatBox("Free", formatBytes(info.freeSwap), Modifier.weight(1f), AppColors.green)
                }
            }
            item {
                InfoCard {
                    val swapPct = info.usedSwap.toFloat() / info.totalSwap
                    GaugeBar("Usage", swapPct, "${formatBytes(info.usedSwap)} / ${formatBytes(info.totalSwap)}")
                }
            }
        } else {
            item {
                InfoCard { MiniLabel("No swap configured") }
            }
        }

        val cgroup = info.cgroupLimits
        if (cgroup != null) {
            item { SectionHeader("CGroup Limits") }
            item {
                InfoCard {
                    MetricRow("Total Memory", formatBytes(cgroup.total_memory))
                    MetricRow("Free Memory", formatBytes(cgroup.free_memory))
                    MetricRow("Free Swap", formatBytes(cgroup.free_swap))
                    MetricRow("RSS", formatBytes(cgroup.rss))
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
