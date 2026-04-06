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
fun MessageTab(onResult: (DialogResult) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<DialogResult?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Info Messages")

        InfoCard {
            Text(
                "Show native message dialogs with Info level. Demonstrates different button configurations.",
                style = MaterialTheme.typography.body2,
            )
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("OK", enabled = !busy, color = AppColors.accent, onClick = {
                    busy = true
                    scope.launch {
                        val r = showMessage(
                            title = "Information",
                            description = "This is an informational message from the rfd crate, displayed through a Rust FFM bridge.",
                            level = MessageLevel.Info,
                            buttons = MessageButtons.ok(),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("OK / Cancel", enabled = !busy, color = AppColors.accent, onClick = {
                    busy = true
                    scope.launch {
                        val r = showMessage(
                            title = "Confirm Action",
                            description = "Do you want to proceed with this action?",
                            level = MessageLevel.Info,
                            buttons = MessageButtons.okCancel(),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Yes / No", enabled = !busy, color = AppColors.accent, onClick = {
                    busy = true
                    scope.launch {
                        val r = showMessage(
                            title = "Question",
                            description = "Would you like to enable this feature?",
                            level = MessageLevel.Info,
                            buttons = MessageButtons.yesNo(),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
            }
        }

        SectionHeader("Warning Messages")

        InfoCard {
            Text("Show native warning dialogs.", style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Warning OK", enabled = !busy, color = AppColors.orange, onClick = {
                    busy = true
                    scope.launch {
                        val r = showMessage(
                            title = "Warning",
                            description = "This operation may take a long time. Are you sure?",
                            level = MessageLevel.Warning,
                            buttons = MessageButtons.ok(),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Warning Yes/No", enabled = !busy, color = AppColors.orange, onClick = {
                    busy = true
                    scope.launch {
                        val r = showMessage(
                            title = "Warning",
                            description = "Unsaved changes will be lost. Continue?",
                            level = MessageLevel.Warning,
                            buttons = MessageButtons.yesNo(),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Warning Yes/No/Cancel", enabled = !busy, color = AppColors.orange, onClick = {
                    busy = true
                    scope.launch {
                        val r = showMessage(
                            title = "Save Changes?",
                            description = "You have unsaved changes. Do you want to save before closing?",
                            level = MessageLevel.Warning,
                            buttons = MessageButtons.yesNoCancel(),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
            }
        }

        SectionHeader("Error Messages")

        InfoCard {
            Text("Show native error dialogs.", style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Error OK", enabled = !busy, color = AppColors.red, onClick = {
                    busy = true
                    scope.launch {
                        val r = showMessage(
                            title = "Error",
                            description = "An unexpected error occurred. The operation could not be completed.",
                            level = MessageLevel.Error,
                            buttons = MessageButtons.ok(),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Error OK/Cancel", enabled = !busy, color = AppColors.red, onClick = {
                    busy = true
                    scope.launch {
                        val r = showMessage(
                            title = "Critical Error",
                            description = "A critical error was detected. Would you like to try again?",
                            level = MessageLevel.Error,
                            buttons = MessageButtons.okCancel(),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
            }
        }

        SectionHeader("Custom Buttons")

        InfoCard {
            Text("Message dialogs with custom button labels.", style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Custom OK", enabled = !busy, color = AppColors.purple, onClick = {
                    busy = true
                    scope.launch {
                        val r = showMessage(
                            title = "Custom Dialog",
                            description = "This dialog has a custom button label.",
                            level = MessageLevel.Info,
                            buttons = MessageButtons.okCustom("Got it!"),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Custom OK/Cancel", enabled = !busy, color = AppColors.purple, onClick = {
                    busy = true
                    scope.launch {
                        val r = showMessage(
                            title = "Custom Confirm",
                            description = "Choose your action.",
                            level = MessageLevel.Info,
                            buttons = MessageButtons.okCancelCustom("Proceed", "Abort"),
                        )
                        lastResult = r; onResult(r); busy = false
                    }
                })
                ActionButton("Custom Yes/No/Cancel", enabled = !busy, color = AppColors.purple, onClick = {
                    busy = true
                    scope.launch {
                        val r = showMessage(
                            title = "Three Choices",
                            description = "Pick one of three custom options.",
                            level = MessageLevel.Warning,
                            buttons = MessageButtons.yesNoCancelCustom("Save", "Discard", "Go Back"),
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
                MetricRow("Result", result.messageResult ?: "N/A")
                MetricRow("Time", result.formattedTime)
            }
        }
    }
}
