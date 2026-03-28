package com.example.systeminfo

import androidx.compose.desktop.ui.tooling.preview.Preview
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
        title = "Native System Info (via FFM)",
        state = rememberWindowState(width = 500.dp, height = 620.dp),
    ) {
        MaterialTheme(colors = darkColors()) {
            Surface(modifier = Modifier.fillMaxSize()) {
                SystemInfoScreen()
            }
        }
    }
}

@Composable
@Preview
fun SystemInfoScreen() {
    val desktop = remember { SystemDesktop() }
    var hostname by remember { mutableStateOf("...") }
    var cpuModel by remember { mutableStateOf("...") }
    var cpuCores by remember { mutableStateOf(0) }
    var totalMem by remember { mutableStateOf(0L) }
    var availMem by remember { mutableStateOf(0L) }
    var uptime by remember { mutableStateOf(0.0) }
    var kernel by remember { mutableStateOf("...") }
    var notifSent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hostname = desktop.getHostname()
        cpuModel = desktop.getCpuModel()
        cpuCores = desktop.getCpuCoreCount()
        totalMem = desktop.getTotalMemoryMB()
        availMem = desktop.getAvailableMemoryMB()
        uptime = desktop.getUptime()
        kernel = desktop.getKernelVersion()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Native OS APIs via Kotlin/Native + FFM",
            fontSize = 11.sp, color = Color(0xFF81C784),
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Text(
            "All data below is read from native OS APIs.\nImpossible from pure JVM without JNI.",
            fontSize = 12.sp, color = Color.Gray,
        )
        Spacer(Modifier.height(4.dp))

        InfoCard("Hostname", hostname)
        InfoCard("Kernel", kernel)
        InfoCard("CPU", cpuModel)
        InfoCard("Cores", cpuCores.toString())
        InfoCard("Memory", "${availMem} MB free / ${totalMem} MB total")
        InfoCard("Uptime", formatUptime(uptime))

        Spacer(Modifier.height(8.dp))
        Divider(color = Color.DarkGray)
        Spacer(Modifier.height(8.dp))

        Text("Native Notification", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            "Desktop notification via native API, not a JVM workaround.",
            fontSize = 12.sp, color = Color.Gray,
        )

        Button(
            onClick = {
                desktop.sendNotification(
                    "Kotlin/Native + FFM",
                    "Sent from JVM -> FFM -> Kotlin/Native -> native OS API",
                    "dialog-information",
                )
                notifSent = true
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1565C0)),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(if (notifSent) "Notification sent!" else "Send native notification", color = Color.White)
        }

        var trayVisible by remember { mutableStateOf(false) }

        Button(
            onClick = {
                if (trayVisible) {
                    desktop.hideSystemTray()
                    trayVisible = false
                } else {
                    desktop.showSystemTray()
                    trayVisible = true
                }
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2E7D32)),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(
                if (trayVisible) "Hide native menu bar item" else "Show native menu bar item",
                color = Color.White,
            )
        }

        Button(
            onClick = {
                availMem = desktop.getAvailableMemoryMB()
                uptime = desktop.getUptime()
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF424242)),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("Refresh", color = Color.White)
        }
    }
}

@Composable
fun InfoCard(label: String, value: String) {
    Surface(color = Color(0xFF1E1E1E), shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
    val h = (seconds / 3600).toInt()
    val m = ((seconds % 3600) / 60).toInt()
    return "${h}h ${m}m"
}
