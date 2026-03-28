@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.example.systeminfo

import kotlinx.cinterop.*
import libnotify.*
import platform.posix.*
import systray.*

actual class SystemDesktop {

    private var notifyInitialized = false

    actual fun sendNotification(title: String, body: String, icon: String): Boolean {
        if (!notifyInitialized) {
            notify_init("KotlinNativeExport")
            notifyInitialized = true
        }
        val notification = notify_notification_new(title, body, icon) ?: return false
        val result = notify_notification_show(notification, null)
        return result != 0
    }

    actual fun getHostname(): String = memScoped {
        val buf = allocArray<ByteVar>(256)
        gethostname(buf, 256u)
        buf.toKString()
    }

    actual fun getUptime(): Double = memScoped {
        val fp = fopen("/proc/uptime", "r") ?: return -1.0
        val buf = allocArray<ByteVar>(64)
        fgets(buf, 64, fp)
        fclose(fp)
        buf.toKString().split(" ").firstOrNull()?.toDoubleOrNull() ?: -1.0
    }

    actual fun getTotalMemoryMB(): Long = readProcValue("/proc/meminfo", "MemTotal") / 1024

    actual fun getAvailableMemoryMB(): Long = readProcValue("/proc/meminfo", "MemAvailable") / 1024

    actual fun getCpuModel(): String = memScoped {
        val fp = fopen("/proc/cpuinfo", "r") ?: return "Unknown"
        val buf = allocArray<ByteVar>(512)
        try {
            while (fgets(buf, 512, fp) != null) {
                val line = buf.toKString()
                if (line.startsWith("model name")) {
                    return line.substringAfter(":").trim()
                }
            }
            return "Unknown"
        } finally {
            fclose(fp)
        }
    }

    actual fun getCpuCoreCount(): Int = memScoped {
        val fp = fopen("/proc/cpuinfo", "r") ?: return 0
        val buf = allocArray<ByteVar>(256)
        var count = 0
        while (fgets(buf, 256, fp) != null) {
            if (buf.toKString().startsWith("processor")) count++
        }
        fclose(fp)
        count
    }

    actual fun getKernelVersion(): String = memScoped {
        val fp = fopen("/proc/version", "r") ?: return "Unknown"
        val buf = allocArray<ByteVar>(512)
        fgets(buf, 512, fp)
        fclose(fp)
        buf.toKString().trim().substringBefore("(").trim()
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
        systray_show_dbus("System Info", cLabels, labels.size) != 0
    }

    actual fun hideSystemTray(): Boolean {
        return systray_hide_dbus() != 0
    }

    private fun formatUptime(seconds: Double): String {
        if (seconds < 0) return "N/A"
        val h = (seconds / 3600).toInt()
        val m = ((seconds % 3600) / 60).toInt()
        return "${h}h ${m}m"
    }

    private fun readProcValue(path: String, key: String): Long = memScoped {
        val fp = fopen(path, "r") ?: return 0
        val buf = allocArray<ByteVar>(256)
        try {
            while (fgets(buf, 256, fp) != null) {
                val line = buf.toKString()
                if (line.startsWith(key)) {
                    return line.filter { it.isDigit() }.toLongOrNull() ?: 0
                }
            }
            return 0
        } finally {
            fclose(fp)
        }
    }
}
