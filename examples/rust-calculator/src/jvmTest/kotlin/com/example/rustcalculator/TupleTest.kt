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
}
