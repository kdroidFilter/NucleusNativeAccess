package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class NullableTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase 3: Nullable types
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `nullable Int return - non-null`() {
        Calculator(10).use { calc ->
            assertEquals(5, calc.divideOrNull(2))
        }
    }

    @Test
    fun `nullable Int return - null`() {
        Calculator(10).use { calc ->
            assertNull(calc.divideOrNull(0))
        }
    }

    @Test
    fun `nullable String return - non-null`() {
        Calculator(5).use { calc ->
            assertEquals("Positive(5)", calc.describeOrNull())
        }
    }

    @Test
    fun `nullable String return - null`() {
        Calculator(0).use { calc ->
            assertNull(calc.describeOrNull())
        }
    }

    @Test
    fun `nullable Boolean return - true`() {
        Calculator(5).use { calc ->
            assertEquals(true, calc.isPositiveOrNull())
        }
    }

    @Test
    fun `nullable Boolean return - false`() {
        Calculator(-1).use { calc ->
            assertEquals(false, calc.isPositiveOrNull())
        }
    }

    @Test
    fun `nullable Boolean return - null`() {
        Calculator(0).use { calc ->
            assertNull(calc.isPositiveOrNull())
        }
    }

    @Test
    fun `nullable Enum return - non-null`() {
        Calculator(0).use { calc ->
            assertEquals(Operation.ADD, calc.findOp("ADD"))
        }
    }

    @Test
    fun `nullable Enum return - null from invalid name`() {
        Calculator(0).use { calc ->
            assertNull(calc.findOp("INVALID"))
        }
    }

    @Test
    fun `nullable Enum return - null from null param`() {
        Calculator(0).use { calc ->
            assertNull(calc.findOp(null))
        }
    }

    @Test
    fun `nullable Long return - non-null`() {
        Calculator(42).use { calc ->
            assertEquals(42L, calc.toLongOrNull())
        }
    }

    @Test
    fun `nullable Long return - null`() {
        Calculator(0).use { calc ->
            assertNull(calc.toLongOrNull())
        }
    }

    @Test
    fun `nullable Double return - non-null`() {
        Calculator(7).use { calc ->
            assertEquals(7.0, calc.toDoubleOrNull()!!, 0.001)
        }
    }

    @Test
    fun `nullable Double return - null`() {
        Calculator(0).use { calc ->
            assertNull(calc.toDoubleOrNull())
        }
    }

    @Test
    fun `nullable String property - initially null`() {
        Calculator(0).use { calc ->
            assertNull(calc.nickname)
        }
    }

    @Test
    fun `nullable String property - set and get`() {
        Calculator(0).use { calc ->
            calc.nickname = "myCalc"
            assertEquals("myCalc", calc.nickname)
        }
    }

    @Test
    fun `nullable String property - set to null`() {
        Calculator(0).use { calc ->
            calc.nickname = "temp"
            assertEquals("temp", calc.nickname)
            calc.nickname = null
            assertNull(calc.nickname)
        }
    }

    @Test
    fun `nullable Object return - non-null`() {
        CalculatorManager().use { mgr ->
            mgr.create("x", 42)
            mgr.getOrNull("x")?.use { calc ->
                assertEquals(42, calc.current)
            } ?: error("Expected non-null")
        }
    }

    @Test
    fun `nullable Object return - null`() {
        CalculatorManager().use { mgr ->
            assertNull(mgr.getOrNull("nonexistent"))
        }
    }

}
