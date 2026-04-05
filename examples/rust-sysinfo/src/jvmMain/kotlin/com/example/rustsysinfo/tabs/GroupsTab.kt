package com.example.rustsysinfo.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rustsysinfo.*

@Composable
fun GroupsTab(groups: List<GroupInfo>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("System Groups", groups.size) }

        if (groups.isEmpty()) {
            item {
                InfoCard { MiniLabel("No groups found") }
            }
        }

        items(groups) { group ->
            InfoCard {
                Badge(group.name, color = AppColors.purple)
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
