package com.example.rustsysinfo.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rustsysinfo.*

@Composable
fun ProcessesTab(processes: List<ProcessInfo>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Top Processes by CPU", processes.size) }

        items(processes) { proc ->
            InfoCard {
                // Header: name + PID
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        proc.name,
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Badge("PID ${proc.pid}", color = AppColors.textMuted)
                }

                // CPU gauge
                GaugeBar("CPU", proc.cpuUsage / 100f, "%.1f%%".format(proc.cpuUsage))

                // Memory stats
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("Memory", formatBytes(proc.memory), Modifier.weight(1f), AppColors.accent)
                    StatBox("Virtual", formatBytes(proc.virtualMemory), Modifier.weight(1f), AppColors.purple)
                }

                // Disk I/O
                if (proc.diskReadBytes > 0 || proc.diskWrittenBytes > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox("Disk R", formatBytes(proc.diskReadBytes), Modifier.weight(1f), AppColors.cyan)
                        StatBox("Disk W", formatBytes(proc.diskWrittenBytes), Modifier.weight(1f), AppColors.orange)
                    }
                }

                // Status row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(proc.status, color = when (proc.status) {
                        "Run" -> AppColors.green
                        "Sleep" -> AppColors.textMuted
                        "Zombie" -> AppColors.red
                        else -> AppColors.orange
                    })
                    Badge(formatDuration(proc.runTime), color = AppColors.textSecondary)
                    Badge("CPU ${formatDuration(proc.accumulatedCpuTime)}", color = AppColors.textSecondary)
                }

                // Details row
                val details = buildList {
                    proc.parentPid?.let { add("Parent: $it") }
                    proc.openFiles?.let { add("FDs: $it") }
                    proc.openFilesLimit?.let { add("Limit: $it") }
                    proc.taskCount?.let { add("Threads: $it") }
                    proc.threadKind?.let { add("Kind: $it") }
                    proc.sessionId?.let { add("Session: $it") }
                }
                if (details.isNotEmpty()) {
                    MiniLabel(details.joinToString("  |  "))
                }

                // Command / exe / root
                if (proc.cmd.isNotEmpty()) {
                    MiniLabel("CMD: ${proc.cmd.joinToString(" ")}")
                } else if (proc.exe != null) {
                    MiniLabel("EXE: ${proc.exe}")
                }
                if (proc.root != null) {
                    MiniLabel("Root: ${proc.root}")
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
