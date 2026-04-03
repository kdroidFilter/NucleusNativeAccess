package com.example.rustcamera

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import kotlinx.coroutines.*

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Rust Camera",
        state = rememberWindowState(width = 800.dp, height = 600.dp)
    ) {
        CameraApp()
    }
}

@Composable
fun CameraApp() {
    val camera = remember { Rustcamera.create_camera(640, 480) }
    var frameBitmap by remember { mutableStateOf<BufferedImage?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (isActive) {
            try {
                val bytes = withContext(Dispatchers.Default) {
                    Rustcamera.camera_frame(camera)
                }
                if (bytes != null && bytes.isNotEmpty()) {
                    val w = Rustcamera.camera_width(camera)
                    val h = Rustcamera.camera_height(camera)
                    val img = BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR)
                    val buffer = img.raster.dataBuffer as DataBufferByte
                    System.arraycopy(bytes, 0, buffer.data, 0, minOf(bytes.size, buffer.data.size))
                    frameBitmap = img
                }
            } catch (e: Throwable) {
                errorMessage = "Error: ${e.message}"
                break
            }
            delay(33)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            errorMessage != null -> Text(errorMessage!!)
            frameBitmap != null -> {
                Image(
                    bitmap = frameBitmap!!.toComposeImageBitmap(),
                    contentDescription = "Camera Feed",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            else -> Text("Starting...")
        }
    }
}
