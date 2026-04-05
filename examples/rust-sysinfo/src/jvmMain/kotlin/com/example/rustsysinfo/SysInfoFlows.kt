package com.example.rustsysinfo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun systemInfoFlow(): Flow<SystemInfo> = flow {
    emit(
        SystemInfo(
            name = System.name() ?: "Unknown",
            osVersion = System.os_version() ?: "Unknown",
            longOsVersion = System.long_os_version(),
            kernelVersion = System.kernel_version() ?: "Unknown",
            kernelLongVersion = System.kernel_long_version(),
            hostname = System.host_name() ?: "Unknown",
            cpuArch = System.cpu_arch(),
            uptime = System.uptime(),
            physicalCores = System.physical_core_count(),
            distributionId = System.distribution_id(),
            distributionIdLike = System.distribution_id_like(),
            openFilesLimit = System.open_files_limit(),
        )
    )
}.flowOn(Dispatchers.IO)

fun dynamicStateFlow(interval: Duration = 2.seconds): Flow<DynamicState> = flow {
    val sys = System.new_all()
    try {
        while (true) {
            sys.refresh_all()

            val memory = MemoryInfo(
                totalMemory = sys.total_memory(),
                usedMemory = sys.used_memory(),
                freeMemory = sys.free_memory(),
                availableMemory = sys.available_memory(),
                totalSwap = sys.total_swap(),
                usedSwap = sys.used_swap(),
                freeSwap = sys.free_swap(),
                cgroupLimits = sys.cgroup_limits(),
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
                    totalPacketsReceived = data.total_packets_received(),
                    totalPacketsTransmitted = data.total_packets_transmitted(),
                    errorsReceived = data.errors_on_received(),
                    errorsTransmitted = data.errors_on_transmitted(),
                    totalErrorsReceived = data.total_errors_on_received(),
                    totalErrorsTransmitted = data.total_errors_on_transmitted(),
                    mtu = data.mtu(),
                )
            }
            netList.close()

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
                        root = proc.root(),
                        diskReadBytes = du.total_read_bytes,
                        diskWrittenBytes = du.total_written_bytes,
                        cmd = proc.cmd(),
                        environ = proc.environ(),
                        parentPid = proc.parent()?.as_u32()?.toLong(),
                        sessionId = proc.session_id()?.as_u32()?.toLong(),
                        threadKind = proc.thread_kind()?.name,
                        openFiles = proc.open_files(),
                        openFilesLimit = proc.open_files_limit(),
                        accumulatedCpuTime = proc.accumulated_cpu_time(),
                        taskCount = proc.tasks()?.size,
                    )
                }

            val compList = Components.new_with_refreshed_list()
            val sensors = compList.list().map { comp ->
                SensorInfo(
                    label = comp.label(),
                    id = comp.id(),
                    temperature = comp.temperature(),
                    max = comp.max(),
                    critical = comp.critical(),
                )
            }
            compList.close()

            val userList = Users.new_with_refreshed_list()
            val users = userList.list().map { user ->
                UserInfo(
                    name = user.name(),
                    groups = user.groups().map { it.name() },
                )
            }
            userList.close()

            val groupList = Groups.new_with_refreshed_list()
            val groups = groupList.list().map { group ->
                GroupInfo(name = group.name())
            }
            groupList.close()

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
                    groups = groups,
                )
            )

            delay(interval)
        }
    } finally {
        sys.close()
    }
}.flowOn(Dispatchers.IO)
