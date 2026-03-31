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
}
