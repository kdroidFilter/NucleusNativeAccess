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
            Text("Open a native file picker with optional filters (via add_filter).", style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Any File", enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFile(title = "Select any file")
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Images", enabled = !busy, color = AppColors.green, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFile(
                            title = "Select an image",
                            filters = listOf("Images" to listOf("png", "jpg", "jpeg", "gif", "webp", "bmp")),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Documents", enabled = !busy, color = AppColors.purple, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFile(
                            title = "Select a document",
                            filters = listOf(
                                "Documents" to listOf("pdf", "doc", "docx", "txt", "md"),
                                "Spreadsheets" to listOf("xls", "xlsx", "csv"),
                            ),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Source Code", enabled = !busy, color = AppColors.cyan, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFile(
                            title = "Select source code",
                            filters = listOf(
                                "Rust" to listOf("rs", "toml"),
                                "Kotlin" to listOf("kt", "kts"),
                                "All Source" to listOf("rs", "kt", "java", "py", "js", "ts"),
                            ),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
            }
        }

        SectionHeader("Pick Multiple Files")

        InfoCard {
            Text("Select multiple files with filters.", style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Any Files", enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFiles(title = "Select files")
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Images", enabled = !busy, color = AppColors.green, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFiles(
                            title = "Select images",
                            filters = listOf("Images" to listOf("png", "jpg", "jpeg", "gif", "webp")),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
            }
        }

        SectionHeader("Pick File or Folder")

        InfoCard {
            Text("Native picker allowing both files and folders.", style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("File or Folder", enabled = !busy, color = AppColors.orange, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFileOrFolder(title = "Select a file or folder")
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Files or Folders", enabled = !busy, color = AppColors.orange, onClick = {
                    busy = true
                    scope.launch {
                        val r = pickFilesOrFolders(title = "Select files or folders")
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
                            Badge(path.substringAfterLast('/'), AppColors.green)
                            MiniLabel(path, AppColors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
