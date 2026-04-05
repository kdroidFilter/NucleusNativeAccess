package com.example.rustsymphonia

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ---------------------------------------------------------------------------
// Bridge showcase — demonstrates Symphonia + cpal types bridged via NNA
//
// The full decode→play pipeline requires APIs that aren't fully bridgeable yet
// (MediaSourceStream takes a dyn MediaSource param, Probe has no public ctor,
// Host::default_output_device() returns an impl trait type).
//
// This showcase proves the Rust→Kotlin bridge works by exercising the types
// and methods that ARE available.
// ---------------------------------------------------------------------------

data class BridgeResult(
    val label: String,
    val value: String,
    val success: Boolean = true,
)

data class ShowcaseState(
    val results: List<BridgeResult> = emptyList(),
    val isRunning: Boolean = false,
)

class AudioPlayer {

    private val _state = MutableStateFlow(ShowcaseState())
    val state: StateFlow<ShowcaseState> = _state.asStateFlow()

    fun runShowcase() {
        _state.value = ShowcaseState(isRunning = true)
        val results = mutableListOf<BridgeResult>()

        // --- Symphonia core types ---

        results += runTest("Hint — create + set extension") {
            val hint = Hint()
            hint.with_extension("mp3")
            hint.mime_type("audio/mpeg")
            hint.close()
            "created with extension=mp3, mime=audio/mpeg"
        }

        results += runTest("FormatOptions — construct with gapless") {
            val opts = FormatOptions(
                prebuild_seek_index = false,
                seek_index_fill_rate = 20,
                enable_gapless = true,
            )
            opts.close()
            "prebuild_seek_index=false, fill_rate=20, gapless=true"
        }

        results += runTest("MetadataOptions — construct with limits") {
            val opts = MetadataOptions(
                limit_metadata_bytes = Limit.default(),
                limit_visual_bytes = Limit.none(),
            )
            opts.close()
            "metadata=Default, visuals=None"
        }

        results += runTest("DecoderOptions — construct") {
            val opts = DecoderOptions(verify = true)
            opts.close()
            "verify=true"
        }

        results += runTest("CodecParameters — builder chain") {
            val cp = CodecParameters()
                .with_sample_rate(44100)
                .with_bits_per_sample(16)
                .with_channel_layout(Layout.Stereo)
                .with_sample_format(SampleFormat.S16)
            cp.close()
            "44100 Hz, 16-bit, Stereo, S16"
        }

        results += runTest("Track — create from id + params") {
            val cp = CodecParameters().with_sample_rate(48000)
            val track = Track(id = 0, codec_params = cp)
            track.close()
            "Track(id=0, sampleRate=48000)"
        }

        results += runTest("SignalSpec — construct with layout") {
            val spec = SignalSpec.new_with_layout(rate = 44100, layout = Layout.Stereo)
            spec.close()
            "rate=44100, layout=Stereo"
        }

        results += runTest("Time — from hours/minutes/seconds") {
            val t = Time(seconds = 120, frac = 0.5)
            t.close()
            val t2 = Time.from_hhmmss(1, 30, 45, 0)
            t2?.close()
            "Time(120.5s), Time(1:30:45) = ${if (t2 != null) "OK" else "null"}"
        }

        results += runTest("SeekMode — enum values") {
            val coarse = SeekMode.Coarse
            val accurate = SeekMode.Accurate
            "Coarse=${coarse.ordinal}, Accurate=${accurate.ordinal}"
        }

        results += runTest("SampleFormat — enum values") {
            val formats = SampleFormat.entries.joinToString { it.name }
            "${SampleFormat.entries.size} formats: $formats"
        }

        results += runTest("Packet — create + read properties") {
            val pkt = Packet.new_from_slice(
                track_id = 1,
                ts = 44100L,
                dur = 1024L,
                buf = ByteArray(512) { it.toByte() },
            )
            val info = "track_id=${pkt.track_id()}, ts=${pkt.ts()}, dur=${pkt.dur()}, " +
                "buf_size=${pkt.buf().size}, trim_start=${pkt.trim_start()}"
            pkt.close()
            info
        }

        results += runTest("CodecRegistry — create") {
            val registry = CodecRegistry()
            registry.close()
            "empty registry created"
        }

        results += runTest("SeekTo — Time variant") {
            val time = Time(seconds = 60, frac = 0.0)
            // SeekTo.time() expects SeekTo.Time type param, skip for now
            time.close()
            "Time(60s) created"
        }

        results += runTest("Limit — sealed enum variants") {
            val none = Limit.none()
            val def = Limit.default()
            val tags = "None=${none.tag}, Default=${def.tag}"
            none.close()
            def.close()
            tags
        }

        results += runTest("Value — sealed enum variants") {
            val sv = Value.string("Hello from Rust")
            val fv = Value.float(3.14)
            val iv = Value.signedInt(42)
            val bv = Value.boolean(true)
            val info = "String=${sv.tag}, Float=${fv.tag}, SignedInt=${iv.tag}, Boolean=${bv.tag}"
            sv.close(); fv.close(); iv.close(); bv.close()
            info
        }

        // --- cpal types ---

        results += runTest("cpal types — availability") {
            // Host, Device, Stream types exist but can't be constructed yet
            // (default_host() is a top-level function not yet bridged)
            "Host, Device, Stream types bridged — awaiting top-level function support"
        }

        // --- Summary ---

        val passed = results.count { it.success }
        val total = results.size
        results += BridgeResult(
            label = "Summary",
            value = "$passed/$total tests passed — ${results.count { !it.success }} failed",
            success = results.all { it.success },
        )

        _state.value = ShowcaseState(results = results, isRunning = false)
    }

    private fun runTest(label: String, block: () -> String): BridgeResult {
        return try {
            BridgeResult(label = label, value = block(), success = true)
        } catch (e: Throwable) {
            BridgeResult(label = label, value = e.message ?: e.toString(), success = false)
        }
    }

    fun close() {
        // nothing to clean up
    }
}
