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
fun FilePickerTab(onResult: (DialogResult) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<DialogResult?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Pick Single File")

        InfoCard {
            Text("Open a native file picker to select one file.", style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Pick File", enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFile(title = "Select a file")
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("From Home", enabled = !busy, color = AppColors.cyan, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFile(
                            title = "Select a file",
                            directory = System.getProperty("user.home"),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("From Desktop", enabled = !busy, color = AppColors.purple, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFile(
                            title = "Select a file",
                            directory = System.getProperty("user.home") + "/Desktop",
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
            }
        }

        SectionHeader("Pick Multiple Files")

        InfoCard {
            Text("Open a native file picker to select multiple files.", style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Pick Files", enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFiles(title = "Select files")
                        lastResult = r; onResult(r); busy = false
                    }
                })
            }
        }

        SectionHeader("Pick File or Folder")

        InfoCard {
            Text("Open a native picker allowing both files and folders.", style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("File or Folder", enabled = !busy, color = AppColors.green, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFileOrFolder(title = "Select a file or folder")
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Files or Folders", enabled = !busy, color = AppColors.green, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFilesOrFolders(title = "Select files or folders")
                        lastResult = r; onResult(r); busy = false
                    }
                })
            }
        }

        // Last result display
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
                            Badge(path.substringAfterLast('/'), AppColors.green)
                            MiniLabel(path, AppColors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
