package io.github.kdroidfilter.nucleusnativeaccess.plugin.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class RustWorkActionTest {

    @Test
    fun `select rustdoc json prefers current lib target over stale files`() {
        val tempDir = createTempDirectory("kne-rustdoc-select").toFile()
        try {
            File(tempDir, "Cargo.toml").writeText(
                """
                [package]
                name = "kne-test-wrapper"

                [lib]
                name = "test"
                """.trimIndent()
            )

            val docDir = File(tempDir, "target/doc").apply { mkdirs() }
            File(docDir, "rustsysinfo.json").writeText("{}")
            val expected = File(docDir, "test.json").apply { writeText("{}") }

            val selected = RustWorkAction.selectRustdocJson(docDir, tempDir, "test")

            assertEquals(expected.absolutePath, selected?.absolutePath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `resolve rustdoc target name falls back to package name when lib section is absent`() {
        val tempDir = createTempDirectory("kne-rustdoc-target").toFile()
        try {
            File(tempDir, "Cargo.toml").writeText(
                """
                [package]
                name = "rust-sysinfo"
                """.trimIndent()
            )

            assertEquals("rust_sysinfo", RustWorkAction.resolveRustdocTargetName(tempDir))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `resolve rustdoc target name returns null when cargo toml is missing`() {
        val tempDir = createTempDirectory("kne-rustdoc-missing").toFile()
        try {
            assertNull(RustWorkAction.resolveRustdocTargetName(tempDir))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `find package manifest dir resolves registry dependency from cargo metadata`() {
        val metadataJson = """
            {
              "packages": [
                {
                  "name": "kne-test-wrapper",
                  "version": "0.1.0",
                  "manifest_path": "/tmp/wrapper/Cargo.toml"
                },
                {
                  "name": "tray",
                  "version": "0.1.2",
                  "manifest_path": "/home/user/.cargo/registry/src/index.crates.io-xxxx/tray-0.1.2/Cargo.toml"
                }
              ]
            }
        """.trimIndent()

        val manifest = RustWorkAction.findPackageManifestDir(metadataJson, io.github.kdroidfilter.nucleusnativeaccess.plugin.CrateDependency(name = "tray", version = "0.1.2"))

        assertNotNull(manifest)
        assertEquals("/home/user/.cargo/registry/src/index.crates.io-xxxx/tray-0.1.2/Cargo.toml", manifest!!.path)
    }
}
