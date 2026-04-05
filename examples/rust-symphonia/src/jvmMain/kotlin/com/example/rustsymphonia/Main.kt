package com.example.rustsymphonia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "RustSymphonia — Symphonia + cpal bridge showcase via NNA",
        state = rememberWindowState(width = 800.dp, height = 700.dp),
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                BridgeShowcaseApp()
            }
        }
    }
}

@Composable
fun BridgeShowcaseApp() {
    val player = remember { AudioPlayer() }
    val state by player.state.collectAsState()

    DisposableEffect(Unit) { onDispose { player.close() } }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("RustSymphonia Bridge Showcase", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "symphonia + cpal types bridged via NNA — zero Rust code written",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = { player.runShowcase() },
                enabled = !state.isRunning,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Run Tests")
            }
        }

        if (state.isRunning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Science, contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        "Click 'Run Tests' to exercise the Rust bridge",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        "Creates symphonia + cpal types, calls methods, verifies round-trips",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.results.forEach { result ->
                    ResultCard(result)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: BridgeResult) {
    val isSummary = result.label == "Summary"
    val containerColor = when {
        isSummary && result.success -> MaterialTheme.colorScheme.primaryContainer
        isSummary -> MaterialTheme.colorScheme.errorContainer
        result.success -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.errorContainer
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = if (isSummary) Modifier.fillMaxWidth().padding(top = 8.dp) else Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (result.success) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.label,
                    style = if (isSummary) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.bodyMedium,
                )
                Text(
                    result.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
