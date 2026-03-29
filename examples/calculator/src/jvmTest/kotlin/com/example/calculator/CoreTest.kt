package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class CoreTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase 1: Core functionality (Int, String, Unit, properties)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `constructor with Int parameter`() {
        Calculator(42).use { calc ->
            assertEquals(42, calc.current)
        }
    }

    @Test
    fun `constructor with zero`() {
        Calculator(0).use { calc ->
            assertEquals(0, calc.current)
        }
    }

    @Test
    fun `add returns accumulated value`() {
        Calculator(0).use { calc ->
            assertEquals(5, calc.add(5))
            assertEquals(8, calc.add(3))
            assertEquals(8, calc.current)
        }
    }

    @Test
    fun `add with negative values`() {
        Calculator(10).use { calc ->
            assertEquals(7, calc.add(-3))
        }
    }

    @Test
    fun `subtract returns accumulated value`() {
        Calculator(10).use { calc ->
            assertEquals(7, calc.subtract(3))
            assertEquals(7, calc.current)
        }
    }

    @Test
    fun `multiply returns accumulated value`() {
        Calculator(4).use { calc ->
            assertEquals(12, calc.multiply(3))
        }
    }

    @Test
    fun `multiply by zero`() {
        Calculator(100).use { calc ->
            assertEquals(0, calc.multiply(0))
        }
    }

    @Test
    fun `reset clears accumulator`() {
        Calculator(0).use { calc ->
            calc.add(42)
            calc.reset()
            assertEquals(0, calc.current)
        }
    }

    @Test
    fun `val property current reads correctly`() {
        Calculator(99).use { calc ->
            assertEquals(99, calc.current)
            calc.add(1)
            assertEquals(100, calc.current)
        }
    }

    @Test
    fun `chain multiple operations`() {
        Calculator(0).use { calc ->
            calc.add(10)
            calc.multiply(3)
            calc.subtract(5)
            assertEquals(25, calc.current)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // String type: param, return, edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `describe returns formatted string`() {
        Calculator(7).use { calc ->
            assertEquals("Calculator(current=7)", calc.describe())
        }
    }

    @Test
    fun `echo returns same string`() {
        Calculator(0).use { calc ->
            assertEquals("hello", calc.echo("hello"))
        }
    }

    @Test
    fun `echo empty string`() {
        Calculator(0).use { calc ->
            assertEquals("", calc.echo(""))
        }
    }

    @Test
    fun `echo unicode string`() {
        Calculator(0).use { calc ->
            assertEquals("café ☕ 日本語", calc.echo("café ☕ 日本語"))
        }
    }

    @Test
    fun `concat two strings`() {
        Calculator(0).use { calc ->
            assertEquals("helloworld", calc.concat("hello", "world"))
        }
    }

    @Test
    fun `concat with empty strings`() {
        Calculator(0).use { calc ->
            assertEquals("hello", calc.concat("hello", ""))
            assertEquals("world", calc.concat("", "world"))
            assertEquals("", calc.concat("", ""))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // All primitive types: Long, Double, Float, Short, Byte, Boolean
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Long param and return`() {
        Calculator(10).use { calc ->
            assertEquals(15L, calc.addLong(5L))
        }
    }

    @Test
    fun `Long with large values`() {
        Calculator(0).use { calc ->
            assertEquals(1_000_000L, calc.addLong(1_000_000L))
        }
    }

    @Test
    fun `Double param and return`() {
        Calculator(10).use { calc ->
            assertEquals(13.5, calc.addDouble(3.5), 0.001)
        }
    }

    @Test
    fun `Double precision`() {
        Calculator(0).use { calc ->
            val result = calc.addDouble(0.1 + 0.2)
            assertTrue(result > 0.29 && result < 0.31)
        }
    }

    @Test
    fun `Float param and return`() {
        Calculator(10).use { calc ->
            assertEquals(12.5f, calc.addFloat(2.5f), 0.01f)
        }
    }

    @Test
    fun `Short param and return`() {
        Calculator(10).use { calc ->
            assertEquals(15.toShort(), calc.addShort(5.toShort()))
        }
    }

    @Test
    fun `Byte param and return`() {
        Calculator(10).use { calc ->
            assertEquals(13.toByte(), calc.addByte(3.toByte()))
        }
    }

    @Test
    fun `Boolean return true`() {
        Calculator(5).use { calc ->
            assertTrue(calc.isPositive())
        }
    }

    @Test
    fun `Boolean return false`() {
        Calculator(0).use { calc ->
            assertFalse(calc.isPositive())
        }
    }

    @Test
    fun `Boolean param true`() {
        Calculator(5).use { calc ->
            assertTrue(calc.checkFlag(true))
        }
    }

    @Test
    fun `Boolean param false`() {
        Calculator(5).use { calc ->
            assertFalse(calc.checkFlag(false))
        }
    }

    @Test
    fun `Boolean both false when accumulator zero`() {
        Calculator(0).use { calc ->
            assertFalse(calc.checkFlag(true))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase 3b: Exception propagation
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `divide works normally`() {
        Calculator(10).use { calc ->
            assertEquals(5, calc.divide(2))
            assertEquals(5, calc.current)
        }
    }

    @Test
    fun `divide by zero throws KotlinNativeException`() {
        Calculator(10).use { calc ->
            val ex = assertFailsWith<KotlinNativeException> {
                calc.divide(0)
            }
            assertTrue(ex.message!!.contains("Division by zero"), "Expected 'Division by zero' but got: ${ex.message}")
        }
    }

    @Test
    fun `failAlways throws with correct message`() {
        Calculator(0).use { calc ->
            val ex = assertFailsWith<KotlinNativeException> {
                calc.failAlways()
            }
            assertTrue(ex.message!!.contains("Intentional error"), "Expected 'Intentional error' but got: ${ex.message}")
        }
    }

    @Test
    fun `calculator works normally after exception`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            // Subsequent calls should work fine
            assertEquals(15, calc.add(5))
            assertEquals(15, calc.current)
        }
    }

    @Test
    fun `multiple exceptions in sequence`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            // Still works
            assertEquals(5, calc.divide(2))
        }
    }

}
