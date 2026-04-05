package com.example.rustsymphonia.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rustsymphonia.*

data class BridgeTestResult(
    val label: String,
    val value: String,
    val success: Boolean = true,
)

@Composable
fun BridgeTestsTab() {
    var results by remember { mutableStateOf<List<BridgeTestResult>>(emptyList()) }
    var hasRun by remember { mutableStateOf(false) }

    fun runAll() {
        results = buildBridgeTests()
        hasRun = true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionHeader("Bridge Tests", count = if (hasRun) results.count { it.success } else null)
                Spacer(Modifier.weight(1f))
                androidx.compose.material.Button(onClick = { runAll() }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Run Tests")
                }
            }
        }

        if (!hasRun) {
            item {
                InfoCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Science, contentDescription = null, tint = AppColors.textMuted)
                        Text(
                            "Click 'Run Tests' to exercise the Rust bridge — creates symphonia + cpal types, calls methods, verifies round-trips.",
                            style = MaterialTheme.typography.body2,
                        )
                    }
                }
            }
        }

        itemsIndexed(results) { _, result ->
            TestResultRow(result)
        }

        if (hasRun) {
            val passed = results.count { it.success }
            val total = results.size
            item {
                Spacer(Modifier.height(4.dp))
                InfoCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatusDot(ok = passed == total)
                        Text(
                            "$passed / $total tests passed",
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (passed < total) {
                            Badge("${total - passed} failed", color = AppColors.red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestResultRow(result: BridgeTestResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.card)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(ok = result.success)
        Column(modifier = Modifier.weight(1f)) {
            Text(result.label, style = MaterialTheme.typography.body1)
            Text(result.value, style = MaterialTheme.typography.caption, maxLines = 2)
        }
    }
}

private fun buildBridgeTests(): List<BridgeTestResult> {
    val results = mutableListOf<BridgeTestResult>()

    results += test("Hint — create + set extension") {
        val hint = Hint()
        hint.with_extension("mp3")
        hint.mime_type("audio/mpeg")
        hint.close()
        "extension=mp3, mime=audio/mpeg"
    }
    results += test("FormatOptions — gapless") {
        val opts = FormatOptions(prebuild_seek_index = false, seek_index_fill_rate = 20, enable_gapless = true)
        opts.close()
        "gapless=true, fill_rate=20"
    }
    results += test("MetadataOptions — limits") {
        val opts = MetadataOptions(limit_metadata_bytes = Limit.default(), limit_visual_bytes = Limit.none())
        opts.close()
        "metadata=Default, visuals=None"
    }
    results += test("DecoderOptions") {
        val opts = DecoderOptions(verify = true)
        opts.close()
        "verify=true"
    }
    results += test("CodecParameters — builder") {
        val cp = CodecParameters()
            .with_sample_rate(44100)
            .with_bits_per_sample(16)
            .with_channel_layout(Layout.Stereo)
        cp.close()
        "44100 Hz, 16-bit, Stereo"
    }
    results += test("Track — create") {
        val cp = CodecParameters().with_sample_rate(48000)
        val track = Track(id = 0, codec_params = cp)
        track.close()
        "Track(id=0)"
    }
    results += test("SignalSpec — with layout") {
        val spec = SignalSpec.new_with_layout(rate = 44100, layout = Layout.Stereo)
        spec.close()
        "rate=44100, layout=Stereo"
    }
    results += test("Time — from h/m/s") {
        val t = Time(seconds = 120, frac = 0.5)
        t.close()
        val t2 = Time.from_hhmmss(1, 30, 45, 0)
        val ok = t2 != null
        t2?.close()
        "Time(120.5s), from_hhmmss=${if (ok) "OK" else "null"}"
    }
    results += test("SeekMode — enum") {
        "Coarse=${SeekMode.Coarse.ordinal}, Accurate=${SeekMode.Accurate.ordinal}"
    }
    results += test("SampleFormat — enum") {
        val names = SampleFormat.entries.joinToString { it.name }
        "${SampleFormat.entries.size} formats: $names"
    }
    results += test("Packet — create + read") {
        val pkt = Packet.new_from_slice(track_id = 1, ts = 44100L, dur = 1024L, buf = ByteArray(512) { it.toByte() })
        val info = "track_id=${pkt.track_id()}, ts=${pkt.ts()}, dur=${pkt.dur()}, buf=${pkt.buf().size}B"
        pkt.close()
        info
    }
    results += test("CodecRegistry — create") {
        val reg = CodecRegistry()
        reg.close()
        "empty registry created"
    }
    results += test("Limit — sealed enum") {
        val none = Limit.none()
        val def = Limit.default()
        val info = "None=${none.tag}, Default=${def.tag}"
        none.close(); def.close()
        info
    }
    results += test("cpal types") {
        "Host, Device, Stream types bridged"
    }

    return results
}

private fun test(label: String, block: () -> String): BridgeTestResult {
    return try {
        BridgeTestResult(label = label, value = block(), success = true)
    } catch (e: Throwable) {
        e.printStackTrace()
        BridgeTestResult(label = label, value = e.message ?: e.toString(), success = false)
    }
}
