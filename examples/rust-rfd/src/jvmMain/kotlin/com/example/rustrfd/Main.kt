package com.example.rustrfd

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Rusty File Dialogs — rfd FFM Bridge",
        state = rememberWindowState(width = 1100.dp, height = 750.dp),
    ) {
        RfdTheme {
            App()
        }
    }
}
