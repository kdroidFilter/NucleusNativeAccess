package io.github.kdroidfilter.nucleusnativeaccess.plugin.analysis

import io.github.kdroidfilter.nucleusnativeaccess.plugin.ir.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests lazy cross-crate type resolution: when a method references a type from
 * a sub-crate (e.g., `nokhwa_core::Resolution`), the parser should discover it
 * from the rustdoc JSON index and resolve it as a proper data class, enum, or
 * sealed enum — not as an opaque class.
 */
class CrossCrateReexportTest {

    private lateinit var module: KneModule
    private val unsupportedMessages = mutableListOf<String>()

    @Before
    fun setUp() {
        val json = javaClass.classLoader
            .getResourceAsStream("rustdoc-fixtures/cross-crate-reexport.json")!!
            .bufferedReader()
            .readText()
        module = RustdocJsonParser().parse(json, "mylib") { unsupportedMessages.add(it) }
    }

    @Test
    fun `Camera class is parsed`() {
        val camera = module.classes.find { it.simpleName == "Camera" }
        assertNotNull("Camera class should exist", camera)
    }

    @Test
    fun `Camera has get_resolution property`() {
        val camera = module.classes.first { it.simpleName == "Camera" }
        // get_resolution is a no-arg getter, promoted to a property
        val prop = camera.properties.find { it.name == "resolution" }
        assertNotNull("get_resolution property should exist, properties: ${camera.properties.map { it.name }}", prop)
    }

    @Test
    fun `Resolution from sub-crate is resolved as data class`() {
        val camera = module.classes.first { it.simpleName == "Camera" }
        val prop = camera.properties.find { it.name == "resolution" }!!
        val returnType = prop.type
        assertTrue(
            "Resolution should be DATA_CLASS, got $returnType",
            returnType is KneType.DATA_CLASS
        )
        val dc = returnType as KneType.DATA_CLASS
        assertEquals("Resolution", dc.simpleName)
        assertEquals(2, dc.fields.size)
        assertEquals("width", dc.fields[0].name)
        assertEquals(KneType.INT, dc.fields[0].type)
        assertEquals("height", dc.fields[1].name)
        assertEquals(KneType.INT, dc.fields[1].type)
    }

    @Test
    fun `Resolution appears in module dataClasses`() {
        val dc = module.dataClasses.find { it.simpleName == "Resolution" }
        assertNotNull("Resolution data class should be in module", dc)
        assertEquals(2, dc!!.fields.size)
    }

    @Test
    fun `FrameFormat from sub-crate is resolved as simple enum`() {
        val camera = module.classes.first { it.simpleName == "Camera" }
        // get_format() returns ENUM and is extracted as a property by extractProperties
        val prop = camera.properties.find { it.name == "format" }
        assertNotNull("Camera should have 'format' property, has: ${camera.properties.map { it.name }}", prop)
        val type = prop!!.type
        assertTrue("format type should be ENUM, got $type", type is KneType.ENUM)
        assertEquals("FrameFormat", (type as KneType.ENUM).simpleName)
    }

    @Test
    fun `FrameFormat appears in module enums`() {
        val enum = module.enums.find { it.simpleName == "FrameFormat" }
        assertNotNull("FrameFormat enum should be in module", enum)
        assertEquals(3, enum!!.entries.size)
        assertTrue(enum.entries.contains("Rgb"))
        assertTrue(enum.entries.contains("Yuv"))
        assertTrue(enum.entries.contains("Gray"))
    }

    @Test
    fun `CaptureError from sub-crate is resolved as sealed enum`() {
        val camera = module.classes.first { it.simpleName == "Camera" }
        val method = camera.methods.find { it.name == "capture" }!!
        // Result<i32, CaptureError> — canFail=true, return type should be INT
        assertTrue("capture should be canFail", method.canFail)
    }

    @Test
    fun `cross-crate types are not opaque`() {
        val opaqueNames = module.classes.filter { it.isOpaque }.map { it.simpleName }
        assertFalse("Resolution should not be opaque", opaqueNames.contains("Resolution"))
        assertFalse("FrameFormat should not be opaque", opaqueNames.contains("FrameFormat"))
        assertFalse("CameraFormat should not be opaque", opaqueNames.contains("CameraFormat"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Nested cross-crate data class: CameraFormat { resolution, format, frame_rate }
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `CameraFormat from sub-crate is resolved as data class`() {
        val camera = module.classes.first { it.simpleName == "Camera" }
        val prop = camera.properties.find { it.name == "camera_format" }
        assertNotNull("get_camera_format property should exist", prop)
        val returnType = prop!!.type
        assertTrue(
            "CameraFormat should be DATA_CLASS, got $returnType",
            returnType is KneType.DATA_CLASS
        )
    }

    @Test
    fun `CameraFormat has resolution field as nested data class`() {
        val camera = module.classes.first { it.simpleName == "Camera" }
        val prop = camera.properties.first { it.name == "camera_format" }
        val dc = prop.type as KneType.DATA_CLASS
        assertEquals("CameraFormat", dc.simpleName)
        assertEquals(3, dc.fields.size)

        val resolutionField = dc.fields.find { it.name == "resolution" }
        assertNotNull("resolution field should exist", resolutionField)
        assertTrue(
            "resolution should be DATA_CLASS, got ${resolutionField!!.type}",
            resolutionField.type is KneType.DATA_CLASS
        )
        val nestedDc = resolutionField.type as KneType.DATA_CLASS
        assertEquals("Resolution", nestedDc.simpleName)
        assertEquals(2, nestedDc.fields.size)
    }

    @Test
    fun `CameraFormat has format field as enum`() {
        val camera = module.classes.first { it.simpleName == "Camera" }
        val prop = camera.properties.first { it.name == "camera_format" }
        val dc = prop.type as KneType.DATA_CLASS

        val formatField = dc.fields.find { it.name == "format" }
        assertNotNull("format field should exist", formatField)
        assertTrue(
            "format should be ENUM, got ${formatField!!.type}",
            formatField.type is KneType.ENUM
        )
        assertEquals("FrameFormat", (formatField.type as KneType.ENUM).simpleName)
    }

    @Test
    fun `CameraFormat has frame_rate field as primitive`() {
        val camera = module.classes.first { it.simpleName == "Camera" }
        val prop = camera.properties.first { it.name == "camera_format" }
        val dc = prop.type as KneType.DATA_CLASS

        val frameRateField = dc.fields.find { it.name == "frame_rate" }
        assertNotNull("frame_rate field should exist", frameRateField)
        assertEquals(KneType.INT, frameRateField!!.type)
    }

    @Test
    fun `CameraFormat appears in module dataClasses`() {
        val dc = module.dataClasses.find { it.simpleName == "CameraFormat" }
        assertNotNull("CameraFormat data class should be in module", dc)
        assertEquals(3, dc!!.fields.size)
    }
}
