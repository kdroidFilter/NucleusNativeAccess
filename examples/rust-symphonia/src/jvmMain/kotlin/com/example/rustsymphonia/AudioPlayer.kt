package com.example.rustsymphonia

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

// ---------------------------------------------------------------------------
// Domain models
// ---------------------------------------------------------------------------

data class TrackInfo(
    val id: Int,
    val codec: String,
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int?,
)

data class AudioInfo(
    val path: String,
    val fileName: String,
    // Symphonia: FormatReader.format_info().short_name()
    val container: String,
    // Symphonia: CodecParameters.codec().to_string()
    val codec: String,
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int?,
    val durationSecs: Double,
    val bitrateKbps: Int?,
    // Symphonia: ID3v2 / Vorbis comments / APE tags / MP4 atoms
    val tags: Map<String, String>,
    // Symphonia: FormatReader.tracks() — all demuxed tracks in the container
    val tracks: List<TrackInfo>,
    // Symphonia: FormatReader.cues() — chapter / cue-point markers
    val cues: List<Pair<String, Double>>,
    // Decoded-PCM waveform thumbnail (normalized 0..1)
    val waveform: FloatArray,
    val primaryTrackId: Int,
)

// ---------------------------------------------------------------------------
// Player state machine
// ---------------------------------------------------------------------------

sealed interface PlayerState {
    data object Idle : PlayerState
    data class Loading(val message: String) : PlayerState
    data class Ready(
        val info: AudioInfo,
        val position: Double,
        val isPlaying: Boolean,
        val volume: Float,
        // cpal output device name — proves cpal is active
        val outputDeviceName: String,
        val spectrumBands: FloatArray,
    ) : PlayerState
    data class Error(val message: String) : PlayerState
}

// ---------------------------------------------------------------------------
// AudioPlayer
//
// Audio pipeline:
//   File → MediaSourceStream (Symphonia)
//        → Probe / FormatReader   (Symphonia — demuxing)
//        → Decoder                (Symphonia — PCM decode)
//        → CpalOutputStream       (cpal — CoreAudio / WASAPI / ALSA output)
// ---------------------------------------------------------------------------

class AudioPlayer {

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var playbackJob: Job? = null
    @Volatile private var cpalStream: CpalStream? = null
    @Volatile private var currentVolume: Float = 0.8f
    @Volatile private var seekTarget: Double? = null

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    fun load(path: String) {
        playbackJob?.cancel()
        cpalStream?.pause()
        cpalStream = null

        scope.launch {
            _state.value = PlayerState.Loading("Probing format…")
            try {
                // Discover the cpal output device name up front so the UI can show it.
                // cpal: Host::default_output_device() → Device::name()
                val deviceName = CpalHost.default_host()
                    .default_output_device()
                    ?.name()
                    ?: "Unknown device"

                val info = probeAndAnalyze(path)

                _state.value = PlayerState.Ready(
                    info = info,
                    position = 0.0,
                    isPlaying = false,
                    volume = currentVolume,
                    outputDeviceName = deviceName,
                    spectrumBands = FloatArray(32),
                )
            } catch (e: Throwable) {
                _state.value = PlayerState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun play() {
        val s = _state.value as? PlayerState.Ready ?: return
        if (s.isPlaying) return
        _state.value = s.copy(isPlaying = true)
        playbackJob = scope.launch { startPlaybackLoop(s.info, s.position) }
    }

    fun pause() {
        val s = _state.value as? PlayerState.Ready ?: return
        playbackJob?.cancel()
        // cpal: Stream::pause() — suspends the hardware callback without closing the device
        cpalStream?.pause()
        _state.value = s.copy(isPlaying = false)
    }

    fun stop() {
        val s = _state.value as? PlayerState.Ready ?: return
        playbackJob?.cancel()
        cpalStream?.pause()
        cpalStream = null
        _state.value = s.copy(isPlaying = false, position = 0.0, spectrumBands = FloatArray(32))
    }

    fun seekTo(seconds: Double) {
        seekTarget = seconds
        val s = _state.value as? PlayerState.Ready ?: return
        if (!s.isPlaying) _state.value = s.copy(position = seconds)
    }

    fun setVolume(v: Float) {
        currentVolume = v
        val s = _state.value as? PlayerState.Ready ?: return
        _state.value = s.copy(volume = v)
    }

    fun close() {
        scope.cancel()
        cpalStream?.pause()
    }

    // -----------------------------------------------------------------------
    // Symphonia: probe + full analysis
    // -----------------------------------------------------------------------

    private fun probeAndAnalyze(path: String): AudioInfo {
        val file = java.io.File(path)

        // Symphonia: Hint tells the prober the likely format so it can skip slow sniffing
        val hint = Hint.new()
        when (file.extension.lowercase()) {
            "mp3"        -> { hint.with_extension("mp3"); hint.with_mime_type("audio/mpeg") }
            "flac"       -> { hint.with_extension("flac"); hint.with_mime_type("audio/flac") }
            "ogg", "oga" -> { hint.with_extension("ogg"); hint.with_mime_type("audio/ogg") }
            "m4a", "aac" -> { hint.with_extension("m4a"); hint.with_mime_type("audio/mp4") }
            "wav"        -> { hint.with_extension("wav"); hint.with_mime_type("audio/wav") }
            "aiff","aif" -> { hint.with_extension("aiff"); hint.with_mime_type("audio/aiff") }
            "opus"       -> { hint.with_extension("opus"); hint.with_mime_type("audio/opus") }
            else         -> hint.with_extension(file.extension.lowercase())
        }

        // Symphonia: MediaSourceStream wraps any Read+Seek source
        val mss = MediaSourceStream.open(path)

        // Symphonia: FormatOptions — enable gapless decoding (strips encoder delay / padding)
        val fmtOpts = FormatOptions.new().enable_gapless(true)

        // Symphonia: Probe auto-detects the container (MP3/OGG/FLAC/MKV/MP4/RIFF…)
        val probed = get_probe().format(hint, mss, fmtOpts, MetadataOptions.default())
        val format = probed.format()

        // Symphonia: enumerate every demuxed track (audio, video, subtitle…)
        val tracks = format.tracks().map { t ->
            val cp = t.codec_params()
            TrackInfo(
                id = t.id().toInt(),
                codec = cp.codec().to_string(),
                sampleRate = cp.sample_rate()?.toInt() ?: 0,
                channels = cp.channels()?.count()?.toInt() ?: 0,
                bitsPerSample = cp.bits_per_coded_sample()?.toInt(),
            )
        }

        // Symphonia: default_track() picks the primary audio track automatically
        val defaultTrack = format.default_track() ?: error("No audio track found")
        val trackId = defaultTrack.id().toInt()
        val cp = defaultTrack.codec_params()

        val sampleRate = cp.sample_rate()?.toInt() ?: 44100
        val channels = cp.channels()?.count()?.toInt() ?: 2
        val nFrames = cp.n_frames()?.toLong()
        val durationSecs = nFrames?.let { it.toDouble() / sampleRate } ?: 0.0
        val bitrateKbps = cp.bits_per_coded_sample()?.toInt()?.let { it / 1000 }

        // Symphonia: reads all embedded tag formats automatically
        //   MP3  → ID3v1 / ID3v2
        //   OGG/FLAC → Vorbis comments
        //   M4A  → iTunes MP4 atoms
        //   WV   → APEv2
        val tags = linkedMapOf<String, String>()
        fun collectTags(m: MetadataRevision?) = m?.tags()?.forEach { tag ->
            val key = tag.std_key()?.to_string() ?: tag.key()
            val value = tag.value().to_string()
            if (value.isNotBlank()) tags.putIfAbsent(key, value)
        }
        collectTags(probed.metadata().current())
        collectTags(format.metadata().current())

        // Symphonia: cue points map to chapters / cue-sheets in the container
        val cues = format.cues().mapIndexed { i, cue ->
            val secs = cue.start_ts().toDouble() / maxOf(sampleRate, 1)
            (cue.index().to_string().ifBlank { "Chapter ${i + 1}" }) to secs
        }

        val waveform = buildWaveform(format, trackId, sampleRate, channels)
        val containerName = format.format_info().short_name().uppercase()
        val codecName = cp.codec().to_string().uppercase()
        format.close()

        return AudioInfo(
            path = path,
            fileName = file.name,
            container = containerName,
            codec = codecName,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = cp.bits_per_coded_sample()?.toInt(),
            durationSecs = durationSecs,
            bitrateKbps = bitrateKbps,
            tags = tags,
            tracks = tracks,
            cues = cues,
            waveform = waveform,
            primaryTrackId = trackId,
        )
    }

    // -----------------------------------------------------------------------
    // Symphonia: decode PCM thumbnail for waveform rendering
    // -----------------------------------------------------------------------

    private fun buildWaveform(
        format: FormatReader,
        trackId: Int,
        sampleRate: Int,
        channels: Int,
    ): FloatArray {
        // Symphonia: CodecRegistry contains every compiled-in decoder
        val decoder = get_codecs().make(
            format.tracks().first { it.id().toInt() == trackId }.codec_params(),
            DecoderOptions.default(),
        )

        val maxFrames = sampleRate * 60
        val output = ArrayList<Float>(1200)
        val chunkSize = maxOf(sampleRate / 20, 1) // one waveform point per 50 ms

        try {
            while (output.size < 1200) {
                // Symphonia: FormatReader::next_packet() demuxes the next encoded packet
                val packet = try { format.next_packet() } catch (_: Exception) { break }
                if (packet.track_id().toInt() != trackId) { packet.close(); continue }

                // Symphonia: Decoder::decode() converts a packet to an AudioBufferRef.
                // The buffer type (s16, s24le, f32, …) depends on the codec.
                val audioRef = try { decoder.decode(packet) } catch (_: Exception) {
                    packet.close(); continue
                }
                val frameCount = audioRef.frames().toInt()

                // Symphonia: SampleBuffer::copy_interleaved_ref converts ANY sample
                // format to interleaved f32 — no manual bit-depth handling required.
                val sb = SampleBuffer.new(frameCount.toLong(), audioRef.spec())
                sb.copy_interleaved_ref(audioRef)
                val samples = sb.samples() // FloatArray, interleaved channels

                var f = 0
                while (f < frameCount) {
                    var peak = 0f
                    val end = minOf(f + chunkSize, frameCount)
                    for (i in f until end) {
                        var rms = 0f
                        for (ch in 0 until channels) {
                            val s = samples.getOrElse(i * channels + ch) { 0f }
                            rms += s * s
                        }
                        peak = maxOf(peak, sqrt(rms / channels))
                    }
                    output.add(peak)
                    f += chunkSize
                }

                sb.close()
                packet.close()
                if (f >= maxFrames) break
            }
        } finally {
            decoder.close()
        }

        val max = output.maxOrNull()?.takeIf { it > 0f } ?: 1f
        return FloatArray(output.size) { output[it] / max }
    }

    // -----------------------------------------------------------------------
    // Playback loop: Symphonia decode → cpal output
    //
    // cpal exposes a callback-based API: Device::build_output_stream() receives
    // a closure that is called from the platform audio thread (CoreAudio / WASAPI
    // / ALSA) whenever the hardware needs more samples.  NNA bridges this as a
    // Kotlin lambda passed to the Rust closure slot.
    //
    // Shared state between the decode coroutine and the cpal callback:
    //   - SampleRingBuffer — lock-free ring buffer (produced by coroutine,
    //     consumed by cpal callback)
    // -----------------------------------------------------------------------

    private suspend fun startPlaybackLoop(info: AudioInfo, startAt: Double) = withContext(Dispatchers.IO) {
        // cpal: discover the platform default audio output device
        val host   = CpalHost.default_host()
        val device = host.default_output_device() ?: error("No audio output device")

        // cpal: negotiate the closest supported config to what Symphonia decoded
        val config = device.default_output_config()
        val streamConfig = StreamConfig.new(
            info.channels.toUInt(),
            SampleRate.new(info.sampleRate.toUInt()),
        )

        // Shared ring buffer — Symphonia fills it, cpal drains it
        // capacity: 1 second worth of f32 samples
        val ringBuffer = SampleRingBuffer.new((info.sampleRate * info.channels).toLong())

        // cpal: build_output_stream — the Kotlin lambda IS the audio callback.
        // NNA passes it as a Rust fn pointer to the cpal stream builder.
        // The callback runs on the platform audio thread; it must be wait-free.
        val stream = device.build_output_stream_f32(
            streamConfig,
            onData  = { outputBuffer: FloatArray ->
                // drain ring buffer into the cpal output buffer
                ringBuffer.read_into(outputBuffer, currentVolume)
            },
            onError = { err: String -> /* log only — cannot throw from audio thread */ },
        )

        // cpal: Stream::play() starts the hardware callback loop
        stream.play()
        cpalStream = stream

        // Re-open Symphonia reader for streaming decode
        val mss = MediaSourceStream.open(info.path)
        val hint = Hint.new().apply { with_extension(info.fileName.substringAfterLast('.', "")) }
        val probed = get_probe().format(hint, mss, FormatOptions.new().enable_gapless(true), MetadataOptions.default())
        val format = probed.format()
        val decoder = get_codecs().make(
            format.tracks().first { it.id().toInt() == info.primaryTrackId }.codec_params(),
            DecoderOptions.default(),
        )

        // Symphonia: seek to resume position if needed
        //   SeekMode::Accurate  — sample-accurate, slower (needs full decode from keyframe)
        //   SeekMode::Coarse    — fast, lands on nearest keyframe
        if (startAt > 0.0) {
            format.seek(SeekMode.Accurate, SeekTo.time(startAt, info.primaryTrackId.toLong()))
            decoder.reset()
        }

        val spectrumAccum = FloatArray(32)
        var spectrumFrames = 0

        try {
            while (isActive) {
                seekTarget?.let { target ->
                    seekTarget = null
                    ringBuffer.clear()
                    // Symphonia: FormatReader::seek
                    format.seek(SeekMode.Accurate, SeekTo.time(target, info.primaryTrackId.toLong()))
                    decoder.reset()
                }

                val packet = try { format.next_packet() } catch (_: Exception) { break }
                if (packet.track_id().toInt() != info.primaryTrackId) { packet.close(); continue }

                val audioRef = try { decoder.decode(packet) } catch (_: Exception) {
                    packet.close(); continue
                }
                val frameCount = audioRef.frames().toInt()

                val sb = SampleBuffer.new(frameCount.toLong(), audioRef.spec())
                sb.copy_interleaved_ref(audioRef)
                val samples = sb.samples()

                // Push decoded samples into the ring buffer for cpal to consume
                ringBuffer.write(samples)

                // Accumulate per-band energy for spectrum display
                for (i in samples.indices) {
                    val band = (i * 32 / maxOf(samples.size, 1)).coerceIn(0, 31)
                    spectrumAccum[band] += abs(samples[i])
                }
                spectrumFrames += frameCount

                // Emit UI update every ~200 ms
                if (spectrumFrames >= info.sampleRate / 5) {
                    val maxE = spectrumAccum.max().takeIf { it > 0f } ?: 1f
                    val bands = FloatArray(32) { spectrumAccum[it] / maxE }
                    spectrumAccum.fill(0f)
                    spectrumFrames = 0
                    val positionSecs = packet.ts().toDouble() / info.sampleRate
                    val cur = _state.value
                    if (cur is PlayerState.Ready) {
                        _state.value = cur.copy(position = positionSecs, spectrumBands = bands)
                    }
                }

                sb.close()
                packet.close()
            }
        } finally {
            decoder.close()
            format.close()
            // cpal: drain remaining samples, then pause the stream
            delay(500)
            stream.pause()
            cpalStream = null
            val cur = _state.value
            if (cur is PlayerState.Ready) {
                _state.value = cur.copy(isPlaying = false, spectrumBands = FloatArray(32))
            }
        }
    }
}
