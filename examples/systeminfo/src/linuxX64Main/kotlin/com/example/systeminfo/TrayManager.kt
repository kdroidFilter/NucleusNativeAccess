@file:OptIn(ExperimentalForeignApi::class)

package com.example.systeminfo

import gio.*
import kotlinx.cinterop.*

private const val SNI_XML = """
<node>
  <interface name='org.kde.StatusNotifierItem'>
    <method name='Activate'>
      <arg type='i' name='x' direction='in'/>
      <arg type='i' name='y' direction='in'/>
    </method>
    <method name='SecondaryActivate'>
      <arg type='i' name='x' direction='in'/>
      <arg type='i' name='y' direction='in'/>
    </method>
    <method name='ContextMenu'>
      <arg type='i' name='x' direction='in'/>
      <arg type='i' name='y' direction='in'/>
    </method>
    <method name='Scroll'>
      <arg type='i' name='delta' direction='in'/>
      <arg type='s' name='orientation' direction='in'/>
    </method>
    <signal name='NewTitle'/>
    <signal name='NewIcon'/>
    <signal name='NewStatus'>
      <arg type='s' name='status'/>
    </signal>
    <signal name='NewToolTip'/>
    <property name='Category' type='s' access='read'/>
    <property name='Id' type='s' access='read'/>
    <property name='Title' type='s' access='read'/>
    <property name='Status' type='s' access='read'/>
    <property name='IconName' type='s' access='read'/>
    <property name='IconThemePath' type='s' access='read'/>
    <property name='ToolTip' type='(sa(iiay)ss)' access='read'/>
    <property name='ItemIsMenu' type='b' access='read'/>
    <property name='Menu' type='o' access='read'/>
  </interface>
</node>
"""

internal class TrayManager(
    var title: String = "System Info",
    var body: String = "",
    var menuLabels: List<String> = emptyList()
) {
    var connection: CPointer<GDBusConnection>? = null
    var registrationId: UInt = 0u
    var busId: UInt = 0u
    var mainLoop: CPointer<GMainLoop>? = null
    var thread: CPointer<GThread>? = null
    var nodeInfo: CPointer<GDBusNodeInfo>? = null
    var menuServer: CPointer<systray.DbusmenuServer>? = null
    val menuItems = mutableListOf<CPointer<systray.DbusmenuMenuitem>?>()

    fun updateLabel(index: Int, label: String) {
        if (index in menuItems.indices) {
            val item = menuItems[index]
            if (item != null) {
                systray.dbusmenu_menuitem_property_set(item, systray.DBUSMENU_MENUITEM_PROP_LABEL, label)
            }
        }
    }

    fun start(title: String, labels: List<String>) {
        if (mainLoop != null) return
        this.title = title
        this.menuLabels = labels
        this.body = labels.joinToString("\n")

        nodeInfo = g_dbus_node_info_new_for_xml(SNI_XML, null)

        val root = systray.dbusmenu_menuitem_new()
        labels.forEachIndexed { i, label ->
            val item = systray.dbusmenu_menuitem_new()
            systray.dbusmenu_menuitem_property_set(item, systray.DBUSMENU_MENUITEM_PROP_LABEL, label)
            systray.dbusmenu_menuitem_property_set_bool(item, systray.DBUSMENU_MENUITEM_PROP_ENABLED, 1)

            g_signal_connect_data(
                item?.reinterpret(),
                "item-activated",
                staticCFunction { _: CPointer<systray.DbusmenuMenuitem>?, _: UInt, userData: COpaquePointer? ->
                    val index = userData.toLong().toInt()
                    trayClickEmitter?.invoke(index)
                    Unit
                }.reinterpret(),
                i.toLong().toCPointer(),
                null,
                0u
            )
            systray.dbusmenu_menuitem_child_append(root, item)
            menuItems.add(item)
        }

        menuServer = systray.dbusmenu_server_new("/MenuBar")
        systray.dbusmenu_server_set_root(menuServer, root)

        mainLoop = g_main_loop_new(null, 0)

        val pid = platform.posix.getpid()
        val busName = "org.kde.StatusNotifierItem-$pid-1"

        val stableRef = StableRef.create(this)
        trayManagerStableRef = stableRef

        busId = g_bus_own_name(
            G_BUS_TYPE_SESSION,
            busName,
            G_BUS_NAME_OWNER_FLAGS_NONE,
            staticCFunction { conn: CPointer<GDBusConnection>?, _: CPointer<ByteVar>?, userData: COpaquePointer? ->
                val manager = userData?.asStableRef<TrayManager>()?.get() ?: return@staticCFunction
                manager.connection = conn
                memScoped {
                    val error = alloc<CPointerVar<GError>>()
                    manager.registrationId = g_dbus_connection_register_object(
                        conn,
                        "/StatusNotifierItem",
                        manager.nodeInfo?.pointed?.interfaces?.get(0),
                        globalSniVTablePtr,
                        userData,
                        null,
                        error.ptr
                    )
                }
            },
            staticCFunction { conn: CPointer<GDBusConnection>?, name: CPointer<ByteVar>?, _: COpaquePointer? ->
                memScoped {
                    val args = allocArray<CPointerVar<GVariant>>(1)
                    args[0] = g_variant_new_string(name?.toKString())
                    val parameters = g_variant_new_tuple(args, 1u)

                    g_dbus_connection_call(
                        conn,
                        "org.kde.StatusNotifierWatcher",
                        "/StatusNotifierWatcher",
                        "org.kde.StatusNotifierWatcher",
                        "RegisterStatusNotifierItem",
                        parameters,
                        null,
                        G_DBUS_CALL_FLAGS_NONE,
                        -1,
                        null,
                        null,
                        null
                    )
                }
            },
            null,
            stableRef.asCPointer(),
            null
        )

        thread = g_thread_new("sni", staticCFunction { data: COpaquePointer? ->
            g_main_loop_run(data?.reinterpret())
            null
        }, mainLoop)
    }

    fun stop() {
        if (mainLoop == null) return

        if (registrationId > 0u && connection != null) {
            g_dbus_connection_unregister_object(connection, registrationId)
        }
        if (busId > 0u) {
            g_bus_unown_name(busId)
        }

        menuServer?.let { g_object_unref(it) }

        g_main_loop_quit(mainLoop)
        thread?.let { g_thread_join(it) }
        g_main_loop_unref(mainLoop)
        nodeInfo?.let { g_dbus_node_info_unref(it) }

        mainLoop = null
        thread = null
        connection = null
        registrationId = 0u
        busId = 0u
        nodeInfo = null
        menuServer = null
        menuItems.clear()

        trayManagerStableRef?.dispose()
        trayManagerStableRef = null
    }
}

internal var currentTrayManager: TrayManager? = null
private var trayManagerStableRef: StableRef<TrayManager>? = null

private val globalSniVTablePtr: CPointer<GDBusInterfaceVTable> = nativeHeap.alloc<GDBusInterfaceVTable>().apply {
    method_call = staticCFunction { _: CPointer<GDBusConnection>?, _: CPointer<ByteVar>?, _: CPointer<ByteVar>?, _: CPointer<ByteVar>?, _: CPointer<ByteVar>?, _: CPointer<GVariant>?, invocation: CPointer<GDBusMethodInvocation>?, _: COpaquePointer? ->
        g_dbus_method_invocation_return_value(invocation, null)
    }
    get_property = staticCFunction { _: CPointer<GDBusConnection>?, _: CPointer<ByteVar>?, _: CPointer<ByteVar>?, _: CPointer<ByteVar>?, prop: CPointer<ByteVar>?, _: CPointer<CPointerVar<GError>>?, userData: COpaquePointer? ->
        val manager = userData?.asStableRef<TrayManager>()?.get() ?: return@staticCFunction null
        val propertyName = prop?.toKString()
        when (propertyName) {
            "Category" -> g_variant_new_string("SystemServices")
            "Id" -> g_variant_new_string("kotlin-systeminfo")
            "Title" -> g_variant_new_string(manager.title)
            "Status" -> g_variant_new_string("Active")
            "IconName" -> g_variant_new_string("computer")
            "IconThemePath" -> g_variant_new_string("")
            "ItemIsMenu" -> g_variant_new_boolean(1)
            "Menu" -> g_variant_new_object_path("/MenuBar")
            "ToolTip" -> memScoped {
                val b = alloc<GVariantBuilder>()
                val arrayType = g_variant_type_new("a(iiay)")
                g_variant_builder_init(b.ptr, arrayType)
                g_variant_type_free(arrayType)
                val builderRes = g_variant_builder_end(b.ptr)

                val tupleBuilder = alloc<GVariantBuilder>()
                val tupleType = g_variant_type_new("(sa(iiay)ss)")
                g_variant_builder_init(tupleBuilder.ptr, tupleType)
                g_variant_type_free(tupleType)

                g_variant_builder_add_value(tupleBuilder.ptr, g_variant_new_string("computer"))
                g_variant_builder_add_value(tupleBuilder.ptr, builderRes)
                g_variant_builder_add_value(tupleBuilder.ptr, g_variant_new_string(manager.title))
                g_variant_builder_add_value(tupleBuilder.ptr, g_variant_new_string(manager.body))

                g_variant_builder_end(tupleBuilder.ptr)
            }
            else -> null
        }
    }
    set_property = null
}.ptr

// Global required because staticCFunction cannot capture variables (Kotlin/Native limitation)
internal var trayClickEmitter: ((Int) -> Unit)? = null
