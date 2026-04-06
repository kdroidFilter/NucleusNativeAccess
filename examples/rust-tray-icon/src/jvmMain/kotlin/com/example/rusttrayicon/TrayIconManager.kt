package com.example.rusttrayicon

/**
 * Manages the lifecycle of a Rust tray icon instance.
 * The Rust wrapper dispatches all calls to the macOS main thread internally via dispatch_sync.
 */
class TrayIconManager {

    val isActive: Boolean get() = Tray_icon_wrapper.is_active()

    fun create(tooltip: String, title: String, menuItems: String, onEvent: (TrayEvent) -> Unit) {
        try {
            Tray_icon_wrapper.create_tray(
                icon_r = 108, icon_g = 142, icon_b = 255,
                tooltip = tooltip.ifEmpty { null },
                title = title.ifEmpty { null },
                menu_items = menuItems.ifEmpty { null },
            )
            // Register event callbacks — Rust calls these directly, no polling needed
            Tray_icon_wrapper.on_tray_event { desc ->
                onEvent(TrayEvent(type = "TrayEvent", details = desc))
            }
            Tray_icon_wrapper.on_menu_event { label ->
                onEvent(TrayEvent(type = "MenuEvent", details = label))
            }
            onEvent(TrayEvent(type = "Created", details = "Tray icon with menu: $menuItems"))
        } catch (e: Exception) {
            onEvent(TrayEvent(type = "Error", details = e.message ?: e.toString()))
        }
    }

    fun destroy() {
        try {
            // Clear event handlers before destroying
            Tray_icon_wrapper.on_tray_event(null)
            Tray_icon_wrapper.on_menu_event(null)
            Tray_icon_wrapper.destroy_tray()
        } catch (_: Exception) {}
    }

    fun setTooltip(tooltip: String) {
        try { Tray_icon_wrapper.set_tooltip(tooltip) } catch (_: Exception) {}
    }

    fun setTitle(title: String) {
        try { Tray_icon_wrapper.set_title(title) } catch (_: Exception) {}
    }

    fun setVisible(visible: Boolean) {
        try { Tray_icon_wrapper.set_visible(visible) } catch (_: Exception) {}
    }

    fun setIconAsTemplate(isTemplate: Boolean) {
        try { Tray_icon_wrapper.set_icon_as_template(isTemplate) } catch (_: Exception) {}
    }

    fun setIconColor(r: Int, g: Int, b: Int) {
        try { Tray_icon_wrapper.set_icon_color(r, g, b) } catch (_: Exception) {}
    }
}
