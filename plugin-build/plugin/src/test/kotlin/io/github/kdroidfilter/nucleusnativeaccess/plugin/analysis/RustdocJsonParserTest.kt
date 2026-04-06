package io.github.kdroidfilter.nucleusnativeaccess.plugin.analysis

import io.github.kdroidfilter.nucleusnativeaccess.plugin.ir.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RustdocJsonParserTest {

    private lateinit var module: KneModule

    @Before
    fun setUp() {
        val json = javaClass.classLoader
            .getResourceAsStream("rustdoc-fixtures/mini-calculator.json")!!
            .bufferedReader()
            .readText()
        module = RustdocJsonParser().parse(json, "calculator")
    }

    // --- Module-level ---

    @Test
    fun `module has correct lib name`() {
        assertEquals("calculator", module.libName)
    }

    @Test
    fun `module has a package`() {
        assertTrue(module.packages.isNotEmpty())
    }

    // --- Structs → KneClass ---

    @Test
    fun `parses Calculator struct as KneClass`() {
        val calc = module.classes.find { it.simpleName == "Calculator" }
        assertNotNull("Calculator class should exist", calc)
    }

    @Test
    fun `Calculator has constructor with correct params`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        // new(initial_value: i32, name: String) -> Self
        assertEquals(2, calc.constructor.params.size)
        assertEquals("initial_value", calc.constructor.params[0].name)
        assertEquals(KneType.INT, calc.constructor.params[0].type)
        assertEquals("name", calc.constructor.params[1].name)
        assertEquals(KneType.STRING, calc.constructor.params[1].type)
    }

    @Test
    fun `Calculator has add method`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val add = calc.methods.find { it.name == "add" }
        assertNotNull("add method should exist", add)
        assertEquals(1, add!!.params.size)
        assertEquals("n", add.params[0].name)
        assertEquals(KneType.INT, add.params[0].type)
        assertEquals(KneType.INT, add.returnType)
    }

    @Test
    fun `Calculator has subtract method`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val sub = calc.methods.find { it.name == "subtract" }
        assertNotNull("subtract method should exist", sub)
    }

    @Test
    fun `Calculator has multiply method`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val mul = calc.methods.find { it.name == "multiply" }
        assertNotNull("multiply method should exist", mul)
    }

    @Test
    fun `Calculator has value property extracted from get_value`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val valueProp = calc.properties.find { it.name == "value" }
        assertNotNull("value property should exist (extracted from get_value)", valueProp)
        assertEquals(KneType.INT, valueProp!!.type)
    }

    @Test
    fun `Calculator has name property extracted from get_name`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val nameProp = calc.properties.find { it.name == "name" }
        assertNotNull("name property should exist (extracted from get_name)", nameProp)
        assertEquals(KneType.STRING, nameProp!!.type)
    }

    @Test
    fun `Calculator has reset method with mut self`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val reset = calc.methods.find { it.name == "reset" }
        assertNotNull("reset method should exist", reset)
        assertEquals(1, reset!!.params.size)
        assertEquals("new_value", reset.params[0].name)
        assertEquals(KneType.INT, reset.returnType)
    }

    // --- Point struct ---

    @Test
    fun `parses Point struct as KneClass`() {
        val point = module.classes.find { it.simpleName == "Point" }
        assertNotNull("Point class should exist", point)
    }

    @Test
    fun `Point has constructor with x and y as Double`() {
        val point = module.classes.first { it.simpleName == "Point" }
        assertEquals(2, point.constructor.params.size)
        assertEquals("x", point.constructor.params[0].name)
        assertEquals(KneType.DOUBLE, point.constructor.params[0].type)
        assertEquals("y", point.constructor.params[1].name)
        assertEquals(KneType.DOUBLE, point.constructor.params[1].type)
    }

    @Test
    fun `Point has distance_to method taking another Point`() {
        val point = module.classes.first { it.simpleName == "Point" }
        val dist = point.methods.find { it.name == "distance_to" }
        assertNotNull("distance_to method should exist", dist)
        assertEquals(1, dist!!.params.size)
        assertTrue("param should be OBJECT(Point)", dist.params[0].type is KneType.OBJECT)
        assertEquals("Point", (dist.params[0].type as KneType.OBJECT).simpleName)
        assertEquals(KneType.DOUBLE, dist.returnType)
    }

    @Test
    fun `Point has to_string_repr method returning String`() {
        val point = module.classes.first { it.simpleName == "Point" }
        val toStr = point.methods.find { it.name == "to_string_repr" }
        assertNotNull("to_string_repr method should exist", toStr)
        assertEquals(KneType.STRING, toStr!!.returnType)
    }

    // --- Enum ---

    @Test
    fun `parses Operation enum`() {
        val op = module.enums.find { it.simpleName == "Operation" }
        assertNotNull("Operation enum should exist", op)
    }

    @Test
    fun `Operation enum has 4 variants`() {
        val op = module.enums.first { it.simpleName == "Operation" }
        assertEquals(4, op.entries.size)
        assertTrue(op.entries.containsAll(listOf("Add", "Subtract", "Multiply", "Divide")))
    }

    // --- Top-level functions ---

    @Test
    fun `parses compute as top-level function`() {
        val compute = module.functions.find { it.name == "compute" }
        assertNotNull("compute function should exist", compute)
    }

    @Test
    fun `compute has correct params`() {
        val compute = module.functions.first { it.name == "compute" }
        assertEquals(3, compute.params.size)
        assertEquals("a", compute.params[0].name)
        assertEquals(KneType.INT, compute.params[0].type)
        assertEquals("b", compute.params[1].name)
        assertEquals(KneType.INT, compute.params[1].type)
        assertEquals("op", compute.params[2].name)
        assertTrue("op should be ENUM type", compute.params[2].type is KneType.ENUM)
        assertEquals("Operation", (compute.params[2].type as KneType.ENUM).simpleName)
    }

    @Test
    fun `parses sum_all with Vec param mapped to LIST`() {
        val sumAll = module.functions.find { it.name == "sum_all" }
        assertNotNull("sum_all function should exist", sumAll)
        assertEquals(1, sumAll!!.params.size)
        val paramType = sumAll.params[0].type
        assertTrue("param should be LIST", paramType is KneType.LIST)
        assertEquals(KneType.INT, (paramType as KneType.LIST).elementType)
        assertEquals(KneType.INT, sumAll.returnType)
    }

    @Test
    fun `parses greet with str param mapped to STRING`() {
        val greet = module.functions.find { it.name == "greet" }
        assertNotNull("greet function should exist", greet)
        assertEquals(1, greet!!.params.size)
        assertEquals(KneType.STRING, greet.params[0].type)
        assertEquals(KneType.STRING, greet.returnType)
    }

    @Test
    fun `parses find_max with Option return mapped to NULLABLE`() {
        val findMax = module.functions.find { it.name == "find_max" }
        assertNotNull("find_max function should exist", findMax)
        val retType = findMax!!.returnType
        assertTrue("return should be NULLABLE", retType is KneType.NULLABLE)
        assertEquals(KneType.INT, (retType as KneType.NULLABLE).inner)
    }

    // --- Filtering ---

    @Test
    fun `does not include private or default visibility items as classes`() {
        // Standard library trait impls (From, Into, etc.) should not be classes
        val classNames = module.classes.map { it.simpleName }
        assertFalse("Should not contain standard lib types", classNames.any {
            it in listOf("From", "Into", "Borrow", "BorrowMut", "Any")
        })
    }

    @Test
    fun `no suspend functions since Rust has no suspend`() {
        val allMethods = module.classes.flatMap { it.methods } + module.functions
        assertTrue(allMethods.none { it.isSuspend })
    }

    @Test
    fun `ignores generated bridge functions from kne bridges file`() {
        val json = """
            {
              "root": 0,
              "index": {
                "0": {
                  "id": 0,
                  "crate_id": 0,
                  "name": "sample",
                  "visibility": "public",
                  "inner": {
                    "module": {
                      "is_crate": true,
                      "items": [1, 2],
                      "is_stripped": false
                    }
                  }
                },
                "1": {
                  "id": 1,
                  "crate_id": 0,
                  "name": "real_api",
                  "visibility": "public",
                  "span": {
                    "filename": "src/lib.rs"
                  },
                  "inner": {
                    "function": {
                      "sig": {
                        "inputs": [],
                        "output": null,
                        "is_c_variadic": false
                      },
                      "generics": {
                        "params": [],
                        "where_predicates": []
                      }
                    }
                  }
                },
                "2": {
                  "id": 2,
                  "crate_id": 0,
                  "name": "sample_kne_hasError",
                  "visibility": "public",
                  "span": {
                    "filename": "/tmp/out/kne_bridges.rs"
                  },
                  "inner": {
                    "function": {
                      "sig": {
                        "inputs": [],
                        "output": {
                          "primitive": "i32"
                        },
                        "is_c_variadic": false
                      },
                      "generics": {
                        "params": [],
                        "where_predicates": []
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val parsed = RustdocJsonParser().parse(json, "ffi_name")

        assertEquals(listOf("real_api"), parsed.functions.map { it.name })
    }

    @Test
    fun `preserves dyn trait generics in rust type hints and marks unsafe methods`() {
        val json = """
            {
              "root": 0,
              "index": {
                "0": {
                  "id": 0,
                  "crate_id": 0,
                  "name": "sample",
                  "visibility": "public",
                  "inner": {
                    "module": {
                      "is_crate": true,
                      "items": [1, 2],
                      "is_stripped": false
                    }
                  }
                },
                "1": {
                  "id": 1,
                  "crate_id": 0,
                  "name": "Builder",
                  "visibility": "public",
                  "inner": {
                    "struct": {
                      "kind": { "unit": true },
                      "generics": {
                        "params": [],
                        "where_predicates": []
                      },
                      "impls": [2]
                    }
                  }
                },
                "2": {
                  "id": 2,
                  "crate_id": 0,
                  "name": null,
                  "visibility": "default",
                  "inner": {
                    "impl": {
                      "is_unsafe": false,
                      "generics": {
                        "params": [],
                        "where_predicates": []
                      },
                      "provided_trait_methods": [],
                      "trait": null,
                      "for": {
                        "resolved_path": {
                          "path": "Builder",
                          "id": 1,
                          "args": null
                        }
                      },
                      "items": [3, 4],
                      "is_negative": false,
                      "is_synthetic": false,
                      "blanket_impl": null
                    }
                  }
                },
                "3": {
                  "id": 3,
                  "crate_id": 0,
                  "name": "with_menu",
                  "visibility": "public",
                  "inner": {
                    "function": {
                      "sig": {
                        "inputs": [
                          ["self", { "generic": "Self" }],
                          ["menu", {
                            "resolved_path": {
                              "path": "Box",
                              "id": 9,
                              "args": {
                                "angle_bracketed": {
                                  "args": [
                                    {
                                      "type": {
                                        "dyn_trait": {
                                          "traits": [
                                            {
                                              "trait": {
                                                "path": "menu::ContextMenu",
                                                "id": 10,
                                                "args": null
                                              },
                                              "generic_params": []
                                            }
                                          ],
                                          "lifetime": null
                                        }
                                      }
                                    }
                                  ],
                                  "constraints": []
                                }
                              }
                            }
                          }]
                        ],
                        "output": { "generic": "Self" },
                        "is_c_variadic": false
                      },
                      "generics": {
                        "params": [],
                        "where_predicates": []
                      },
                      "header": {
                        "is_const": false,
                        "is_unsafe": false,
                        "is_async": false,
                        "abi": "Rust"
                      },
                      "has_body": true
                    }
                  }
                },
                "4": {
                  "id": 4,
                  "crate_id": 0,
                  "name": "app_indicator",
                  "visibility": "public",
                  "inner": {
                    "function": {
                      "sig": {
                        "inputs": [
                          [
                            "self",
                            {
                              "borrowed_ref": {
                                "lifetime": null,
                                "is_mutable": false,
                                "type": { "generic": "Self" }
                              }
                            }
                          ]
                        ],
                        "output": null,
                        "is_c_variadic": false
                      },
                      "generics": {
                        "params": [],
                        "where_predicates": []
                      },
                      "header": {
                        "is_const": false,
                        "is_unsafe": true,
                        "is_async": false,
                        "abi": "Rust"
                      },
                      "has_body": true
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val parsed = RustdocJsonParser().parse(json, "sample")
        val builder = parsed.classes.first { it.simpleName == "Builder" }

        val withMenu = builder.methods.first { it.name == "with_menu" }
        assertTrue(withMenu.params[0].type is KneType.OBJECT)
        assertEquals("Box", (withMenu.params[0].type as KneType.OBJECT).simpleName)
        assertEquals("Box<dyn menu::ContextMenu>", withMenu.params[0].rustType)

        val appIndicator = builder.methods.first { it.name == "app_indicator" }
        assertTrue(appIndicator.isUnsafe)
    }

    @Test
    fun `impl Trait return resolved as INTERFACE for known crate trait`() {
        // Fixture: a crate with a trait "Describable" and a function "get_describable() -> impl Describable"
        val json = """
            {
              "root": 0,
              "index": {
                "0": {
                  "id": 0,
                  "crate_id": 0,
                  "name": "mylib",
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
                  "name": "Describable",
                  "visibility": "public",
                  "inner": {
                    "trait": {
                      "items": [],
                      "generics": { "params": [], "where_predicates": [] },
                      "bounds": [],
                      "is_auto": false,
                      "is_unsafe": false
                    }
                  }
                },
                "2": {
                  "id": 2,
                  "crate_id": 0,
                  "name": "get_describable",
                  "visibility": "public",
                  "span": { "filename": "src/lib.rs" },
                  "inner": {
                    "function": {
                      "sig": {
                        "inputs": [],
                        "output": {
                          "impl_trait": [
                            {
                              "trait_bound": {
                                "trait": {
                                  "id": 1,
                                  "path": "mylib::Describable",
                                  "args": { "angle_bracketed": { "args": [], "bindings": [] } }
                                }
                              }
                            }
                          ]
                        },
                        "is_c_variadic": false
                      },
                      "generics": { "params": [], "where_predicates": [] }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val parsed = RustdocJsonParser().parse(json, "mylib")

        // The function should be bridged (not skipped)
        assertEquals(1, parsed.functions.size)
        val fn = parsed.functions[0]
        assertEquals("get_describable", fn.name)
        assertTrue("Return type should be INTERFACE", fn.returnType is KneType.INTERFACE)
        assertEquals("Describable", (fn.returnType as KneType.INTERFACE).simpleName)
        assertEquals("impl dyn Describable", fn.returnRustType)

        // A DynDescribable wrapper class should be generated
        assertTrue(
            "DynDescribable wrapper class should be generated",
            parsed.classes.any { it.simpleName == "DynDescribable" && it.isDynTrait },
        )
    }

    @Test
    fun `reports unsupported signatures through callback`() {
        // Use an unbounded generic T which is truly unsupported
        val json = """
            {
              "root": 0,
              "index": {
                "0": {
                  "id": 0,
                  "crate_id": 0,
                  "name": "sample",
                  "visibility": "public",
                  "inner": {
                    "module": {
                      "items": [1],
                      "is_crate": true,
                      "is_stripped": false
                    }
                  }
                },
                "1": {
                  "id": 1,
                  "crate_id": 0,
                  "name": "bad_fn",
                  "visibility": "public",
                  "span": {
                    "filename": "src/lib.rs"
                  },
                  "inner": {
                    "function": {
                      "sig": {
                        "inputs": [
                          ["value", {"generic": "T"}]
                        ],
                        "output": {"primitive": "i32"},
                        "is_c_variadic": false
                      },
                      "generics": {
                        "params": [{"name": "T", "kind": {"type": {"bounds": [], "default": null}}}],
                        "where_predicates": []
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val unsupported = mutableListOf<String>()
        val parsed = RustdocJsonParser().parse(json, "sample") { unsupported.add(it) }

        assertTrue("Function with unsupported generic should be skipped", parsed.functions.isEmpty())
        assertTrue("At least one unsupported warning expected", unsupported.size >= 1)
        assertTrue("Warning should mention bad_fn", unsupported.any { it.contains("bad_fn") })
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // impl Future return types
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `async fn method sets isAsync flag`() {
        val json = """
            {
              "root": 0,
              "index": {
                "0": {
                  "id": 0, "crate_id": 0, "name": "sample", "visibility": "public",
                  "inner": {"module": {"items": [1], "is_crate": true, "is_stripped": false}}
                },
                "1": {
                  "id": 1, "crate_id": 0, "name": "MyStruct", "visibility": "public",
                  "span": {"filename": "src/lib.rs"},
                  "inner": {"struct": {"kind": {"plain": {"fields": []}}, "generics": {"params": [], "where_predicates": []}, "impls": [2]}}
                },
                "2": {
                  "id": 2, "crate_id": 0, "name": null, "visibility": "public",
                  "inner": {"impl": {"is_synthetic": false, "is_negative": false, "trait": null,
                    "for": {"resolved_path": {"path": "MyStruct", "id": 1, "args": null}},
                    "items": [3]
                  }}
                },
                "3": {
                  "id": 3, "crate_id": 0, "name": "fetch_data", "visibility": "public",
                  "span": {"filename": "src/lib.rs"},
                  "inner": {"function": {
                    "sig": {
                      "inputs": [["self", {"borrowed_ref": {"lifetime": null, "is_mutable": false, "type": {"generic": "Self"}}}]],
                      "output": {"resolved_path": {"path": "Vec", "id": 99, "args": {"angle_bracketed": {"args": [{"type": {"primitive": "u8"}}], "constraints": []}}}},
                      "is_c_variadic": false
                    },
                    "generics": {"params": [], "where_predicates": []},
                    "header": {"is_const": false, "is_unsafe": false, "is_async": true, "abi": "Rust"},
                    "has_body": true
                  }}
                }
              },
              "paths": {"1": {"crate_id": 0, "path": ["sample", "MyStruct"], "kind": "struct"}}
            }
        """.trimIndent()

        val module = RustdocJsonParser().parse(json, "sample")
        val cls = module.classes.first { it.simpleName == "MyStruct" }
        val method = cls.methods.find { it.name == "fetch_data" }
        assertNotNull("fetch_data should exist", method)
        assertTrue("fetch_data should be async", method!!.isAsync)
        assertEquals(KneType.BYTE_ARRAY, method.returnType)
    }

    @Test
    fun `impl Future return type sets isAsync flag`() {
        val json = """
            {
              "root": 0,
              "index": {
                "0": {
                  "id": 0, "crate_id": 0, "name": "sample", "visibility": "public",
                  "inner": {"module": {"items": [1], "is_crate": true, "is_stripped": false}}
                },
                "1": {
                  "id": 1, "crate_id": 0, "name": "MyStruct", "visibility": "public",
                  "span": {"filename": "src/lib.rs"},
                  "inner": {"struct": {"kind": {"plain": {"fields": []}}, "generics": {"params": [], "where_predicates": []}, "impls": [2]}}
                },
                "2": {
                  "id": 2, "crate_id": 0, "name": null, "visibility": "public",
                  "inner": {"impl": {"is_synthetic": false, "is_negative": false, "trait": null,
                    "for": {"resolved_path": {"path": "MyStruct", "id": 1, "args": null}},
                    "items": [3]
                  }}
                },
                "3": {
                  "id": 3, "crate_id": 0, "name": "do_work", "visibility": "public",
                  "span": {"filename": "src/lib.rs"},
                  "inner": {"function": {
                    "sig": {
                      "inputs": [["self", {"generic": "Self"}]],
                      "output": {"impl_trait": [{"trait_bound": {"trait": {"path": "Future", "id": 50, "args": {"angle_bracketed": {"args": [], "constraints": [{"name": "Output", "args": null, "binding": {"equality": {"type": {"primitive": "i32"}}}}]}}}, "generic_params": [], "modifier": "none"}}]},
                      "is_c_variadic": false
                    },
                    "generics": {"params": [], "where_predicates": []},
                    "header": {"is_const": false, "is_unsafe": false, "is_async": false, "abi": "Rust"},
                    "has_body": true
                  }}
                }
              },
              "paths": {"1": {"crate_id": 0, "path": ["sample", "MyStruct"], "kind": "struct"}}
            }
        """.trimIndent()

        val module = RustdocJsonParser().parse(json, "sample")
        val cls = module.classes.first { it.simpleName == "MyStruct" }
        val method = cls.methods.find { it.name == "do_work" }
        assertNotNull("do_work should exist", method)
        assertTrue("do_work should be async (impl Future)", method!!.isAsync)
        assertEquals(KneType.INT, method.returnType)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Synthetic impl Trait generic params (is_synthetic: true)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `synthetic impl Trait params do not block method parsing`() {
        val json = """
            {
              "root": 0,
              "index": {
                "0": {
                  "id": 0, "crate_id": 0, "name": "sample", "visibility": "public",
                  "inner": {"module": {"items": [1], "is_crate": true, "is_stripped": false}}
                },
                "1": {
                  "id": 1, "crate_id": 0, "name": "greet", "visibility": "public",
                  "span": {"filename": "src/lib.rs"},
                  "inner": {"function": {
                    "sig": {
                      "inputs": [["name", {"impl_trait": [{"trait_bound": {"trait": {"path": "Into", "id": 55, "args": {"angle_bracketed": {"args": [{"type": {"resolved_path": {"path": "String", "id": 21, "args": null}}}], "constraints": []}}}, "generic_params": [], "modifier": "none"}}]}]],
                      "output": {"resolved_path": {"path": "String", "id": 21, "args": null}},
                      "is_c_variadic": false
                    },
                    "generics": {"params": [{"name": "impl Into<String>", "kind": {"type": {"bounds": [{"trait_bound": {"trait": {"path": "Into", "id": 55, "args": {"angle_bracketed": {"args": [{"type": {"resolved_path": {"path": "String", "id": 21, "args": null}}}], "constraints": []}}}, "generic_params": [], "modifier": "none"}}], "default": null, "is_synthetic": true}}}], "where_predicates": []}
                  }}
                }
              }
            }
        """.trimIndent()

        val parsed = RustdocJsonParser().parse(json, "sample")
        assertEquals("greet should be parsed", 1, parsed.functions.size)
        assertEquals("greet", parsed.functions[0].name)
        assertEquals(KneType.STRING, parsed.functions[0].params[0].type)
        assertEquals(KneType.STRING, parsed.functions[0].returnType)
    }
}
