package com.example.rustcamera

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
        title = "Rust Camera (nokhwa via NNA — zero Rust code)",
        state = rememberWindowState(width = 800.dp, height = 600.dp)
    ) {
        MaterialTheme {
            CameraApp()
        }
    }
}

@Composable
fun CameraApp() {
    var camera by remember { mutableStateOf<Camera?>(null) }
    var frameBitmap by remember { mutableStateOf<BufferedImage?>(null) }
    var statusMessage by remember { mutableStateOf("Opening camera...") }
    var fps by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val cam = withContext(Dispatchers.IO) {
                val format = RequestedFormatType.absoluteHighestFrameRate()
                val requested = RequestedFormat.new_rgb_format(format)
                val index = CameraIndex.index(0)
                val c = Camera(index, requested)
                c.open_stream()
                c
            }

            camera = cam
            val res = cam.resolution()
            statusMessage = "${res.width()}x${res.height()}"
            res.close()
        } catch (e: Throwable) {
            statusMessage = "Failed: ${e.message}"
        }
    }

    val cam = camera
    if (cam != null) {
        LaunchedEffect(cam) {
            var frameCount = 0
            var lastFpsTime = System.currentTimeMillis()
            val camRes = cam.resolution()
            val w = camRes.width()
            val h = camRes.height()
            camRes.close()

            while (isActive && cam.is_stream_open()) {
                try {
                    val buf = withContext(Dispatchers.Default) {
                        cam.frame()
                    }
                    val bytes = buf.buffer()
                    buf.close()

                    if (bytes.isNotEmpty()) {
                        val img = BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR)
                        val data = (img.raster.dataBuffer as DataBufferByte).data
                        val pixelCount = minOf(bytes.size / 3, data.size / 3)
                        for (i in 0 until pixelCount) {
                            data[i * 3] = bytes[i * 3 + 2]     // B
                            data[i * 3 + 1] = bytes[i * 3 + 1] // G
                            data[i * 3 + 2] = bytes[i * 3]     // R
                        }
                        frameBitmap = img
                        frameCount++

                        val now = System.currentTimeMillis()
                        if (now - lastFpsTime >= 1000) {
                            fps = frameCount
                            frameCount = 0
                            lastFpsTime = now
                        }
                    }
                } catch (e: Throwable) {
                    statusMessage = "Capture error: ${e.message}"
                    break
                }
                delay(1)
            }
        }

        DisposableEffect(cam) {
            onDispose {
                cam.stop_stream()
                cam.close()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (frameBitmap != null) {
            Image(
                bitmap = frameBitmap!!.toComposeImageBitmap(),
                contentDescription = "Camera Feed",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        ) {
            Text(statusMessage, style = MaterialTheme.typography.labelMedium)
            if (fps > 0) {
                Text("$fps FPS", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
