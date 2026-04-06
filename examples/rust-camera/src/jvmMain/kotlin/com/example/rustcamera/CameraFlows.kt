package com.example.rustcamera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte

fun cameraStateFlow(): Flow<CameraState> = flow {
    val format = RequestedFormatType.absoluteHighestFrameRate()
    val requested = RequestedFormat.new_rgb_format(format)
    val index = CameraIndex.index(0)
    val cam = Camera(index, requested)

    try {
        cam.open_stream()

        // Collect static info once
        val deviceInfo = runCatching {
            val info = cam.info()
            val idx = info.index()
            val idxDesc = when (idx) {
                is CameraIndex.Index -> "Index(${idx.value})"
                is CameraIndex.StringValue -> "String(${idx.value})"
                else -> idx.toString()
            }
            CameraDeviceInfo(
                humanName = info.human_name(),
                description = info.description(),
                misc = info.misc(),
                indexDescription = idxDesc,
            )
        }.getOrNull()

        val compatibleFormats = runCatching {
            cam.compatible_camera_formats().map { fmt ->
                val res = fmt.resolution()
                val cf = CompatibleFormat(
                    width = res.width(),
                    height = res.height(),
                    frameRate = fmt.frame_rate(),
                    format = fmt.format().name,
                )
                res.close()
                cf
            }
        }.getOrElse { emptyList() }

        val supportedFourcc = runCatching {
            cam.compatible_fourcc().map { it.name }
        }.getOrElse { emptyList() }

        val controls = readControls(cam)

        val camRes = cam.resolution()
        val w = camRes.width()
        val h = camRes.height()
        camRes.close()

        var frameCount = 0
        var lastFpsTime = System.currentTimeMillis()
        var currentFps = 0

        while (cam.is_stream_open()) {
            val currentFormat = runCatching {
                val fmt = cam.camera_format()
                val fmtRes = fmt.resolution()
                val fi = FormatInfo(
                    width = fmtRes.width(),
                    height = fmtRes.height(),
                    frameRate = fmt.frame_rate(),
                    format = fmt.format().name,
                )
                fmtRes.close()
                fmt.close()
                fi
            }.getOrNull()

            val buf = withContext(Dispatchers.Default) { cam.frame() }
            val rgbBytes = ByteArray(w * h * 3)
            buf.decode_image_to_buffer_rgb_format(rgbBytes)
            buf.close()

            val img = BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR)
            val data = (img.raster.dataBuffer as DataBufferByte).data
            val pixelCount = minOf(rgbBytes.size / 3, data.size / 3)
            for (i in 0 until pixelCount) {
                data[i * 3] = rgbBytes[i * 3 + 2]     // B
                data[i * 3 + 1] = rgbBytes[i * 3 + 1] // G
                data[i * 3 + 2] = rgbBytes[i * 3]     // R
            }

            frameCount++
            val now = System.currentTimeMillis()
            if (now - lastFpsTime >= 1000) {
                currentFps = frameCount
                frameCount = 0
                lastFpsTime = now
            }

            emit(
                CameraState(
                    isOpen = true,
                    fps = currentFps,
                    frame = img,
                    deviceInfo = deviceInfo,
                    currentFormat = currentFormat,
                    compatibleFormats = compatibleFormats,
                    supportedFourcc = supportedFourcc,
                    controls = controls,
                )
            )

            delay(16)
        }
    } finally {
        runCatching { cam.stop_stream() }
        cam.close()
    }
}.flowOn(Dispatchers.IO)

private fun readControls(cam: Camera): List<ControlInfo> = runCatching {
    cam.camera_controls().map { ctrl ->
        val desc = ctrl.description()
        val value = ctrl.value()
        val descTag = desc.tag.name
        val currentValue = formatControlValue(value)
        val valueDetails = formatControlDetails(desc)

        val info = ControlInfo(
            name = ctrl.name(),
            controlType = ctrl.control().tag.name,
            active = ctrl.active(),
            flags = ctrl.flag().joinToString(", ") { it.name },
            descriptionTag = descTag,
            currentValue = currentValue,
            valueDetails = valueDetails,
        )
        info
    }
}.getOrElse { emptyList() }

private fun formatControlValue(value: ControlValueSetter): String = runCatching {
    when (value) {
        is ControlValueSetter.Integer -> "${value.value}"
        is ControlValueSetter.FloatValue -> "%.2f".format(value.value)
        is ControlValueSetter.BooleanValue -> if (value.value) "On" else "Off"
        is ControlValueSetter.StringValue -> value.value
        is ControlValueSetter.None -> "N/A"
        else -> value.tag.name
    }
}.getOrElse { "N/A" }

private fun formatControlDetails(desc: ControlValueDescription): String = runCatching {
    when (desc) {
        is ControlValueDescription.IntegerRange -> "Range: ${desc.min}..${desc.max} (step ${desc.step}, default ${desc.default})"
        is ControlValueDescription.Integer -> "Value: ${desc.value} (step ${desc.step}, default ${desc.default})"
        is ControlValueDescription.FloatRange -> "Range: %.2f..%.2f (step %.2f, default %.2f)".format(desc.min, desc.max, desc.step, desc.default)
        is ControlValueDescription.FloatValue -> "Value: %.2f (step %.2f, default %.2f)".format(desc.value, desc.step, desc.default)
        is ControlValueDescription.BooleanValue -> "Default: ${if (desc.default) "On" else "Off"}"
        is ControlValueDescription.StringValue -> "Value: ${desc.value}"
        is ControlValueDescription.Enum -> "Enum value: ${desc.value}, possible: ${desc.possible}"
        is ControlValueDescription.None -> "No description"
        else -> desc.tag.name
    }
}.getOrElse { "N/A" }
