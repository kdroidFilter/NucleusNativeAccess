package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for the Kotlin Native Export plugin.
 * Every test compiles native code, generates FFM bridges, and verifies on JVM.
 */
class CalculatorTest {

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
    // Mutable properties (var): String, Double, Boolean
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `var String property set and get`() {
        Calculator(0).use { calc ->
            assertEquals("", calc.label)
            calc.label = "test"
            assertEquals("test", calc.label)
        }
    }

    @Test
    fun `var String property unicode`() {
        Calculator(0).use { calc ->
            calc.label = "日本語テスト"
            assertEquals("日本語テスト", calc.label)
        }
    }

    @Test
    fun `var Double property set and get`() {
        Calculator(0).use { calc ->
            assertEquals(1.0, calc.scale, 0.001)
            calc.scale = 2.5
            assertEquals(2.5, calc.scale, 0.001)
        }
    }

    @Test
    fun `var Boolean property set and get`() {
        Calculator(0).use { calc ->
            assertTrue(calc.enabled)
            calc.enabled = false
            assertFalse(calc.enabled)
            calc.enabled = true
            assertTrue(calc.enabled)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase 2: Enum type
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `enum has correct entries`() {
        assertEquals(3, Operation.entries.size)
        assertEquals("ADD", Operation.ADD.name)
        assertEquals("SUBTRACT", Operation.SUBTRACT.name)
        assertEquals("MULTIPLY", Operation.MULTIPLY.name)
    }

    @Test
    fun `enum ordinals match`() {
        assertEquals(0, Operation.ADD.ordinal)
        assertEquals(1, Operation.SUBTRACT.ordinal)
        assertEquals(2, Operation.MULTIPLY.ordinal)
    }

    @Test
    fun `enum as parameter - ADD`() {
        Calculator(0).use { calc ->
            assertEquals(5, calc.applyOp(Operation.ADD, 5))
        }
    }

    @Test
    fun `enum as parameter - SUBTRACT`() {
        Calculator(10).use { calc ->
            assertEquals(7, calc.applyOp(Operation.SUBTRACT, 3))
        }
    }

    @Test
    fun `enum as parameter - MULTIPLY`() {
        Calculator(4).use { calc ->
            assertEquals(12, calc.applyOp(Operation.MULTIPLY, 3))
        }
    }

    @Test
    fun `enum as return value`() {
        Calculator(0).use { calc ->
            assertEquals(Operation.ADD, calc.getLastOp())
        }
    }

    @Test
    fun `enum as mutable property`() {
        Calculator(0).use { calc ->
            assertEquals(Operation.ADD, calc.lastOperation)
            calc.applyOp(Operation.MULTIPLY, 2)
            assertEquals(Operation.MULTIPLY, calc.lastOperation)
        }
    }

    @Test
    fun `enum roundtrip through all values`() {
        Calculator(1).use { calc ->
            for (op in Operation.entries) {
                calc.applyOp(op, 1)
                assertEquals(op, calc.getLastOp())
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase 2: Companion object
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `companion fun returning String`() {
        assertEquals("2.0", Calculator.version())
    }

    @Test
    fun `companion fun returning object`() {
        Calculator.create(42).use { calc ->
            assertEquals(42, calc.current)
            calc.add(8)
            assertEquals(50, calc.current)
        }
    }

    @Test
    fun `companion fun with Int param`() {
        Calculator.create(100).use { calc ->
            assertEquals(100, calc.current)
        }
    }

    @Test
    fun `companion var property read and write`() {
        val before = Calculator.instanceCount
        Calculator.create(0).close()
        assertEquals(before + 1, Calculator.instanceCount)
    }

    @Test
    fun `companion val property`() {
        assertEquals("2.0", Calculator.VERSION)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase 2: Object types (class-as-param, class-as-return)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `object return - create returns usable Calculator`() {
        CalculatorManager().use { mgr ->
            mgr.create("a", 10).use { calc ->
                assertEquals(10, calc.current)
                assertEquals(15, calc.add(5))
            }
        }
    }

    @Test
    fun `object return - get returns Calculator`() {
        CalculatorManager().use { mgr ->
            mgr.create("x", 42)
            mgr.get("x").use { calc ->
                assertEquals(42, calc.current)
            }
        }
    }

    @Test
    fun `object return - get unknown returns default`() {
        CalculatorManager().use { mgr ->
            mgr.get("nonexistent").use { calc ->
                assertEquals(0, calc.current)
            }
        }
    }

    @Test
    fun `object as parameter`() {
        CalculatorManager().use { mgr ->
            Calculator(0).use { calc ->
                assertEquals(7, mgr.addWith(calc, 7))
                assertEquals(7, calc.current)
            }
        }
    }

    @Test
    fun `object as parameter - string method delegation`() {
        CalculatorManager().use { mgr ->
            Calculator(99).use { calc ->
                assertEquals("Calculator(current=99)", mgr.describe(calc))
            }
        }
    }

    @Test
    fun `multiple objects from same manager`() {
        CalculatorManager().use { mgr ->
            mgr.create("a", 1).use { a ->
                mgr.create("b", 2).use { b ->
                    assertEquals(1, a.current)
                    assertEquals(2, b.current)
                    assertEquals(2, mgr.count())
                }
            }
        }
    }

    @Test
    fun `returned object survives source operations`() {
        CalculatorManager().use { mgr ->
            val calc = mgr.create("test", 10)
            mgr.create("other", 99) // create another to ensure no interference
            assertEquals(10, calc.current)
            calc.add(5)
            assertEquals(15, calc.current)
            calc.close()
        }
    }

    @Test
    fun `companion object returns usable object`() {
        Calculator.create(77).use { calc ->
            assertEquals(77, calc.current)
            calc.add(3)
            assertEquals(80, calc.current)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle: AutoCloseable, close(), double-close safety
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `use block auto-closes`() {
        Calculator(0).use { calc ->
            calc.add(1)
            assertEquals(1, calc.current)
        }
        // No crash = success (Cleaner handles cleanup)
    }

    @Test
    fun `explicit close does not crash`() {
        val calc = Calculator(0)
        calc.add(1)
        calc.close()
        // No crash = success
    }

    @Test
    fun `double close does not crash`() {
        val calc = Calculator(0)
        calc.close()
        calc.close() // Second close should be safe (runCatching)
    }

    @Test
    fun `manager double close does not crash`() {
        val mgr = CalculatorManager()
        mgr.close()
        mgr.close()
    }

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
