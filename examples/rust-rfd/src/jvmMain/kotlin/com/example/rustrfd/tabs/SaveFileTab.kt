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
fun SaveFileTab(onResult: (DialogResult) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<DialogResult?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Save File Dialog")

        InfoCard {
            Text(
                "Native save dialog with set_file_name, set_directory, and add_filter.",
                style = MaterialTheme.typography.body2,
            )
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Save Any", enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val r = saveFile(title = "Save file")
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Save .txt", enabled = !busy, color = AppColors.green, onClick = {
                    busy = true
                    scope.launch {
                        val r = saveFile(
                            title = "Save text file",
                            fileName = "untitled.txt",
                            filters = listOf("Text Files" to listOf("txt", "md")),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Save .json", enabled = !busy, color = AppColors.cyan, onClick = {
                    busy = true
                    scope.launch {
                        val r = saveFile(
                            title = "Save JSON",
                            fileName = "data.json",
                            filters = listOf("JSON" to listOf("json")),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Save Image", enabled = !busy, color = AppColors.purple, onClick = {
                    busy = true
                    scope.launch {
                        val r = saveFile(
                            title = "Save image",
                            fileName = "screenshot.png",
                            filters = listOf(
                                "PNG" to listOf("png"),
                                "JPEG" to listOf("jpg", "jpeg"),
                                "All Images" to listOf("png", "jpg", "jpeg", "webp", "bmp"),
                            ),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
            }
        }

        SectionHeader("Save with Starting Directory")

        InfoCard {
            Text("Save dialog that opens in a specific directory.", style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Save to Home", enabled = !busy, color = AppColors.orange, onClick = {
                    busy = true
                    scope.launch {
                        val r = saveFile(
                            title = "Save to home directory",
                            fileName = "export.csv",
                            directory = System.getProperty("user.home"),
                            filters = listOf("CSV" to listOf("csv")),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Save to Desktop", enabled = !busy, color = AppColors.orange, onClick = {
                    busy = true
                    scope.launch {
                        val r = saveFile(
                            title = "Save to desktop",
                            fileName = "report.pdf",
                            directory = System.getProperty("user.home") + "/Desktop",
                            filters = listOf("PDF" to listOf("pdf")),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
            }
        }

        lastResult?.let { result ->
            SectionHeader("Last Result")
            InfoCard {
                MetricRow("Type", result.type.label)
                MetricRow("Status", if (result.cancelled) "Cancelled" else "Path chosen")
                if (result.paths.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    result.paths.forEach { path ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Badge(path.substringAfterLast('/'), AppColors.orange)
                            MiniLabel(path, AppColors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
