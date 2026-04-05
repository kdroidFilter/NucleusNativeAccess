package io.github.kdroidfilter.nucleusnativeaccess.plugin.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `parse with unsupported signatures collects warnings through callback`() {
        val json = """
            {
              "root": 0,
              "crate_version": "0.1.0",
              "index": {
                "0": {
                  "id": 0,
                  "crate_id": 0,
                  "name": "sample",
                  "visibility": "public",
                  "inner": {
                    "module": {
                      "items": [1, 2, 3, 4],
                      "is_crate": true,
                      "is_stripped": false
                    }
                  }
                },
                "1": {
                  "id": 1,
                  "crate_id": 0,
                  "name": "MyStruct",
                  "visibility": "public",
                  "span": {"filename": "src/lib.rs", "begin": [1, 1], "end": [1, 10]},
                  "inner": {"struct": {"kind": {"plain": {"fields": []}}, "generics": {"params": [], "where_predicates": []}, "impls": []}}
                },
                "2": {
                  "id": 2,
                  "crate_id": 0,
                  "name": "supported_fn",
                  "visibility": "public",
                  "span": {"filename": "src/lib.rs", "begin": [5, 1], "end": [5, 20]},
                  "inner": {
                    "function": {
                      "sig": {
                        "inputs": [["value", {"primitive": "i32"}]],
                        "output": {"primitive": "i32"},
                        "is_c_variadic": false
                      },
                      "generics": {"params": [], "where_predicates": []},
                      "header": {"is_const": false, "is_unsafe": false, "is_async": false, "abi": "Rust"},
                      "has_body": true
                    }
                  }
                },
                "3": {
                  "id": 3,
                  "crate_id": 0,
                  "name": "unit_return_fn",
                  "visibility": "public",
                  "span": {"filename": "src/lib.rs", "begin": [10, 1], "end": [10, 30]},
                  "inner": {
                    "function": {
                      "sig": {
                        "inputs": [["self", {"borrowed_ref": {"lifetime": null, "is_mutable": true, "type": {"generic": "Self"}}}]],
                        "output": null,
                        "is_c_variadic": false
                      },
                      "generics": {"params": [], "where_predicates": []},
                      "header": {"is_const": false, "is_unsafe": false, "is_async": false, "abi": "Rust"},
                      "has_body": true
                    }
                  }
                },
                "4": {
                  "id": 4,
                  "crate_id": 0,
                  "name": "unsupported_tuple_param",
                  "visibility": "public",
                  "span": {"filename": "src/lib.rs", "begin": [15, 1], "end": [15, 40]},
                  "inner": {
                    "function": {
                      "sig": {
                        "inputs": [["value", {"tuple": [{"primitive": "i32"}, {"primitive": "i32"}]}]],
                        "output": {"primitive": "i32"},
                        "is_c_variadic": false
                      },
                      "generics": {"params": [], "where_predicates": []},
                      "header": {"is_const": false, "is_unsafe": false, "is_async": false, "abi": "Rust"},
                      "has_body": true
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val unsupported = mutableListOf<String>()
        val module = RustdocJsonParser().parse(json, "sample") { unsupported.add(it) }

        // supported_fn should be present (i32 -> i32)
        assertNotNull(module.functions.find { it.name == "supported_fn" })
        // unsupported_tuple_param should NOT be present (tuple is not a supported param type)
        assertNull(module.functions.find { it.name == "unsupported_tuple_param" })
        // At least one unsupported warning must have been reported for the tuple-param function
        assertTrue("Expected at least 1 unsupported warning, got: $unsupported", unsupported.size >= 1)
        assertTrue("Expected unsupported warning for unsupported_tuple_param, got: $unsupported", unsupported.any { it.contains("unsupported_tuple_param") })
        assertTrue("Expected unsupported param warning, got: $unsupported", unsupported.any { it.contains("unsupported param 'value'") || it.contains("unsupported parameter type") })
    }

    @Test
    fun `parse with unit-returning method does not report unsupported`() {
        val json = """
            {
              "root": 0,
              "crate_version": "0.1.0",
              "index": {
                "0": {
                  "id": 0,
                  "crate_id": 0,
                  "name": "sample",
                  "visibility": "public",
                  "inner": {
                    "module": {
                      "items": [1, 2],
                      "is_crate": true,
                      "is_stripped": false
                    }
                  }
                },
                "1": {
                  "id": 1,
                  "crate_id": 0,
                  "name": "Counter",
                  "visibility": "public",
                  "span": {"filename": "src/lib.rs", "begin": [1, 1], "end": [1, 10]},
                  "inner": {"struct": {"kind": {"plain": {"fields": []}}, "generics": {"params": [], "where_predicates": []}, "impls": [2]}}
                },
                "2": {
                  "id": 2,
                  "crate_id": 0,
                  "name": null,
                  "visibility": "default",
                  "inner": {
                    "impl": {
                      "is_unsafe": false,
                      "generics": {"params": [], "where_predicates": []},
                      "provided_trait_methods": [],
                      "trait": null,
                      "for": {"resolved_path": {"path": "Counter", "id": 1, "args": null}},
                      "items": [3],
                      "is_negative": false,
                      "is_synthetic": false,
                      "blanket_impl": null
                    }
                  }
                },
                "3": {
                  "id": 3,
                  "crate_id": 0,
                  "name": "reset",
                  "visibility": "public",
                  "span": {"filename": "src/lib.rs", "begin": [10, 1], "end": [10, 20]},
                  "inner": {
                    "function": {
                      "sig": {
                        "inputs": [["self", {"borrowed_ref": {"lifetime": null, "is_mutable": true, "type": {"generic": "Self"}}}]],
                        "output": null,
                        "is_c_variadic": false
                      },
                      "generics": {"params": [], "where_predicates": []},
                      "header": {"is_const": false, "is_unsafe": false, "is_async": false, "abi": "Rust"},
                      "has_body": true
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val unsupported = mutableListOf<String>()
        val module = RustdocJsonParser().parse(json, "sample") { unsupported.add(it) }

        val counterClass = module.classes.find { it.simpleName == "Counter" }
        assertNotNull("Counter class should be present", counterClass)
        // reset() with output=null should NOT be reported as unsupported
        assertTrue(unsupported.none { it.contains("reset") })
        // Counter should have exactly one method: reset
        assertEquals(1, counterClass!!.methods.size)
        assertEquals("reset", counterClass.methods[0].name)
        assertEquals(io.github.kdroidfilter.nucleusnativeaccess.plugin.ir.KneType.UNIT, counterClass.methods[0].returnType)
    }
}
