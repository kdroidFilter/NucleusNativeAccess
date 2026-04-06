package com.example.rusttrayicon

data class TrayIconState(
    val isActive: Boolean = false,
    val tooltip: String = "Rust Tray Icon Demo",
    val title: String = "",
    val menuItems: List<MenuItemState> = emptyList(),
    val eventLog: List<TrayEvent> = emptyList(),
)

data class MenuItemState(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    val isCheckable: Boolean = false,
    val isChecked: Boolean = false,
)

data class TrayEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val details: String,
)
