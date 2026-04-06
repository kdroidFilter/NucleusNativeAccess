package com.example.rustsysinfo.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rustsysinfo.*

@Composable
fun UsersTab(users: List<UserInfo>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionHeader("System Users", users.size) }

        if (users.isEmpty()) {
            item {
                InfoCard { MiniLabel("No users found") }
            }
        }

        items(users) { user ->
            InfoCard {
                Badge(user.name, color = AppColors.cyan)
                if (user.groups.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        user.groups.forEach { group ->
                            Badge(group, color = AppColors.textMuted)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
