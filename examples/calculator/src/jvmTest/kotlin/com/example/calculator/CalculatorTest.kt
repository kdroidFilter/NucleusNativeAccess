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

    // ── data class with enum field ─────────────────────────────────────────

    @Test
    fun `data class with enum field - return`() {
        Calculator(5).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 2)
            val tp = calc.getTaggedPoint()
            assertEquals(Point(10, 20), tp.point)
            assertEquals(Operation.MULTIPLY, tp.tag)
        }
    }

    @Test
    fun `data class with enum field - param`() {
        Calculator(0).use { calc ->
            calc.setFromTagged(TaggedPoint(Point(3, 7), Operation.SUBTRACT))
            assertEquals(10, calc.current)
            assertEquals(Operation.SUBTRACT, calc.lastOperation)
        }
    }

    // ── nested data class ───────────────────────────────────────────────────

    @Test
    fun `nested data class return - Rect`() {
        Calculator(5).use { calc ->
            val r = calc.getRect()
            assertEquals(Point(0, 0), r.topLeft)
            assertEquals(Point(5, 5), r.bottomRight)
        }
    }

    @Test
    fun `nested data class return - zero`() {
        Calculator(0).use { calc ->
            val r = calc.getRect()
            assertEquals(Point(0, 0), r.topLeft)
            assertEquals(Point(0, 0), r.bottomRight)
        }
    }

    // ── data class with Object field ──────────────────────────────────────

    @Test
    fun `data class with Object field - snapshot return`() {
        Calculator(42).use { calc ->
            calc.label = "test"
            val snap = calc.snapshot()
            assertEquals(42, snap.calc.current)
            assertEquals("test", snap.label)
            snap.calc.close()
        }
    }

    @Test
    fun `data class with Object field - snapshot then modify original`() {
        Calculator(10).use { calc ->
            val snap = calc.snapshot()
            calc.add(20)
            // snapshot's calc is the SAME object (StableRef), so current reflects changes
            assertEquals(30, snap.calc.current)
            snap.calc.close()
        }
    }

    @Test
    fun `data class with Object field - restore from snapshot`() {
        Calculator(100).use { calc ->
            calc.label = "original"
            Calculator(42).use { other ->
                other.label = "other"
                val snap = other.snapshot()
                val result = calc.restoreFrom(snap)
                assertEquals(42, result)
                assertEquals("other", calc.label)
                snap.calc.close()
            }
        }
    }

    @Test
    fun `data class with Object field - param`() {
        Calculator(0).use { calc ->
            Calculator(77).use { source ->
                source.label = "from-source"
                val result = calc.restoreFrom(CalculatorSnapshot(source, "injected"))
                assertEquals(77, result)
                assertEquals("injected", calc.label)
            }
        }
    }

    @Test
    fun `CalculatorSnapshot default label`() {
        Calculator(5).use { calc ->
            val snap = calc.snapshot()
            assertEquals("snapshot", snap.label)
            snap.calc.close()
        }
    }

    @Test
    fun `CalculatorSnapshot param with same calculator`() {
        Calculator(10).use { calc ->
            val result = calc.restoreFrom(CalculatorSnapshot(calc, "self"))
            assertEquals(10, result)
            assertEquals("self", calc.label)
        }
    }

    // ── cross-feature: data class fields + other features ───────────────────

    @Test
    fun `TaggedPoint after exception recovery`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            calc.applyOp(Operation.ADD, 5)
            val tp = calc.getTaggedPoint()
            assertEquals(15, tp.point.x)
            assertEquals(Operation.ADD, tp.tag)
        }
    }

    @Test
    fun `Rect after callback modifies accumulator`() {
        Calculator(0).use { calc ->
            calc.transform { 42 }
            val r = calc.getRect()
            assertEquals(Point(42, 42), r.bottomRight)
        }
    }

    @Test
    fun `snapshot inside callback`() {
        Calculator(100).use { calc ->
            calc.label = "in-callback"
            var snapLabel = ""
            calc.onValueChanged { _ ->
                // Can't call calc methods here (reentrant), but test the value
                snapLabel = "got-it"
            }
            assertEquals("got-it", snapLabel)
        }
    }

    @Test
    fun `CalculatorSnapshot with unicode label`() {
        Calculator(7).use { calc ->
            calc.label = "日本語 🚀"
            val snap = calc.snapshot()
            assertEquals("日本語 🚀", snap.label)
            snap.calc.close()
        }
    }

    @Test
    fun `nested Rect then modify and get again`() {
        Calculator(3).use { calc ->
            val r1 = calc.getRect()
            calc.multiply(4)
            val r2 = calc.getRect()
            assertEquals(Point(3, 3), r1.bottomRight)
            assertEquals(Point(12, 12), r2.bottomRight)
        }
    }

    @Test
    fun `TaggedPoint param then return consistency`() {
        Calculator(0).use { calc ->
            val input = TaggedPoint(Point(5, 10), Operation.MULTIPLY)
            calc.setFromTagged(input)
            val output = calc.getTaggedPoint()
            assertEquals(Operation.MULTIPLY, output.tag)
            assertEquals(15, output.point.x) // 5+10
        }
    }

    @Test
    fun `NamedValue with special chars in string`() {
        Calculator(0).use { calc ->
            calc.setFromNamed(NamedValue("tab\there\nnewline", 1))
            assertEquals("tab\there\nnewline", calc.label)
        }
    }

    @Test
    fun `Point and NamedValue in sequence`() {
        Calculator(0).use { calc ->
            calc.addPoint(Point(10, 20))
            calc.setFromNamed(NamedValue("after-point", 99))
            assertEquals(99, calc.current)
            assertEquals("after-point", calc.label)
            val p = calc.getPoint()
            assertEquals(99, p.x)
        }
    }

    @Test
    fun `CalcResult after ByteArray operations`() {
        Calculator(0).use { calc ->
            calc.sumBytes(byteArrayOf(10, 20, 30))
            val r = calc.getResult()
            assertEquals(60, r.value)
            assertEquals("Result: 60", r.description)
        }
    }

    @Test
    fun `20 TaggedPoint roundtrips`() {
        Calculator(0).use { calc ->
            for (i in 1..20) {
                val op = Operation.entries[i % Operation.entries.size]
                calc.setFromTagged(TaggedPoint(Point(i, i * 2), op))
                val tp = calc.getTaggedPoint()
                assertEquals(op, tp.tag)
                assertEquals(i * 3, tp.point.x) // i + i*2
            }
        }
    }

    @Test
    fun `10 Rect calls`() {
        Calculator(0).use { calc ->
            for (i in 1..10) {
                calc.add(1)
                val r = calc.getRect()
                assertEquals(Point(i, i), r.bottomRight)
            }
        }
    }

    // ── data class with enum field — edge cases ────────────────────────────

    @Test
    fun `TaggedPoint with ADD operation`() {
        Calculator(0).use { calc ->
            val tp = calc.getTaggedPoint()
            assertEquals(Operation.ADD, tp.tag) // default lastOperation
            assertEquals(Point(0, 0), tp.point)
        }
    }

    @Test
    fun `TaggedPoint roundtrip all operations`() {
        Calculator(1).use { calc ->
            for (op in Operation.entries) {
                calc.setFromTagged(TaggedPoint(Point(10, 20), op))
                assertEquals(30, calc.current)
                assertEquals(op, calc.lastOperation)
                val tp = calc.getTaggedPoint()
                assertEquals(op, tp.tag)
            }
        }
    }

    @Test
    fun `TaggedPoint param with negative point`() {
        Calculator(0).use { calc ->
            calc.setFromTagged(TaggedPoint(Point(-5, -3), Operation.SUBTRACT))
            assertEquals(-8, calc.current)
        }
    }

    // ── nested data class — edge cases ──────────────────────────────────────

    @Test
    fun `Rect with negative coordinates`() {
        Calculator(-10).use { calc ->
            val r = calc.getRect()
            assertEquals(Point(0, 0), r.topLeft)
            assertEquals(Point(-10, -10), r.bottomRight)
        }
    }

    @Test
    fun `Rect after operations`() {
        Calculator(0).use { calc ->
            calc.add(7)
            val r = calc.getRect()
            assertEquals(Point(7, 7), r.bottomRight)
        }
    }

    @Test
    fun `multiple Rect calls return independent values`() {
        Calculator(3).use { calc ->
            val r1 = calc.getRect()
            calc.add(2)
            val r2 = calc.getRect()
            assertEquals(Point(3, 3), r1.bottomRight)
            assertEquals(Point(5, 5), r2.bottomRight)
        }
    }

    // ── nullable data class ───────────────────────────────────────────────

    @Test
    fun `nullable Point return - non-null`() {
        Calculator(5).use { calc ->
            val p = calc.getPointOrNull()
            assertEquals(Point(5, 10), p)
        }
    }

    @Test
    fun `nullable Point return - null`() {
        Calculator(0).use { calc ->
            assertNull(calc.getPointOrNull())
        }
    }

    @Test
    fun `nullable Point param - non-null`() {
        Calculator(0).use { calc ->
            val result = calc.addPointOrNull(Point(3, 7))
            assertEquals(10, result)
        }
    }

    @Test
    fun `nullable Point param - null`() {
        Calculator(5).use { calc ->
            val result = calc.addPointOrNull(null)
            assertEquals(5, result) // unchanged
        }
    }

    @Test
    fun `nullable CalcResult return - non-null`() {
        Calculator(42).use { calc ->
            val r = calc.getResultOrNull()
            assertEquals(CalcResult(42, "Result: 42"), r)
        }
    }

    @Test
    fun `nullable CalcResult return - null`() {
        Calculator(0).use { calc ->
            assertNull(calc.getResultOrNull())
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
    // Data classes in callbacks
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `callback (Point) to Unit - receives point`() {
        Calculator(5).use { calc ->
            var received: Point? = null
            calc.onPointComputed { p -> received = p }
            assertEquals(Point(5, 10), received)
        }
    }

    @Test
    fun `callback (Point) to Unit - zero values`() {
        Calculator(0).use { calc ->
            var received: Point? = null
            calc.onPointComputed { p -> received = p }
            assertEquals(Point(0, 0), received)
        }
    }

    @Test
    fun `callback (Point) to Unit - negative values`() {
        Calculator(-3).use { calc ->
            var received: Point? = null
            calc.onPointComputed { p -> received = p }
            assertEquals(Point(-3, -6), received)
        }
    }

    @Test
    fun `callback (CalcResult) to Unit - common data class`() {
        Calculator(42).use { calc ->
            var received: CalcResult? = null
            calc.onResultReady { r -> received = r }
            assertEquals(CalcResult(42, "Result: 42"), received)
        }
    }

    @Test
    fun `callback (CalcResult) to Unit - String field preserved`() {
        Calculator(7).use { calc ->
            var receivedDesc = ""
            calc.onResultReady { r -> receivedDesc = r.description }
            assertEquals("Result: 7", receivedDesc)
        }
    }

    @Test
    fun `callback (Int) to Point - create point from value`() {
        Calculator(5).use { calc ->
            val p = calc.createPoint { v -> Point(v, v * 3) }
            assertEquals(Point(5, 15), p)
        }
    }

    @Test
    fun `callback (Int) to Point - zero`() {
        Calculator(0).use { calc ->
            val p = calc.createPoint { v -> Point(v, v) }
            assertEquals(Point(0, 0), p)
        }
    }

    @Test
    fun `callback (Int) to Point - negative`() {
        Calculator(-4).use { calc ->
            val p = calc.createPoint { v -> Point(v, -v) }
            assertEquals(Point(-4, 4), p)
        }
    }

    @Test
    fun `callback (Point) to Int - transform point`() {
        Calculator(5).use { calc ->
            val result = calc.transformPoint { p -> p.x + p.y }
            assertEquals(15, result) // 5 + 10
        }
    }

    @Test
    fun `callback (Point) to Int - use only x`() {
        Calculator(7).use { calc ->
            val result = calc.transformPoint { p -> p.x * 3 }
            assertEquals(21, result)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ByteArray
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ByteArray return`() {
        Calculator(42).use { calc ->
            val bytes = calc.toBytes()
            assertEquals("42", String(bytes))
        }
    }

    @Test
    fun `ByteArray return - negative`() {
        Calculator(-7).use { calc ->
            val bytes = calc.toBytes()
            assertEquals("-7", String(bytes))
        }
    }

    @Test
    fun `ByteArray param - sum`() {
        Calculator(0).use { calc ->
            val result = calc.sumBytes(byteArrayOf(1, 2, 3, 4))
            assertEquals(10, result)
        }
    }

    @Test
    fun `ByteArray param - empty`() {
        Calculator(0).use { calc ->
            val result = calc.sumBytes(byteArrayOf())
            assertEquals(0, result)
        }
    }

    @Test
    fun `ByteArray roundtrip - reverse`() {
        Calculator(0).use { calc ->
            val input = byteArrayOf(1, 2, 3, 4, 5)
            val reversed = calc.reverseBytes(input)
            assertEquals(listOf<Byte>(5, 4, 3, 2, 1), reversed.toList())
        }
    }

    @Test
    fun `ByteArray roundtrip - large`() {
        Calculator(0).use { calc ->
            val input = ByteArray(1000) { (it % 256).toByte() }
            val reversed = calc.reverseBytes(input)
            assertEquals(1000, reversed.size)
            assertEquals(input.last(), reversed.first())
            assertEquals(input.first(), reversed.last())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Enums in callbacks
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `callback (Operation) to Unit - receives enum`() {
        Calculator(0).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 3)
            var received: Operation? = null
            calc.onOperation { op -> received = op }
            assertEquals(Operation.MULTIPLY, received)
        }
    }

    @Test
    fun `callback (Operation) to Unit - all values`() {
        Calculator(1).use { calc ->
            for (expected in Operation.entries) {
                calc.lastOperation = expected
                var received: Operation? = null
                calc.onOperation { op -> received = op }
                assertEquals(expected, received)
            }
        }
    }

    @Test
    fun `callback (Int) to Operation - choose based on value`() {
        Calculator(5).use { calc ->
            val result = calc.chooseOp { v -> if (v > 0) Operation.ADD else Operation.SUBTRACT }
            assertEquals(Operation.ADD, result)
            assertEquals(Operation.ADD, calc.lastOperation)
        }
    }

    @Test
    fun `callback (Int) to Operation - negative triggers subtract`() {
        Calculator(-3).use { calc ->
            val result = calc.chooseOp { v -> if (v > 0) Operation.ADD else Operation.SUBTRACT }
            assertEquals(Operation.SUBTRACT, result)
        }
    }

    @Test
    fun `callback (Int) to Operation - return MULTIPLY`() {
        Calculator(0).use { calc ->
            val result = calc.chooseOp { Operation.MULTIPLY }
            assertEquals(Operation.MULTIPLY, result)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Callbacks: all primitive types (battle-test each type individually)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `callback Long param and return`() {
        Calculator(10).use { calc ->
            val result = calc.withLong { it * 3L }
            assertEquals(30L, result)
        }
    }

    @Test
    fun `callback Long large value`() {
        Calculator(1).use { calc ->
            val result = calc.withLong { it + 1_000_000_000L }
            assertEquals(1_000_000_001L, result)
        }
    }

    @Test
    fun `callback Double param and return`() {
        Calculator(7).use { calc ->
            val result = calc.withDouble { it * 1.5 }
            assertEquals(10.5, result, 0.001)
        }
    }

    @Test
    fun `callback Double precision`() {
        Calculator(0).use { calc ->
            val result = calc.withDouble { 3.141592653589793 }
            assertEquals(3.141592653589793, result, 1e-10)
        }
    }

    @Test
    fun `callback Float param and return`() {
        Calculator(5).use { calc ->
            val result = calc.withFloat { it + 0.5f }
            assertEquals(5.5f, result, 0.01f)
        }
    }

    @Test
    fun `callback Short param and return`() {
        Calculator(100).use { calc ->
            val result = calc.withShort { (it * 2).toShort() }
            assertEquals(200.toShort(), result)
        }
    }

    @Test
    fun `callback Byte param and return`() {
        Calculator(10).use { calc ->
            val result = calc.withByte { (it + 5).toByte() }
            assertEquals(15.toByte(), result)
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

    // ── Collection: List<Int> ───────────────────────────────────────────────

    @Test
    fun `List Int return - getScores`() {
        Calculator(10).use { calc ->
            val scores = calc.getScores()
            assertEquals(listOf(10, 20, 30), scores)
        }
    }

    @Test
    fun `List Int return - zero accumulator`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(0, 0, 0), calc.getScores())
        }
    }

    @Test
    fun `List Int return - negative accumulator`() {
        Calculator(-3).use { calc ->
            assertEquals(listOf(-3, -6, -9), calc.getScores())
        }
    }

    @Test
    fun `List Int param - sumAll`() {
        Calculator(0).use { calc ->
            val result = calc.sumAll(listOf(1, 2, 3, 4, 5))
            assertEquals(15, result)
        }
    }

    @Test
    fun `List Int param - empty list`() {
        Calculator(42).use { calc ->
            assertEquals(0, calc.sumAll(emptyList()))
        }
    }

    @Test
    fun `List Int param - single element`() {
        Calculator(0).use { calc ->
            assertEquals(99, calc.sumAll(listOf(99)))
        }
    }

    @Test
    fun `List Int param - large list`() {
        Calculator(0).use { calc ->
            val largeList = List(1000) { it + 1 }
            assertEquals(500500, calc.sumAll(largeList))
        }
    }

    // ── Collection: List<String> ────────────────────────────────────────────

    @Test
    fun `List String return - getLabels`() {
        Calculator(5).use { calc ->
            calc.label = "test"
            val labels = calc.getLabels()
            assertEquals(listOf("test", "item_5"), labels)
        }
    }

    @Test
    fun `List String return - default label`() {
        Calculator(0).use { calc ->
            assertEquals(listOf("default", "item_0"), calc.getLabels())
        }
    }

    @Test
    fun `List String param - joinLabels`() {
        Calculator(0).use { calc ->
            val result = calc.joinLabels(listOf("a", "b", "c"))
            assertEquals("a, b, c", result)
        }
    }

    @Test
    fun `List String param - empty list`() {
        Calculator(0).use { calc ->
            assertEquals("", calc.joinLabels(emptyList()))
        }
    }

    @Test
    fun `List String param - special characters`() {
        Calculator(0).use { calc ->
            val result = calc.joinLabels(listOf("hello world", "foo/bar", "a=b"))
            assertEquals("hello world, foo/bar, a=b", result)
        }
    }

    // ── Collection: List<Double> ────────────────────────────────────────────

    @Test
    fun `List Double return - getWeights`() {
        Calculator(10).use { calc ->
            val weights = calc.getWeights()
            assertEquals(2, weights.size)
            assertEquals(10.0, weights[0], 0.001)
            assertEquals(15.0, weights[1], 0.001)
        }
    }

    // ── Collection: List<Boolean> ───────────────────────────────────────────

    @Test
    fun `List Boolean return - getFlags positive`() {
        Calculator(5).use { calc ->
            calc.label = "test"
            val flags = calc.getFlags()
            assertEquals(listOf(true, false, true), flags)
        }
    }

    @Test
    fun `List Boolean return - getFlags zero`() {
        Calculator(0).use { calc ->
            val flags = calc.getFlags()
            assertEquals(listOf(false, true, false), flags)
        }
    }

    // ── Collection: List<Enum> ──────────────────────────────────────────────

    @Test
    fun `List Enum return - getOperations`() {
        Calculator(0).use { calc ->
            val ops = calc.getOperations()
            assertEquals(listOf(Operation.ADD, Operation.SUBTRACT, Operation.MULTIPLY), ops)
        }
    }

    @Test
    fun `List Enum param - countOps`() {
        Calculator(0).use { calc ->
            assertEquals(2, calc.countOps(listOf(Operation.ADD, Operation.SUBTRACT)))
        }
    }

    @Test
    fun `List Enum param - empty list`() {
        Calculator(0).use { calc ->
            assertEquals(0, calc.countOps(emptyList()))
        }
    }

    // ── Collection: Set<Int> ────────────────────────────────────────────────

    @Test
    fun `Set Int return - getUniqueDigits`() {
        Calculator(123).use { calc ->
            val digits = calc.getUniqueDigits()
            assertEquals(setOf(1, 2, 3), digits)
        }
    }

    @Test
    fun `Set Int return - repeated digits`() {
        Calculator(111).use { calc ->
            assertEquals(setOf(1), calc.getUniqueDigits())
        }
    }

    @Test
    fun `Set Int return - zero`() {
        Calculator(0).use { calc ->
            assertEquals(setOf(0), calc.getUniqueDigits())
        }
    }

    @Test
    fun `Set Int param - sumUnique`() {
        Calculator(0).use { calc ->
            assertEquals(6, calc.sumUnique(setOf(1, 2, 3)))
        }
    }

    @Test
    fun `Set Int param - empty set`() {
        Calculator(42).use { calc ->
            assertEquals(0, calc.sumUnique(emptySet()))
        }
    }

    // ── Collection: Map<String, Int> ────────────────────────────────────────

    @Test
    fun `Map String Int return - getMetadata`() {
        Calculator(42).use { calc ->
            calc.scale = 3.0
            val meta = calc.getMetadata()
            assertEquals(42, meta["current"])
            assertEquals(3, meta["scale"])
            assertEquals(2, meta.size)
        }
    }

    @Test
    fun `Map String Int param - sumMap`() {
        Calculator(0).use { calc ->
            val result = calc.sumMap(mapOf("a" to 10, "b" to 20, "c" to 30))
            assertEquals(60, result)
        }
    }

    @Test
    fun `Map String Int param - empty map`() {
        Calculator(42).use { calc ->
            assertEquals(0, calc.sumMap(emptyMap()))
        }
    }

    // ── Collection: List<Long> ────────────────────────────────────────────

    @Test
    fun `List Long return - getLongScores`() {
        Calculator(5).use { calc ->
            val scores = calc.getLongScores()
            assertEquals(listOf(5L, 500_000L), scores)
        }
    }

    @Test
    fun `List Long param - sumLongs`() {
        Calculator(0).use { calc ->
            assertEquals(300_000L, calc.sumLongs(listOf(100_000L, 200_000L)))
        }
    }

    @Test
    fun `List Long param - empty`() {
        Calculator(0).use { calc ->
            assertEquals(0L, calc.sumLongs(emptyList()))
        }
    }

    @Test
    fun `List Long param - large values`() {
        Calculator(0).use { calc ->
            assertEquals(2_000_000_000L, calc.sumLongs(listOf(1_000_000_000L, 1_000_000_000L)))
        }
    }

    // ── Collection: List<Float> ─────────────────────────────────────────────

    @Test
    fun `List Float return - getFloatWeights`() {
        Calculator(10).use { calc ->
            val weights = calc.getFloatWeights()
            assertEquals(2, weights.size)
            assertEquals(10.0f, weights[0], 0.001f)
            assertEquals(5.0f, weights[1], 0.001f)
        }
    }

    @Test
    fun `List Float return - zero`() {
        Calculator(0).use { calc ->
            val weights = calc.getFloatWeights()
            assertEquals(0.0f, weights[0], 0.001f)
            assertEquals(0.0f, weights[1], 0.001f)
        }
    }

    // ── Collection: List<Short> ─────────────────────────────────────────────

    @Test
    fun `List Short return - getShortValues`() {
        Calculator(7).use { calc ->
            val values = calc.getShortValues()
            assertEquals(listOf(7.toShort(), 14.toShort()), values)
        }
    }

    // ── Collection: List<Byte> ──────────────────────────────────────────────

    @Test
    fun `List Byte return - getByteValues`() {
        Calculator(3).use { calc ->
            val values = calc.getByteValues()
            assertEquals(listOf(3.toByte(), 4.toByte()), values)
        }
    }

    // ── Collection: List<Double> extended ───────────────────────────────────

    @Test
    fun `List Double return - zero accumulator`() {
        Calculator(0).use { calc ->
            val weights = calc.getWeights()
            assertEquals(0.0, weights[0], 0.001)
            assertEquals(0.0, weights[1], 0.001)
        }
    }

    @Test
    fun `List Double return - negative accumulator`() {
        Calculator(-4).use { calc ->
            val weights = calc.getWeights()
            assertEquals(-4.0, weights[0], 0.001)
            assertEquals(-6.0, weights[1], 0.001)
        }
    }

    // ── Collection: List<Int> extended ──────────────────────────────────────

    @Test
    fun `List Int param - negative values`() {
        Calculator(0).use { calc ->
            assertEquals(-6, calc.sumAll(listOf(-1, -2, -3)))
        }
    }

    @Test
    fun `List Int param and return roundtrip`() {
        Calculator(0).use { calc ->
            calc.sumAll(listOf(10, 20, 30))
            val scores = calc.getScores()
            assertEquals(listOf(60, 120, 180), scores)
        }
    }

    // ── Collection: List<String> extended ───────────────────────────────────

    @Test
    fun `List String param - unicode strings`() {
        Calculator(0).use { calc ->
            val result = calc.joinLabels(listOf("café", "naïve", "über"))
            assertEquals("café, naïve, über", result)
        }
    }

    @Test
    fun `List String param - single element`() {
        Calculator(0).use { calc ->
            assertEquals("only", calc.joinLabels(listOf("only")))
        }
    }

    @Test
    fun `List String param - long strings`() {
        Calculator(0).use { calc ->
            val longStr = "a".repeat(200)
            val result = calc.joinLabels(listOf(longStr, "b"))
            assertEquals("$longStr, b", result)
        }
    }

    // ── Collection: List<Boolean> extended ──────────────────────────────────

    @Test
    fun `List Boolean return - negative accumulator`() {
        Calculator(-5).use { calc ->
            val flags = calc.getFlags()
            assertEquals(false, flags[0]) // not positive
            assertEquals(false, flags[1]) // -5 is odd
            assertEquals(false, flags[2]) // label is empty
        }
    }

    // ── Collection: List<Enum> extended ─────────────────────────────────────

    @Test
    fun `List Enum param - all entries`() {
        Calculator(0).use { calc ->
            assertEquals(3, calc.countOps(listOf(Operation.ADD, Operation.SUBTRACT, Operation.MULTIPLY)))
        }
    }

    @Test
    fun `List Enum param - duplicates`() {
        Calculator(0).use { calc ->
            assertEquals(4, calc.countOps(listOf(Operation.ADD, Operation.ADD, Operation.ADD, Operation.MULTIPLY)))
        }
    }

    @Test
    fun `List Enum return and param roundtrip`() {
        Calculator(0).use { calc ->
            val ops = calc.getOperations()
            assertEquals(3, calc.countOps(ops))
        }
    }

    // ── Collection: Set<Int> extended ───────────────────────────────────────

    @Test
    fun `Set Int return - large number`() {
        Calculator(9876).use { calc ->
            val digits = calc.getUniqueDigits()
            assertEquals(setOf(9, 8, 7, 6), digits)
        }
    }

    @Test
    fun `Set Int param - single element`() {
        Calculator(0).use { calc ->
            assertEquals(42, calc.sumUnique(setOf(42)))
        }
    }

    @Test
    fun `Set Int param and return roundtrip`() {
        Calculator(321).use { calc ->
            val digits = calc.getUniqueDigits()
            calc.sumUnique(digits)
            assertEquals(6, calc.current) // 1+2+3=6
        }
    }

    // ── Collection: Set<String> ─────────────────────────────────────────────

    @Test
    fun `Set String return - getUniqueLabels`() {
        Calculator(5).use { calc ->
            calc.label = "test"
            val labels = calc.getUniqueLabels()
            assertTrue(labels.contains("test"))
            assertTrue(labels.contains("item_5"))
            // "test" appears twice in input → deduped in set
            assertEquals(2, labels.size)
        }
    }

    @Test
    fun `Set String return - default label dedup`() {
        Calculator(0).use { calc ->
            val labels = calc.getUniqueLabels()
            assertTrue(labels.contains("default"))
            assertTrue(labels.contains("item_0"))
        }
    }

    @Test
    fun `Set String param - joinUniqueStrings`() {
        Calculator(0).use { calc ->
            val result = calc.joinUniqueStrings(setOf("c", "a", "b"))
            assertEquals("a;b;c", result)
        }
    }

    @Test
    fun `Set String param - empty set`() {
        Calculator(0).use { calc ->
            assertEquals("", calc.joinUniqueStrings(emptySet()))
        }
    }

    // ── Collection: Set<Enum> ───────────────────────────────────────────────

    @Test
    fun `Set Enum return - getUsedOps`() {
        Calculator(0).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 5)
            val ops = calc.getUsedOps()
            assertTrue(ops.contains(Operation.MULTIPLY))
            assertTrue(ops.contains(Operation.ADD))
        }
    }

    @Test
    fun `Set Enum return - dedup when same`() {
        Calculator(0).use { calc ->
            // lastOperation defaults to ADD, getUsedOps returns {lastOperation, ADD}
            val ops = calc.getUsedOps()
            assertEquals(setOf(Operation.ADD), ops)
        }
    }

    // ── Collection: Map<String, Int> extended ───────────────────────────────

    @Test
    fun `Map String Int return - different scale`() {
        Calculator(100).use { calc ->
            calc.scale = 7.0
            val meta = calc.getMetadata()
            assertEquals(100, meta["current"])
            assertEquals(7, meta["scale"])
        }
    }

    @Test
    fun `Map String Int param - single entry`() {
        Calculator(0).use { calc ->
            assertEquals(42, calc.sumMap(mapOf("x" to 42)))
        }
    }

    @Test
    fun `Map String Int param - negative values`() {
        Calculator(0).use { calc ->
            assertEquals(-10, calc.sumMap(mapOf("a" to -3, "b" to -7)))
        }
    }

    @Test
    fun `Map String Int roundtrip`() {
        Calculator(50).use { calc ->
            calc.scale = 2.0
            val meta = calc.getMetadata()
            calc.sumMap(meta) // sum of current(50) + scale(2) = 52
            assertEquals(52, calc.current)
        }
    }

    // ── Collection: Map<Int, String> ────────────────────────────────────────

    @Test
    fun `Map Int String return - getIndexedLabels`() {
        Calculator(7).use { calc ->
            calc.label = "hello"
            val labels = calc.getIndexedLabels()
            assertEquals("hello", labels[0])
            assertEquals("item_7", labels[1])
            assertEquals(2, labels.size)
        }
    }

    @Test
    fun `Map Int String return - default label`() {
        Calculator(0).use { calc ->
            val labels = calc.getIndexedLabels()
            assertEquals("default", labels[0])
            assertEquals("item_0", labels[1])
        }
    }

    // ── Collection: Map<Int, Int> ───────────────────────────────────────────

    @Test
    fun `Map Int Int return - getSquares`() {
        Calculator(5).use { calc ->
            val squares = calc.getSquares()
            assertEquals(1, squares[1])
            assertEquals(4, squares[2])
            assertEquals(9, squares[3])
            assertEquals(25, squares[5])
        }
    }

    @Test
    fun `Map Int Int param - sumMapValues`() {
        Calculator(0).use { calc ->
            assertEquals(14, calc.sumMapValues(mapOf(1 to 4, 2 to 10)))
        }
    }

    @Test
    fun `Map Int Int param - empty`() {
        Calculator(42).use { calc ->
            assertEquals(0, calc.sumMapValues(emptyMap()))
        }
    }

    @Test
    fun `Map Int Int roundtrip`() {
        Calculator(3).use { calc ->
            val squares = calc.getSquares()
            // getSquares: {1:1, 2:4, 3:9, 3:9} → key 3 = accumulator so deduped → {1:1, 2:4, 3:9}
            calc.sumMapValues(squares)
            assertEquals(14, calc.current) // 1 + 4 + 9
        }
    }

    // ── Collection: Map<String, String> ─────────────────────────────────────

    @Test
    fun `Map String String return - getStringMap`() {
        Calculator(42).use { calc ->
            calc.label = "test"
            val map = calc.getStringMap()
            assertEquals("test", map["name"])
            assertEquals("42", map["value"])
        }
    }

    @Test
    fun `Map String String return - unnamed`() {
        Calculator(0).use { calc ->
            val map = calc.getStringMap()
            assertEquals("unnamed", map["name"])
            assertEquals("0", map["value"])
        }
    }

    @Test
    fun `Map String String param - concatMapEntries`() {
        Calculator(0).use { calc ->
            // Note: map iteration order may vary, so use a sorted map
            val result = calc.concatMapEntries(sortedMapOf("a" to "1", "b" to "2"))
            assertEquals("a=1, b=2", result)
        }
    }

    @Test
    fun `Map String String param - empty`() {
        Calculator(0).use { calc ->
            assertEquals("", calc.concatMapEntries(emptyMap()))
        }
    }

    @Test
    fun `Map String String param - special chars`() {
        Calculator(0).use { calc ->
            val result = calc.concatMapEntries(sortedMapOf("key" to "hello world"))
            assertEquals("key=hello world", result)
        }
    }

    @Test
    fun `Map String String roundtrip`() {
        Calculator(99).use { calc ->
            calc.label = "myCalc"
            val map = calc.getStringMap()
            val result = calc.concatMapEntries(map)
            assertTrue(result.contains("name=myCalc"))
            assertTrue(result.contains("value=99"))
        }
    }

    // ── Nullable Collection: List<Int>? ─────────────────────────────────────

    @Test
    fun `nullable List Int return - non-null`() {
        Calculator(5).use { calc ->
            val scores = calc.getScoresOrNull()
            assertEquals(listOf(5, 10), scores)
        }
    }

    @Test
    fun `nullable List Int return - null`() {
        Calculator(0).use { calc ->
            assertNull(calc.getScoresOrNull())
        }
    }

    @Test
    fun `nullable List Int return - negative`() {
        Calculator(-3).use { calc ->
            val scores = calc.getScoresOrNull()
            assertEquals(listOf(-3, -6), scores)
        }
    }

    // ── Nullable Collection: List<String>? ──────────────────────────────────

    @Test
    fun `nullable List String return - non-null`() {
        Calculator(0).use { calc ->
            calc.label = "hello"
            val labels = calc.getLabelsOrNull()
            assertEquals(listOf("hello", "extra"), labels)
        }
    }

    @Test
    fun `nullable List String return - null`() {
        Calculator(0).use { calc ->
            assertNull(calc.getLabelsOrNull())
        }
    }

    // ── Nullable Collection param: List<Int>? ───────────────────────────────

    @Test
    fun `nullable List Int param - non-null`() {
        Calculator(0).use { calc ->
            assertEquals(15, calc.sumAllOrNull(listOf(5, 10)))
        }
    }

    @Test
    fun `nullable List Int param - null`() {
        Calculator(0).use { calc ->
            assertEquals(-1, calc.sumAllOrNull(null))
        }
    }

    @Test
    fun `nullable List Int param - empty`() {
        Calculator(42).use { calc ->
            assertEquals(0, calc.sumAllOrNull(emptyList()))
        }
    }

    // ── Nullable Collection: Set<Enum>? ─────────────────────────────────────

    @Test
    fun `nullable Set Enum return - non-null`() {
        Calculator(5).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 2)
            val ops = calc.getOpsOrNull()
            assertTrue(ops != null)
            assertTrue(ops!!.contains(Operation.MULTIPLY))
            assertTrue(ops.contains(Operation.ADD))
        }
    }

    @Test
    fun `nullable Set Enum return - null`() {
        Calculator(-1).use { calc ->
            assertNull(calc.getOpsOrNull())
        }
    }

    // ── Nullable Collection: Map<String, Int>? ──────────────────────────────

    @Test
    fun `nullable Map return - non-null`() {
        Calculator(42).use { calc ->
            val meta = calc.getMetadataOrNull()
            assertEquals(mapOf("val" to 42), meta)
        }
    }

    @Test
    fun `nullable Map return - null`() {
        Calculator(0).use { calc ->
            assertNull(calc.getMetadataOrNull())
        }
    }

    // ── Collection: List<Object> ────────────────────────────────────────────

    @Test
    fun `List Object return - getAll`() {
        CalculatorManager().use { mgr ->
            mgr.create("a", 10)
            mgr.create("b", 20)
            val all = mgr.getAll()
            assertEquals(2, all.size)
            // Each element is a Calculator proxy
            val currents = all.map { it.current }.sorted()
            assertEquals(listOf(10, 20), currents)
            all.forEach { it.close() }
        }
    }

    @Test
    fun `List Object return - empty`() {
        CalculatorManager().use { mgr ->
            val all = mgr.getAll()
            assertEquals(0, all.size)
        }
    }

    @Test
    fun `List Object param - sumAll`() {
        CalculatorManager().use { mgr ->
            val c1 = mgr.create("a", 10)
            val c2 = mgr.create("b", 20)
            val sum = mgr.sumAll(listOf(c1, c2))
            assertEquals(30, sum)
        }
    }

    @Test
    fun `List Object param - empty list`() {
        CalculatorManager().use { mgr ->
            assertEquals(0, mgr.sumAll(emptyList()))
        }
    }

    @Test
    fun `nullable List Object return - non-null`() {
        CalculatorManager().use { mgr ->
            mgr.create("x", 5)
            val all = mgr.getAllOrNull()
            assertTrue(all != null)
            assertEquals(1, all!!.size)
            assertEquals(5, all[0].current)
            all.forEach { it.close() }
        }
    }

    @Test
    fun `nullable List Object return - null`() {
        CalculatorManager().use { mgr ->
            assertNull(mgr.getAllOrNull())
        }
    }

    // ── Callback: List<Int> ─────────────────────────────────────────────────

    @Test
    fun `callback List Int - onScoresReady`() {
        Calculator(10).use { calc ->
            var received = emptyList<Int>()
            calc.onScoresReady { scores -> received = scores }
            assertEquals(listOf(10, 20, 30), received)
        }
    }

    @Test
    fun `callback List Int - zero`() {
        Calculator(0).use { calc ->
            var received = emptyList<Int>()
            calc.onScoresReady { received = it }
            assertEquals(listOf(0, 0, 0), received)
        }
    }

    // ── Callback: List<String> ──────────────────────────────────────────────

    @Test
    fun `callback List String - onLabelsReady`() {
        Calculator(5).use { calc ->
            calc.label = "test"
            var received = emptyList<String>()
            calc.onLabelsReady { received = it }
            assertEquals(listOf("test", "item_5"), received)
        }
    }

    @Test
    fun `callback List String - default label`() {
        Calculator(0).use { calc ->
            var received = emptyList<String>()
            calc.onLabelsReady { received = it }
            assertEquals(listOf("default", "item_0"), received)
        }
    }

    // ── Callback: List<Enum> ────────────────────────────────────────────────

    @Test
    fun `callback List Enum - onOpsReady`() {
        Calculator(0).use { calc ->
            var received = emptyList<Operation>()
            calc.onOpsReady { received = it }
            assertEquals(listOf(Operation.ADD, Operation.SUBTRACT, Operation.MULTIPLY), received)
        }
    }

    // ── Callback: List<Boolean> ─────────────────────────────────────────────

    @Test
    fun `callback List Boolean - onFlagsReady positive`() {
        Calculator(4).use { calc ->
            var received = emptyList<Boolean>()
            calc.onFlagsReady { received = it }
            assertEquals(listOf(true, true), received) // 4 > 0, 4 % 2 == 0
        }
    }

    @Test
    fun `callback List Boolean - onFlagsReady zero`() {
        Calculator(0).use { calc ->
            var received = emptyList<Boolean>()
            calc.onFlagsReady { received = it }
            assertEquals(listOf(false, true), received) // 0 > 0 = false, 0 % 2 == 0 = true
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EDGE-CASE BATTERY: Collections
    // ══════════════════════════════════════════════════════════════════════════

    // ── Singleton / Empty List ───────────────────────────────────────────────

    @Test
    fun `edge - singleton list return`() {
        Calculator(42).use { calc ->
            assertEquals(listOf(42), calc.getSingletonList())
        }
    }

    @Test
    fun `edge - empty list return`() {
        Calculator(99).use { calc ->
            assertEquals(emptyList<Int>(), calc.getEmptyList())
        }
    }

    @Test
    fun `edge - large list 500 elements`() {
        Calculator(2).use { calc ->
            val result = calc.getLargeList(500)
            assertEquals(500, result.size)
            assertEquals(0, result[0])
            assertEquals(2, result[1])
            assertEquals(998, result[499])
        }
    }

    @Test
    fun `edge - large list 2000 elements`() {
        Calculator(1).use { calc ->
            val result = calc.getLargeList(2000)
            assertEquals(2000, result.size)
            assertEquals(1999, result[1999])
        }
    }

    @Test
    fun `edge - getLargeList zero accumulator`() {
        Calculator(0).use { calc ->
            val result = calc.getLargeList(100)
            assertTrue(result.all { it == 0 })
        }
    }

    @Test
    fun `edge - getLargeList size 1`() {
        Calculator(7).use { calc ->
            assertEquals(listOf(0), calc.getLargeList(1))
        }
    }

    @Test
    fun `edge - getLargeList size 0`() {
        Calculator(7).use { calc ->
            assertEquals(emptyList<Int>(), calc.getLargeList(0))
        }
    }

    // ── List reversal / transform ───────────────────────────────────────────

    @Test
    fun `edge - reverseList basic`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(3, 2, 1), calc.reverseList(listOf(1, 2, 3)))
            assertEquals(3, calc.current) // first of reversed
        }
    }

    @Test
    fun `edge - reverseList single`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(42), calc.reverseList(listOf(42)))
        }
    }

    @Test
    fun `edge - reverseList empty`() {
        Calculator(99).use { calc ->
            assertEquals(emptyList<Int>(), calc.reverseList(emptyList()))
            assertEquals(0, calc.current) // firstOrNull = null → 0
        }
    }

    @Test
    fun `edge - reverseList preserves all elements`() {
        Calculator(0).use { calc ->
            val input = List(100) { it }
            val result = calc.reverseList(input)
            assertEquals(input.reversed(), result)
        }
    }

    @Test
    fun `edge - filterPositive mixed`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(1, 3, 5), calc.filterPositive(listOf(-2, 1, -4, 3, 0, 5)))
        }
    }

    @Test
    fun `edge - filterPositive all negative`() {
        Calculator(0).use { calc ->
            assertEquals(emptyList<Int>(), calc.filterPositive(listOf(-1, -2, -3)))
        }
    }

    @Test
    fun `edge - filterPositive all positive`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(1, 2, 3), calc.filterPositive(listOf(1, 2, 3)))
        }
    }

    @Test
    fun `edge - filterPositive empty`() {
        Calculator(0).use { calc ->
            assertEquals(emptyList<Int>(), calc.filterPositive(emptyList()))
        }
    }

    // ── String list edge cases ──────────────────────────────────────────────

    @Test
    fun `edge - empty string list return`() {
        Calculator(0).use { calc ->
            assertEquals(emptyList<String>(), calc.getEmptyStringList())
        }
    }

    @Test
    fun `edge - repeatLabel generates correct count`() {
        Calculator(0).use { calc ->
            calc.label = "test"
            val result = calc.repeatLabel(5)
            assertEquals(5, result.size)
            assertEquals("test_0", result[0])
            assertEquals("test_4", result[4])
        }
    }

    @Test
    fun `edge - repeatLabel zero count`() {
        Calculator(0).use { calc ->
            assertEquals(emptyList<String>(), calc.repeatLabel(0))
        }
    }

    @Test
    fun `edge - repeatLabel one count`() {
        Calculator(0).use { calc ->
            calc.label = "x"
            assertEquals(listOf("x_0"), calc.repeatLabel(1))
        }
    }

    @Test
    fun `edge - repeatLabel large count`() {
        Calculator(0).use { calc ->
            calc.label = "item"
            val result = calc.repeatLabel(200)
            assertEquals(200, result.size)
            assertEquals("item_199", result[199])
        }
    }

    @Test
    fun `edge - transformStrings uppercase`() {
        Calculator(0).use { calc ->
            assertEquals(listOf("HELLO", "WORLD"), calc.transformStrings(listOf("hello", "world")))
        }
    }

    @Test
    fun `edge - transformStrings empty`() {
        Calculator(0).use { calc ->
            assertEquals(emptyList<String>(), calc.transformStrings(emptyList()))
        }
    }

    @Test
    fun `edge - transformStrings unicode`() {
        Calculator(0).use { calc ->
            assertEquals(listOf("CAFÉ", "NAÏVE"), calc.transformStrings(listOf("café", "naïve")))
        }
    }

    @Test
    fun `edge - transformStrings single char`() {
        Calculator(0).use { calc ->
            assertEquals(listOf("A"), calc.transformStrings(listOf("a")))
        }
    }

    @Test
    fun `edge - joinLabels many items`() {
        Calculator(0).use { calc ->
            val items = List(50) { "item$it" }
            val result = calc.joinLabels(items)
            assertEquals(items.joinToString(", "), result)
        }
    }

    // ── Map edge cases ──────────────────────────────────────────────────────

    @Test
    fun `edge - singleton map return`() {
        Calculator(42).use { calc ->
            val map = calc.getSingletonMap()
            assertEquals(1, map.size)
            assertEquals(42, map["only"])
        }
    }

    @Test
    fun `edge - empty map return`() {
        Calculator(0).use { calc ->
            assertEquals(emptyMap<String, Int>(), calc.getEmptyMap())
        }
    }

    @Test
    fun `edge - mergeMapValues basic`() {
        Calculator(0).use { calc ->
            val result = calc.mergeMapValues(mapOf("a" to 1), mapOf("b" to 2))
            assertEquals(mapOf("a" to 1, "b" to 2), result)
        }
    }

    @Test
    fun `edge - mergeMapValues overlap`() {
        Calculator(0).use { calc ->
            val result = calc.mergeMapValues(mapOf("a" to 1, "b" to 2), mapOf("b" to 99, "c" to 3))
            assertEquals(3, result.size)
            assertEquals(99, result["b"]) // second map wins
            assertEquals(3, result["c"])
        }
    }

    @Test
    fun `edge - mergeMapValues both empty`() {
        Calculator(0).use { calc ->
            assertEquals(emptyMap<String, Int>(), calc.mergeMapValues(emptyMap(), emptyMap()))
        }
    }

    @Test
    fun `edge - mergeMapValues one empty`() {
        Calculator(0).use { calc ->
            val a = mapOf("x" to 42)
            assertEquals(a, calc.mergeMapValues(a, emptyMap()))
            assertEquals(a, calc.mergeMapValues(emptyMap(), a))
        }
    }

    // ── Set edge cases ──────────────────────────────────────────────────────

    @Test
    fun `edge - empty set return`() {
        Calculator(0).use { calc ->
            assertEquals(emptySet<Int>(), calc.getEmptySet())
        }
    }

    @Test
    fun `edge - intersectSets overlap`() {
        Calculator(0).use { calc ->
            assertEquals(setOf(2, 3), calc.intersectSets(setOf(1, 2, 3), setOf(2, 3, 4)))
        }
    }

    @Test
    fun `edge - intersectSets no overlap`() {
        Calculator(0).use { calc ->
            assertEquals(emptySet<Int>(), calc.intersectSets(setOf(1, 2), setOf(3, 4)))
        }
    }

    @Test
    fun `edge - intersectSets same`() {
        Calculator(0).use { calc ->
            assertEquals(setOf(1, 2, 3), calc.intersectSets(setOf(1, 2, 3), setOf(1, 2, 3)))
        }
    }

    @Test
    fun `edge - intersectSets one empty`() {
        Calculator(0).use { calc ->
            assertEquals(emptySet<Int>(), calc.intersectSets(setOf(1, 2), emptySet()))
        }
    }

    @Test
    fun `edge - intersectSets both empty`() {
        Calculator(0).use { calc ->
            assertEquals(emptySet<Int>(), calc.intersectSets(emptySet(), emptySet()))
        }
    }

    // ── Callback edge cases ─────────────────────────────────────────────────

    @Test
    fun `edge - callback large list 500`() {
        Calculator(0).use { calc ->
            var received = emptyList<Int>()
            calc.onLargeListReady(500) { received = it }
            assertEquals(500, received.size)
            assertEquals(0, received[0])
            assertEquals(499, received[499])
        }
    }

    @Test
    fun `edge - callback large list 1000`() {
        Calculator(0).use { calc ->
            var received = emptyList<Int>()
            calc.onLargeListReady(1000) { received = it }
            assertEquals(1000, received.size)
        }
    }

    @Test
    fun `edge - callback empty list`() {
        Calculator(0).use { calc ->
            var received: List<Int>? = null
            calc.onEmptyListReady { received = it }
            assertEquals(emptyList<Int>(), received)
        }
    }

    @Test
    fun `edge - callback list int negative accumulator`() {
        Calculator(-7).use { calc ->
            var received = emptyList<Int>()
            calc.onScoresReady { received = it }
            assertEquals(listOf(-7, -14, -21), received)
        }
    }

    @Test
    fun `edge - multiple callback invocations`() {
        Calculator(1).use { calc ->
            val all = mutableListOf<List<Int>>()
            calc.onScoresReady { all.add(it) }
            calc.add(9)
            calc.onScoresReady { all.add(it) }
            assertEquals(2, all.size)
            assertEquals(listOf(1, 2, 3), all[0])
            assertEquals(listOf(10, 20, 30), all[1])
        }
    }

    // ── Nullable collection edge cases ──────────────────────────────────────

    @Test
    fun `edge - nullable List String return - non-null then null`() {
        Calculator(0).use { calc ->
            calc.label = "hello"
            val r1 = calc.getScoresOrNullByLabel()
            assertEquals(listOf("hello"), r1)
            calc.label = ""
            assertNull(calc.getScoresOrNullByLabel())
        }
    }

    @Test
    fun `edge - nullable Set Int return - non-null`() {
        Calculator(5).use { calc ->
            val result = calc.getNullableSetByAccum()
            assertEquals(setOf(5, 6), result)
        }
    }

    @Test
    fun `edge - nullable Set Int return - null`() {
        Calculator(-1).use { calc ->
            assertNull(calc.getNullableSetByAccum())
        }
    }

    @Test
    fun `edge - nullable Set Int return - zero is non-null`() {
        Calculator(0).use { calc ->
            val result = calc.getNullableSetByAccum()
            assertEquals(setOf(0, 1), result)
        }
    }

    @Test
    fun `edge - nullable Map String String return - non-null`() {
        Calculator(0).use { calc ->
            calc.label = "test"
            val result = calc.getNullableMapByLabel()
            assertEquals(mapOf("label" to "test"), result)
        }
    }

    @Test
    fun `edge - nullable Map String String return - null`() {
        Calculator(0).use { calc ->
            assertNull(calc.getNullableMapByLabel())
        }
    }

    @Test
    fun `edge - nullable List Int param - large list`() {
        Calculator(0).use { calc ->
            val result = calc.sumAllOrNull(List(500) { 1 })
            assertEquals(500, result)
        }
    }

    @Test
    fun `edge - nullable List Int param - alternating null and non-null`() {
        Calculator(0).use { calc ->
            assertEquals(-1, calc.sumAllOrNull(null))
            assertEquals(10, calc.sumAllOrNull(listOf(10)))
            assertEquals(-1, calc.sumAllOrNull(null))
            assertEquals(5, calc.sumAllOrNull(listOf(2, 3)))
        }
    }

    // ── Cross-feature: collection after state mutation ───────────────────────

    @Test
    fun `cross - collection return reflects state changes`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(0, 0, 0), calc.getScores())
            calc.add(10)
            assertEquals(listOf(10, 20, 30), calc.getScores())
            calc.multiply(2)
            assertEquals(listOf(20, 40, 60), calc.getScores())
        }
    }

    @Test
    fun `cross - collection param mutates state then return`() {
        Calculator(0).use { calc ->
            calc.sumAll(listOf(5, 10, 15))
            assertEquals(30, calc.current)
            val weights = calc.getWeights()
            assertEquals(30.0, weights[0], 0.001)
        }
    }

    @Test
    fun `cross - map return after label and scale change`() {
        Calculator(0).use { calc ->
            calc.add(100)
            calc.scale = 5.0
            val meta = calc.getMetadata()
            assertEquals(100, meta["current"])
            assertEquals(5, meta["scale"])
        }
    }

    @Test
    fun `cross - set return after multiple operations`() {
        Calculator(0).use { calc ->
            calc.add(12345)
            val digits = calc.getUniqueDigits()
            assertEquals(setOf(1, 2, 3, 4, 5), digits)
        }
    }

    @Test
    fun `cross - callback list after state change`() {
        Calculator(0).use { calc ->
            calc.add(7)
            var received = emptyList<Int>()
            calc.onScoresReady { received = it }
            assertEquals(listOf(7, 14, 21), received)
        }
    }

    @Test
    fun `cross - List Object with state mutation`() {
        CalculatorManager().use { mgr ->
            val c1 = mgr.create("a", 10)
            val c2 = mgr.create("b", 20)
            c1.add(5) // c1 now 15
            val all = mgr.getAll()
            val currents = all.map { it.current }.sorted()
            assertEquals(listOf(15, 20), currents)
            all.forEach { it.close() }
        }
    }

    @Test
    fun `cross - nullable collection transition`() {
        Calculator(0).use { calc ->
            // accumulator = 0 → null
            assertNull(calc.getScoresOrNull())
            calc.add(5)
            // accumulator = 5 → non-null
            assertEquals(listOf(5, 10), calc.getScoresOrNull())
            calc.subtract(5)
            // back to 0 → null again
            assertNull(calc.getScoresOrNull())
        }
    }

    // ── Callback: Map param ─────────────────────────────────────────────────

    @Test
    fun `callback Map String Int param - onMetadataReady`() {
        Calculator(10).use { calc ->
            var received = emptyMap<String, Int>()
            calc.onMetadataReady { received = it }
            assertEquals(10, received["current"])
            assertEquals(20, received["doubled"])
        }
    }

    @Test
    fun `callback Map String Int param - zero`() {
        Calculator(0).use { calc ->
            var received = emptyMap<String, Int>()
            calc.onMetadataReady { received = it }
            assertEquals(0, received["current"])
            assertEquals(0, received["doubled"])
        }
    }

    // ── Callback: collection return ─────────────────────────────────────────

    @Test
    fun `callback return List Int - getTransformedScores`() {
        Calculator(5).use { calc ->
            val result = calc.getTransformedScores { v -> listOf(v, v * 10, v * 100) }
            assertEquals(listOf(5, 50, 500), result)
        }
    }

    @Test
    fun `callback return List Int - empty`() {
        Calculator(0).use { calc ->
            val result = calc.getTransformedScores { emptyList() }
            assertEquals(emptyList<Int>(), result)
        }
    }

    @Test
    fun `callback return List Int - single`() {
        Calculator(42).use { calc ->
            val result = calc.getTransformedScores { listOf(it * 2) }
            assertEquals(listOf(84), result)
        }
    }

    @Test
    fun `callback return List String - getComputedLabels`() {
        Calculator(3).use { calc ->
            val result = calc.getComputedLabels { v -> listOf("val=$v", "doubled=${v * 2}") }
            assertEquals(listOf("val=3", "doubled=6"), result)
        }
    }

    @Test
    fun `callback return List String - empty`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedLabels { emptyList() }
            assertEquals(emptyList<String>(), result)
        }
    }

    @Test
    fun `callback return Map String Int - getComputedMap`() {
        Calculator(7).use { calc ->
            val result = calc.getComputedMap { v -> mapOf("input" to v, "squared" to v * v) }
            assertEquals(7, result["input"])
            assertEquals(49, result["squared"])
        }
    }

    @Test
    fun `callback return Map String Int - empty`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedMap { emptyMap() }
            assertEquals(emptyMap<String, Int>(), result)
        }
    }
}
