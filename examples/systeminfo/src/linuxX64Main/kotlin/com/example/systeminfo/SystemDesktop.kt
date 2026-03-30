@file:OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.example.systeminfo

import gio.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import libnotify.*
import platform.posix.*
import systray.*

// Global required because staticCFunction cannot capture variables (Kotlin/Native limitation)
private var trayClickEmitter: ((Int) -> Unit)? = null

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

    actual fun updateTrayLabel(index: Int, label: String): Boolean {
        return systray_update_label(index, label) != 0
    }

    actual fun trayClicks(): Flow<Int> = callbackFlow {
        trayClickEmitter = { index -> trySend(index) }
        systray_set_click_callback(staticCFunction { index ->
            trayClickEmitter?.invoke(index)
        })
        awaitClose {
            systray_set_click_callback(null)
            trayClickEmitter = null
        }
    }

    actual suspend fun captureScreen(): ByteArray = withContext(Dispatchers.Default) {
        val pathPtr = capture_screenshot_portal() ?: return@withContext byteArrayOf()
        val path = pathPtr.toKString()
        free(pathPtr)

        val file = fopen(path, "rb") ?: return@withContext byteArrayOf()
        fseek(file, 0, SEEK_END)
        val size = ftell(file)
        fseek(file, 0, SEEK_SET)

        val buffer = ByteArray(size.toInt())
        buffer.usePinned { pinned ->
            fread(pinned.addressOf(0), 1u, size.toULong(), file)
        }
        fclose(file)
        remove(path)
        buffer
    }

    private fun formatUptime(seconds: Double): String {
        if (seconds < 0) return "N/A"
        val h = (seconds / 3600).toInt()
        val m = ((seconds % 3600) / 60).toInt()
        return "${h}h ${m}m"
    }

    actual fun memoryFlow(intervalMs: Long): Flow<MemoryInfo> = flow {
        while (true) {
            emit(MemoryInfo(getTotalMemoryMB(), getAvailableMemoryMB()))
            delay(intervalMs)
        }
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
