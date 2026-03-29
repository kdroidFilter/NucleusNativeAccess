@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.example.systeminfo

import kotlinx.cinterop.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.windows.*
import wintray.*

// Global required because staticCFunction cannot capture variables (Kotlin/Native limitation)
private var trayClickEmitter: ((Int) -> Unit)? = null

actual class SystemDesktop {

    actual fun sendNotification(title: String, body: String, icon: String): Boolean {
        return wintray_notify(title, body) != 0
    }

    actual fun getHostname(): String = memScoped {
        val buf = allocArray<UShortVar>(MAX_COMPUTERNAME_LENGTH + 1)
        val size = alloc<DWORDVar>()
        size.value = (MAX_COMPUTERNAME_LENGTH + 1).toUInt()
        if (GetComputerNameW(buf, size.ptr) != 0) {
            buf.toKStringFromUtf16()
        } else {
            "Unknown"
        }
    }

    actual fun getUptime(): Double {
        return GetTickCount64().toDouble() / 1000.0
    }

    actual fun getTotalMemoryMB(): Long = memScoped {
        val mem = alloc<MEMORYSTATUSEX>()
        mem.dwLength = sizeOf<MEMORYSTATUSEX>().toUInt()
        if (GlobalMemoryStatusEx(mem.ptr) != 0) {
            (mem.ullTotalPhys / (1024uL * 1024uL)).toLong()
        } else {
            0L
        }
    }

    actual fun getAvailableMemoryMB(): Long = memScoped {
        val mem = alloc<MEMORYSTATUSEX>()
        mem.dwLength = sizeOf<MEMORYSTATUSEX>().toUInt()
        if (GlobalMemoryStatusEx(mem.ptr) != 0) {
            (mem.ullAvailPhys / (1024uL * 1024uL)).toLong()
        } else {
            0L
        }
    }

    actual fun getCpuModel(): String = readRegistryString(
        "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0",
        "ProcessorNameString"
    ) ?: "Unknown"

    actual fun getCpuCoreCount(): Int = memScoped {
        val info = alloc<SYSTEM_INFO>()
        GetSystemInfo(info.ptr)
        info.dwNumberOfProcessors.toInt()
    }

    // GetVersionExW lies on Windows 10+ (reports 6.2 = Win8 due to compat shim).
    // Registry ProductName also lies — it says "Windows 10" even on Windows 11.
    // The reliable method: read CurrentBuildNumber, if >= 22000 it's Windows 11.
    actual fun getKernelVersion(): String {
        val regKey = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion"
        val buildStr = readRegistryString(regKey, "CurrentBuildNumber") ?: ""
        val buildNumber = buildStr.toIntOrNull() ?: 0
        val displayVersion = readRegistryString(regKey, "DisplayVersion")
        val editionId = readRegistryString(regKey, "EditionID") ?: ""

        val version = when {
            buildNumber >= 22000 -> "Windows 11"
            buildNumber >= 10240 -> "Windows 10"
            else -> readRegistryString(regKey, "ProductName") ?: "Windows"
        }

        return buildString {
            append(version)
            if (editionId.isNotEmpty()) append(" $editionId")
            if (displayVersion != null) append(" $displayVersion")
            if (buildStr.isNotEmpty()) append(" (Build $buildStr)")
        }
    }

    actual fun showSystemTray(): Boolean = memScoped {
        val labels = listOf(
            "Hostname: ${getHostname()}",
            "CPU: ${getCpuModel()}",
            "Cores: ${getCpuCoreCount()}",
            "Memory: ${getAvailableMemoryMB()} MB / ${getTotalMemoryMB()} MB",
            "Uptime: ${formatUptime(getUptime())}",
            "Kernel: ${getKernelVersion()}"
        )
        val cLabels = allocArray<CPointerVar<ByteVar>>(labels.size)
        labels.forEachIndexed { i, label -> cLabels[i] = label.cstr.ptr }
        wintray_show(cLabels, labels.size) != 0
    }

    actual fun hideSystemTray(): Boolean {
        return wintray_hide() != 0
    }

    actual fun updateTrayLabel(index: Int, label: String): Boolean {
        return wintray_update_label(index, label) != 0
    }

    actual fun trayClicks(): Flow<Int> = callbackFlow {
        trayClickEmitter = { index -> trySend(index) }
        wintray_set_click_callback(staticCFunction { index ->
            trayClickEmitter?.invoke(index)
        })
        awaitClose {
            wintray_set_click_callback(null)
            trayClickEmitter = null
        }
    }

    private fun formatUptime(seconds: Double): String {
        if (seconds < 0) return "N/A"
        val h = (seconds / 3600).toInt()
        val m = ((seconds % 3600) / 60).toInt()
        return "${h}h ${m}m"
    }

    private fun readRegistryString(keyPath: String, valueName: String): String? = memScoped {
        val hKey = alloc<HKEYVar>()
        if (RegOpenKeyExW(HKEY_LOCAL_MACHINE, keyPath, 0u, KEY_READ.toUInt(), hKey.ptr) != 0) {
            return null
        }
        try {
            val bufSize = alloc<DWORDVar>().apply { value = 512u }
            val buf = allocArray<UShortVar>(256)
            val type = alloc<DWORDVar>()
            if (RegQueryValueExW(hKey.value, valueName, null, type.ptr, buf.reinterpret(), bufSize.ptr) == 0) {
                buf.toKStringFromUtf16()
            } else {
                null
            }
        } finally {
            RegCloseKey(hKey.value)
        }
    }
}
