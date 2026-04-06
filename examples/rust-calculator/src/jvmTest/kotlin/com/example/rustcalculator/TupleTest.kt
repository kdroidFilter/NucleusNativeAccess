package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals

class TupleTest {

    @Test
    fun `get_coordinates returns correct tuple`() {
        Calculator(5).use { calc ->
            val coords = calc.get_coordinates()
            assertEquals(5, coords._0)
            assertEquals(10, coords._1)
        }
    }

    @Test
    fun `get_coordinates with zero accumulator`() {
        Calculator(0).use { calc ->
            val coords = calc.get_coordinates()
            assertEquals(0, coords._0)
            assertEquals(0, coords._1)
        }
    }

    @Test
    fun `get_triple returns correct tuple`() {
        Calculator(5).use { calc ->
            calc.label = "test"
            val triple = calc.get_triple()
            assertEquals(5, triple._0)
            assertEquals("test", triple._1)
            assertEquals(true, triple._2)
        }
    }

    @Test
    fun `get_triple with empty label`() {
        Calculator(5).use { calc ->
            val triple = calc.get_triple()
            assertEquals(5, triple._0)
            assertEquals("", triple._1)
            assertEquals(true, triple._2)
        }
    }

    @Test
    fun `sum_tuple adds coordinates to accumulator`() {
        Calculator(5).use { calc ->
            val result = calc.sum_tuple(KneTuple2_TII(1, 2))
            assertEquals(8, result)
        }
    }

    @Test
    fun `sum_tuple with zero coordinates`() {
        Calculator(10).use { calc ->
            val result = calc.sum_tuple(KneTuple2_TII(0, 0))
            assertEquals(10, result)
        }
    }

    // ── Nested tuple tests ──────────────────────────────────────────────────

    @Test
    fun `get_nested_tuple returns correct nested tuple`() {
        Calculator(5).use { calc ->
            calc.label = "nested"
            val result = calc.get_nested_tuple()
            assertEquals(5, result._0)
            assertEquals("nested", result._1._0)
            assertEquals(true, result._1._1)
        }
    }

    @Test
    fun `get_nested_tuple with empty label`() {
        Calculator(5).use { calc ->
            val result = calc.get_nested_tuple()
            assertEquals(5, result._0)
            assertEquals("", result._1._0)
            assertEquals(true, result._1._1)
        }
    }

    @Test
    fun `get_nested_tuple with disabled state`() {
        Calculator(5).use { calc ->
            calc.label = "test"
            calc.enabled = false
            val result = calc.get_nested_tuple()
            assertEquals(5, result._0)
            assertEquals("test", result._1._0)
            assertEquals(false, result._1._1)
        }
    }

    @Test
    fun `get_nested_tuple with unicode string`() {
        Calculator(5).use { calc ->
            calc.label = "héllo wörld 🌍"
            val result = calc.get_nested_tuple()
            assertEquals("héllo wörld 🌍", result._1._0)
        }
    }

    // ── 3-level deep nesting ────────────────────────────────────────────────

    @Test
    fun `get_deep_tuple returns correct 3-level nested tuple`() {
        Calculator(7).use { calc ->
            calc.label = "deep"
            val result = calc.get_deep_tuple()
            assertEquals(7, result._0)
            assertEquals("deep", result._1._0)
            assertEquals(true, result._1._1._0)
            assertEquals(21, result._1._1._1) // 7 * 3
        }
    }

    @Test
    fun `get_deep_tuple with empty label`() {
        Calculator(3).use { calc ->
            val result = calc.get_deep_tuple()
            assertEquals(3, result._0)
            assertEquals("", result._1._0)
            assertEquals(true, result._1._1._0)
            assertEquals(9, result._1._1._1)
        }
    }

    @Test
    fun `get_deep_tuple with disabled state`() {
        Calculator(4).use { calc ->
            calc.label = "x"
            calc.enabled = false
            val result = calc.get_deep_tuple()
            assertEquals(4, result._0)
            assertEquals("x", result._1._0)
            assertEquals(false, result._1._1._0)
            assertEquals(12, result._1._1._1)
        }
    }

    // ── Double nested (two sibling nested tuples) ───────────────────────────

    @Test
    fun `get_double_nested returns two nested tuples`() {
        Calculator(5).use { calc ->
            calc.label = "double"
            val result = calc.get_double_nested()
            assertEquals(5, result._0._0)
            assertEquals(10, result._0._1)
            assertEquals("double", result._1._0)
            assertEquals(true, result._1._1)
        }
    }

    @Test
    fun `get_double_nested with empty label`() {
        Calculator(3).use { calc ->
            val result = calc.get_double_nested()
            assertEquals(3, result._0._0)
            assertEquals(6, result._0._1)
            assertEquals("", result._1._0)
            assertEquals(true, result._1._1)
        }
    }

    @Test
    fun `get_double_nested with zero accumulator`() {
        Calculator(0).use { calc ->
            calc.label = "zero"
            val result = calc.get_double_nested()
            assertEquals(0, result._0._0)
            assertEquals(0, result._0._1)
            assertEquals("zero", result._1._0)
            assertEquals(true, result._1._1)
        }
    }

    // ── Typed nested (f64, i32 inside) ──────────────────────────────────────

    @Test
    fun `get_typed_nested returns correct long and nested double+int`() {
        Calculator(5).use { calc ->
            calc.scale = 2.5
            val result = calc.get_typed_nested()
            assertEquals(5000L, result._0)
            assertEquals(2.5, result._1._0)
            assertEquals(5, result._1._1)
        }
    }

    @Test
    fun `get_typed_nested with zero scale`() {
        Calculator(0).use { calc ->
            calc.scale = 0.0
            val result = calc.get_typed_nested()
            assertEquals(0L, result._0)
            assertEquals(0.0, result._1._0)
            assertEquals(0, result._1._1)
        }
    }

    @Test
    fun `get_typed_nested with negative scale`() {
        Calculator(3).use { calc ->
            calc.scale = -1.5
            val result = calc.get_typed_nested()
            assertEquals(3000L, result._0)
            assertEquals(-1.5, result._1._0)
            assertEquals(3, result._1._1)
        }
    }

    // ── Repeated calls (memory leak regression) ─────────────────────────────

    @Test
    fun `repeated nested tuple calls do not crash`() {
        Calculator(1).use { calc ->
            calc.label = "repeat"
            repeat(100) {
                val result = calc.get_nested_tuple()
                assertEquals("repeat", result._1._0)
            }
        }
    }

    @Test
    fun `repeated deep tuple calls do not crash`() {
        Calculator(2).use { calc ->
            calc.label = "stress"
            repeat(100) {
                val result = calc.get_deep_tuple()
                assertEquals("stress", result._1._0)
                assertEquals(6, result._1._1._1)
            }
        }
    }

    @Test
    fun `repeated double nested calls do not crash`() {
        Calculator(3).use { calc ->
            calc.label = "multi"
            repeat(100) {
                val result = calc.get_double_nested()
                assertEquals("multi", result._1._0)
                assertEquals(3, result._0._0)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tuple with Vec<T> element
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `tuple - get_with_scores returns list and string`() {
        Calculator(10).use { calc ->
            calc.label = "test"
            val result = calc.get_with_scores()
            assertEquals(listOf(10, 20, 30), result._0)
            assertEquals("test", result._1)
        }
    }

    @Test
    fun `tuple - get_with_scores with zero`() {
        Calculator(0).use { calc ->
            calc.label = ""
            val result = calc.get_with_scores()
            assertEquals(listOf(0, 0, 0), result._0)
            assertEquals("", result._1)
        }
    }

    @Test
    fun `tuple - get_with_scores with negative`() {
        Calculator(-5).use { calc ->
            calc.label = "neg"
            val result = calc.get_with_scores()
            assertEquals(listOf(-5, -10, -15), result._0)
            assertEquals("neg", result._1)
        }
    }

    @Test
    fun `tuple - get_with_scores repeated calls`() {
        Calculator(7).use { calc ->
            calc.label = "repeat"
            repeat(100) {
                val result = calc.get_with_scores()
                assertEquals(3, result._0.size)
                assertEquals(7, result._0[0])
                assertEquals("repeat", result._1)
            }
        }
    }

    @Test
    fun `edge tuple - get_with_scores with MAX_VALUE`() {
        Calculator(Int.MAX_VALUE).use { calc ->
            calc.label = "max"
            val result = calc.get_with_scores()
            assertEquals(Int.MAX_VALUE, result._0[0])
            assertEquals("max", result._1)
        }
    }

    @Test
    fun `edge tuple - get_with_scores with MIN_VALUE`() {
        Calculator(Int.MIN_VALUE).use { calc ->
            calc.label = "min"
            val result = calc.get_with_scores()
            assertEquals(Int.MIN_VALUE, result._0[0])
            assertEquals("min", result._1)
        }
    }

    @Test
    fun `str tuple - get_with_scores with unicode label`() {
        Calculator(1).use { calc ->
            calc.label = "\u00e9\u00e0\u00fc\u00f1 \u4e16\u754c \ud83d\ude80"
            val result = calc.get_with_scores()
            assertEquals(listOf(1, 2, 3), result._0)
            assertEquals("\u00e9\u00e0\u00fc\u00f1 \u4e16\u754c \ud83d\ude80", result._1)
        }
    }

    @Test
    fun `edge tuple - get_with_scores lifecycle create use close`() {
        repeat(50) {
            Calculator(it).use { calc ->
                calc.label = "iter$it"
                val result = calc.get_with_scores()
                assertEquals(it, result._0[0])
                assertEquals("iter$it", result._1)
            }
        }
    }

    @Test
    fun `load - 100K get_with_scores calls`() {
        Calculator(1).use { calc ->
            calc.label = "load"
            repeat(100_000) {
                val result = calc.get_with_scores()
                assertEquals(listOf(1, 2, 3), result._0)
            }
        }
    }

    @Test
    fun `concurrent - 10 threads x 10K get_with_scores`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid).use { calc ->
                    calc.label = "t$tid"
                    repeat(10_000) {
                        val result = calc.get_with_scores()
                        assertEquals(listOf(tid, tid * 2, tid * 3), result._0)
                        assertEquals("t$tid", result._1)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    // ── MAP in tuple tests ──────────────────────────────────────────────────

    @Test
    fun `tuple - get_with_metadata returns tuple with map`() {
        Calculator(10).use { calc ->
            val result = calc.get_with_metadata()
            assertEquals(10, result._0)
            assertEquals(mapOf("current" to 10, "scale" to 1, "double" to 20), result._1)
        }
    }

    @Test
    fun `tuple - get_with_metadata with zero accumulator`() {
        Calculator(0).use { calc ->
            val result = calc.get_with_metadata()
            assertEquals(0, result._0)
            assertEquals(mapOf("current" to 0, "scale" to 1, "double" to 0), result._1)
        }
    }

    @Test
    fun `tuple - get_with_metadata repeated calls`() {
        Calculator(5).use { calc ->
            repeat(100) {
                val result = calc.get_with_metadata()
                assertEquals(5, result._0)
                assertEquals(mapOf("current" to 5, "scale" to 1, "double" to 10), result._1)
            }
        }
    }

    // ── MAP with nested LIST values ─────────────────────────────────────────

    @Test
    fun `get_metadata returns map with list values`() {
        Calculator(5).use { calc ->
            val result = calc.metadata
            assertEquals(listOf(5, 10, 15), result["values"])
            assertEquals(listOf(1, 2, 3, 5), result["factors"])
        }
    }

    @Test
    fun `get_metadata with zero accumulator`() {
        Calculator(0).use { calc ->
            val result = calc.metadata
            assertEquals(listOf(0, 0, 0), result["values"])
            assertEquals(listOf(1, 2, 3, 5), result["factors"])
        }
    }

    @Test
    fun `get_with_metadata_map returns tuple with nested map`() {
        Calculator(3).use { calc ->
            calc.label = "hello"
            val result = calc.get_with_metadata_map()
            assertEquals("hello", result._0)
            assertEquals(listOf(3, 6), result._1["values"])
            assertEquals(listOf(1, 2, 3), result._1["labels"])
        }
    }

    @Test
    fun `get_with_metadata_map repeated calls`() {
        Calculator(2).use { calc ->
            calc.label = "test"
            repeat(100) {
                val result = calc.get_with_metadata_map()
                assertEquals("test", result._0)
                assertEquals(listOf(2, 4), result._1["values"])
            }
        }
    }

    @Test
    fun `edge nested map - negative accumulator`() {
        Calculator(-7).use { calc ->
            val result = calc.metadata
            assertEquals(listOf(-7, -14, -21), result["values"])
            assertEquals(2, result.size)
        }
    }

    @Test
    fun `edge nested map - MAX_VALUE accumulator`() {
        Calculator(Int.MAX_VALUE).use { calc ->
            val result = calc.metadata
            assertEquals(Int.MAX_VALUE, result["values"]!![0])
            assertEquals(listOf(1, 2, 3, 5), result["factors"])
        }
    }

    @Test
    fun `edge nested map - key count and content`() {
        Calculator(1).use { calc ->
            val result = calc.metadata
            assertEquals(setOf("values", "factors"), result.keys)
            assertEquals(3, result["values"]!!.size)
            assertEquals(4, result["factors"]!!.size)
        }
    }

    @Test
    fun `edge nested tuple map - empty label`() {
        Calculator(1).use { calc ->
            calc.label = ""
            val result = calc.get_with_metadata_map()
            assertEquals("", result._0)
            assertEquals(listOf(1, 2), result._1["values"])
        }
    }

    @Test
    fun `edge nested tuple map - unicode label`() {
        Calculator(1).use { calc ->
            calc.label = "日本語テスト🎉"
            val result = calc.get_with_metadata_map()
            assertEquals("日本語テスト🎉", result._0)
            assertEquals(listOf(1, 2, 3), result._1["labels"])
        }
    }

    @Test
    fun `load - 100K metadata calls`() {
        Calculator(42).use { calc ->
            repeat(100_000) {
                val result = calc.metadata
                assertEquals(listOf(42, 84, 126), result["values"])
            }
        }
    }

    @Test
    fun `load - 100K get_with_metadata_map calls`() {
        Calculator(3).use { calc ->
            calc.label = "load"
            repeat(100_000) {
                val result = calc.get_with_metadata_map()
                assertEquals("load", result._0)
                assertEquals(listOf(3, 6), result._1["values"])
            }
        }
    }

    @Test
    fun `concurrent - 10 threads x 10K metadata calls`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid).use { calc ->
                    repeat(10_000) {
                        val result = calc.metadata
                        assertEquals(listOf(tid, tid * 2, tid * 3), result["values"])
                        assertEquals(listOf(1, 2, 3, 5), result["factors"])
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test
    fun `concurrent - 10 threads x 10K get_with_metadata_map calls`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid).use { calc ->
                    calc.label = "t$tid"
                    repeat(10_000) {
                        val result = calc.get_with_metadata_map()
                        assertEquals("t$tid", result._0)
                        assertEquals(listOf(tid, tid * 2), result._1["values"])
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
