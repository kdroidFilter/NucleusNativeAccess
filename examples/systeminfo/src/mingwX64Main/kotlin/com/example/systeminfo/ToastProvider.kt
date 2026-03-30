package com.example.systeminfo

import platform.windows.MB_ICONINFORMATION
import platform.windows.MB_OK
import platform.windows.MessageBoxW

internal const val TOAST_AUMID = "KNE.SystemInfo"
internal const val TOAST_LNK = "SystemInfo"

private var toastInitialized = false

internal fun toastInit(): Boolean {
    toastInitialized = true
    return true
}

internal fun toastShow(title: String, body: String): Boolean {
    if (!toastInitialized && !toastInit()) return false

    // Fallback notification for Windows Native target when WinRT toast COM bindings
    // are unavailable or unstable in current cinterop setup.
    MessageBoxW(null, body, title, (MB_OK or MB_ICONINFORMATION).toUInt())
    return true
}
