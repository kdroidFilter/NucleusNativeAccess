package com.example.systeminfo

data class MemoryInfo(val totalMB: Long, val availableMB: Long)

/**
 * Cross-platform access to native OS APIs:
 *  - Desktop notifications (libnotify on Linux, NSUserNotification on macOS)
 *  - System information (hostname, CPU, memory, kernel, uptime)
 */
expect class SystemDesktop() {
    fun sendNotification(title: String, body: String, icon: String): Boolean
    fun getHostname(): String
    fun getUptime(): Double
    fun getTotalMemoryMB(): Long
    fun getAvailableMemoryMB(): Long
    fun getCpuModel(): String
    fun getCpuCoreCount(): Int
    fun getKernelVersion(): String
    fun showSystemTray(): Boolean
    fun hideSystemTray(): Boolean
    fun updateTrayLabel(index: Int, label: String): Boolean
    fun trayClicks(): kotlinx.coroutines.flow.Flow<Int>
    fun memoryFlow(intervalMs: Long = 1000L): kotlinx.coroutines.flow.Flow<MemoryInfo>
}
