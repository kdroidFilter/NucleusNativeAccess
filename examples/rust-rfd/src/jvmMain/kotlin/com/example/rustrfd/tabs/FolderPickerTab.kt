package com.example.rustrfd.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rustrfd.*
import kotlinx.coroutines.launch

@Composable
fun FolderPickerTab(onResult: (DialogResult) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<DialogResult?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Pick Single Folder")

        InfoCard {
            Text("Open a native folder picker to select one directory.", style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Select Folder", enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFolder(title = "Select a folder")
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("From Home", enabled = !busy, color = AppColors.cyan, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFolder(
                            title = "Select a folder",
                            directory = System.getProperty("user.home"),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("From Desktop", enabled = !busy, color = AppColors.purple, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFolder(
                            title = "Select a folder",
                            directory = System.getProperty("user.home") + "/Desktop",
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
            }
        }

        SectionHeader("Pick Multiple Folders")

        InfoCard {
            Text("Open a native folder picker to select multiple directories.", style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Select Folders", enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFolders(title = "Select folders")
                        lastResult = r; onResult(r); busy = false
                    }
                })
            }
        }

        lastResult?.let { result ->
            SectionHeader("Last Result")
            InfoCard {
                MetricRow("Type", result.type.label)
                MetricRow("Status", if (result.cancelled) "Cancelled" else "Selected")
                if (result.paths.isNotEmpty()) {
                    MetricRow("Count", "${result.paths.size}")
                    Spacer(Modifier.height(4.dp))
                    result.paths.forEach { path ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Badge(path.substringAfterLast('/'), AppColors.cyan)
                            MiniLabel(path, AppColors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
