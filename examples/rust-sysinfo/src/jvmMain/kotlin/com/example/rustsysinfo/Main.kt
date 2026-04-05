package com.example.rustsysinfo

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Sysinfo — Rust FFM Bridge",
        state = rememberWindowState(width = 1100.dp, height = 750.dp),
    ) {
        SysInfoTheme {
            App()
        }
    }
}
