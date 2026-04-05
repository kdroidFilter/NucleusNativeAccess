package com.example.rustsymphonia

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.*
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.*

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "RustSymphonia — Symphonia + cpal via NNA · zero Rust code",
        state = rememberWindowState(width = 1000.dp, height = 760.dp),
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                MediaPlayerApp()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Root composable
// ---------------------------------------------------------------------------

@Composable
fun MediaPlayerApp() {
    val player = remember { AudioPlayer() }
    val state by player.state.collectAsState()
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) { onDispose { player.close() } }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Header ──────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("RustSymphonia", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "symphonia (decode) + cpal (output) via NNA — zero Rust code written",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Open audio file"
                        fileFilter = FileNameExtensionFilter(
                            "Audio (mp3, flac, ogg, wav, m4a, aiff, opus, wv)",
                            "mp3", "flac", "ogg", "oga", "wav", "m4a", "aac", "aiff", "aif", "opus", "wv",
                        )
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                        player.load(chooser.selectedFile.absolutePath)
                }
            }) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Open File")
            }
        }

        // ── States ──────────────────────────────────────────────────────────
        when (val s = state) {
            is PlayerState.Idle    -> IdleScreen()
            is PlayerState.Loading -> LoadingScreen(s.message)
            is PlayerState.Error   -> ErrorScreen(s.message)
            is PlayerState.Ready   -> ReadyScreen(s, player)
        }
    }
}

// ---------------------------------------------------------------------------
// Idle / Loading / Error screens
// ---------------------------------------------------------------------------

@Composable
private fun IdleScreen() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline)
        Text("Open an audio file to start", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline)
        Text("MP3 · FLAC · OGG/Vorbis · WAV · AAC/M4A · Opus · WavPack · AIFF",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun LoadingScreen(msg: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator()
        Text(msg)
    }
}

@Composable
private fun ErrorScreen(msg: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

// ---------------------------------------------------------------------------
// Ready screen — full player
// ---------------------------------------------------------------------------

@Composable
private fun ReadyScreen(s: PlayerState.Ready, player: AudioPlayer) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Row 1: Now playing + codec info ─────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NowPlayingCard(s.info, modifier = Modifier.weight(1.4f))
            FormatInfoCard(s.info, s.outputDeviceName, modifier = Modifier.weight(1f))
        }

        // ── Waveform ─────────────────────────────────────────────────────────
        // Demonstrates: Symphonia Decoder → SampleBuffer → PCM peaks
        FeatureCard(label = "Waveform  ·  Symphonia Decoder → SampleBuffer<f32> → PCM peaks") {
            WaveformCanvas(
                samples = s.info.waveform,
                progress = if (s.info.durationSecs > 0) s.position / s.info.durationSecs else 0.0,
                modifier = Modifier.fillMaxWidth().height(96.dp),
            )
        }

        // ── Spectrum ─────────────────────────────────────────────────────────
        // Demonstrates: cpal audio callback drives real-time band energy
        FeatureCard(label = "Spectrum  ·  cpal callback → real-time band energy") {
            SpectrumCanvas(
                bands = s.spectrumBands,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            )
        }

        // ── Transport ────────────────────────────────────────────────────────
        TransportBar(
            state = s,
            onPlay   = { player.play() },
            onPause  = { player.pause() },
            onStop   = { player.stop() },
            onSeek   = { player.seekTo(it) },
            onVolume = { player.setVolume(it) },
        )

        // ── Row 2: Metadata + Tracks + Cues ─────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.info.tags.isNotEmpty()) {
                MetadataCard(s.info.tags, modifier = Modifier.weight(1f))
            }
            if (s.info.tracks.size > 1 || s.info.cues.isNotEmpty()) {
                TracksAndCuesCard(s.info, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Now playing card
// ---------------------------------------------------------------------------

@Composable
private fun NowPlayingCard(info: AudioInfo, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Now Playing", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                info.tags["TrackTitle"] ?: info.tags["Title"] ?: info.fileName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
            )
            info.tags["Artist"]?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
            info.tags["Album"]?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(info.container)
                Chip(info.codec)
                info.tags["Date"]?.let { Chip(it.take(4)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Format / codec info card
// ---------------------------------------------------------------------------

@Composable
private fun FormatInfoCard(info: AudioInfo, deviceName: String, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Audio · cpal output", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            InfoRow("Container",   info.container)
            InfoRow("Codec",       info.codec)
            InfoRow("Sample rate", "${info.sampleRate} Hz")
            InfoRow("Channels",    "${info.channels} (${channelLabel(info.channels)})")
            info.bitsPerSample?.let { InfoRow("Bit depth", "$it-bit") }
            InfoRow("Duration",    formatTime(info.durationSecs))
            info.bitrateKbps?.let { InfoRow("Bitrate", "$it kbps") }
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            InfoRow("Output device", deviceName, labelWidth = 90.dp)
            InfoRow("Tracks", "${info.tracks.size}", labelWidth = 90.dp)
            InfoRow("Gapless", "enabled", labelWidth = 90.dp)
        }
    }
}

// ---------------------------------------------------------------------------
// Transport bar: seek + volume + play/pause/stop
// ---------------------------------------------------------------------------

@Composable
private fun TransportBar(
    state: PlayerState.Ready,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Double) -> Unit,
    onVolume: (Float) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {

            // Seek bar
            val duration = state.info.durationSecs.toFloat().takeIf { it > 0f } ?: 1f
            var seeking by remember { mutableStateOf(false) }
            var seekValue by remember { mutableStateOf(state.position.toFloat()) }
            if (!seeking) seekValue = state.position.toFloat()

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatTime(seekValue.toDouble()), style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace)
                Slider(
                    value = seekValue,
                    onValueChange = { seekValue = it; seeking = true },
                    onValueChangeFinished = { onSeek(seekValue.toDouble()); seeking = false },
                    valueRange = 0f..duration,
                    modifier = Modifier.weight(1f),
                )
                Text(formatTime(state.info.durationSecs), style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace)
            }

            // Controls row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Stop
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
                // Play / Pause
                FilledIconButton(
                    onClick = { if (state.isPlaying) onPause() else onPlay() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Volume
                Icon(Icons.Default.VolumeUp, contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                Slider(
                    value = state.volume,
                    onValueChange = onVolume,
                    valueRange = 0f..1f,
                    modifier = Modifier.width(120.dp),
                )
                Text("${(state.volume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)

                Spacer(Modifier.weight(1f))

                // Symphonia: FormatReader::seek — SeekMode label
                val seekModeLabel = if (state.isPlaying) "SeekMode::Accurate" else "ready"
                Text(seekModeLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Metadata card
// ---------------------------------------------------------------------------

@Composable
private fun MetadataCard(tags: Map<String, String>, modifier: Modifier) {
    FeatureCard(
        label = "Metadata  ·  Symphonia reads ID3v2 / Vorbis comments / APE / MP4 atoms",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            tags.entries.take(12).forEach { (k, v) ->
                InfoRow(k, v)
            }
            if (tags.size > 12) {
                Text("…and ${tags.size - 12} more", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tracks + cue points card
// ---------------------------------------------------------------------------

@Composable
private fun TracksAndCuesCard(info: AudioInfo, modifier: Modifier) {
    FeatureCard(
        label = "Tracks  ·  Symphonia FormatReader::tracks() + cues()",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (info.tracks.size > 1) {
                Text("Tracks", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                info.tracks.forEach { t ->
                    Text(
                        "Track ${t.id}: ${t.codec}  ${t.sampleRate} Hz  ${channelLabel(t.channels)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            if (info.cues.isNotEmpty()) {
                Text("Chapters / Cue points", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                info.cues.forEach { (label, secs) ->
                    Text("${formatTime(secs)}  $label", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Waveform canvas
// ---------------------------------------------------------------------------

@Composable
private fun WaveformCanvas(samples: FloatArray, progress: Double, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryDim = primary.copy(alpha = 0.35f)

    Canvas(modifier = modifier) {
        if (samples.isEmpty()) return@Canvas
        val mid = size.height / 2f
        val progressX = (progress * size.width).toFloat()
        val step = samples.size.toFloat() / size.width

        for (x in 0 until size.width.toInt()) {
            val idx = (x * step).toInt().coerceIn(0, samples.lastIndex)
            val amp = samples[idx] * mid * 0.9f
            val color = if (x < progressX) primary else primaryDim
            drawLine(color, Offset(x.toFloat(), mid - amp), Offset(x.toFloat(), mid + amp), strokeWidth = 1f)
        }

        // playhead
        drawLine(primary, Offset(progressX, 0f), Offset(progressX, size.height), strokeWidth = 2f)
    }
}

// ---------------------------------------------------------------------------
// Spectrum canvas — 32 bars driven by cpal callback data
// ---------------------------------------------------------------------------

@Composable
private fun SpectrumCanvas(bands: FloatArray, modifier: Modifier) {
    val secondary = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier) {
        if (bands.isEmpty()) return@Canvas
        val barCount = bands.size
        val barW = size.width / barCount
        val gap = 2f

        bands.forEachIndexed { i, value ->
            val barH = value * size.height
            drawRect(
                color = secondary.copy(alpha = 0.7f + value * 0.3f),
                topLeft = Offset(i * barW + gap / 2, size.height - barH),
                size = Size(barW - gap, barH),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable composables
// ---------------------------------------------------------------------------

@Composable
private fun FeatureCard(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, labelWidth: androidx.compose.ui.unit.Dp = 80.dp) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(labelWidth),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Chip(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun formatTime(secs: Double): String {
    val total = secs.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun channelLabel(n: Int) = when (n) {
    1    -> "Mono"
    2    -> "Stereo"
    6    -> "5.1"
    8    -> "7.1"
    else -> "$n ch"
}
