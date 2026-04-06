package com.example.rusttrayicon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rusttrayicon.tabs.*

enum class NavItem(val label: String, val icon: ImageVector) {
    TrayControl("Tray Control", Icons.Default.DesktopWindows),
    TrayInfo("Info", Icons.Default.Info),
    EventLog("Event Log", Icons.Default.History),
}

@Composable
fun App() {
    var selected by remember { mutableStateOf(NavItem.TrayControl) }
    val manager = remember { TrayIconManager() }
    val events = remember { mutableStateListOf<TrayEvent>() }

    val onEvent: (TrayEvent) -> Unit = { events.add(0, it) }

    DisposableEffect(Unit) {
        onDispose { manager.destroy() }
    }

    Row(Modifier.fillMaxSize().background(AppColors.bg)) {
        Sidebar(selected, events.size, manager.isActive) { selected = it }

        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 0.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)
        ) {
            when (selected) {
                NavItem.TrayControl -> TrayControlTab(manager, onEvent)
                NavItem.TrayInfo -> TrayInfoTab(manager, onEvent)
                NavItem.EventLog -> EventLogTab(events)
            }
        }
    }
}

@Composable
private fun Sidebar(selected: NavItem, eventCount: Int, trayActive: Boolean, onSelect: (NavItem) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(200.dp)
            .background(AppColors.sidebarBg)
            .padding(vertical = 16.dp, horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (trayActive) AppColors.green else AppColors.red)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Tray Icon",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.textPrimary,
            )
        }
        Text(
            "  tray-icon FFM Bridge",
            fontSize = 10.sp,
            color = AppColors.textMuted,
            modifier = Modifier.padding(horizontal = 10.dp),
        )

        Spacer(Modifier.height(20.dp))
        Divider(color = AppColors.divider, thickness = 1.dp)
        Spacer(Modifier.height(12.dp))

        NavItem.entries.forEach { item ->
            SidebarItem(
                item = item,
                isSelected = item == selected,
                badge = if (item == NavItem.EventLog && eventCount > 0) "$eventCount" else null,
                onClick = { onSelect(item) },
            )
        }

        Spacer(Modifier.weight(1f))
        Divider(color = AppColors.divider, thickness = 1.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            "  Powered by tray-icon 0.19",
            fontSize = 10.sp,
            color = AppColors.textMuted,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun SidebarItem(item: NavItem, isSelected: Boolean, badge: String? = null, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        isSelected -> AppColors.sidebarSelected
        isHovered -> AppColors.sidebarHover
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> AppColors.accent
        isHovered -> AppColors.textPrimary
        else -> AppColors.textSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            item.label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
        )

        if (badge != null) {
            Spacer(Modifier.width(6.dp))
            Badge(badge, AppColors.cyan)
        }

        if (isSelected) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppColors.accent)
            )
        }
    }
}
