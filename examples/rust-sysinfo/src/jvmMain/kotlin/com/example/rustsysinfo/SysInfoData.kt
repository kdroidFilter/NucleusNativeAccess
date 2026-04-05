package com.example.rustsysinfo

data class SystemInfo(
    val name: String,
    val osVersion: String,
    val longOsVersion: String?,
    val kernelVersion: String,
    val kernelLongVersion: String,
    val hostname: String,
    val cpuArch: String,
    val uptime: Long,
    val physicalCores: Long?,
    val distributionId: String,
    val distributionIdLike: List<String>,
    val openFilesLimit: Long?,
)

data class MemoryInfo(
    val totalMemory: Long,
    val usedMemory: Long,
    val freeMemory: Long,
    val availableMemory: Long,
    val totalSwap: Long,
    val usedSwap: Long,
    val freeSwap: Long,
    val cgroupLimits: CGroupLimits?,
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
    val totalPacketsReceived: Long,
    val totalPacketsTransmitted: Long,
    val errorsReceived: Long,
    val errorsTransmitted: Long,
    val totalErrorsReceived: Long,
    val totalErrorsTransmitted: Long,
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
    val root: String?,
    val diskReadBytes: Long,
    val diskWrittenBytes: Long,
    val cmd: List<String>,
    val environ: List<String>,
    val parentPid: Long?,
    val sessionId: Long?,
    val threadKind: String?,
    val openFiles: Long?,
    val openFilesLimit: Long?,
    val accumulatedCpuTime: Long,
    val taskCount: Int?,
)

data class SensorInfo(
    val label: String,
    val id: String?,
    val temperature: Float?,
    val max: Float?,
    val critical: Float?,
)

data class UserInfo(
    val name: String,
    val groups: List<String>,
)

data class GroupInfo(
    val name: String,
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
    val groups: List<GroupInfo>,
)
