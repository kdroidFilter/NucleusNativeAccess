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
    val kind: String,
    val isReadOnly: Boolean,
    val readBytes: Long,
    val writtenBytes: Long,
)

data class NetworkInfo(
    val name: String,
    val received: Long,
    val totalReceived: Long,
    val transmitted: Long,
    val totalTransmitted: Long,
    val packetsReceived: Long,
    val packetsTransmitted: Long,
    val errorsReceived: Long,
    val errorsTransmitted: Long,
    val mtu: Long,
)

data class ProcessInfo(
    val pid: Long,
    val name: String,
    val cpuUsage: Float,
    val memory: Long,
    val virtualMemory: Long,
    val status: String,
    val startTime: Long,
    val runTime: Long,
    val exe: String?,
    val cwd: String?,
    val diskReadBytes: Long,
    val diskWrittenBytes: Long,
)

data class SensorInfo(
    val label: String,
    val temperature: Float?,
    val max: Float?,
    val critical: Float?,
)

data class UserInfo(
    val name: String,
    val groups: List<String>,
)

data class DynamicState(
    val memory: MemoryInfo,
    val globalCpuUsage: Float,
    val cpus: List<CpuInfo>,
    val disks: List<DiskInfo>,
    val loadAvg: LoadAvg,
    val networks: List<NetworkInfo>,
    val processes: List<ProcessInfo>,
    val sensors: List<SensorInfo>,
    val users: List<UserInfo>,
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
                val usage = disk.usage()
                DiskInfo(
                    name = disk.name(),
                    mountPoint = disk.mount_point(),
                    fileSystem = disk.file_system(),
                    totalSpace = disk.total_space(),
                    availableSpace = disk.available_space(),
                    isRemovable = disk.is_removable(),
                    kind = disk.kind().tag.name,
                    isReadOnly = disk.is_read_only(),
                    readBytes = usage.total_read_bytes,
                    writtenBytes = usage.total_written_bytes,
                )
            }
            diskList.close()

            // Networks (MAP: String -> NetworkData)
            val netList = Networks.new_with_refreshed_list()
            val networks = netList.list().map { (name, data) ->
                NetworkInfo(
                    name = name,
                    received = data.received(),
                    totalReceived = data.total_received(),
                    transmitted = data.transmitted(),
                    totalTransmitted = data.total_transmitted(),
                    packetsReceived = data.packets_received(),
                    packetsTransmitted = data.packets_transmitted(),
                    errorsReceived = data.errors_on_received(),
                    errorsTransmitted = data.errors_on_transmitted(),
                    mtu = data.mtu(),
                )
            }
            netList.close()

            // Processes (MAP: Pid -> Process)
            val processes = sys.processes().values
                .sortedByDescending { it.cpu_usage() }
                .take(30)
                .map { proc ->
                    val du = proc.disk_usage()
                    ProcessInfo(
                        pid = proc.pid().as_u32().toLong(),
                        name = proc.name(),
                        cpuUsage = proc.cpu_usage(),
                        memory = proc.memory(),
                        virtualMemory = proc.virtual_memory(),
                        status = proc.status().tag.name,
                        startTime = proc.start_time(),
                        runTime = proc.run_time(),
                        exe = proc.exe(),
                        cwd = proc.cwd(),
                        diskReadBytes = du.total_read_bytes,
                        diskWrittenBytes = du.total_written_bytes,
                    )
                }

            // Sensors / Components
            val compList = Components.new_with_refreshed_list()
            val sensors = compList.list().map { comp ->
                SensorInfo(
                    label = comp.label(),
                    temperature = comp.temperature(),
                    max = comp.max(),
                    critical = comp.critical(),
                )
            }
            compList.close()

            // Users
            val userList = Users.new_with_refreshed_list()
            val users = userList.list().map { user ->
                UserInfo(
                    name = user.name(),
                    groups = user.groups().map { it.name() },
                )
            }
            userList.close()

            emit(
                DynamicState(
                    memory = memory,
                    globalCpuUsage = sys.global_cpu_usage(),
                    cpus = cpus,
                    disks = disks,
                    loadAvg = System.load_average(),
                    networks = networks,
                    processes = processes,
                    sensors = sensors,
                    users = users,
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
@androidx.compose.ui.tooling.preview.Preview
fun App() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("System", "CPU", "Memory", "Disks", "Network", "Processes", "Sensors", "Users")

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
                4 -> NetworkTab(state?.networks ?: emptyList())
                5 -> ProcessesTab(state?.processes ?: emptyList())
                6 -> SensorsTab(state?.sensors ?: emptyList())
                7 -> UsersTab(state?.users ?: emptyList())
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(disk.name.ifEmpty { disk.mountPoint }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            disk.kind,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (disk.kind) {
                                "SSD" -> Color(0xFF4CAF50)
                                "HDD" -> Color(0xFFFFA726)
                                else -> MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            },
                        )
                    }
                    InfoRow("Mount", disk.mountPoint)
                    InfoRow("Filesystem", disk.fileSystem)
                    InfoRow("Removable", if (disk.isRemovable) "Yes" else "No")
                    if (disk.isReadOnly) {
                        InfoRow("Read-only", "Yes")
                    }
                    val usedSpace = disk.totalSpace - disk.availableSpace
                    val usedPct = if (disk.totalSpace > 0) usedSpace.toFloat() / disk.totalSpace else 0f
                    UsageBar("Space", usedPct, "${formatBytes(usedSpace)} / ${formatBytes(disk.totalSpace)}")
                    if (disk.readBytes > 0 || disk.writtenBytes > 0) {
                        Spacer(Modifier.height(2.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Read: ${formatBytes(disk.readBytes)}", fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                            Text("Written: ${formatBytes(disk.writtenBytes)}", fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

// ── Network Tab ─────────────────────────────────────────────────────────

@Composable
fun NetworkTab(networks: List<NetworkInfo>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Network Interfaces (${networks.size})") }
        items(networks) { net ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 2.dp,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(net.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    InfoRow("Received", formatBytes(net.totalReceived))
                    InfoRow("Transmitted", formatBytes(net.totalTransmitted))
                    InfoRow("Rx rate", "${formatBytes(net.received)}/s")
                    InfoRow("Tx rate", "${formatBytes(net.transmitted)}/s")
                    InfoRow("Packets Rx", "${net.packetsReceived}")
                    InfoRow("Packets Tx", "${net.packetsTransmitted}")
                    if (net.errorsReceived > 0 || net.errorsTransmitted > 0) {
                        InfoRow("Errors Rx/Tx", "${net.errorsReceived} / ${net.errorsTransmitted}")
                    }
                    InfoRow("MTU", "${net.mtu}")
                }
            }
        }
    }
}

// ── Processes Tab ────────────────────────────────────────────────────────

@Composable
fun ProcessesTab(processes: List<ProcessInfo>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { SectionTitle("Top Processes by CPU (${processes.size})") }
        items(processes) { proc ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 1.dp,
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(proc.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("PID ${proc.pid}", fontSize = 12.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                    }
                    UsageBar("CPU", proc.cpuUsage / 100f, "%.1f%%".format(proc.cpuUsage))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Mem: ${formatBytes(proc.memory)}", fontSize = 12.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
                        Text("VMem: ${formatBytes(proc.virtualMemory)}", fontSize = 12.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
                    }
                    if (proc.diskReadBytes > 0 || proc.diskWrittenBytes > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Disk R: ${formatBytes(proc.diskReadBytes)}", fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                            Text("Disk W: ${formatBytes(proc.diskWrittenBytes)}", fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    if (proc.exe != null) {
                        Text("Exe: ${proc.exe}", fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f), maxLines = 1)
                    }
                    if (proc.cwd != null) {
                        Text("Cwd: ${proc.cwd}", fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f), maxLines = 1)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Status: ${proc.status}", fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                        Text("Running: ${formatDuration(proc.runTime)}", fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

// ── Sensors Tab ──────────────────────────────────────────────────────────

@Composable
fun SensorsTab(sensors: List<SensorInfo>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("Hardware Sensors (${sensors.size})") }
        if (sensors.isEmpty()) {
            item { Text("No sensors detected", color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)) }
        }
        items(sensors) { sensor ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 2.dp,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(sensor.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (sensor.temperature != null) {
                        val critical = sensor.critical
                        val max = sensor.max
                        val fraction = when {
                            critical != null && critical > 0 -> (sensor.temperature / critical).coerceIn(0f, 1f)
                            max != null && max > 0 -> (sensor.temperature / max).coerceIn(0f, 1f)
                            else -> 0f
                        }
                        UsageBar(
                            "Temp",
                            fraction,
                            buildString {
                                append("%.1f\u00B0C".format(sensor.temperature))
                                if (max != null) append(" / max %.0f\u00B0C".format(max))
                                if (critical != null) append(" / crit %.0f\u00B0C".format(critical))
                            }
                        )
                    } else {
                        Text("N/A", color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

// ── Users Tab ────────────────────────────────────────────────────────────

@Composable
fun UsersTab(users: List<UserInfo>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("System Users (${users.size})") }
        if (users.isEmpty()) {
            item { Text("No users found", color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)) }
        }
        items(users) { user ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colors.surface,
                elevation = 2.dp,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (user.groups.isNotEmpty()) {
                        InfoRow("Groups", user.groups.joinToString(", "))
                    }
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
