package com.example.rustsysinfo

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Duration.Companion.seconds

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Rust Sysinfo — Kotlin/JVM via FFM"
    ) {
        MaterialTheme(colors = lightColors()) {
            App()
        }
    }
}

// ── Data holders ─────────────────────────────────────────────────────────

data class SystemInfo(
    val name: String,
    val osVersion: String,
    val kernelVersion: String,
    val hostname: String,
    val cpuArch: String,
    val uptime: Long,
    val physicalCores: Long?,
    val distributionId: String,
)

data class MemoryInfo(
    val totalMemory: Long,
    val usedMemory: Long,
    val availableMemory: Long,
    val totalSwap: Long,
    val usedSwap: Long,
)

data class CpuInfo(
    val name: String,
    val brand: String,
    val vendorId: String,
    val frequency: Long,
    val usage: Float,
)

data class DiskInfo(
    val name: String,
    val mountPoint: String,
    val fileSystem: String,
    val totalSpace: Long,
    val availableSpace: Long,
    val isRemovable: Boolean,
)

data class DynamicState(
    val memory: MemoryInfo,
    val globalCpuUsage: Float,
    val cpus: List<CpuInfo>,
    val disks: List<DiskInfo>,
    val loadAvg: LoadAvg,
)

// ── Flows ────────────────────────────────────────────────────────────────

fun systemInfoFlow(): Flow<SystemInfo> = flow {
    emit(
        SystemInfo(
            name = System.name() ?: "Unknown",
            osVersion = System.os_version() ?: "Unknown",
            kernelVersion = System.kernel_version() ?: "Unknown",
            hostname = System.host_name() ?: "Unknown",
            cpuArch = System.cpu_arch(),
            uptime = System.uptime(),
            physicalCores = System.physical_core_count(),
            distributionId = System.distribution_id(),
        )
    )
}.flowOn(Dispatchers.IO)

fun dynamicStateFlow(interval: kotlin.time.Duration = 2.seconds): Flow<DynamicState> = flow {
    val sys = System.new_all()
    try {
        while (true) {
            sys.refresh_all()

            val memory = MemoryInfo(
                totalMemory = sys.total_memory(),
                usedMemory = sys.used_memory(),
                availableMemory = sys.available_memory(),
                totalSwap = sys.total_swap(),
                usedSwap = sys.used_swap(),
            )

            val cpus = sys.cpus().map { cpu ->
                CpuInfo(
                    name = cpu.name(),
                    brand = cpu.brand(),
                    vendorId = cpu.vendor_id(),
                    frequency = cpu.frequency(),
                    usage = cpu.cpu_usage(),
                )
            }

            val diskList = Disks.new_with_refreshed_list()
            val disks = diskList.list().map { disk ->
                DiskInfo(
                    name = disk.name(),
                    mountPoint = disk.mount_point(),
                    fileSystem = disk.file_system(),
                    totalSpace = disk.total_space(),
                    availableSpace = disk.available_space(),
                    isRemovable = disk.is_removable(),
                )
            }
            diskList.close()

            emit(
                DynamicState(
                    memory = memory,
                    globalCpuUsage = sys.global_cpu_usage(),
                    cpus = cpus,
                    disks = disks,
                    loadAvg = System.load_average(),
                )
            )

            delay(interval)
        }
    } finally {
        sys.close()
    }
}.flowOn(Dispatchers.IO)

// ── App ──────────────────────────────────────────────────────────────────

@Composable
@Preview
fun App() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("System", "CPU", "Memory", "Disks")

    val systemFlow = remember { systemInfoFlow() }
    val dynamicFlow = remember { dynamicStateFlow() }
    val systemInfo by systemFlow.collectAsState(initial = null)
    val state by dynamicFlow.collectAsState(initial = null)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            backgroundColor = MaterialTheme.colors.surface,
            edgePadding = 8.dp,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }

        Box(Modifier.fillMaxSize().padding(16.dp)) {
            when (selectedTab) {
                0 -> SystemTab(systemInfo, state?.loadAvg)
                1 -> CpuTab(state?.cpus ?: emptyList(), state?.globalCpuUsage ?: 0f)
                2 -> MemoryTab(state?.memory)
                3 -> DisksTab(state?.disks ?: emptyList())
            }
        }
    }
}

// ── System Tab ───────────────────────────────────────────────────────────

@Composable
fun SystemTab(info: SystemInfo?, loadAvg: LoadAvg?) {
    if (info == null) {
        CircularProgressIndicator()
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("Operating System") }
        item { InfoRow("Name", info.name) }
        item { InfoRow("OS Version", info.osVersion) }
        item { InfoRow("Kernel", info.kernelVersion) }
        item { InfoRow("Hostname", info.hostname) }
        item { InfoRow("Architecture", info.cpuArch) }
        item { InfoRow("Distribution", info.distributionId) }
        item { InfoRow("Physical Cores", info.physicalCores?.toString() ?: "N/A") }
        item { InfoRow("Uptime", formatDuration(info.uptime)) }
        item { InfoRow("Boot Time", "${System.boot_time()}s since epoch") }

        if (loadAvg != null) {
            item { Spacer(Modifier.height(8.dp)) }
            item { SectionTitle("Load Average") }
            item { InfoRow("1 min", "%.2f".format(loadAvg.one)) }
            item { InfoRow("5 min", "%.2f".format(loadAvg.five)) }
            item { InfoRow("15 min", "%.2f".format(loadAvg.fifteen)) }
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { SectionTitle("Product Info") }
        item { InfoRow("Product Name", Product.name() ?: "N/A") }
        item { InfoRow("Product Family", Product.family() ?: "N/A") }
        item { InfoRow("Vendor", Product.vendor_name() ?: "N/A") }
        item { InfoRow("Version", Product.version() ?: "N/A") }

        item { Spacer(Modifier.height(8.dp)) }
        item { SectionTitle("Motherboard") }
        item {
            val mb = Motherboard.new()
            if (mb != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoRow("Name", mb.name() ?: "N/A")
                    InfoRow("Vendor", mb.vendor_name() ?: "N/A")
                    InfoRow("Version", mb.version() ?: "N/A")
                    InfoRow("Serial", mb.serial_number() ?: "N/A")
                }
                mb.close()
            } else {
                Text("Not available", color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

// ── CPU Tab ──────────────────────────────────────────────────────────────

@Composable
fun CpuTab(cpus: List<CpuInfo>, globalUsage: Float) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("Global CPU Usage") }
        item { UsageBar("Total", globalUsage / 100f, "%.1f%%".format(globalUsage)) }

        if (cpus.isNotEmpty()) {
            item { Spacer(Modifier.height(4.dp)) }
            item { InfoRow("Brand", cpus[0].brand) }
            item { InfoRow("Vendor", cpus[0].vendorId) }
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { SectionTitle("Per-Core Usage (${cpus.size} cores)") }

        items(cpus) { cpu ->
            UsageBar(cpu.name, cpu.usage / 100f, "%.1f%% @ ${cpu.frequency} MHz".format(cpu.usage))
        }
    }
}

// ── Memory Tab ───────────────────────────────────────────────────────────

@Composable
fun MemoryTab(info: MemoryInfo?) {
    if (info == null) {
        CircularProgressIndicator()
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("RAM") }
        item {
            val usedPct = if (info.totalMemory > 0) info.usedMemory.toFloat() / info.totalMemory else 0f
            UsageBar("Used", usedPct, "${formatBytes(info.usedMemory)} / ${formatBytes(info.totalMemory)}")
        }
        item { InfoRow("Available", formatBytes(info.availableMemory)) }

        item { Spacer(Modifier.height(8.dp)) }
        item { SectionTitle("Swap") }
        if (info.totalSwap > 0) {
            item {
                val swapPct = info.usedSwap.toFloat() / info.totalSwap
                UsageBar("Used", swapPct, "${formatBytes(info.usedSwap)} / ${formatBytes(info.totalSwap)}")
            }
        } else {
            item { Text("No swap configured", color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)) }
        }
    }
}

// ── Disks Tab ────────────────────────────────────────────────────────────

@Composable
fun DisksTab(disks: List<DiskInfo>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Disks (${disks.size})") }
        items(disks) { disk ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 2.dp,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(disk.name.ifEmpty { disk.mountPoint }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    InfoRow("Mount", disk.mountPoint)
                    InfoRow("Filesystem", disk.fileSystem)
                    InfoRow("Removable", if (disk.isRemovable) "Yes" else "No")
                    val usedSpace = disk.totalSpace - disk.availableSpace
                    val usedPct = if (disk.totalSpace > 0) usedSpace.toFloat() / disk.totalSpace else 0f
                    UsageBar("Space", usedPct, "${formatBytes(usedSpace)} / ${formatBytes(disk.totalSpace)}")
                }
            }
        }
    }
}

// ── Shared UI Components ─────────────────────────────────────────────────

@Composable
fun SectionTitle(title: String) {
    Text(
        title,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = MaterialTheme.colors.primary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
fun UsageBar(label: String, fraction: Float, detail: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
            Text(detail, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = fraction.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = when {
                fraction > 0.9f -> Color(0xFFE53935)
                fraction > 0.7f -> Color(0xFFFFA726)
                else -> MaterialTheme.colors.primary
            },
            backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
        )
    }
}

// ── Formatters ───────────────────────────────────────────────────────────

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

fun formatDuration(seconds: Long): String {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0) append("${hours}h ")
        append("${minutes}m")
    }
}
