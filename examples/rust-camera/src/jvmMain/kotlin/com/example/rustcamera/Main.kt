package com.example.rustcamera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Rust Camera (nokhwa via NNA)",
        state = rememberWindowState(width = 800.dp, height = 600.dp)
    ) {
        MaterialTheme {
            CameraApp()
        }
    }
}

@Composable
fun CameraApp() {
    // nokhwa Camera API is auto-generated from the crate via NNA:
    //   Camera(index: CameraIndex, format: RequestedFormat)
    //   camera.open_stream()
    //   camera.frame() -> Buffer
    //   camera.stop_stream()
    //
    // TODO: Buffer.buffer() -> ByteArray and decode_image need additional support
    // (borrowed slice return + generic monomorphisation for decode_image<RgbFormat>)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "nokhwa imported via crate(\"nokhwa\", \"0.10\")\n" +
                "14 classes generated automatically\n" +
                "See build/generated/kne/ for generated code",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
