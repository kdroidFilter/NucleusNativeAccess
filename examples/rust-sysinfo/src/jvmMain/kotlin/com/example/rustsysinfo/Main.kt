package com.example.rustsysinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Rust System Info (via FFM)",
        state = rememberWindowState(width = 500.dp, height = 700.dp),
    ) {
        MaterialTheme(colors = darkColors()) {
            Surface(modifier = Modifier.fillMaxSize()) {
                SystemInfoScreen()
            }
        }
    }
}

@Composable
fun SystemInfoScreen() {
    val desktop = remember { SystemDesktop() }
    var hostname by remember { mutableStateOf("...") }
    var osName by remember { mutableStateOf("...") }
    var osVersion by remember { mutableStateOf("...") }
    var cpuModel by remember { mutableStateOf("...") }
    var cpuCores by remember { mutableStateOf(0) }
    var cpuFreq by remember { mutableStateOf(0L) }
    var totalMem by remember { mutableStateOf(0L) }
    var availMem by remember { mutableStateOf(0L) }
    var usedMem by remember { mutableStateOf(0L) }
    var totalSwap by remember { mutableStateOf(0L) }
    var usedSwap by remember { mutableStateOf(0L) }
    var uptime by remember { mutableStateOf(0.0) }
    var kernel by remember { mutableStateOf("...") }
    var processCount by remember { mutableStateOf(0) }
    var diskCount by remember { mutableStateOf(0) }

    fun refresh() {
        desktop.refresh()
        hostname = desktop.hostname
        osName = desktop.os_name
        osVersion = desktop.os_version
        cpuModel = desktop.cpu_model
        cpuCores = desktop.cpu_core_count
        cpuFreq = desktop.cpu_frequency
        totalMem = desktop.total_memory_mb
        availMem = desktop.available_memory_mb
        usedMem = desktop.used_memory_mb
        totalSwap = desktop.total_swap_mb
        usedSwap = desktop.used_swap_mb
        uptime = desktop.uptime
        kernel = desktop.kernel_version
        processCount = desktop.process_count
        diskCount = desktop.disk_count
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Powered by Rust sysinfo crate via FFM",
            fontSize = 11.sp, color = Color(0xFFFF9800),
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Text(
            "All data below is read from native Rust APIs.\nNo JNI, no JVM workarounds.",
            fontSize = 12.sp, color = Color.Gray,
        )
        Spacer(Modifier.height(4.dp))

        SectionTitle("System")
        InfoCard("Hostname", hostname)
        InfoCard("OS", "$osName $osVersion")
        InfoCard("Kernel", kernel)
        InfoCard("Uptime", formatUptime(uptime))

        SectionTitle("CPU")
        InfoCard("Model", cpuModel)
        InfoCard("Cores", "$cpuCores")
        InfoCard("Frequency", "$cpuFreq MHz")

        SectionTitle("Memory")
        InfoCard("Total", "$totalMem MB")
        InfoCard("Available", "$availMem MB")
        InfoCard("Used", "$usedMem MB")
        val memPercent = if (totalMem > 0) (usedMem * 100 / totalMem) else 0
        InfoCard("Usage", "$memPercent%")

        SectionTitle("Swap")
        InfoCard("Total", "$totalSwap MB")
        InfoCard("Used", "$usedSwap MB")

        SectionTitle("Other")
        InfoCard("Processes", "$processCount")
        InfoCard("Disks", "$diskCount")

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { refresh() },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF424242)),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("Refresh", color = Color.White)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            desktop.summary,
            fontSize = 10.sp, color = Color.Gray,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SectionTitle(title: String) {
    Spacer(Modifier.height(4.dp))
    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF90CAF9))
    Divider(color = Color.DarkGray)
}

@Composable
fun InfoCard(label: String, value: String) {
    Surface(color = Color(0xFF1E1E1E), shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Color(0xFF90CAF9), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                value, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp),
            )
        }
    }
}

fun formatUptime(seconds: Double): String {
    if (seconds < 0) return "N/A"
    val d = (seconds / 86400).toInt()
    val h = ((seconds % 86400) / 3600).toInt()
    val m = ((seconds % 3600) / 60).toInt()
    return if (d > 0) "${d}d ${h}h ${m}m" else "${h}h ${m}m"
}
