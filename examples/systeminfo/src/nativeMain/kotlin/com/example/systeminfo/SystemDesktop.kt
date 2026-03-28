package com.example.systeminfo

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
}
