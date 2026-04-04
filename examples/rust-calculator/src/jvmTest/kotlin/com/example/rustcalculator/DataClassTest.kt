package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals

class DataClassTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Point data class (x: Int, y: Int)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `get_point returns correct values`() {
        Calculator(5).use { calc ->
            val p = calc.get_point()
            assertEquals(5, p.x)
            assertEquals(10, p.y)
        }
    }

    @Test fun `get_point with zero`() {
        Calculator(0).use { calc ->
            val p = calc.get_point()
            assertEquals(0, p.x)
            assertEquals(0, p.y)
        }
    }

    @Test fun `get_point with negative`() {
        Calculator(-3).use { calc ->
            val p = calc.get_point()
            assertEquals(-3, p.x)
            assertEquals(-6, p.y)
        }
    }

    @Test fun `add_point accumulates x + y`() {
        Calculator(0).use { calc ->
            val result = calc.add_point(Point(3, 7))
            assertEquals(10, result)
            assertEquals(10, calc.current)
        }
    }

    @Test fun `add_point with negative values`() {
        Calculator(10).use { calc ->
            val result = calc.add_point(Point(-3, -2))
            assertEquals(5, result)
        }
    }

    @Test fun `get_point then add_point roundtrip`() {
        Calculator(5).use { calc ->
            val p = calc.get_point()
            calc.add_point(p) // adds 5 + 10 = 15
            assertEquals(20, calc.current) // 5 + 15
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NamedValue data class (name: String, value: Int)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `get_named_value default label`() {
        Calculator(5).use { calc ->
            val nv = calc.get_named_value()
            assertEquals("default", nv.name)
            assertEquals(5, nv.value)
        }
    }

    @Test fun `get_named_value custom label`() {
        Calculator(5).use { calc ->
            calc.label = "myCalc"
            val nv = calc.get_named_value()
            assertEquals("myCalc", nv.name)
            assertEquals(5, nv.value)
        }
    }

    @Test fun `set_from_named updates accumulator and label`() {
        Calculator(0).use { calc ->
            calc.set_from_named(NamedValue("imported", 42))
            assertEquals(42, calc.current)
            assertEquals("imported", calc.label)
        }
    }

    @Test fun `set_from_named then get_named_value roundtrip`() {
        Calculator(0).use { calc ->
            val nv = NamedValue("test", 99)
            calc.set_from_named(nv)
            val got = calc.get_named_value()
            assertEquals("test", got.name)
            assertEquals(99, got.value)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge dc - Point with MAX_VALUE`() {
        Calculator(Int.MAX_VALUE).use { calc ->
            val p = calc.get_point()
            assertEquals(Int.MAX_VALUE, p.x)
        }
    }

    @Test fun `edge dc - Point with MIN_VALUE`() {
        Calculator(Int.MIN_VALUE).use { calc ->
            val p = calc.get_point()
            assertEquals(Int.MIN_VALUE, p.x)
        }
    }

    @Test fun `edge dc - add_point with zero point`() {
        Calculator(42).use { calc ->
            val result = calc.add_point(Point(0, 0))
            assertEquals(42, result)
        }
    }

    @Test fun `edge dc - NamedValue with unicode name`() {
        Calculator(0).use { calc ->
            calc.set_from_named(NamedValue("日本語テスト 🚀", 7))
            val got = calc.get_named_value()
            assertEquals("日本語テスト 🚀", got.name)
            assertEquals(7, got.value)
        }
    }

    @Test fun `edge dc - NamedValue with empty name`() {
        Calculator(0).use { calc ->
            calc.set_from_named(NamedValue("", 0))
            val got = calc.get_named_value()
            // empty label returns "default"
            assertEquals("default", got.name)
            assertEquals(0, got.value)
        }
    }

    @Test fun `edge dc - lifecycle create use close`() {
        repeat(50) { i ->
            Calculator(i).use { calc ->
                val p = calc.get_point()
                assertEquals(i, p.x)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K get_point calls`() {
        Calculator(7).use { calc ->
            repeat(100_000) {
                val p = calc.get_point()
                assertEquals(7, p.x)
                assertEquals(14, p.y)
            }
        }
    }

    @Test fun `load - 100K add_point calls`() {
        Calculator(0).use { calc ->
            repeat(100_000) {
                calc.add_point(Point(0, 0))
            }
            assertEquals(0, calc.current)
        }
    }

    @Test fun `load - 100K get_named_value calls`() {
        Calculator(1).use { calc ->
            calc.label = "load"
            repeat(100_000) {
                val nv = calc.get_named_value()
                assertEquals("load", nv.name)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrency tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 10K get_point`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid).use { calc ->
                    repeat(10_000) {
                        val p = calc.get_point()
                        assertEquals(tid, p.x)
                        assertEquals(tid * 2, p.y)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K set_from_named`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(0).use { calc ->
                    repeat(10_000) {
                        calc.set_from_named(NamedValue("t$tid", tid))
                        assertEquals(tid, calc.current)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
