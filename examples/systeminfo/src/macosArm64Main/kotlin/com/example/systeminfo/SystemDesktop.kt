@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.example.systeminfo

import kotlinx.cinterop.*
import platform.AppKit.*
import platform.Foundation.NSProcessInfo
import platform.UserNotifications.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.sysctlbyname
import platform.posix.*

actual class SystemDesktop {

    private var statusItem: NSStatusItem? = null

    actual fun sendNotification(title: String, body: String, icon: String): Boolean {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = "kne-${platform.Foundation.NSDate().timeIntervalSinceReferenceDate}",
            content = content,
            trigger = null,
        )
        UNUserNotificationCenter.currentNotificationCenter().apply {
            requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
            addNotificationRequest(request, withCompletionHandler = null)
        }
        return true
    }

    actual fun getHostname(): String = memScoped {
        val buf = allocArray<ByteVar>(256)
        gethostname(buf, 256u)
        buf.toKString()
    }

    actual fun getUptime(): Double {
        return NSProcessInfo.processInfo.systemUptime
    }

    actual fun getTotalMemoryMB(): Long {
        return (NSProcessInfo.processInfo.physicalMemory / (1024uL * 1024uL)).toLong()
    }

    actual fun getAvailableMemoryMB(): Long = memScoped {
        // Use sysctl vm.page_pageable_internal_count and vm.page_purgeable_count
        // Simpler fallback: estimate from os_proc_available_memory or sysctl
        val pageSize = sysconf(_SC_PAGESIZE)
        val total = getTotalMemoryMB()

        // Read vm.swapusage or approximate with active memory
        val size = alloc<size_tVar>()
        size.value = sizeOf<IntVar>().toULong()
        val vmPageFree = alloc<IntVar>()
        if (sysctlbyname("vm.page_free_count", vmPageFree.ptr, size.ptr, null, 0u) == 0) {
            return (vmPageFree.value.toLong() * pageSize) / (1024 * 1024)
        }
        // Fallback: return half of total as estimate
        return total / 2
    }

    actual fun getCpuModel(): String = sysctlString("machdep.cpu.brand_string") ?: "Unknown"

    actual fun getCpuCoreCount(): Int {
        return NSProcessInfo.processInfo.processorCount.toInt()
    }

    actual fun getKernelVersion(): String {
        val sysname = sysctlString("kern.ostype") ?: "Darwin"
        val release = sysctlString("kern.osrelease") ?: ""
        return "$sysname $release".trim()
    }

    actual fun showSystemTray(): Boolean {
        dispatch_async(dispatch_get_main_queue()) {
            val bar = NSStatusBar.systemStatusBar
            val item = bar.statusItemWithLength(NSVariableStatusItemLength)
            item.button?.title = "\uD83D\uDCBB"

            val menu = NSMenu()
            menu.addItemWithTitle("Hostname: ${getHostname()}", action = null, keyEquivalent = "")
            menu.addItemWithTitle("CPU: ${getCpuModel()}", action = null, keyEquivalent = "")
            menu.addItemWithTitle("Cores: ${getCpuCoreCount()}", action = null, keyEquivalent = "")
            menu.addItemWithTitle("Memory: ${getAvailableMemoryMB()} MB / ${getTotalMemoryMB()} MB", action = null, keyEquivalent = "")
            menu.addItemWithTitle("Uptime: ${formatUptime(getUptime())}", action = null, keyEquivalent = "")
            menu.addItem(NSMenuItem.separatorItem())
            menu.addItemWithTitle("Kernel: ${getKernelVersion()}", action = null, keyEquivalent = "")

            item.menu = menu
            statusItem = item
        }
        return true
    }

    actual fun hideSystemTray(): Boolean {
        val item = statusItem ?: return false
        dispatch_async(dispatch_get_main_queue()) {
            NSStatusBar.systemStatusBar.removeStatusItem(item)
            statusItem = null
        }
        return true
    }

    actual fun setTrayClickCallback(callback: (Int) -> Unit) {
        // TODO: implement macOS tray click callback
    }

    actual fun trayClicks(): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.emptyFlow()

    private fun formatUptime(seconds: Double): String {
        if (seconds < 0) return "N/A"
        val h = (seconds / 3600).toInt()
        val m = ((seconds % 3600) / 60).toInt()
        return "${h}h ${m}m"
    }

    private fun sysctlString(name: String): String? = memScoped {
        val size = alloc<size_tVar>()
        if (sysctlbyname(name, null, size.ptr, null, 0u) != 0) return null
        val len = size.value.toInt()
        if (len <= 0) return null
        val buf = allocArray<ByteVar>(len)
        if (sysctlbyname(name, buf, size.ptr, null, 0u) != 0) return null
        buf.toKString()
    }
}
