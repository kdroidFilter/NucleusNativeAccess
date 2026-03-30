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
import platform.posix.*
import libnotify.notify_init
import libnotify.notify_notification_new
import libnotify.notify_notification_show
import systray.systray_show_dbus
import systray.systray_hide_dbus
import systray.systray_update_label
import systray.systray_set_click_callback
import kotlin.time.Duration.Companion.milliseconds

// Context for D-Bus signal callback
private class ScreenshotContext(val loop: CPointer<GMainLoop>?) {
    var path: String? = null
}

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
        val path = memScoped {
            val error = alloc<CPointerVar<GError>>()
            val conn = g_bus_get_sync(G_BUS_TYPE_SESSION, null, error.ptr)
            if (conn == null) {
                error.value?.let { errPtr ->
                    val err = errPtr.pointed
                    println("[DEBUG_LOG] Screenshot: D-Bus connection error: ${err.message?.toKString()}")
                    g_error_free(errPtr)
                }
                return@memScoped null
            }

            val loop = g_main_loop_new(null, 0)
            val context = ScreenshotContext(loop)
            val stableRef = StableRef.create(context)

            try {
                val handleToken = "kscreenshot${kotlin.random.Random.nextInt(0, Int.MAX_VALUE)}"
                val uniqueNameRaw = g_dbus_connection_get_unique_name(conn)?.toKString() ?: ""
                val sender = uniqueNameRaw.removePrefix(":").replace(".", "_")
                val requestPath = "/org/freedesktop/portal/desktop/request/$sender/$handleToken"

                println("[DEBUG_LOG] Screenshot: Subscribing to path: $requestPath")

                val subId = g_dbus_connection_signal_subscribe(
                    conn,
                    null,
                    "org.freedesktop.portal.Request",
                    "Response",
                    requestPath,
                    null,
                    G_DBUS_SIGNAL_FLAGS_NONE,
                    staticCFunction { _: CPointer<GDBusConnection>?, _: CPointer<ByteVar>?, _: CPointer<ByteVar>?, _: CPointer<ByteVar>?, _: CPointer<ByteVar>?, params: CPointer<GVariant>?, userData: COpaquePointer? ->
                        val ref = userData?.asStableRef<ScreenshotContext>() ?: return@staticCFunction
                        val ctx = ref.get()

                        val respVal = g_variant_get_child_value(params, 0u)
                        val responseCode = g_variant_get_uint32(respVal)
                        g_variant_unref(respVal)

                        println("[DEBUG_LOG] Screenshot: Portal response: $responseCode")

                        if (responseCode == 0u) {
                            val resultsVal = g_variant_get_child_value(params, 1u)
                            val uriVal = g_variant_lookup_value(resultsVal, "uri", null)
                            if (uriVal != null) {
                                val uri = g_variant_get_string(uriVal, null)?.toKString()
                                if (uri != null) {
                                    val filename = g_filename_from_uri(uri, null, null)
                                    if (filename != null) {
                                        ctx.path = filename.toKString()
                                        g_free(filename)
                                        println("[DEBUG_LOG] Screenshot: Path extracted: ${ctx.path}")
                                    }
                                }
                                g_variant_unref(uriVal)
                            }
                            g_variant_unref(resultsVal)
                        }
                        g_main_loop_quit(ctx.loop)
                    },
                    stableRef.asCPointer(),
                    null
                )

                val optBuilder = alloc<GVariantBuilder>()
                val dictType = g_variant_type_new("a{sv}")
                g_variant_builder_init(optBuilder.ptr, dictType)
                g_variant_type_free(dictType)

                // handle_token
                val htValue = g_variant_new_string(handleToken)
                val htEntry = g_variant_new_dict_entry(
                    g_variant_new_string("handle_token"),
                    g_variant_new_variant(htValue)
                )
                g_variant_builder_add_value(optBuilder.ptr, htEntry)

                // interactive
                val iValue = g_variant_new_boolean(1)
                val iEntry = g_variant_new_dict_entry(g_variant_new_string("interactive"),
                    g_variant_new_variant(iValue)
                )
                g_variant_builder_add_value(optBuilder.ptr, iEntry)

                val options = g_variant_builder_end(optBuilder.ptr)

                val builder = alloc<GVariantBuilder>()
                val tupleType = g_variant_type_new("(sa{sv})")
                g_variant_builder_init(builder.ptr, tupleType)
                g_variant_type_free(tupleType)

                g_variant_builder_add_value(builder.ptr, g_variant_new_string(""))
                g_variant_builder_add_value(builder.ptr, options)
                val parameters = g_variant_builder_end(builder.ptr)

                println("[DEBUG_LOG] Screenshot: Calling Portal Screenshot method...")
                val res = g_dbus_connection_call_sync(
                    conn,
                    "org.freedesktop.portal.Desktop",
                    "/org/freedesktop/portal/desktop",
                    "org.freedesktop.portal.Screenshot",
                    "Screenshot",
                    parameters,
                    null,
                    G_DBUS_CALL_FLAGS_NONE,
                    -1,
                    null,
                    error.ptr
                )

                if (res != null) {
                    val handleVal = g_variant_get_child_value(res, 0u)
                    val handleStr = g_variant_get_string(handleVal, null)?.toKString()
                    println("[DEBUG_LOG] Screenshot: Portal returned handle: $handleStr")
                    g_variant_unref(handleVal)

                    g_main_loop_run(loop)
                    g_variant_unref(res)
                } else {
                    error.value?.let { errPtr ->
                        val err = errPtr.pointed
                        println("[DEBUG_LOG] Screenshot: Portal method call failed: ${err.message?.toKString()}")
                        g_error_free(errPtr)
                    }
                }

                g_dbus_connection_signal_unsubscribe(conn, subId)
                context.path
            } finally {
                stableRef.dispose()
                g_main_loop_unref(loop)
                g_object_unref(conn)
            }
        }

        if (path == null) return@withContext byteArrayOf()

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
            delay(intervalMs.milliseconds)
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
