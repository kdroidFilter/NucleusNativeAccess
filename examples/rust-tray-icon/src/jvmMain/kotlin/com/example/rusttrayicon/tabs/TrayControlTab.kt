package com.example.rusttrayicon.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rusttrayicon.*

@Composable
fun TrayControlTab(manager: TrayIconManager, onEvent: (TrayEvent) -> Unit) {
    var tooltip by remember { mutableStateOf("Rust Tray Icon Demo") }
    var title by remember { mutableStateOf("") }
    var menuItems by remember { mutableStateOf("Open|Settings|About") }
    var isVisible by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader("Tray Icon Control")

        InfoCard {
            Text("Status", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox(
                    label = "State",
                    value = if (manager.isActive) "Active" else "Inactive",
                    accentColor = if (manager.isActive) AppColors.green else AppColors.red,
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    label = "Visible",
                    value = if (isVisible) "Yes" else "No",
                    accentColor = if (isVisible) AppColors.green else AppColors.orange,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        InfoCard {
            Text("Create / Destroy", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary)
            Spacer(Modifier.height(4.dp))
            MiniLabel("Context menu items (|-separated)")
            Spacer(Modifier.height(4.dp))
            SimpleTextField(
                value = menuItems,
                onValueChange = { menuItems = it },
                placeholder = "Open|Settings|About",
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = "Create (RGB)",
                    color = AppColors.green,
                    enabled = !manager.isActive,
                    onClick = {
                        manager.create(
                            tooltip = tooltip,
                            title = title,
                            menuItems = menuItems,
                            onEvent = onEvent,
                        )
                    },
                )
                ActionButton(
                    text = "Create (Icon)",
                    color = AppColors.accent,
                    enabled = !manager.isActive,
                    onClick = {
                        // Demonstrates opaque type bridging:
                        // make_icon returns an opaque Icon handle, passed to create_tray_with_icon
                        Tray_icon_wrapper.make_icon(108, 142, 255, 32).use { icon ->
                            manager.createWithIcon(
                                icon = icon,
                                tooltip = tooltip,
                                title = title,
                                menuItems = menuItems,
                                onEvent = onEvent,
                            )
                        }
                    },
                )
                ActionButton(
                    text = "Destroy",
                    color = AppColors.red,
                    enabled = manager.isActive,
                    onClick = {
                        manager.destroy()
                        onEvent(TrayEvent(type = "Destroyed", details = "Tray icon removed"))
                    },
                )
            }
        }

        InfoCard {
            Text("Tooltip", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleTextField(
                    value = tooltip,
                    onValueChange = { tooltip = it },
                    placeholder = "Enter tooltip...",
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = "Apply",
                    enabled = manager.isActive,
                    onClick = {
                        manager.setTooltip(tooltip)
                        onEvent(TrayEvent(type = "Tooltip", details = "Set to '$tooltip'"))
                    },
                )
            }
        }

        InfoCard {
            Text("Title", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary)
            Spacer(Modifier.height(4.dp))
            MiniLabel("Text next to the icon (macOS/Linux)")
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Enter title...",
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = "Apply",
                    enabled = manager.isActive,
                    onClick = {
                        manager.setTitle(title)
                        onEvent(TrayEvent(type = "Title", details = "Set to '$title'"))
                    },
                )
            }
        }

        InfoCard {
            Text("Visibility & Icon", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(text = "Show", color = AppColors.green, enabled = manager.isActive && !isVisible, onClick = {
                    manager.setVisible(true); isVisible = true
                    onEvent(TrayEvent(type = "Visibility", details = "Shown"))
                })
                ActionButton(text = "Hide", color = AppColors.orange, enabled = manager.isActive && isVisible, onClick = {
                    manager.setVisible(false); isVisible = false
                    onEvent(TrayEvent(type = "Visibility", details = "Hidden"))
                })
            }
            Spacer(Modifier.height(4.dp))
            MiniLabel("Via set_icon_color (flat API)")
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(text = "Red", color = AppColors.red, enabled = manager.isActive, onClick = {
                    manager.setIconColor(239, 68, 68)
                    onEvent(TrayEvent(type = "Icon", details = "Color changed to Red (flat)"))
                })
                ActionButton(text = "Green", color = AppColors.green, enabled = manager.isActive, onClick = {
                    manager.setIconColor(74, 222, 128)
                    onEvent(TrayEvent(type = "Icon", details = "Color changed to Green (flat)"))
                })
                ActionButton(text = "Blue", color = AppColors.accent, enabled = manager.isActive, onClick = {
                    manager.setIconColor(108, 142, 255)
                    onEvent(TrayEvent(type = "Icon", details = "Color changed to Blue (flat)"))
                })
            }
            Spacer(Modifier.height(4.dp))
            MiniLabel("Via update_icon (opaque Icon handle)")
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(text = "Red", color = AppColors.red, enabled = manager.isActive, onClick = {
                    Tray_icon_wrapper.make_icon(239, 68, 68, 32).use { manager.updateIcon(it) }
                    onEvent(TrayEvent(type = "Icon", details = "Color changed to Red (opaque)"))
                })
                ActionButton(text = "Green", color = AppColors.green, enabled = manager.isActive, onClick = {
                    Tray_icon_wrapper.make_icon(74, 222, 128, 32).use { manager.updateIcon(it) }
                    onEvent(TrayEvent(type = "Icon", details = "Color changed to Green (opaque)"))
                })
                ActionButton(text = "Blue", color = AppColors.accent, enabled = manager.isActive, onClick = {
                    Tray_icon_wrapper.make_icon(108, 142, 255, 32).use { manager.updateIcon(it) }
                    onEvent(TrayEvent(type = "Icon", details = "Color changed to Blue (opaque)"))
                })
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
