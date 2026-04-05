package com.example.rustsysinfo

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
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
import com.example.rustsysinfo.tabs.*

enum class NavItem(val label: String, val icon: ImageVector) {
    System("System", Icons.Default.Info),
    Cpu("CPU", Icons.Default.Memory),
    Memory("Memory", Icons.Default.Storage),
    Disks("Disks", Icons.Default.Album),
    Network("Network", Icons.Default.Wifi),
    Processes("Processes", Icons.Default.List),
    Sensors("Sensors", Icons.Default.Thermostat),
    Users("Users", Icons.Default.Person),
    Groups("Groups", Icons.Default.Group),
}

@Composable
fun App() {
    var selected by remember { mutableStateOf(NavItem.System) }

    val systemFlow = remember { systemInfoFlow() }
    val dynamicFlow = remember { dynamicStateFlow() }
    val systemInfo by systemFlow.collectAsState(initial = null)
    val state by dynamicFlow.collectAsState(initial = null)

    Row(Modifier.fillMaxSize().background(AppColors.bg)) {
        // Sidebar
        Sidebar(selected) { selected = it }

        // Content
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 0.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)
        ) {
            when (selected) {
                NavItem.System -> SystemTab(systemInfo, state?.loadAvg)
                NavItem.Cpu -> CpuTab(state?.cpus ?: emptyList(), state?.globalCpuUsage ?: 0f)
                NavItem.Memory -> MemoryTab(state?.memory)
                NavItem.Disks -> DisksTab(state?.disks ?: emptyList())
                NavItem.Network -> NetworkTab(state?.networks ?: emptyList())
                NavItem.Processes -> ProcessesTab(state?.processes ?: emptyList())
                NavItem.Sensors -> SensorsTab(state?.sensors ?: emptyList())
                NavItem.Users -> UsersTab(state?.users ?: emptyList())
                NavItem.Groups -> GroupsTab(state?.groups ?: emptyList())
            }
        }
    }
}

@Composable
private fun Sidebar(selected: NavItem, onSelect: (NavItem) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(200.dp)
            .background(AppColors.sidebarBg)
            .padding(vertical = 16.dp, horizontal = 8.dp),
    ) {
        // App title
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppColors.green)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Sysinfo",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.textPrimary,
            )
        }
        Text(
            "  Rust FFM Bridge",
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
                onClick = { onSelect(item) },
            )
        }

        Spacer(Modifier.weight(1f))
        Divider(color = AppColors.divider, thickness = 1.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            "  Powered by sysinfo 0.38",
            fontSize = 10.sp,
            color = AppColors.textMuted,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun SidebarItem(item: NavItem, isSelected: Boolean, onClick: () -> Unit) {
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
