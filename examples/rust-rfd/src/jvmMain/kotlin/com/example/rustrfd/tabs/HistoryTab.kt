package com.example.rustrfd.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rustrfd.*

@Composable
fun HistoryTab(history: List<DialogResult>) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Dialog History", history.size)

        if (history.isEmpty()) {
            InfoCard {
                Text("No dialogs opened yet. Use the other tabs to trigger native dialogs.", style = MaterialTheme.typography.body2)
            }
            return@Column
        }

        // Summary stats
        val fileDialogs = history.count { it.type in listOf(DialogType.PickFile, DialogType.PickFiles) }
        val folderDialogs = history.count { it.type in listOf(DialogType.PickFolder, DialogType.PickFolders) }
        val saveDialogs = history.count { it.type == DialogType.SaveFile }
        val messageDialogs = history.count { it.type in listOf(DialogType.MessageInfo, DialogType.MessageWarning, DialogType.MessageError) }
        val cancelled = history.count { it.cancelled }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("File", "$fileDialogs", Modifier.weight(1f), accentColor = AppColors.green)
            StatBox("Folder", "$folderDialogs", Modifier.weight(1f), accentColor = AppColors.cyan)
            StatBox("Save", "$saveDialogs", Modifier.weight(1f), accentColor = AppColors.orange)
            StatBox("Message", "$messageDialogs", Modifier.weight(1f), accentColor = AppColors.purple)
            StatBox("Cancelled", "$cancelled", Modifier.weight(1f), accentColor = AppColors.red)
        }

        // Entries (most recent first)
        history.reversed().forEach { result ->
            val typeColor = when (result.type) {
                DialogType.PickFile, DialogType.PickFiles -> AppColors.green
                DialogType.PickFolder, DialogType.PickFolders -> AppColors.cyan
                DialogType.SaveFile -> AppColors.orange
                DialogType.MessageInfo -> AppColors.accent
                DialogType.MessageWarning -> AppColors.orange
                DialogType.MessageError -> AppColors.red
            }

            InfoCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Badge(result.type.label, typeColor)
                        if (result.cancelled) {
                            Badge("Cancelled", AppColors.red)
                        }
                    }
                    MiniLabel(result.formattedTime, AppColors.textSecondary)
                }

                MetricRow("Result", result.summary)

                if (result.paths.isNotEmpty()) {
                    result.paths.forEach { path ->
                        MiniLabel(path, AppColors.textSecondary)
                    }
                }
            }
        }
    }
}
