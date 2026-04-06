package com.example.rusttrayicon

/**
 * Manages the lifecycle of a Rust tray icon instance.
 * The Rust wrapper dispatches all calls to the macOS main thread internally via dispatch_sync.
 *
 * Demonstrates two approaches:
 * - RGB-based: `create()` takes color values directly (simple flat API)
 * - Icon-based: `createWithIcon()` takes an opaque [Icon] handle (opaque type bridging)
 */
class TrayIconManager {

    val isActive: Boolean get() = Tray_icon_wrapper.is_active()

    /** Creates a tray icon from RGB color values (flat API). */
    fun create(tooltip: String, title: String, menuItems: String, onEvent: (TrayEvent) -> Unit) {
        try {
            Tray_icon_wrapper.create_tray(
                icon_r = 108, icon_g = 142, icon_b = 255,
                tooltip = tooltip.ifEmpty { null },
                title = title.ifEmpty { null },
                menu_items = menuItems.ifEmpty { null },
            )
            registerEventCallbacks(onEvent)
            onEvent(TrayEvent(type = "Created", details = "Tray icon with menu: $menuItems"))
        } catch (e: Exception) {
            onEvent(TrayEvent(type = "Error", details = e.message ?: e.toString()))
        }
    }

    /** Creates a tray icon using an opaque [Icon] handle (demonstrates opaque type bridging). */
    fun createWithIcon(icon: Icon, tooltip: String, title: String, menuItems: String, onEvent: (TrayEvent) -> Unit) {
        try {
            Tray_icon_wrapper.create_tray_with_icon(
                icon = icon,
                tooltip = tooltip.ifEmpty { null },
                title = title.ifEmpty { null },
                menu_items = menuItems.ifEmpty { null },
            )
            registerEventCallbacks(onEvent)
            onEvent(TrayEvent(type = "Created", details = "Tray icon (opaque Icon) with menu: $menuItems"))
        } catch (e: Exception) {
            onEvent(TrayEvent(type = "Error", details = e.message ?: e.toString()))
        }
    }

    fun destroy() {
        try {
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

    /** Updates the tray icon using an opaque [Icon] handle. */
    fun updateIcon(icon: Icon) {
        try { Tray_icon_wrapper.update_icon(icon) } catch (_: Exception) {}
    }

    // ── MenuItem management (opaque MenuItem handles) ──

    /** Creates a new menu item with the given label and enabled state. */
    fun createMenuItem(label: String, enabled: Boolean = true): MenuItem =
        Tray_icon_wrapper.create_menu_item(label, enabled)

    /** Adds a menu item to the current tray context menu. */
    fun addMenuItem(item: MenuItem) {
        try { Tray_icon_wrapper.add_menu_item(item) } catch (_: Exception) {}
    }

    /** Removes a menu item from the current tray context menu. */
    fun removeMenuItem(item: MenuItem) {
        try { Tray_icon_wrapper.remove_menu_item(item) } catch (_: Exception) {}
    }

    /** Adds a separator to the current tray context menu. */
    fun addSeparator() {
        try { Tray_icon_wrapper.add_separator() } catch (_: Exception) {}
    }

    /** Gets the text of a menu item. */
    fun getMenuItemText(item: MenuItem): String =
        Tray_icon_wrapper.get_menu_item_text(item)

    /** Sets the text of a menu item. */
    fun setMenuItemText(item: MenuItem, text: String) {
        try { Tray_icon_wrapper.set_menu_item_text(item, text) } catch (_: Exception) {}
    }

    /** Returns whether a menu item is enabled. */
    fun isMenuItemEnabled(item: MenuItem): Boolean =
        Tray_icon_wrapper.is_menu_item_enabled(item)

    /** Enables or disables a menu item. */
    fun setMenuItemEnabled(item: MenuItem, enabled: Boolean) {
        try { Tray_icon_wrapper.set_menu_item_enabled(item, enabled) } catch (_: Exception) {}
    }

    private fun registerEventCallbacks(onEvent: (TrayEvent) -> Unit) {
        Tray_icon_wrapper.on_tray_event { desc ->
            onEvent(TrayEvent(type = "TrayEvent", details = desc))
        }
        Tray_icon_wrapper.on_menu_event { label ->
            onEvent(TrayEvent(type = "MenuEvent", details = label))
        }
    }
}
