@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.example.systeminfo

import kotlinx.cinterop.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import platform.AppKit.*
import platform.CoreGraphics.*
import platform.darwin.NSObject
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSProcessInfo
import platform.UserNotifications.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.sysctlbyname
import platform.posix.*

// Global emitter — staticCFunction-style constraint: target/action can't capture locals
private var trayClickEmitter: ((Int) -> Unit)? = null

private class TrayClickTarget : NSObject() {
    @ObjCAction
    fun itemClicked(sender: NSMenuItem) {
        trayClickEmitter?.invoke(sender.tag.toInt())
    }
}

actual class SystemDesktop {

    private var statusItem: NSStatusItem? = null
    private var clickTarget: TrayClickTarget? = null

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

            val target = TrayClickTarget()
            val action = NSSelectorFromString("itemClicked:")
            val menu = NSMenu()

            fun addClickableItem(title: String, tag: Int) {
                val menuItem = menu.addItemWithTitle(title, action = action, keyEquivalent = "")
                menuItem?.target = target
                menuItem?.setTag(tag.toLong())
            }

            addClickableItem("Hostname: ${getHostname()}", 0)
            addClickableItem("CPU: ${getCpuModel()}", 1)
            addClickableItem("Cores: ${getCpuCoreCount()}", 2)
            addClickableItem("Memory: ${getAvailableMemoryMB()} MB / ${getTotalMemoryMB()} MB", 3)
            addClickableItem("Uptime: ${formatUptime(getUptime())}", 4)
            menu.addItem(NSMenuItem.separatorItem())
            addClickableItem("Kernel: ${getKernelVersion()}", 6)

            item.menu = menu
            statusItem = item
            clickTarget = target
        }
        return true
    }

    actual fun hideSystemTray(): Boolean {
        val item = statusItem ?: return false
        dispatch_async(dispatch_get_main_queue()) {
            NSStatusBar.systemStatusBar.removeStatusItem(item)
            statusItem = null
            clickTarget = null
        }
        return true
    }

    actual fun updateTrayLabel(index: Int, label: String): Boolean {
        val menu = statusItem?.menu ?: return false
        val menuItem = menu.itemAtIndex(index.toLong()) ?: return false
        dispatch_async(dispatch_get_main_queue()) {
            menuItem.setTitle(label)
        }
        return true
    }

    actual fun trayClicks(): Flow<Int> = callbackFlow {
        trayClickEmitter = { index -> trySend(index) }
        awaitClose { trayClickEmitter = null }
    }

    actual fun memoryFlow(intervalMs: Long): Flow<MemoryInfo> = flow {
        while (true) {
            emit(MemoryInfo(getTotalMemoryMB(), getAvailableMemoryMB()))
            delay(intervalMs)
        }
    }

    actual suspend fun captureScreen(): ByteArray = memScoped {
        if (!CGPreflightScreenCaptureAccess()) {
            CGRequestScreenCaptureAccess()
            return@memScoped ByteArray(0)
        }

        val rect = alloc<CGRect>()
        rect.origin.x = CGRectInfinite.origin.x
        rect.origin.y = CGRectInfinite.origin.y
        rect.size.width = CGRectInfinite.size.width
        rect.size.height = CGRectInfinite.size.height
        val cgImage = CGWindowListCreateImage(
            rect.readValue(),
            kCGWindowListOptionOnScreenOnly,
            kCGNullWindowID,
            kCGWindowImageDefault,
        ) ?: return@memScoped ByteArray(0)

        val bitmapRep = NSBitmapImageRep(cGImage = cgImage)
        CGImageRelease(cgImage)

        val pngData = bitmapRep.representationUsingType(
            NSBitmapImageFileType.NSBitmapImageFileTypePNG,
            properties = emptyMap<Any?, Any>(),
        ) ?: return@memScoped ByteArray(0)

        ByteArray(pngData.length.toInt()) { index ->
            (pngData.bytes!!.reinterpret<ByteVar>() + index)!!.pointed.value
        }
    }

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
