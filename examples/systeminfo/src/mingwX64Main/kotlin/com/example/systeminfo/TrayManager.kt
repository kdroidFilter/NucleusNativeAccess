@file:OptIn(
    ExperimentalForeignApi::class,
    kotlin.experimental.ExperimentalNativeApi::class
)

package com.example.systeminfo

import kotlinx.cinterop.*
import platform.windows.*

// --- Tray and UI Constants ---
private const val WM_TRAY_CB = 1049u
private const val WC_TRAY = "KNETrayWnd"
private const val ID_TRAY = 1u
private const val ID_MENU_BASE = 1000u

internal class TrayManager {
    private var g_hwnd: HWND? = null
    private var g_menu: HMENU? = null
    private val g_nid = nativeHeap.alloc<NOTIFYICONDATAW>()
    private var g_thread: HANDLE? = null
    private val g_ready = nativeHeap.alloc<IntVar>().apply { value = 0 }
    private val g_labels = mutableListOf<String>()
    private var g_wm_taskbar: UINT = 0u
    private val g_cs = nativeHeap.alloc<CRITICAL_SECTION>()
    private var g_cs_ok = false

    private fun ecs() {
        if (!g_cs_ok) {
            InitializeCriticalSection(g_cs.ptr)
            g_cs_ok = true
        }
    }

    private fun buildMenu(): HMENU? = memScoped {
        val m = CreatePopupMenu() ?: return null
        g_labels.forEachIndexed { i, label ->
            val mi = alloc<MENUITEMINFOW>()
            mi.cbSize = sizeOf<MENUITEMINFOW>().toUInt()
            mi.fMask = (MIIM_ID or MIIM_STRING or MIIM_STATE or MIIM_FTYPE).toUInt()
            mi.fType = MFT_STRING.toUInt()
            mi.dwTypeData = label.wcstr.ptr
            mi.cch = label.length.toUInt()
            mi.wID = ID_MENU_BASE + i.toUInt()
            mi.fState = MFS_ENABLED.toUInt()
            InsertMenuItemW(m, 0xFFFFFFFFu, 1, mi.ptr)
        }
        m
    }

    private fun trayProc(hwnd: HWND?, msg: UINT, wp: WPARAM, lp: LPARAM): LRESULT {
        when (msg) {
            WM_TRAY_CB -> {
                val event = lp.toUInt()
                if (event == WM_LBUTTONUP.toUInt() || event == WM_RBUTTONUP.toUInt()) {
                    memScoped {
                        val pt = alloc<POINT>()
                        GetCursorPos(pt.ptr)
                        SetForegroundWindow(hwnd)
                        ecs()
                        EnterCriticalSection(g_cs.ptr)
                        val menu = g_menu
                        if (menu != null) {
                            val cmd = TrackPopupMenu(
                                menu,
                                (TPM_LEFTALIGN or TPM_RIGHTBUTTON or TPM_RETURNCMD or TPM_NONOTIFY).toUInt(),
                                pt.x, pt.y, 0, hwnd, null
                            ).toUInt()
                            if (cmd >= ID_MENU_BASE) {
                                val idx = (cmd - ID_MENU_BASE).toInt()
                                if (idx in 0 until g_labels.size) {
                                    trayClickEmitter?.invoke(idx)
                                }
                            }
                        }
                        LeaveCriticalSection(g_cs.ptr)
                    }
                }
                return 0
            }
            WM_CLOSE.toUInt() -> {
                Shell_NotifyIconW(NIM_DELETE.toUInt(), g_nid.ptr)
                DestroyWindow(hwnd)
                return 0
            }
            WM_DESTROY.toUInt() -> {
                PostQuitMessage(0)
                return 0
            }
            else -> {
                if (msg == g_wm_taskbar && g_wm_taskbar != 0u) {
                    Shell_NotifyIconW(NIM_ADD.toUInt(), g_nid.ptr)
                    return 0
                }
            }
        }
        return DefWindowProcW(hwnd, msg, wp, lp)
    }

    private fun applyDarkMode() {
        val h = LoadLibraryW("uxtheme.dll") ?: return
        val ordinalStr = "#135"
        val f = GetProcAddress(h, ordinalStr) ?: return
        val func = f.reinterpret<CFunction<(Int) -> Int>>()
        func(1) // _AD_AllowDark
    }

    private fun trayLoop(p: COpaquePointer?): DWORD {
        ecs()
        // Initialize dark mode before anything else, like in the C code
        applyDarkMode()

        g_wm_taskbar = RegisterWindowMessageW("TaskbarCreated")

        val inst = GetModuleHandleW(null)
        memScoped {
            val wc = alloc<WNDCLASSEXW>()
            wc.cbSize = sizeOf<WNDCLASSEXW>().toUInt()
            wc.lpfnWndProc = staticCFunction { hwnd, msg, wp, lp ->
                currentTrayManager?.trayProc(hwnd, msg, wp, lp) ?: DefWindowProcW(hwnd, msg, wp, lp)
            }
            wc.hInstance = inst
            wc.lpszClassName = WC_TRAY.wcstr.ptr
            RegisterClassExW(wc.ptr)
        }

        g_hwnd = CreateWindowExW(
            0u, WC_TRAY, null, 0u,
            0, 0, 0, 0, null, null, inst, null
        )

        EnterCriticalSection(g_cs.ptr)
        g_menu = buildMenu()
        LeaveCriticalSection(g_cs.ptr)

        g_nid.apply {
            cbSize = sizeOf<NOTIFYICONDATAW>().toUInt()
            hWnd = g_hwnd
            uID = ID_TRAY
            uFlags = (NIF_ICON or NIF_MESSAGE or NIF_TIP).toUInt()
            uCallbackMessage = WM_TRAY_CB
            hIcon = LoadIconW(null, IDI_INFORMATION)
            "System Info".copyToTray(szTip.reinterpret())
        }
        Shell_NotifyIconW(NIM_ADD.toUInt(), g_nid.ptr)

        g_ready.value = 1

        memScoped {
            val msg = alloc<MSG>()
            while (GetMessageW(msg.ptr, null, 0u, 0u) > 0) {
                TranslateMessage(msg.ptr)
                DispatchMessageW(msg.ptr)
            }
        }
        return 0u
    }

    fun start(labels: List<String>): Boolean {
        if (g_hwnd != null) return false
        ecs()

        EnterCriticalSection(g_cs.ptr)
        g_labels.clear()
        g_labels.addAll(labels)
        LeaveCriticalSection(g_cs.ptr)

        currentTrayManager = this
        g_thread = CreateThread(null, 0u, staticCFunction { p ->
            currentTrayManager?.trayLoop(p) ?: 0u
        }, null, 0u, null)

        if (g_thread == null) return false

        while (g_ready.value == 0) {
            Sleep(10u)
        }
        return true
    }

    fun stop(): Boolean {
        if (g_hwnd == null) return false
        PostMessageW(g_hwnd, WM_CLOSE.toUInt(), 0u, 0)
        WaitForSingleObject(g_thread, 5000u)
        CloseHandle(g_thread)

        EnterCriticalSection(g_cs.ptr)
        g_menu?.let { DestroyMenu(it) }
        g_menu = null
        g_hwnd = null
        g_thread = null
        g_ready.value = 0
        g_labels.clear()
        LeaveCriticalSection(g_cs.ptr)
        currentTrayManager = null
        return true
    }

    fun notify(title: String, body: String): Boolean = toastShow(title, body)

    fun updateLabel(index: Int, label: String): Boolean {
        ecs()
        EnterCriticalSection(g_cs.ptr)
        if (g_menu == null || index < 0 || index >= g_labels.size) {
            LeaveCriticalSection(g_cs.ptr)
            return false
        }
        g_labels[index] = label
        memScoped {
            val mi = alloc<MENUITEMINFOW>()
            mi.cbSize = sizeOf<MENUITEMINFOW>().toUInt()
            mi.fMask = MIIM_STRING.toUInt()
            mi.dwTypeData = label.wcstr.ptr
            mi.cch = label.length.toUInt()
            SetMenuItemInfoW(g_menu, (ID_MENU_BASE + index.toUInt()), 0, mi.ptr)
        }
        LeaveCriticalSection(g_cs.ptr)
        return true
    }

    private fun String.copyToTray(dest: CPointer<UShortVar>) {
        memScoped {
            val wptr = this@copyToTray.wcstr.ptr
            for (i in 0 until minOf(this@copyToTray.length, 127)) {
                dest[i] = wptr[i]
            }
            dest[minOf(this@copyToTray.length, 127)] = 0u
        }
    }
}

private var currentTrayManager: TrayManager? = null
internal var trayClickEmitter: ((Int) -> Unit)? = null
