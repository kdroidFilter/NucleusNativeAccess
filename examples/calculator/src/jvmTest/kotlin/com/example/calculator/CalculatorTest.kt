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

    // ═══════════════════════════════════════════════════════════════════════════
    // Data classes (value marshalling across FFM boundary)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `data class return - Point with primitive fields`() {
        Calculator(5).use { calc ->
            val p = calc.getPoint()
            assertEquals(5, p.x)
            assertEquals(10, p.y)
        }
    }

    @Test
    fun `data class return - Point with zero`() {
        Calculator(0).use { calc ->
            val p = calc.getPoint()
            assertEquals(0, p.x)
            assertEquals(0, p.y)
        }
    }

    @Test
    fun `data class param - Point`() {
        Calculator(0).use { calc ->
            val result = calc.addPoint(Point(3, 7))
            assertEquals(10, result)
            assertEquals(10, calc.current)
        }
    }

    @Test
    fun `data class param - Point negative`() {
        Calculator(10).use { calc ->
            val result = calc.addPoint(Point(-3, -2))
            assertEquals(5, result)
        }
    }

    @Test
    fun `data class return - NamedValue with String field`() {
        Calculator(42).use { calc ->
            calc.label = "test"
            val nv = calc.getNamedValue()
            assertEquals("test", nv.name)
            assertEquals(42, nv.value)
        }
    }

    @Test
    fun `data class return - NamedValue default label`() {
        Calculator(7).use { calc ->
            val nv = calc.getNamedValue()
            assertEquals("default", nv.name)
            assertEquals(7, nv.value)
        }
    }

    @Test
    fun `data class param - NamedValue with String field`() {
        Calculator(0).use { calc ->
            calc.setFromNamed(NamedValue("hello", 99))
            assertEquals("hello", calc.label)
            assertEquals(99, calc.current)
        }
    }

    @Test
    fun `data class roundtrip - set and get`() {
        Calculator(0).use { calc ->
            calc.setFromNamed(NamedValue("roundtrip", 42))
            val nv = calc.getNamedValue()
            assertEquals("roundtrip", nv.name)
            assertEquals(42, nv.value)
        }
    }

    // ── commonMain data class (CalcResult) ──────────────────────────────────

    @Test
    fun `common data class return - CalcResult`() {
        Calculator(42).use { calc ->
            val r = calc.getResult()
            assertEquals(42, r.value)
            assertEquals("Result: 42", r.description)
        }
    }

    @Test
    fun `common data class return - zero`() {
        Calculator(0).use { calc ->
            val r = calc.getResult()
            assertEquals(0, r.value)
            assertEquals("Result: 0", r.description)
        }
    }

    @Test
    fun `common data class param - applyResult`() {
        Calculator(0).use { calc ->
            val result = calc.applyResult(CalcResult(99, "injected"))
            assertEquals(99, result)
            assertEquals(99, calc.current)
            assertEquals("injected", calc.label)
        }
    }

    @Test
    fun `common data class roundtrip`() {
        Calculator(7).use { calc ->
            calc.label = "test"
            val r = calc.getResult()
            assertEquals(7, r.value)
            Calculator(0).use { other ->
                other.applyResult(r)
                assertEquals(7, other.current)
                assertEquals("Result: 7", other.label)
            }
        }
    }

    @Test
    fun `common data class equality works`() {
        Calculator(5).use { calc ->
            val r1 = calc.getResult()
            val r2 = calc.getResult()
            assertEquals(r1, r2) // data class equals
        }
    }

    @Test
    fun `common data class with unicode string`() {
        Calculator(0).use { calc ->
            calc.applyResult(CalcResult(1, "café ☕"))
            assertEquals("café ☕", calc.label)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases: primitives at boundaries
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Int MAX_VALUE`() {
        Calculator(Int.MAX_VALUE).use { calc ->
            assertEquals(Int.MAX_VALUE, calc.current)
        }
    }

    @Test
    fun `Int MIN_VALUE`() {
        Calculator(Int.MIN_VALUE).use { calc ->
            assertEquals(Int.MIN_VALUE, calc.current)
        }
    }

    @Test
    fun `Long large value`() {
        Calculator(0).use { calc ->
            assertEquals(1_000_000_000L, calc.addLong(1_000_000_000L))
        }
    }

    @Test
    fun `Double precision edge`() {
        Calculator(0).use { calc ->
            assertEquals(Double.MAX_VALUE, calc.addDouble(Double.MAX_VALUE), 1e300)
        }
    }

    @Test
    fun `Float NaN`() {
        Calculator(0).use { calc ->
            assertTrue(calc.addFloat(Float.NaN).isNaN())
        }
    }

    @Test
    fun `Boolean roundtrip both values`() {
        Calculator(5).use { calc ->
            assertTrue(calc.checkFlag(true))
            assertFalse(calc.checkFlag(false))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases: String
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `String with special chars`() {
        Calculator(0).use { calc ->
            val special = "tab\there\nnewline\\backslash\"quote"
            assertEquals(special, calc.echo(special))
        }
    }

    @Test
    fun `String with long content`() {
        Calculator(0).use { calc ->
            val long = "x".repeat(4000)
            assertEquals(long, calc.echo(long))
        }
    }

    @Test
    fun `String property roundtrip unicode`() {
        Calculator(0).use { calc ->
            val emoji = "🎉🚀💻🔥"
            calc.label = emoji
            assertEquals(emoji, calc.label)
        }
    }

    @Test
    fun `String concat unicode`() {
        Calculator(0).use { calc ->
            assertEquals("日本語テスト", calc.concat("日本語", "テスト"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases: Enum exhaustive
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `all enum operations produce correct results`() {
        Calculator(10).use { calc ->
            assertEquals(15, calc.applyOp(Operation.ADD, 5))
            assertEquals(10, calc.applyOp(Operation.SUBTRACT, 5))
            assertEquals(50, calc.applyOp(Operation.MULTIPLY, 5))
        }
    }

    @Test
    fun `enum property set and roundtrip all values`() {
        Calculator(0).use { calc ->
            for (op in Operation.entries) {
                calc.lastOperation = op
                assertEquals(op, calc.lastOperation)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases: Nullable boundary
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `nullable String property set null then non-null then null`() {
        Calculator(0).use { calc ->
            assertNull(calc.nickname)
            calc.nickname = "first"
            assertEquals("first", calc.nickname)
            calc.nickname = null
            assertNull(calc.nickname)
            calc.nickname = "second"
            assertEquals("second", calc.nickname)
        }
    }

    @Test
    fun `nullable Int at zero boundary`() {
        Calculator(10).use { calc ->
            assertEquals(5, calc.divideOrNull(2))
            assertNull(calc.divideOrNull(0))
            // Accumulator unchanged after null
            assertEquals(10, calc.current)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cross-feature: exception + other features
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `exception then callback still works`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            val result = calc.transform { it + 1 }
            assertEquals(11, result)
        }
    }

    @Test
    fun `exception then data class still works`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            val p = calc.getPoint()
            assertEquals(10, p.x)
        }
    }

    @Test
    fun `exception then nullable still works`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            assertEquals(5, calc.divideOrNull(2))
        }
    }

    @Test
    fun `exception then enum still works`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            assertEquals(15, calc.applyOp(Operation.ADD, 5))
        }
    }

    @Test
    fun `exception then companion still works`() {
        assertFailsWith<KotlinNativeException> {
            Calculator(0).use { it.divide(0) }
        }
        assertEquals("2.0", Calculator.version())
    }

    @Test
    fun `exception then string still works`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            assertEquals("Calculator(current=10)", calc.describe())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cross-feature: data class + other features
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `data class Point then modify accumulator then get again`() {
        Calculator(5).use { calc ->
            val p1 = calc.getPoint()
            calc.add(10)
            val p2 = calc.getPoint()
            assertEquals(5, p1.x)
            assertEquals(15, p2.x)
        }
    }

    @Test
    fun `data class NamedValue with empty string`() {
        Calculator(0).use { calc ->
            calc.setFromNamed(NamedValue("", 42))
            assertEquals("", calc.label)
            assertEquals(42, calc.current)
        }
    }

    @Test
    fun `data class NamedValue with unicode`() {
        Calculator(0).use { calc ->
            calc.setFromNamed(NamedValue("привет мир 🌍", 1))
            assertEquals("привет мир 🌍", calc.label)
        }
    }

    @Test
    fun `data class after callback`() {
        Calculator(10).use { calc ->
            calc.transform { it * 3 }
            val p = calc.getPoint()
            assertEquals(30, p.x)
            assertEquals(60, p.y)
        }
    }

    @Test
    fun `common data class CalcResult negative value`() {
        Calculator(-5).use { calc ->
            val r = calc.getResult()
            assertEquals(-5, r.value)
            assertEquals("Result: -5", r.description)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cross-feature: callbacks + other features
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `callback String with unicode`() {
        Calculator(42).use { calc ->
            var received = ""
            calc.withDescription { received = it }
            assertTrue(received.contains("42"))
        }
    }

    @Test
    fun `callback (Int) to String with empty return`() {
        Calculator(0).use { calc ->
            val result = calc.formatWith { "" }
            assertEquals("", result)
        }
    }

    @Test
    fun `callback (String) to String identity`() {
        Calculator(0).use { calc ->
            calc.label = "identity"
            val result = calc.transformLabel { it }
            assertEquals("identity", result)
        }
    }

    @Test
    fun `callback after data class operations`() {
        Calculator(0).use { calc ->
            calc.addPoint(Point(5, 10))
            var received = -1
            calc.onValueChanged { received = it }
            assertEquals(15, received)
        }
    }

    @Test
    fun `callback predicate on negative value`() {
        Calculator(-10).use { calc ->
            assertTrue(calc.checkWith { it < 0 })
            assertFalse(calc.checkWith { it > 0 })
            assertTrue(calc.checkWith { it == -10 })
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cross-feature: object types + other features
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `manager with multiple calculators then nullable`() {
        CalculatorManager().use { mgr ->
            mgr.create("a", 10)
            val found = mgr.getOrNull("a")
            val missing = mgr.getOrNull("z")
            assertTrue(found != null)
            assertEquals(10, found!!.current)
            assertNull(missing)
            found.close()
        }
    }

    @Test
    fun `manager count after multiple creates`() {
        CalculatorManager().use { mgr ->
            assertEquals(0, mgr.count())
            mgr.create("a", 1)
            assertEquals(1, mgr.count())
            mgr.create("b", 2)
            assertEquals(2, mgr.count())
            mgr.create("c", 3)
            assertEquals(3, mgr.count())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Sequential stress: many operations in sequence
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `100 sequential adds`() {
        Calculator(0).use { calc ->
            for (i in 1..100) calc.add(1)
            assertEquals(100, calc.current)
        }
    }

    @Test
    fun `50 callback invocations`() {
        Calculator(0).use { calc ->
            val values = mutableListOf<Int>()
            for (i in 1..50) {
                calc.add(1)
                calc.onValueChanged { values.add(it) }
            }
            assertEquals(50, values.size)
            assertEquals(50, values.last())
        }
    }

    @Test
    fun `20 data class roundtrips`() {
        Calculator(0).use { calc ->
            for (i in 1..20) {
                calc.setFromNamed(NamedValue("iter$i", i))
                val nv = calc.getNamedValue()
                assertEquals("iter$i", nv.name)
                assertEquals(i, nv.value)
            }
        }
    }

    @Test
    fun `10 exception recoveries`() {
        Calculator(10).use { calc ->
            for (i in 1..10) {
                assertFailsWith<KotlinNativeException> { calc.divide(0) }
                assertEquals(10, calc.current)
            }
            // Still works after all exceptions
            assertEquals(5, calc.divide(2))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase 4: Callbacks / Lambdas (FFM upcall stubs)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `callback (Int) to Unit - receives current value`() {
        Calculator(42).use { calc ->
            var received = -1
            calc.onValueChanged { value -> received = value }
            assertEquals(42, received)
        }
    }

    @Test
    fun `callback (Int) to Unit - zero value`() {
        Calculator(0).use { calc ->
            var received = -1
            calc.onValueChanged { value -> received = value }
            assertEquals(0, received)
        }
    }

    @Test
    fun `callback (Int) to Unit - negative value`() {
        Calculator(-5).use { calc ->
            var received = 999
            calc.onValueChanged { value -> received = value }
            assertEquals(-5, received)
        }
    }

    @Test
    fun `transform with (Int) to Int lambda - double`() {
        Calculator(10).use { calc ->
            val result = calc.transform { it * 2 }
            assertEquals(20, result)
            assertEquals(20, calc.current)
        }
    }

    @Test
    fun `transform with (Int) to Int lambda - negate`() {
        Calculator(7).use { calc ->
            val result = calc.transform { -it }
            assertEquals(-7, result)
        }
    }

    @Test
    fun `transform with (Int) to Int lambda - add constant`() {
        Calculator(10).use { calc ->
            val result = calc.transform { it + 100 }
            assertEquals(110, result)
        }
    }

    @Test
    fun `compute with (Int, Int) to Int - addition`() {
        Calculator(0).use { calc ->
            val result = calc.compute(3, 4) { a, b -> a + b }
            assertEquals(7, result)
            assertEquals(7, calc.current)
        }
    }

    @Test
    fun `compute with (Int, Int) to Int - multiplication`() {
        Calculator(0).use { calc ->
            val result = calc.compute(6, 7) { a, b -> a * b }
            assertEquals(42, result)
        }
    }

    @Test
    fun `compute with (Int, Int) to Int - subtraction`() {
        Calculator(0).use { calc ->
            val result = calc.compute(10, 3) { a, b -> a - b }
            assertEquals(7, result)
        }
    }

    @Test
    fun `checkWith (Int) to Boolean - true`() {
        Calculator(10).use { calc ->
            assertTrue(calc.checkWith { it > 0 })
        }
    }

    @Test
    fun `checkWith (Int) to Boolean - false`() {
        Calculator(-5).use { calc ->
            assertFalse(calc.checkWith { it > 0 })
        }
    }

    @Test
    fun `checkWith (Int) to Boolean - equality check`() {
        Calculator(42).use { calc ->
            assertTrue(calc.checkWith { it == 42 })
            assertFalse(calc.checkWith { it == 0 })
        }
    }

    @Test
    fun `callbacks work after exception recovery`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            // Callback should still work after exception
            var received = -1
            calc.onValueChanged { value -> received = value }
            assertEquals(10, received)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase 4b: String in callbacks
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `callback (String) to Unit - receives description`() {
        Calculator(42).use { calc ->
            var received = ""
            calc.withDescription { desc -> received = desc }
            assertEquals("Calculator(current=42)", received)
        }
    }

    @Test
    fun `callback (String) to Unit - empty accumulator`() {
        Calculator(0).use { calc ->
            var received = ""
            calc.withDescription { desc -> received = desc }
            assertEquals("Calculator(current=0)", received)
        }
    }

    @Test
    fun `callback (String) to Unit - negative accumulator`() {
        Calculator(-7).use { calc ->
            var received = ""
            calc.withDescription { desc -> received = desc }
            assertEquals("Calculator(current=-7)", received)
        }
    }

    @Test
    fun `callback (Int) to String - format value`() {
        Calculator(42).use { calc ->
            val result = calc.formatWith { v -> "Value is $v" }
            assertEquals("Value is 42", result)
        }
    }

    @Test
    fun `callback (Int) to String - negative value`() {
        Calculator(-5).use { calc ->
            val result = calc.formatWith { v -> "[$v]" }
            assertEquals("[-5]", result)
        }
    }

    @Test
    fun `callback (Int) to String - empty result`() {
        Calculator(0).use { calc ->
            val result = calc.formatWith { "" }
            assertEquals("", result)
        }
    }

    @Test
    fun `callback (String) to String - transform label`() {
        Calculator(0).use { calc ->
            calc.label = "hello"
            val result = calc.transformLabel { it.uppercase() }
            assertEquals("HELLO", result)
            assertEquals("HELLO", calc.label)
        }
    }

    @Test
    fun `callback (String) to String - prepend prefix`() {
        Calculator(0).use { calc ->
            calc.label = "world"
            val result = calc.transformLabel { "Hello $it" }
            assertEquals("Hello world", result)
        }
    }

    @Test
    fun `callback (String, Int) to Unit - matching keyword`() {
        Calculator(0).use { calc ->
            calc.label = "hello world"
            var receivedLabel = ""
            var receivedFound = -1
            calc.findAndReport("hello") { label, found ->
                receivedLabel = label
                receivedFound = found
            }
            assertEquals("hello world", receivedLabel)
            assertEquals(1, receivedFound)
        }
    }

    @Test
    fun `callback (String, Int) to Unit - no match`() {
        Calculator(0).use { calc ->
            calc.label = "hello"
            var receivedFound = -1
            calc.findAndReport("xyz") { _, found ->
                receivedFound = found
            }
            assertEquals(0, receivedFound)
        }
    }

    @Test
    fun `callback (String, Int) to Unit - empty label`() {
        Calculator(0).use { calc ->
            var receivedLabel = "initial"
            calc.findAndReport("test") { label, _ ->
                receivedLabel = label
            }
            assertEquals("", receivedLabel)
        }
    }

    @Test
    fun `multiple callbacks in sequence`() {
        Calculator(5).use { calc ->
            val values = mutableListOf<Int>()
            calc.onValueChanged { values.add(it) }
            calc.add(3)
            calc.onValueChanged { values.add(it) }
            assertEquals(listOf(5, 8), values)
        }
    }
}
