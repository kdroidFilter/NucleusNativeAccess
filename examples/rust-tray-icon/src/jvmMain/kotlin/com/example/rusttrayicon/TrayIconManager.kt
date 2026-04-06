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
            onEvent(TrayEvent(type = "Created", details = "Tray icon with menu: $menuItems"))
        } catch (e: Exception) {
            onEvent(TrayEvent(type = "Error", details = e.message ?: e.toString()))
        }
    }

    fun destroy() {
        try { Tray_icon_wrapper.destroy_tray() } catch (_: Exception) {}
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

    /** Polls pending tray events (click, enter, leave...). */
    fun pollTrayEvents(): List<String> {
        val events = mutableListOf<String>()
        while (true) {
            val e = try { Tray_icon_wrapper.poll_tray_event() } catch (_: Exception) { null }
            if (e != null) events.add(e) else break
        }
        return events
    }

    /** Polls pending menu events (returns menu item labels). */
    fun pollMenuEvents(): List<String> {
        val events = mutableListOf<String>()
        while (true) {
            val e = try { Tray_icon_wrapper.poll_menu_event() } catch (_: Exception) { null }
            if (e != null) events.add(e) else break
        }
        return events
    }
}
