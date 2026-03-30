// WinRT and COM definitions moved to ToastProvider.kt

@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.experimental.ExperimentalNativeApi::class
)

package com.example.systeminfo

import kotlinx.cinterop.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import platform.windows.*
import platform.posix.*

// --- Tray and UI Constants ---
private const val WM_TRAY_CB = 1049u
private const val WC_TRAY = "KNETrayWnd"
private const val ID_TRAY = 1u
private const val ID_MENU_BASE = 1000u
private const val MAX_ITEMS = 32

// --- Globals ---
private var trayManager: TrayManager? = null

// --- Implementation of actual class ---

actual class SystemDesktop {

    actual fun sendNotification(title: String, body: String, icon: String): Boolean {
        return toastShow(title, body)
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

    actual fun showSystemTray(): Boolean {
        if (trayManager != null) return false
        val manager = TrayManager()
        val labels = listOf(
            "Hostname: ${getHostname()}",
            "CPU: ${getCpuModel()}",
            "Cores: ${getCpuCoreCount()}",
            "Memory: ${getAvailableMemoryMB()} MB / ${getTotalMemoryMB()} MB",
            "Uptime: ${formatUptime(getUptime())}",
            "Kernel: ${getKernelVersion()}"
        )
        if (manager.start(labels)) {
            trayManager = manager
            return true
        }
        return false
    }

    actual fun hideSystemTray(): Boolean {
        val manager = trayManager ?: return false
        if (manager.stop()) {
            trayManager = null
            return true
        }
        return false
    }

    actual fun updateTrayLabel(index: Int, label: String): Boolean {
        return trayManager?.updateLabel(index, label) ?: false
    }

    actual fun trayClicks(): Flow<Int> = callbackFlow {
        trayClickEmitter = { index -> trySend(index) }
        awaitClose {
            trayClickEmitter = null
        }
    }

    actual fun memoryFlow(intervalMs: Long): Flow<MemoryInfo> = flow {
        while (true) {
            emit(MemoryInfo(getTotalMemoryMB(), getAvailableMemoryMB()))
            delay(intervalMs)
        }
    }

    actual suspend fun captureScreen(): ByteArray = memScoped {
        val hScreen = GetDC(null) ?: return ByteArray(0)
        val hDC = CreateCompatibleDC(hScreen) ?: return ByteArray(0).also { ReleaseDC(null, hScreen) }

        val width = GetSystemMetrics(SM_CXSCREEN)
        val height = GetSystemMetrics(SM_CYSCREEN)

        val hBitmap = CreateCompatibleBitmap(hScreen, width, height) ?: run {
            DeleteDC(hDC)
            ReleaseDC(null, hScreen)
            return ByteArray(0)
        }

        val oldObj = SelectObject(hDC, hBitmap)
        BitBlt(hDC, 0, 0, width, height, hScreen, 0, 0, SRCCOPY)
        SelectObject(hDC, oldObj)

        val bmi = alloc<BITMAPINFO>()
        bmi.bmiHeader.apply {
            biSize = sizeOf<BITMAPINFOHEADER>().toUInt()
            biWidth = width
            biHeight = -height // Top-down
            biPlanes = 1u
            biBitCount = 24u
            biCompression = BI_RGB.toUInt()
        }

        val rowSize = ((width * 24 + 31) / 32) * 4
        val dataSize = rowSize * height
        val pixels = nativeHeap.allocArray<ByteVar>(dataSize)

        try {
            if (GetDIBits(hDC, hBitmap, 0u, height.toUInt(), pixels, bmi.ptr, DIB_RGB_COLORS.toUInt()) == 0) {
                return ByteArray(0)
            }

            val fileHeaderSize = 14
            val infoHeaderSize = 40
            val fileSize = fileHeaderSize + infoHeaderSize + dataSize
            val result = ByteArray(fileSize)

            // Bitmap File Header
            result[0] = 'B'.code.toByte()
            result[1] = 'M'.code.toByte()
            writeInt(result, 2, fileSize)
            writeInt(result, 10, fileHeaderSize + infoHeaderSize)

            // Bitmap Info Header
            writeInt(result, 14, infoHeaderSize)
            writeInt(result, 18, width)
            writeInt(result, 22, height)
            result[26] = 1
            result[28] = 24
            writeInt(result, 34, dataSize)

            // Pixels
            val pixelArray = pixels.readBytes(dataSize)
            pixelArray.copyInto(result, fileHeaderSize + infoHeaderSize)

            return result
        } finally {
            nativeHeap.free(pixels)
            DeleteObject(hBitmap)
            DeleteDC(hDC)
            ReleaseDC(null, hScreen)
        }
    }

    private fun writeInt(array: ByteArray, offset: Int, value: Int) {
        array[offset] = (value and 0xFF).toByte()
        array[offset + 1] = ((value shr 8) and 0xFF).toByte()
        array[offset + 2] = ((value shr 16) and 0xFF).toByte()
        array[offset + 3] = ((value shr 24) and 0xFF).toByte()
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
