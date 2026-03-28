@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.example.systeminfo

import kotlinx.cinterop.*
import platform.Foundation.NSProcessInfo
import platform.UserNotifications.*
import platform.darwin.sysctlbyname
import platform.posix.*

actual class SystemDesktop {

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
