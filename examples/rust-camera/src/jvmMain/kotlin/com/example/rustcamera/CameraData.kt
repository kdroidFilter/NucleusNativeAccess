package com.example.rustcamera

import java.awt.image.BufferedImage

data class CameraDeviceInfo(
    val humanName: String,
    val description: String,
    val misc: String,
    val indexDescription: String,
)

data class FormatInfo(
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val format: String,
)

data class CompatibleFormat(
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val format: String,
)

data class ControlInfo(
    val name: String,
    val controlType: String,
    val active: Boolean,
    val flags: String,
    val descriptionTag: String,
    val currentValue: String,
    val valueDetails: String,
)

data class CameraState(
    val isOpen: Boolean,
    val fps: Int,
    val frame: BufferedImage?,
    val deviceInfo: CameraDeviceInfo?,
    val currentFormat: FormatInfo?,
    val compatibleFormats: List<CompatibleFormat>,
    val supportedFourcc: List<String>,
    val controls: List<ControlInfo>,
)
