package com.example.rustsymphonia.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rustsymphonia.*

// ---------------------------------------------------------------------------
// Demonstrates what the Symphonia + cpal bridge can do today.
//
// The full file→decode→play pipeline needs MediaSourceStream (takes dyn
// MediaSource) and Probe (no public ctor), which aren't bridgeable yet.
// This tab exercises every bridged API that IS available.
// ---------------------------------------------------------------------------

@Composable
fun PlayerTab() {
    var probeResults by remember { mutableStateOf<List<Pair<String, String>>?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // --- Pipeline status ---
        item {
            SectionHeader("Audio Pipeline")
            InfoCard {
                Text(
                    "Symphonia decode → cpal output",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                PipelineStep("Hint", "Set format hints for the prober", true)
                PipelineStep("FormatOptions", "Configure gapless decoding", true)
                PipelineStep("MetadataOptions", "Set metadata read limits", true)
                PipelineStep("MediaSourceStream", "Wrap a file as a byte source", false,
                    "takes dyn MediaSource — interface params not bridgeable yet")
                PipelineStep("Probe → FormatReader", "Auto-detect container format", false,
                    "needs MediaSourceStream + no public ctor")
                PipelineStep("CodecRegistry → Decoder", "Decode packets to PCM", true,
                    note = "registry + decoder types available")
                PipelineStep("cpal Host → Device → Stream", "Platform audio output", false,
                    "default_host() is a top-level fn not bridged yet")
            }
        }

        // --- Exercice live APIs ---
        item {
            SectionHeader("Live API Demo")
            InfoCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    androidx.compose.material.Button(onClick = { probeResults = exercisePipeline() }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Run Pipeline Demo")
                    }
                    Text(
                        "Creates real Rust objects via FFM, exercises the bridged API surface",
                        style = MaterialTheme.typography.caption,
                    )
                }
            }
        }

        if (probeResults != null) {
            item {
                InfoCard {
                    probeResults!!.forEach { (label, value) ->
                        MetricRow(label, value)
                    }
                }
            }
        }

        // --- Codec info ---
        item {
            SectionHeader("Supported Codecs")
            InfoCard {
                Text(
                    "Symphonia built-in codecs (compiled into the native library)",
                    style = MaterialTheme.typography.caption,
                )
                Spacer(Modifier.height(4.dp))
                val codecs = listOf(
                    "MP3" to "MPEG-1/2 Audio Layer III",
                    "AAC" to "Advanced Audio Coding (LC)",
                    "ALAC" to "Apple Lossless",
                    "FLAC" to "Free Lossless Audio Codec",
                    "Vorbis" to "OGG Vorbis",
                    "WAV" to "RIFF Waveform (PCM/IEEE)",
                    "AIFF" to "Audio Interchange File Format",
                    "MKV/WebM" to "Matroska container",
                    "ISO MP4" to "MP4/M4A container",
                )
                codecs.forEach { (name, desc) -> MetricRow(name, desc) }
            }
        }

        // --- Sample format + enum info ---
        item {
            SectionHeader("Sample Formats")
            InfoCard {
                SampleFormat.entries.forEach {
                    MetricRow(it.name, "ordinal=${it.ordinal}")
                }
            }
        }

        item {
            SectionHeader("Seek Modes")
            InfoCard {
                MetricRow("Coarse", "Fast — lands on nearest keyframe")
                MetricRow("Accurate", "Sample-accurate — full decode from keyframe")
            }
        }
    }
}

@Composable
private fun PipelineStep(
    name: String,
    description: String,
    available: Boolean,
    note: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(ok = available)
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Medium)
                if (!available) Badge("not yet", color = AppColors.orange)
            }
            Text(description, style = MaterialTheme.typography.caption)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.caption, color = AppColors.orange)
            }
        }
    }
}

private fun exercisePipeline(): List<Pair<String, String>> {
    val results = mutableListOf<Pair<String, String>>()

    // Step 1: Hint
    run {
        val hint = Hint()
        val h2 = hint.with_extension("mp3")
        val h3 = h2.mime_type("audio/mpeg")
        h3.close()
        results += "1. Hint" to "extension=mp3, mime=audio/mpeg"
    }

    // Step 2: FormatOptions
    run {
        val opts = FormatOptions(
            prebuild_seek_index = false,
            seek_index_fill_rate = 20,
            enable_gapless = true,
        )
        opts.close()
        results += "2. FormatOptions" to "gapless=true, seek_fill_rate=20"
    }

    // Step 3: MetadataOptions
    run {
        val metaOpts = MetadataOptions(
            limit_metadata_bytes = Limit.default(),
            limit_visual_bytes = Limit.none(),
        )
        metaOpts.close()
        results += "3. MetadataOptions" to "metadata=Default, visuals=None"
    }

    // Step 4: CodecParameters builder
    run {
        val cp = CodecParameters()
            .with_sample_rate(44100)
            .with_bits_per_sample(16)
            .with_n_frames(44100 * 300)
            .with_channel_layout(Layout.Stereo)
        cp.close()
        results += "4. CodecParameters" to "44100Hz, 16-bit, Stereo, 5min"
    }

    // Step 5: Track
    run {
        val cp = CodecParameters().with_sample_rate(48000)
        val track = Track(id = 0, codec_params = cp)
        track.close()
        results += "5. Track" to "id=0, sample_rate=48000"
    }

    // Step 6: SignalSpec
    run {
        val spec = SignalSpec.new_with_layout(rate = 44100, layout = Layout.Stereo)
        spec.close()
        results += "6. SignalSpec" to "rate=44100, layout=Stereo"
    }

    // Step 7: Packet round-trip
    run {
        val data = ByteArray(1024) { (it % 256).toByte() }
        val pkt = Packet.new_from_slice(track_id = 1, ts = 88200L, dur = 1024L, buf = data)
        val readBack = "track_id=${pkt.track_id()}, ts=${pkt.ts()}, dur=${pkt.dur()}, buf=${pkt.buf().size}B"
        pkt.close()
        results += "7. Packet" to readBack
    }

    // Step 8: Time
    run {
        val t = Time(seconds = 185, frac = 0.5)
        t.close()
        val t2 = Time.from_hhmmss(0, 3, 5, 500_000_000)
        t2?.close()
        results += "8. Time" to "185.5s, from_hhmmss=${if (t2 != null) "OK" else "null"}"
    }

    // Step 9: DecoderOptions
    run {
        val opts = DecoderOptions(verify = false)
        opts.close()
        results += "9. DecoderOptions" to "verify=false"
    }

    // Step 10: CodecRegistry
    run {
        val reg = CodecRegistry()
        reg.close()
        results += "10. CodecRegistry" to "empty registry created"
    }

    // Step 11: Limit sealed enum
    run {
        val none = Limit.none()
        val def = Limit.default()
        val info = "none.tag=${none.tag}, default.tag=${def.tag}"
        none.close(); def.close()
        results += "11. Limit" to info
    }

    results += "Pipeline" to "${results.size} steps executed via Rust FFM bridge"
    return results
}
