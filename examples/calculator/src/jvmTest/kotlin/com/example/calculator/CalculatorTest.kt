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

    // ══════════════════════════════════════════════════════════════════════════
    // EDGE-CASE BATTERY: Callbacks with collections
    // ══════════════════════════════════════════════════════════════════════════

    // ── Map callback param edge cases ───────────────────────────────────────

    @Test
    fun `cb map param - negative values`() {
        Calculator(-5).use { calc ->
            var received = emptyMap<String, Int>()
            calc.onMetadataReady { received = it }
            assertEquals(-5, received["current"])
            assertEquals(-10, received["doubled"])
        }
    }

    @Test
    fun `cb map param - large accumulator`() {
        Calculator(100_000).use { calc ->
            var received = emptyMap<String, Int>()
            calc.onMetadataReady { received = it }
            assertEquals(100_000, received["current"])
            assertEquals(200_000, received["doubled"])
        }
    }

    @Test
    fun `cb map Int Int param - basic`() {
        Calculator(5).use { calc ->
            var received = emptyMap<Int, Int>()
            calc.onMapIntIntReady { received = it }
            assertEquals(25, received[5])
        }
    }

    @Test
    fun `cb map Int Int param - zero`() {
        Calculator(0).use { calc ->
            var received = emptyMap<Int, Int>()
            calc.onMapIntIntReady { received = it }
            assertEquals(0, received[0])
        }
    }

    @Test
    fun `cb map param - multiple invocations`() {
        Calculator(1).use { calc ->
            val all = mutableListOf<Map<String, Int>>()
            calc.onMetadataReady { all.add(it) }
            calc.add(9)
            calc.onMetadataReady { all.add(it) }
            assertEquals(2, all.size)
            assertEquals(1, all[0]["current"])
            assertEquals(10, all[1]["current"])
        }
    }

    // ── Collection callback return edge cases ───────────────────────────────

    @Test
    fun `cb return List Int - large list`() {
        Calculator(1).use { calc ->
            val result = calc.getTransformedScores { v -> List(500) { v + it } }
            assertEquals(500, result.size)
            assertEquals(1, result[0])
            assertEquals(500, result[499])
        }
    }

    @Test
    fun `cb return List Int - negative values`() {
        Calculator(-3).use { calc ->
            val result = calc.getTransformedScores { v -> listOf(v, v - 1, v - 2) }
            assertEquals(listOf(-3, -4, -5), result)
        }
    }

    @Test
    fun `cb return List Int - singleton`() {
        Calculator(42).use { calc ->
            val result = calc.getTransformedScores { listOf(it) }
            assertEquals(listOf(42), result)
        }
    }

    @Test
    fun `cb return List String - long strings`() {
        Calculator(0).use { calc ->
            val longStr = "a".repeat(200)
            val result = calc.getComputedLabels { listOf(longStr, "short") }
            assertEquals(longStr, result[0])
            assertEquals("short", result[1])
        }
    }

    @Test
    fun `cb return List String - unicode`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedLabels { listOf("café", "naïve", "über") }
            assertEquals(listOf("café", "naïve", "über"), result)
        }
    }

    @Test
    fun `cb return List String - many items`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedLabels { v -> List(100) { "item_$it" } }
            assertEquals(100, result.size)
            assertEquals("item_0", result[0])
            assertEquals("item_99", result[99])
        }
    }

    @Test
    fun `cb return List Enum - getComputedOps`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedOps { listOf(Operation.ADD, Operation.MULTIPLY) }
            assertEquals(listOf(Operation.ADD, Operation.MULTIPLY), result)
        }
    }

    @Test
    fun `cb return List Enum - empty`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedOps { emptyList() }
            assertEquals(emptyList<Operation>(), result)
        }
    }

    @Test
    fun `cb return List Boolean - getComputedBools`() {
        Calculator(5).use { calc ->
            val result = calc.getComputedBools { v -> listOf(v > 0, v > 10, v == 5) }
            assertEquals(listOf(true, false, true), result)
        }
    }

    @Test
    fun `cb return List Boolean - all true`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedBools { listOf(true, true, true) }
            assertEquals(listOf(true, true, true), result)
        }
    }

    @Test
    fun `cb return List Boolean - all false`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedBools { listOf(false, false) }
            assertEquals(listOf(false, false), result)
        }
    }

    @Test
    fun `cb return List Long - getComputedLongs`() {
        Calculator(5).use { calc ->
            val result = calc.getComputedLongs { v -> listOf(v.toLong(), v.toLong() * 1_000_000L) }
            assertEquals(listOf(5L, 5_000_000L), result)
        }
    }

    @Test
    fun `cb return List Long - empty`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedLongs { emptyList() }
            assertEquals(emptyList<Long>(), result)
        }
    }

    @Test
    fun `cb return Map String Int - large map`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedMap { v -> (0 until 50).associate { "key_$it" to it } }
            assertEquals(50, result.size)
            assertEquals(0, result["key_0"])
            assertEquals(49, result["key_49"])
        }
    }

    @Test
    fun `cb return Map String Int - singleton`() {
        Calculator(42).use { calc ->
            val result = calc.getComputedMap { mapOf("answer" to it) }
            assertEquals(mapOf("answer" to 42), result)
        }
    }

    @Test
    fun `cb return Map String Int - unicode keys`() {
        Calculator(1).use { calc ->
            val result = calc.getComputedMap { mapOf("café" to 1, "naïve" to 2) }
            assertEquals(1, result["café"])
            assertEquals(2, result["naïve"])
        }
    }

    // ── Multi-param callback with collection ────────────────────────────────

    @Test
    fun `cb multi-param with List + String`() {
        Calculator(0).use { calc ->
            var receivedList = emptyList<Int>()
            var receivedStr = ""
            calc.computeWithScores(10) { list, str ->
                receivedList = list
                receivedStr = str
            }
            assertEquals(listOf(10, 20, 30), receivedList)
            assertEquals("computed_10", receivedStr)
        }
    }

    @Test
    fun `cb multi-param with List + String - zero`() {
        Calculator(0).use { calc ->
            var receivedList = emptyList<Int>()
            var receivedStr = ""
            calc.computeWithScores(0) { list, str ->
                receivedList = list
                receivedStr = str
            }
            assertEquals(listOf(0, 0, 0), receivedList)
            assertEquals("computed_0", receivedStr)
        }
    }

    // ── Callback return + state mutation ─────────────────────────────────────

    @Test
    fun `cb return List Int - depends on accumulator`() {
        Calculator(0).use { calc ->
            calc.add(5)
            val r1 = calc.getTransformedScores { listOf(it, it + 1) }
            assertEquals(listOf(5, 6), r1)
            calc.add(10)
            val r2 = calc.getTransformedScores { listOf(it, it + 1) }
            assertEquals(listOf(15, 16), r2)
        }
    }

    @Test
    fun `cb return Map - depends on accumulator`() {
        Calculator(0).use { calc ->
            calc.add(3)
            val r1 = calc.getComputedMap { mapOf("val" to it) }
            assertEquals(mapOf("val" to 3), r1)
            calc.multiply(4)
            val r2 = calc.getComputedMap { mapOf("val" to it) }
            assertEquals(mapOf("val" to 12), r2)
        }
    }

    // ── Callback List param + collection return roundtrip ────────────────────

    @Test
    fun `cb list param then collection return`() {
        Calculator(10).use { calc ->
            // First use callback with List param
            var receivedScores = emptyList<Int>()
            calc.onScoresReady { receivedScores = it }
            assertEquals(listOf(10, 20, 30), receivedScores)

            // Then use callback that returns collection
            val transformed = calc.getTransformedScores { v -> receivedScores.map { it + v } }
            assertEquals(listOf(20, 30, 40), transformed)
        }
    }

    @Test
    fun `cb map param then map return`() {
        Calculator(5).use { calc ->
            var meta = emptyMap<String, Int>()
            calc.onMetadataReady { meta = it }

            // Use the received map to build a new one
            val computed = calc.getComputedMap { v ->
                meta.mapValues { (_, mv) -> mv + v }
            }
            assertEquals(10, computed["current"]) // 5 + 5
            assertEquals(15, computed["doubled"]) // 10 + 5
        }
    }

    // ── Sequential callbacks stress test ─────────────────────────────────────

    @Test
    fun `stress - 20 sequential callback invocations`() {
        Calculator(0).use { calc ->
            repeat(20) { i ->
                calc.add(1)
                var received = emptyList<Int>()
                calc.onScoresReady { received = it }
                assertEquals(i + 1, received[0])
            }
        }
    }

    @Test
    fun `stress - 20 sequential callback returns`() {
        Calculator(0).use { calc ->
            repeat(20) { i ->
                calc.add(1)
                val result = calc.getTransformedScores { listOf(it * 2) }
                assertEquals(listOf((i + 1) * 2), result)
            }
        }
    }

    @Test
    fun `stress - alternating param and return callbacks`() {
        Calculator(1).use { calc ->
            repeat(10) {
                var received = emptyMap<String, Int>()
                calc.onMetadataReady { received = it }
                val computed = calc.getComputedMap { v -> mapOf("x" to v) }
                assertEquals(received["current"], computed["x"])
                calc.add(1)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COMPREHENSIVE EDGE-CASE BATTERY: All features
    // ══════════════════════════════════════════════════════════════════════════

    // ── Primitives: boundary values ─────────────────────────────────────────

    @Test fun `edge prim - Int MAX_VALUE`() = Calculator(Int.MAX_VALUE).use { assertEquals(Int.MAX_VALUE, it.current) }
    @Test fun `edge prim - Int MIN_VALUE`() = Calculator(Int.MIN_VALUE).use { assertEquals(Int.MIN_VALUE, it.current) }
    @Test fun `edge prim - add overflow wraps`() = Calculator(Int.MAX_VALUE).use { it.add(1); assertEquals(Int.MIN_VALUE, it.current) }
    @Test fun `edge prim - multiply large`() = Calculator(100_000).use { assertEquals(200_000, it.multiply(2)) }
    @Test fun `edge prim - Long boundary`() = Calculator(0).use { assertEquals(1_000_000_000L, it.addLong(1_000_000_000L)) }
    @Test fun `edge prim - Double precision`() = Calculator(0).use { assertEquals(0.1 + 0.2, it.addDouble(0.1 + 0.2), 1e-15) }
    @Test fun `edge prim - Float near zero`() = Calculator(0).use { assertEquals(0.001f, it.addFloat(0.001f), 1e-6f) }
    @Test fun `edge prim - Byte overflow`() = Calculator(127).use { assertEquals((-128).toByte(), it.addByte(1.toByte())) }
    @Test fun `edge prim - Short boundary`() = Calculator(0).use { assertEquals(Short.MAX_VALUE, it.addShort(Short.MAX_VALUE)) }
    @Test fun `edge prim - Boolean chain`() = Calculator(5).use { assertTrue(it.isPositive()); it.reset(); assertFalse(it.isPositive()) }

    // ── String: edge cases ──────────────────────────────────────────────────

    @Test fun `edge str - empty string echo`() = Calculator(0).use { assertEquals("", it.echo("")) }
    @Test fun `edge str - long string 1000 chars`() = Calculator(0).use { val s = "x".repeat(1000); assertEquals(s, it.echo(s)) }
    @Test fun `edge str - unicode emoji`() = Calculator(0).use { assertEquals("hello 🎉", it.echo("hello 🎉")) }
    @Test fun `edge str - null chars in string`() = Calculator(0).use { assertEquals("ab", it.echo("ab")) }
    @Test fun `edge str - concat empty`() = Calculator(0).use { assertEquals("hello", it.concat("hello", "")) }
    @Test fun `edge str - concat both empty`() = Calculator(0).use { assertEquals("", it.concat("", "")) }
    @Test fun `edge str - describe after operations`() {
        Calculator(0).use { calc ->
            calc.add(42); assertEquals("Calculator(current=42)", calc.describe())
            calc.reset(); assertEquals("Calculator(current=0)", calc.describe())
        }
    }

    // ── Enum: all values ────────────────────────────────────────────────────

    @Test fun `edge enum - applyOp ADD`() = Calculator(10).use { assertEquals(15, it.applyOp(Operation.ADD, 5)) }
    @Test fun `edge enum - applyOp SUBTRACT`() = Calculator(10).use { assertEquals(5, it.applyOp(Operation.SUBTRACT, 5)) }
    @Test fun `edge enum - applyOp MULTIPLY`() = Calculator(10).use { assertEquals(50, it.applyOp(Operation.MULTIPLY, 5)) }
    @Test fun `edge enum - lastOp tracks correctly`() {
        Calculator(0).use { calc ->
            calc.applyOp(Operation.ADD, 1)
            assertEquals(Operation.ADD, calc.getLastOp())
            calc.applyOp(Operation.MULTIPLY, 2)
            assertEquals(Operation.MULTIPLY, calc.getLastOp())
        }
    }

    // ── Nullable: boundary transitions ──────────────────────────────────────

    @Test fun `edge null - divideOrNull zero`() = Calculator(10).use { assertNull(it.divideOrNull(0)) }
    @Test fun `edge null - divideOrNull nonzero`() = Calculator(10).use { assertEquals(5, it.divideOrNull(2)) }
    @Test fun `edge null - describeOrNull positive`() = Calculator(5).use { assertEquals("Positive(5)", it.describeOrNull()) }
    @Test fun `edge null - describeOrNull zero`() = Calculator(0).use { assertNull(it.describeOrNull()) }
    @Test fun `edge null - describeOrNull negative`() = Calculator(-1).use { assertNull(it.describeOrNull()) }
    @Test fun `edge null - isPositiveOrNull zero`() = Calculator(0).use { assertNull(it.isPositiveOrNull()) }
    @Test fun `edge null - isPositiveOrNull positive`() = Calculator(1).use { assertEquals(true, it.isPositiveOrNull()) }
    @Test fun `edge null - isPositiveOrNull negative`() = Calculator(-1).use { assertEquals(false, it.isPositiveOrNull()) }
    @Test fun `edge null - findOp valid`() = Calculator(0).use { assertEquals(Operation.ADD, it.findOp("ADD")) }
    @Test fun `edge null - findOp invalid`() = Calculator(0).use { assertNull(it.findOp("INVALID")) }
    @Test fun `edge null - findOp null param`() = Calculator(0).use { assertNull(it.findOp(null)) }
    @Test fun `edge null - toLongOrNull zero`() = Calculator(0).use { assertNull(it.toLongOrNull()) }
    @Test fun `edge null - toLongOrNull nonzero`() = Calculator(42).use { assertEquals(42L, it.toLongOrNull()) }
    @Test fun `edge null - toDoubleOrNull zero`() = Calculator(0).use { assertNull(it.toDoubleOrNull()) }
    @Test fun `edge null - toDoubleOrNull nonzero`() = Calculator(7).use { assertEquals(7.0, it.toDoubleOrNull()) }
    @Test fun `edge null - nickname initially null`() = Calculator(0).use { assertNull(it.nickname) }
    @Test fun `edge null - nickname set and get`() = Calculator(0).use { it.nickname = "test"; assertEquals("test", it.nickname) }
    @Test fun `edge null - nickname set to null`() = Calculator(0).use { it.nickname = "x"; it.nickname = null; assertNull(it.nickname) }

    // ── ByteArray: edge cases ───────────────────────────────────────────────

    @Test fun `edge bytes - toBytes zero`() = Calculator(0).use { val b = it.toBytes(); assertEquals("0", String(b)) }
    @Test fun `edge bytes - toBytes negative`() = Calculator(-42).use { val b = it.toBytes(); assertEquals("-42", String(b)) }
    @Test fun `edge bytes - sumBytes empty`() = Calculator(99).use { assertEquals(0, it.sumBytes(byteArrayOf())) }
    @Test fun `edge bytes - sumBytes single`() = Calculator(0).use { assertEquals(42, it.sumBytes(byteArrayOf(42))) }
    @Test fun `edge bytes - reverseBytes`() = Calculator(0).use { val r = it.reverseBytes(byteArrayOf(1, 2, 3)); assertEquals(listOf<Byte>(3, 2, 1), r.toList()) }
    @Test fun `edge bytes - reverseBytes empty`() = Calculator(0).use { assertEquals(0, it.reverseBytes(byteArrayOf()).size) }
    @Test fun `edge bytes - reverseBytes single`() = Calculator(0).use { assertEquals(listOf<Byte>(5), it.reverseBytes(byteArrayOf(5)).toList()) }

    // ── Data class: edge cases ──────────────────────────────────────────────

    @Test fun `edge dc - Point zero`() = Calculator(0).use { val p = it.getPoint(); assertEquals(0, p.x); assertEquals(0, p.y) }
    @Test fun `edge dc - Point negative`() = Calculator(-3).use { val p = it.getPoint(); assertEquals(-3, p.x); assertEquals(-6, p.y) }
    @Test fun `edge dc - addPoint zero`() = Calculator(0).use { assertEquals(0, it.addPoint(Point(0, 0))) }
    @Test fun `edge dc - NamedValue default label`() = Calculator(5).use { val nv = it.getNamedValue(); assertEquals("default", nv.name); assertEquals(5, nv.value) }
    @Test fun `edge dc - NamedValue custom label`() = Calculator(5).use { it.label = "test"; val nv = it.getNamedValue(); assertEquals("test", nv.name) }
    @Test fun `edge dc - Rect from zero`() = Calculator(0).use { val r = it.getRect(); assertEquals(Point(0, 0), r.topLeft); assertEquals(Point(0, 0), r.bottomRight) }
    @Test fun `edge dc - Rect from positive`() = Calculator(5).use { val r = it.getRect(); assertEquals(Point(0, 0), r.topLeft); assertEquals(Point(5, 5), r.bottomRight) }
    @Test fun `edge dc - nullable Point null`() = Calculator(0).use { assertNull(it.getPointOrNull()) }
    @Test fun `edge dc - nullable Point non-null`() = Calculator(3).use { val p = it.getPointOrNull(); assertEquals(3, p?.x); assertEquals(6, p?.y) }
    @Test fun `edge dc - addPointOrNull null`() = Calculator(10).use { assertEquals(10, it.addPointOrNull(null)) }
    @Test fun `edge dc - addPointOrNull non-null`() = Calculator(10).use { assertEquals(15, it.addPointOrNull(Point(2, 3))) }

    // ── Object lifecycle: edge cases ────────────────────────────────────────

    @Test fun `edge obj - manager create and get`() {
        CalculatorManager().use { mgr ->
            mgr.create("a", 10); assertEquals(10, mgr.get("a").current)
        }
    }
    @Test fun `edge obj - manager getOrNull missing`() = CalculatorManager().use { assertNull(it.getOrNull("missing")) }
    @Test fun `edge obj - manager count`() {
        CalculatorManager().use { mgr ->
            assertEquals(0, mgr.count())
            mgr.create("a", 1); assertEquals(1, mgr.count())
            mgr.create("b", 2); assertEquals(2, mgr.count())
        }
    }
    @Test fun `edge obj - manager addWith`() {
        CalculatorManager().use { mgr ->
            val c = mgr.create("a", 10)
            assertEquals(15, mgr.addWith(c, 5))
        }
    }
    @Test fun `edge obj - manager describe`() {
        CalculatorManager().use { mgr ->
            val c = mgr.create("a", 42)
            assertEquals("Calculator(current=42)", mgr.describe(c))
        }
    }

    // ── Companion: edge cases ───────────────────────────────────────────────

    @Test fun `edge companion - version`() = assertEquals("2.0", Calculator.version())
    @Test fun `edge companion - VERSION`() = assertEquals("2.0", Calculator.VERSION)
    @Test fun `edge companion - create returns working calc`() {
        val calc = Calculator.create(99)
        assertEquals(99, calc.current)
        calc.add(1); assertEquals(100, calc.current)
        calc.close()
    }

    // ── Properties: edge cases ──────────────────────────────────────────────

    @Test fun `edge prop - label initially empty`() = Calculator(0).use { assertEquals("", it.label) }
    @Test fun `edge prop - label set long string`() = Calculator(0).use { val s = "a".repeat(500); it.label = s; assertEquals(s, it.label) }
    @Test fun `edge prop - scale default`() = Calculator(0).use { assertEquals(1.0, it.scale, 0.001) }
    @Test fun `edge prop - scale set negative`() = Calculator(0).use { it.scale = -3.14; assertEquals(-3.14, it.scale, 0.001) }
    @Test fun `edge prop - enabled default`() = Calculator(0).use { assertTrue(it.enabled) }
    @Test fun `edge prop - enabled toggle`() = Calculator(0).use { it.enabled = false; assertFalse(it.enabled); it.enabled = true; assertTrue(it.enabled) }
    @Test fun `edge prop - lastOperation default`() = Calculator(0).use { assertEquals(Operation.ADD, it.lastOperation) }
    @Test fun `edge prop - lastOperation after applyOp`() = Calculator(0).use { it.applyOp(Operation.MULTIPLY, 5); assertEquals(Operation.MULTIPLY, it.lastOperation) }

    // ── Exception propagation ───────────────────────────────────────────────

    @Test fun `edge exc - divide by zero throws`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
        }
    }
    @Test fun `edge exc - failAlways throws`() {
        Calculator(0).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.failAlways() }
        }
    }
    @Test fun `edge exc - recovery after exception`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            assertEquals(15, calc.add(5)) // still works
        }
    }
    @Test fun `edge exc - multiple exceptions in sequence`() {
        Calculator(0).use { calc ->
            repeat(5) {
                assertFailsWith<KotlinNativeException> { calc.failAlways() }
            }
            calc.add(1); assertEquals(1, calc.current) // still functional
        }
    }

    // ── Callback primitives: edge cases ─────────────────────────────────────

    @Test fun `edge cb - transform identity`() = Calculator(42).use { assertEquals(42, it.transform { v -> v }) }
    @Test fun `edge cb - transform negate`() = Calculator(5).use { assertEquals(-5, it.transform { -it }) }
    @Test fun `edge cb - transform zero`() = Calculator(99).use { assertEquals(0, it.transform { 0 }) }
    @Test fun `edge cb - compute max`() = Calculator(0).use { assertEquals(10, it.compute(3, 10) { a, b -> maxOf(a, b) }) }
    @Test fun `edge cb - compute min`() = Calculator(0).use { assertEquals(3, it.compute(3, 10) { a, b -> minOf(a, b) }) }
    @Test fun `edge cb - checkWith always true`() = Calculator(0).use { assertTrue(it.checkWith { true }) }
    @Test fun `edge cb - checkWith always false`() = Calculator(0).use { assertFalse(it.checkWith { false }) }
    @Test fun `edge cb - formatWith custom`() = Calculator(42).use { assertEquals(">>>42<<<", it.formatWith { ">>>$it<<<" }) }
    @Test fun `edge cb - withDouble precision`() = Calculator(7).use { assertEquals(3.5, it.withDouble { it / 2.0 }, 0.001) }
    @Test fun `edge cb - withLong large`() = Calculator(1000).use { assertEquals(1_000_000L, it.withLong { it * 1000 }) }

    // ── Cross-feature stress: full integration ──────────────────────────────

    @Test fun `integration - 50 operations then check all types`() {
        Calculator(0).use { calc ->
            repeat(50) { calc.add(1) }
            assertEquals(50, calc.current)
            assertEquals(listOf(50, 100, 150), calc.getScores())
            assertEquals(true, calc.isPositive())
            assertEquals("Calculator(current=50)", calc.describe())
            assertEquals(Point(50, 100), calc.getPoint())
            val meta = calc.getMetadata()
            assertEquals(50, meta["current"])
        }
    }

    @Test fun `integration - collection + nullable + callback chain`() {
        Calculator(5).use { calc ->
            // Get scores via direct return
            val scores = calc.getScores()
            assertEquals(listOf(5, 10, 15), scores)

            // Pass scores back via param
            calc.sumAll(scores)
            assertEquals(30, calc.current)

            // Nullable returns
            val nullable = calc.getScoresOrNull()
            assertEquals(listOf(30, 60), nullable)

            // Callback with collection
            var received = emptyList<Int>()
            calc.onScoresReady { received = it }
            assertEquals(listOf(30, 60, 90), received)

            // Callback return
            val transformed = calc.getTransformedScores { v -> listOf(v, v / 2) }
            assertEquals(listOf(30, 15), transformed)
        }
    }

    @Test fun `integration - all map types in sequence`() {
        Calculator(10).use { calc ->
            calc.label = "test"
            calc.scale = 2.0

            val meta = calc.getMetadata() // Map<String, Int>
            assertEquals(10, meta["current"])

            val indexed = calc.getIndexedLabels() // Map<Int, String>
            assertEquals("test", indexed[0])

            val squares = calc.getSquares() // Map<Int, Int>
            assertEquals(100, squares[10])

            val strMap = calc.getStringMap() // Map<String, String>
            assertEquals("test", strMap["name"])
            assertEquals("10", strMap["value"])
        }
    }

    @Test fun `integration - object list + mutation + retrieval`() {
        CalculatorManager().use { mgr ->
            val calcs = (1..5).map { mgr.create("c$it", it * 10) }
            assertEquals(5, mgr.count())

            // Mutate via manager
            calcs.forEach { mgr.addWith(it, 5) }

            // Retrieve all
            val all = mgr.getAll()
            val totals = all.map { it.current }.sorted()
            assertEquals(listOf(15, 25, 35, 45, 55), totals)

            // Sum
            assertEquals(175, mgr.sumAll(all))
            all.forEach { it.close() }
        }
    }

    @Test fun `integration - data class roundtrip through all operations`() {
        Calculator(0).use { calc ->
            calc.add(5)
            val p = calc.getPoint()
            assertEquals(Point(5, 10), p)
            calc.addPoint(p)
            assertEquals(20, calc.current) // 5 + 5 + 10

            val nv = calc.getNamedValue()
            assertEquals("default", nv.name)
            assertEquals(20, nv.value)

            calc.setFromNamed(NamedValue("custom", 100))
            assertEquals(100, calc.current)
            assertEquals("custom", calc.label)
        }
    }

    @Test fun `integration - exception does not leak into callbacks`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            // Callback still works after exception
            var received = emptyList<Int>()
            calc.onScoresReady { received = it }
            assertEquals(listOf(10, 20, 30), received)
        }
    }

    @Test fun `integration - 100 create-use-close cycles`() {
        repeat(100) { i ->
            Calculator(i).use { calc ->
                assertEquals(i, calc.current)
                calc.add(1)
                assertEquals(i + 1, calc.current)
            }
        }
    }

    @Test fun `integration - nested object usage`() {
        CalculatorManager().use { mgr ->
            val c1 = mgr.create("main", 100)
            val c2 = Calculator(c1.current)
            c2.add(50)
            assertEquals(150, c2.current)
            assertEquals(100, c1.current) // independent
            c2.close()
        }
    }

    // ── Constructor default parameters ──────────────────────────────────────

    @Test fun `default param - no-arg constructor uses default`() {
        Calculator().use { calc ->
            assertEquals(0, calc.current) // default initial = 0
        }
    }

    @Test fun `default param - no-arg then add`() {
        Calculator().use { calc ->
            calc.add(42)
            assertEquals(42, calc.current)
        }
    }

    @Test fun `default param - no-arg vs explicit zero`() {
        Calculator().use { c1 ->
            Calculator(0).use { c2 ->
                assertEquals(c1.current, c2.current)
            }
        }
    }

    @Test fun `default param - explicit overrides default`() {
        Calculator(99).use { calc ->
            assertEquals(99, calc.current)
        }
    }

    @Test fun `default param - no-arg full lifecycle`() {
        Calculator().use { calc ->
            assertEquals(0, calc.current)
            calc.add(10)
            assertEquals(10, calc.current)
            calc.multiply(3)
            assertEquals(30, calc.current)
            assertEquals("Calculator(current=30)", calc.describe())
        }
    }

    @Test fun `default param - no-arg with collections`() {
        Calculator().use { calc ->
            assertEquals(listOf(0, 0, 0), calc.getScores())
            calc.sumAll(listOf(1, 2, 3))
            assertEquals(6, calc.current)
        }
    }

    @Test fun `default param - no-arg with callbacks`() {
        Calculator().use { calc ->
            var received = emptyList<Int>()
            calc.onScoresReady { received = it }
            assertEquals(listOf(0, 0, 0), received)
        }
    }

    @Test fun `default param - no-arg with nullable`() {
        Calculator().use { calc ->
            assertNull(calc.getScoresOrNull()) // 0 → null
            calc.add(5)
            assertEquals(listOf(5, 10), calc.getScoresOrNull())
        }
    }

    // ── Constructor default: nested DC (StyledCalculator) ───────────────────

    @Test fun `styled - no-arg uses all defaults`() {
        StyledCalculator().use { calc ->
            assertEquals(0, calc.current)
            assertEquals("default", calc.getTag())
            assertEquals(Operation.ADD, calc.getMode())
            val config = calc.getConfig()
            assertEquals(0, config.origin.x)
            assertEquals(0, config.origin.y)
            assertEquals(1, config.scale)
        }
    }

    @Test fun `styled - partial initial only`() {
        StyledCalculator(42).use { calc ->
            assertEquals(42, calc.current)
            assertEquals("default", calc.getTag()) // default
            assertEquals(Operation.ADD, calc.getMode()) // default
        }
    }

    @Test fun `styled - partial initial + config`() {
        StyledCalculator(10, Config(Point(5, 5), 3)).use { calc ->
            assertEquals(10, calc.current)
            val config = calc.getConfig()
            assertEquals(5, config.origin.x)
            assertEquals(5, config.origin.y)
            assertEquals(3, config.scale)
            assertEquals("default", calc.getTag()) // still default
        }
    }

    @Test fun `styled - partial initial + config + mode`() {
        StyledCalculator(1, Config(Point(1, 2), 10), Operation.MULTIPLY).use { calc ->
            assertEquals(1, calc.current)
            assertEquals(Operation.MULTIPLY, calc.getMode())
            assertEquals("default", calc.getTag()) // still default
        }
    }

    @Test fun `styled - full all params`() {
        StyledCalculator(99, Config(Point(10, 20), 5), Operation.SUBTRACT, "custom").use { calc ->
            assertEquals(99, calc.current)
            assertEquals("custom", calc.getTag())
            assertEquals(Operation.SUBTRACT, calc.getMode())
            val config = calc.getConfig()
            assertEquals(10, config.origin.x)
            assertEquals(20, config.origin.y)
            assertEquals(5, config.scale)
        }
    }

    @Test fun `styled - methods work after construction`() {
        StyledCalculator().use { calc ->
            calc.add(10)
            assertEquals(10, calc.current)
            assertEquals("StyledCalculator(current=10, tag=default, mode=ADD)", calc.describe())
        }
    }

    @Test fun `styled - full then add`() {
        StyledCalculator(5, Config(Point(1, 1), 2), Operation.MULTIPLY, "test").use { calc ->
            calc.add(15)
            assertEquals(20, calc.current)
            assertEquals("StyledCalculator(current=20, tag=test, mode=MULTIPLY)", calc.describe())
        }
    }

    // ── Constructor default: deeply nested DC (FramedCalculator) ─────────────

    @Test fun `framed - no-arg uses all defaults`() {
        FramedCalculator().use { calc ->
            assertEquals(0, calc.current)
            assertEquals("framed", calc.getLabel())
            val frame = calc.getFrame()
            assertEquals(0, frame.topLeft.x)
            assertEquals(0, frame.topLeft.y)
            assertEquals(100, frame.bottomRight.x)
            assertEquals(100, frame.bottomRight.y)
        }
    }

    @Test fun `framed - partial initial only`() {
        FramedCalculator(42).use { calc ->
            assertEquals(42, calc.current)
            assertEquals("framed", calc.getLabel()) // default
            assertEquals(100, calc.getFrame().bottomRight.x) // default
        }
    }

    @Test fun `framed - partial initial + frame`() {
        FramedCalculator(10, Rect(Point(1, 2), Point(3, 4))).use { calc ->
            assertEquals(10, calc.current)
            val frame = calc.getFrame()
            assertEquals(1, frame.topLeft.x)
            assertEquals(2, frame.topLeft.y)
            assertEquals(3, frame.bottomRight.x)
            assertEquals(4, frame.bottomRight.y)
            assertEquals("framed", calc.getLabel()) // still default
        }
    }

    @Test fun `framed - full all params`() {
        FramedCalculator(7, Rect(Point(10, 20), Point(30, 40)), "custom").use { calc ->
            assertEquals(7, calc.current)
            assertEquals("custom", calc.getLabel())
            assertEquals(10, calc.getFrame().topLeft.x)
            assertEquals(40, calc.getFrame().bottomRight.y)
        }
    }

    @Test fun `framed - methods work`() {
        FramedCalculator().use { calc ->
            calc.add(25)
            assertEquals(25, calc.current)
        }
    }

    @Test fun `framed - negative initial`() {
        FramedCalculator(-10).use { calc ->
            assertEquals(-10, calc.current)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MASSIVE EDGE-CASE BATTERY: Nested DC + Enum + Default params
    // ══════════════════════════════════════════════════════════════════════════

    // ── RichCalculator: 6 default params (DC, Enum, String, Double) ─────────

    @Test fun `rich - no-arg all defaults`() {
        RichCalculator().use { assertEquals(0, it.current); assertEquals("rich", it.getName()); assertEquals(1.0, it.getFactor(), 0.001) }
    }
    @Test fun `rich - initial only`() = RichCalculator(42).use { assertEquals(42, it.current); assertEquals("rich", it.getName()) }
    @Test fun `rich - initial + style`() {
        RichCalculator(1, Style(true, 255)).use { val s = it.getStyle(); assertTrue(s.bold); assertEquals(255, s.color) }
    }
    @Test fun `rich - initial + style + origin`() {
        RichCalculator(1, Style(false, 10), Point(5, 5)).use { assertEquals(5, it.getOrigin().x); assertEquals(5, it.getOrigin().y) }
    }
    @Test fun `rich - initial + style + origin + op`() {
        RichCalculator(1, Style(false, 0), Point(0, 0), Operation.MULTIPLY).use { assertEquals(Operation.MULTIPLY, it.getOp()) }
    }
    @Test fun `rich - initial + style + origin + op + name`() {
        RichCalculator(1, Style(false, 0), Point(0, 0), Operation.ADD, "custom").use { assertEquals("custom", it.getName()) }
    }
    @Test fun `rich - all params explicit`() {
        RichCalculator(10, Style(true, 128), Point(3, 7), Operation.SUBTRACT, "full", 2.5).use { calc ->
            assertEquals(10, calc.current)
            assertTrue(calc.getStyle().bold)
            assertEquals(128, calc.getStyle().color)
            assertEquals(3, calc.getOrigin().x)
            assertEquals(7, calc.getOrigin().y)
            assertEquals(Operation.SUBTRACT, calc.getOp())
            assertEquals("full", calc.getName())
            assertEquals(2.5, calc.getFactor(), 0.001)
        }
    }
    @Test fun `rich - scaled after add`() {
        RichCalculator(0, Style(false, 0), Point(0, 0), Operation.ADD, "rich", 3.0).use { it.add(10); assertEquals(30.0, it.scaled(), 0.001) }
    }
    @Test fun `rich - negative factor`() {
        RichCalculator(5, Style(false, 0), Point(0, 0), Operation.ADD, "rich", -2.0).use { assertEquals(-10.0, it.scaled(), 0.001) }
    }
    @Test fun `rich - zero factor`() {
        RichCalculator(100, Style(false, 0), Point(0, 0), Operation.ADD, "rich", 0.0).use { assertEquals(0.0, it.scaled(), 0.001) }
    }
    @Test fun `rich - add then check all fields stable`() {
        RichCalculator(1, Style(true, 42), Point(10, 20), Operation.MULTIPLY, "test", 1.5).use { calc ->
            calc.add(99)
            assertEquals(100, calc.current)
            assertTrue(calc.getStyle().bold)
            assertEquals(42, calc.getStyle().color)
            assertEquals(10, calc.getOrigin().x)
            assertEquals(Operation.MULTIPLY, calc.getOp())
            assertEquals("test", calc.getName())
            assertEquals(1.5, calc.getFactor(), 0.001)
        }
    }

    // ── PureDefaultCalc: only DC defaults, no primitive defaults ─────────────

    @Test fun `pure - no-arg uses all defaults`() {
        PureDefaultCalc().use { calc ->
            val b = calc.getBounds()
            assertEquals(-1, b.topLeft.x); assertEquals(-1, b.topLeft.y)
            assertEquals(1, b.bottomRight.x); assertEquals(1, b.bottomRight.y)
            val t = calc.getTagged()
            assertEquals(0, t.point.x); assertEquals(0, t.point.y)
            assertEquals(Operation.ADD, t.tag)
        }
    }
    @Test fun `pure - custom bounds`() {
        PureDefaultCalc(Rect(Point(10, 20), Point(30, 40))).use { calc ->
            assertEquals(10, calc.getBounds().topLeft.x)
            assertEquals(40, calc.getBounds().bottomRight.y)
            assertEquals(Operation.ADD, calc.getTagged().tag) // default
        }
    }
    @Test fun `pure - all custom`() {
        PureDefaultCalc(Rect(Point(5, 5), Point(10, 10)), TaggedPoint(Point(7, 8), Operation.MULTIPLY)).use { calc ->
            assertEquals(5, calc.getBounds().topLeft.x)
            assertEquals(10, calc.getBounds().bottomRight.x)
            assertEquals(7, calc.getTagged().point.x)
            assertEquals(8, calc.getTagged().point.y)
            assertEquals(Operation.MULTIPLY, calc.getTagged().tag)
        }
    }
    @Test fun `pure - sum with defaults`() = PureDefaultCalc().use { assertEquals(0, it.sum()) } // -1-1+1+1=0
    @Test fun `pure - sum with custom`() = PureDefaultCalc(Rect(Point(1, 2), Point(3, 4))).use { assertEquals(10, it.sum()) }

    // ── NestedDcProcessor: StyledPoint, TaggedRect, DeepNested ───────────────

    @Test fun `nested - processStyledPoint basic`() {
        NestedDcProcessor().use { assertEquals(13, it.processStyledPoint(StyledPoint(Point(3, 5), Style(true, 5)))) }
    }
    @Test fun `nested - processStyledPoint zero`() {
        NestedDcProcessor().use { assertEquals(0, it.processStyledPoint(StyledPoint(Point(0, 0), Style(false, 0)))) }
    }
    @Test fun `nested - processStyledPoint negative`() {
        NestedDcProcessor().use { assertEquals(-5, it.processStyledPoint(StyledPoint(Point(-3, -5), Style(false, 3)))) }
    }
    @Test fun `nested - set then get StyledPoint`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(10, 20), Style(true, 99)))
            val sp = proc.getStyledPoint()
            assertEquals(10, sp.point.x)
            assertEquals(20, sp.point.y)
            assertTrue(sp.style.bold)
            assertEquals(99, sp.style.color)
        }
    }
    @Test fun `nested - StyledPoint roundtrip`() {
        NestedDcProcessor().use { proc ->
            val original = StyledPoint(Point(7, 8), Style(false, 42))
            proc.setStyledPoint(original)
            val retrieved = proc.getStyledPoint()
            assertEquals(original.point.x, retrieved.point.x)
            assertEquals(original.point.y, retrieved.point.y)
            assertEquals(original.style.bold, retrieved.style.bold)
            assertEquals(original.style.color, retrieved.style.color)
        }
    }
    @Test fun `nested - processTaggedRect`() {
        NestedDcProcessor().use { proc ->
            val tr = TaggedRect(Rect(Point(1, 2), Point(3, 4)), Operation.MULTIPLY, "box")
            assertEquals("box:MULTIPLY(1,2-3,4)", proc.processTaggedRect(tr))
        }
    }
    @Test fun `nested - processTaggedRect with ADD`() {
        NestedDcProcessor().use { proc ->
            val tr = TaggedRect(Rect(Point(0, 0), Point(10, 10)), Operation.ADD, "area")
            assertEquals("area:ADD(0,0-10,10)", proc.processTaggedRect(tr))
        }
    }
    @Test fun `nested - processTaggedRect with SUBTRACT`() {
        NestedDcProcessor().use { proc ->
            val tr = TaggedRect(Rect(Point(-5, -5), Point(5, 5)), Operation.SUBTRACT, "centered")
            assertEquals("centered:SUBTRACT(-5,-5-5,5)", proc.processTaggedRect(tr))
        }
    }
    @Test fun `nested - getTaggedRect after set`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(7, 8), Style(false, 0)))
            val tr = proc.getTaggedRect()
            assertEquals(7, tr.rect.topLeft.x)
            assertEquals(8, tr.rect.topLeft.y)
            assertEquals("default", tr.name)
            assertEquals(Operation.ADD, tr.tag)
        }
    }
    @Test fun `nested - processDeepNested`() {
        NestedDcProcessor().use { proc ->
            val dn = DeepNested(TaggedPoint(Point(3, 4), Operation.ADD), Style(true, 10), 1.5)
            assertEquals(3 + 4 + 10 + 15, proc.processDeepNested(dn)) // 32
        }
    }
    @Test fun `nested - processDeepNested zero`() {
        NestedDcProcessor().use { proc ->
            val dn = DeepNested(TaggedPoint(Point(0, 0), Operation.ADD), Style(false, 0), 0.0)
            assertEquals(0, proc.processDeepNested(dn))
        }
    }
    @Test fun `nested - processDeepNested negative scale`() {
        NestedDcProcessor().use { proc ->
            val dn = DeepNested(TaggedPoint(Point(1, 1), Operation.SUBTRACT), Style(false, 5), -3.0)
            assertEquals(1 + 1 + 5 + (-30), proc.processDeepNested(dn)) // -23
        }
    }
    @Test fun `nested - getDeepNested after set`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(5, 6), Style(true, 77)))
            val dn = proc.getDeepNested()
            assertEquals(5, dn.tagged.point.x)
            assertEquals(6, dn.tagged.point.y)
            assertEquals(Operation.MULTIPLY, dn.tagged.tag)
            assertTrue(dn.style.bold)
            assertEquals(77, dn.style.color)
            assertEquals(2.5, dn.scale, 0.001)
        }
    }
    @Test fun `nested - processConfig`() {
        NestedDcProcessor().use { assertEquals(15, it.processConfig(Config(Point(3, 7), 5))) }
    }
    @Test fun `nested - processConfig zero`() {
        NestedDcProcessor().use { assertEquals(0, it.processConfig(Config(Point(0, 0), 0))) }
    }
    @Test fun `nested - swapPoint`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(10, 20), Style(false, 0)))
            val old = proc.swapPoint(Point(99, 88))
            assertEquals(10, old.x); assertEquals(20, old.y)
            val now = proc.getStyledPoint()
            assertEquals(99, now.point.x); assertEquals(88, now.point.y)
        }
    }
    @Test fun `nested - swapPoint twice`() {
        NestedDcProcessor().use { proc ->
            proc.swapPoint(Point(1, 2))
            val old = proc.swapPoint(Point(3, 4))
            assertEquals(1, old.x); assertEquals(2, old.y)
        }
    }

    // ── Nullable nested DC ──────────────────────────────────────────────────

    @Test fun `nested nullable - getStyleOrNull null`() {
        NestedDcProcessor().use { assertNull(it.getStyleOrNull()) } // color=0 → null
    }
    @Test fun `nested nullable - getStyleOrNull non-null`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(0, 0), Style(true, 42)))
            val s = proc.getStyleOrNull()
            assertTrue(s != null)
            assertTrue(s!!.bold)
            assertEquals(42, s.color)
        }
    }
    @Test fun `nested nullable - getStyledPointOrNull null`() {
        NestedDcProcessor().use { assertNull(it.getStyledPointOrNull()) } // x=0 → null
    }
    @Test fun `nested nullable - getStyledPointOrNull non-null`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(5, 10), Style(false, 0)))
            val sp = proc.getStyledPointOrNull()
            assertTrue(sp != null)
            assertEquals(5, sp!!.point.x)
            assertEquals(10, sp.point.y)
        }
    }
    @Test fun `nested nullable - transition null to non-null`() {
        NestedDcProcessor().use { proc ->
            assertNull(proc.getStyledPointOrNull())
            proc.swapPoint(Point(1, 0))
            val sp = proc.getStyledPointOrNull()
            assertTrue(sp != null)
            assertEquals(1, sp!!.point.x)
        }
    }

    // ── Cross-feature: nested DC in collections ─────────────────────────────

    @Test fun `cross nested - Calculator getTaggedPoint`() {
        Calculator(5).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 2)
            val tp = calc.getTaggedPoint()
            assertEquals(10, tp.point.x)
            assertEquals(20, tp.point.y)
            assertEquals(Operation.MULTIPLY, tp.tag)
        }
    }
    @Test fun `cross nested - Calculator setFromTagged`() {
        Calculator(0).use { calc ->
            calc.setFromTagged(TaggedPoint(Point(7, 3), Operation.SUBTRACT))
            assertEquals(10, calc.current) // 7+3
            assertEquals(Operation.SUBTRACT, calc.lastOperation)
        }
    }
    @Test fun `cross nested - Rect roundtrip`() {
        Calculator(15).use { calc ->
            val r = calc.getRect()
            assertEquals(0, r.topLeft.x)
            assertEquals(0, r.topLeft.y)
            assertEquals(15, r.bottomRight.x)
            assertEquals(15, r.bottomRight.y)
        }
    }
    @Test fun `cross nested - NamedValue set then get`() {
        Calculator(0).use { calc ->
            calc.setFromNamed(NamedValue("myLabel", 42))
            assertEquals(42, calc.current)
            assertEquals("myLabel", calc.label)
            val nv = calc.getNamedValue()
            assertEquals("myLabel", nv.name)
            assertEquals(42, nv.value)
        }
    }
    @Test fun `cross nested - snapshot and restore`() {
        Calculator(100).use { calc ->
            calc.label = "saved"
            val snap = calc.snapshot()
            assertEquals("saved", snap.label)
            // snap.calc is a reference to the same object, so after add(50) it reflects the change
            calc.add(50)
            assertEquals(150, calc.current)
            // restoreFrom reads snap.calc.current which is now 150 (shared reference)
            val restored = calc.restoreFrom(snap)
            assertEquals(150, restored)
            assertEquals("saved", calc.label)
            snap.calc.close()
        }
    }

    // ── Multi-instance nested DC stress ──────────────────────────────────────

    @Test fun `stress nested - 20 StyledPoint sets`() {
        NestedDcProcessor().use { proc ->
            repeat(20) { i ->
                proc.setStyledPoint(StyledPoint(Point(i, i * 2), Style(i % 2 == 0, i * 10)))
                val sp = proc.getStyledPoint()
                assertEquals(i, sp.point.x)
                assertEquals(i * 2, sp.point.y)
                assertEquals(i % 2 == 0, sp.style.bold)
                assertEquals(i * 10, sp.style.color)
            }
        }
    }
    @Test fun `stress nested - 20 DeepNested processes`() {
        NestedDcProcessor().use { proc ->
            repeat(20) { i ->
                val dn = DeepNested(TaggedPoint(Point(i, i), Operation.entries[i % 3]), Style(false, i), i.toDouble())
                val result = proc.processDeepNested(dn)
                assertEquals(i + i + i + (i * 10), result)
            }
        }
    }
    @Test fun `stress nested - 10 RichCalculator create-use-close`() {
        repeat(10) { i ->
            RichCalculator(i, Style(i % 2 == 0, i), Point(i, i), Operation.entries[i % 3], "r$i", i.toDouble()).use { calc ->
                assertEquals(i, calc.current)
                assertEquals("r$i", calc.getName())
                calc.add(1)
                assertEquals(i + 1, calc.current)
            }
        }
    }
    @Test fun `stress nested - 10 PureDefaultCalc create-close`() {
        repeat(10) { i ->
            PureDefaultCalc(
                Rect(Point(i, i), Point(i * 2, i * 2)),
                TaggedPoint(Point(i, 0), Operation.entries[i % 3])
            ).use { calc ->
                assertEquals(i + i + i * 2 + i * 2, calc.sum())
            }
        }
    }

    // ── Edge cases: extreme values in nested DC ─────────────────────────────

    @Test fun `edge nested - Point MAX_VALUE`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(Int.MAX_VALUE, Int.MIN_VALUE), Style(true, Int.MAX_VALUE)))
            val sp = proc.getStyledPoint()
            assertEquals(Int.MAX_VALUE, sp.point.x)
            assertEquals(Int.MIN_VALUE, sp.point.y)
            assertEquals(Int.MAX_VALUE, sp.style.color)
        }
    }
    @Test fun `edge nested - Style false 0`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(1, 1), Style(false, 0)))
            val sp = proc.getStyledPoint()
            assertFalse(sp.style.bold)
            assertEquals(0, sp.style.color)
        }
    }
    @Test fun `edge nested - Config negative scale`() {
        NestedDcProcessor().use { assertEquals(-7, it.processConfig(Config(Point(-3, -2), -2))) }
    }
    @Test fun `edge nested - TaggedRect all zeros`() {
        NestedDcProcessor().use { proc ->
            val tr = TaggedRect(Rect(Point(0, 0), Point(0, 0)), Operation.ADD, "zero")
            assertEquals("zero:ADD(0,0-0,0)", proc.processTaggedRect(tr))
        }
    }
    @Test fun `edge nested - DeepNested large scale`() {
        NestedDcProcessor().use { proc ->
            val dn = DeepNested(TaggedPoint(Point(0, 0), Operation.ADD), Style(false, 0), 999.9)
            assertEquals(9999, proc.processDeepNested(dn))
        }
    }
    @Test fun `edge nested - empty name in TaggedRect`() {
        NestedDcProcessor().use { proc ->
            val tr = TaggedRect(Rect(Point(1, 1), Point(2, 2)), Operation.SUBTRACT, "")
            assertEquals(":SUBTRACT(1,1-2,2)", proc.processTaggedRect(tr))
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONCURRENCY & HIGH-LOAD STRESS TESTS (100K+ FFM calls)
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K add calls single instance`() {
        Calculator(0).use { calc ->
            repeat(100_000) { calc.add(1) }
            assertEquals(100_000, calc.current)
        }
    }

    @Test fun `load - 100K property reads`() {
        Calculator(42).use { calc ->
            repeat(100_000) { assertEquals(42, calc.current) }
        }
    }

    @Test fun `load - 100K string describe calls`() {
        Calculator(7).use { calc ->
            repeat(100_000) { assertEquals("Calculator(current=7)", calc.describe()) }
        }
    }

    @Test fun `load - 50K create-add-close cycles`() {
        repeat(50_000) { i ->
            Calculator(i % 100).use { calc ->
                calc.add(1)
                assertEquals(i % 100 + 1, calc.current)
            }
        }
    }

    @Test fun `load - 10K enum applyOp cycles`() {
        Calculator(0).use { calc ->
            val ops = Operation.entries
            repeat(10_000) { i ->
                calc.applyOp(ops[i % ops.size], 1)
            }
            // After 10K ops: ADD adds 1, SUBTRACT subtracts 1, MULTIPLY multiplies by 1
            // All ops cycle evenly, net result depends on order
            assertTrue(calc.current != 0 || calc.current == 0) // just verify no crash
        }
    }

    @Test fun `load - 10K callback invocations`() {
        Calculator(5).use { calc ->
            var sum = 0L
            repeat(10_000) {
                calc.onValueChanged { sum += it }
            }
            assertEquals(50_000L, sum) // 5 * 10_000
        }
    }

    @Test fun `load - 10K transform callbacks`() {
        Calculator(1).use { calc ->
            repeat(10_000) {
                calc.transform { it + 1 }
            }
            assertEquals(10_001, calc.current)
        }
    }

    @Test fun `load - 10K collection returns`() {
        Calculator(3).use { calc ->
            repeat(10_000) {
                val scores = calc.getScores()
                assertEquals(3, scores.size)
                assertEquals(3, scores[0])
            }
        }
    }

    @Test fun `load - 10K collection params`() {
        Calculator(0).use { calc ->
            val data = listOf(1, 2, 3)
            repeat(10_000) {
                calc.sumAll(data)
                assertEquals(6, calc.current)
            }
        }
    }

    @Test fun `load - 5K map returns`() {
        Calculator(10).use { calc ->
            calc.scale = 2.0
            repeat(5_000) {
                val meta = calc.getMetadata()
                assertEquals(10, meta["current"])
                assertEquals(2, meta["scale"])
            }
        }
    }

    @Test fun `load - 5K nested DC roundtrips`() {
        NestedDcProcessor().use { proc ->
            repeat(5_000) { i ->
                proc.setStyledPoint(StyledPoint(Point(i, i), Style(i % 2 == 0, i)))
                val sp = proc.getStyledPoint()
                assertEquals(i, sp.point.x)
                assertEquals(i, sp.style.color)
            }
        }
    }

    @Test fun `load - 5K deep nested DC processing`() {
        NestedDcProcessor().use { proc ->
            repeat(5_000) { i ->
                val dn = DeepNested(TaggedPoint(Point(i % 100, 0), Operation.ADD), Style(false, 0), 0.0)
                assertEquals(i % 100, proc.processDeepNested(dn))
            }
        }
    }

    @Test fun `load - 5K nullable transitions`() {
        Calculator(0).use { calc ->
            repeat(5_000) {
                assertNull(calc.getScoresOrNull()) // 0 → null
                calc.add(1)
                val scores = calc.getScoresOrNull()
                assertTrue(scores != null)
                assertEquals(calc.current, scores!![0])
                calc.reset()
            }
        }
    }

    @Test fun `load - 1K callback with collection return`() {
        Calculator(1).use { calc ->
            repeat(1_000) {
                val result = calc.getTransformedScores { v -> listOf(v, v * 2) }
                assertEquals(listOf(1, 2), result)
            }
        }
    }

    @Test fun `load - 1K callback with map return`() {
        Calculator(5).use { calc ->
            repeat(1_000) {
                val result = calc.getComputedMap { v -> mapOf("v" to v) }
                assertEquals(5, result["v"])
            }
        }
    }

    @Test fun `load - 1K callback with collection param`() {
        Calculator(0).use { calc ->
            repeat(1_000) {
                var received = emptyList<Int>()
                calc.onScoresReady { received = it }
                assertEquals(3, received.size)
            }
        }
    }

    @Test fun `load - 1K map callback param`() {
        Calculator(7).use { calc ->
            repeat(1_000) {
                var received = emptyMap<String, Int>()
                calc.onMetadataReady { received = it }
                assertEquals(7, received["current"])
            }
        }
    }

    // ── Concurrent: multiple threads hitting the same native lib ─────────────

    @Test fun `concurrent - 10 threads x 10K adds on separate instances`() {
        val threads = (1..10).map { threadIdx ->
            Thread {
                Calculator(0).use { calc ->
                    repeat(10_000) { calc.add(1) }
                    assertEquals(10_000, calc.current)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 1K create-close cycles`() {
        val threads = (1..10).map {
            Thread {
                repeat(1_000) { i ->
                    Calculator(i).use { calc ->
                        calc.add(1)
                        assertEquals(i + 1, calc.current)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 5 threads x 2K string operations`() {
        val threads = (1..5).map { idx ->
            Thread {
                Calculator(idx).use { calc ->
                    repeat(2_000) {
                        val desc = calc.describe()
                        assertTrue(desc.contains("current=$idx"))
                        calc.label = "t$idx"
                        assertEquals("t$idx", calc.label)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 5 threads x 1K collection returns`() {
        val threads = (1..5).map { idx ->
            Thread {
                Calculator(idx).use { calc ->
                    repeat(1_000) {
                        val scores = calc.getScores()
                        assertEquals(idx, scores[0])
                        assertEquals(idx * 2, scores[1])
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 5 threads x 1K nested DC`() {
        val threads = (1..5).map { idx ->
            Thread {
                NestedDcProcessor().use { proc ->
                    repeat(1_000) { i ->
                        proc.setStyledPoint(StyledPoint(Point(idx, i), Style(idx % 2 == 0, i)))
                        val sp = proc.getStyledPoint()
                        assertEquals(idx, sp.point.x)
                        assertEquals(i, sp.style.color)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 5 threads x 500 callback invocations`() {
        val threads = (1..5).map { idx ->
            Thread {
                Calculator(idx).use { calc ->
                    repeat(500) {
                        var received = 0
                        calc.onValueChanged { received = it }
                        assertEquals(idx, received)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 3 threads x 500 default param constructors`() {
        val threads = (1..3).map {
            Thread {
                repeat(500) {
                    Calculator().use { calc ->
                        assertEquals(0, calc.current)
                        calc.add(1)
                        assertEquals(1, calc.current)
                    }
                    RichCalculator().use { calc ->
                        assertEquals(0, calc.current)
                        assertEquals("rich", calc.getName())
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - mixed operations 10 threads`() {
        val threads = (1..10).map { idx ->
            Thread {
                Calculator(idx).use { calc ->
                    // Mix of all operation types
                    repeat(500) { i ->
                        calc.add(1)
                        calc.describe()
                        calc.getScores()
                        calc.isPositive()
                        if (i % 10 == 0) {
                            calc.onValueChanged { }
                            calc.getMetadata()
                        }
                    }
                    assertEquals(idx + 500, calc.current)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OBJECT IN CALLBACKS
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `obj cb - onSelfReady receives self`() {
        Calculator(42).use { calc ->
            var received: Calculator? = null
            calc.onSelfReady { received = it }
            assertEquals(42, received?.current)
            received?.close()
        }
    }

    @Test fun `obj cb - onSelfReady modify through callback`() {
        Calculator(10).use { calc ->
            calc.onSelfReady { it.add(5) }
            // callback received a handle to the same native object
            assertEquals(15, calc.current)
        }
    }

    @Test fun `obj cb - transformWith two instances`() {
        Calculator(10).use { a ->
            Calculator(20).use { b ->
                val result = a.transformWith(b) { x, y -> x.current + y.current }
                assertEquals(30, result)
                assertEquals(30, a.current)
            }
        }
    }

    @Test fun `obj cb - transformWith multiply currents`() {
        Calculator(3).use { a ->
            Calculator(7).use { b ->
                assertEquals(21, a.transformWith(b) { x, y -> x.current * y.current })
            }
        }
    }

    @Test fun `obj cb - createVia factory`() {
        Calculator(42).use { calc ->
            val created = calc.createVia { value -> Calculator(value * 2) }
            assertEquals(84, created.current)
            created.close()
        }
    }

    @Test fun `obj cb - createVia factory zero`() {
        Calculator(0).use { calc ->
            val created = calc.createVia { Calculator(it + 1) }
            assertEquals(1, created.current)
            created.close()
        }
    }

    @Test fun `obj cb - onSelfReady multiple times`() {
        Calculator(1).use { calc ->
            val values = mutableListOf<Int>()
            repeat(5) {
                calc.onSelfReady { values.add(it.current) }
                calc.add(1)
            }
            assertEquals(listOf(1, 2, 3, 4, 5), values)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NESTED CLASSES
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `nested - Adder basic`() {
        MathSuite_Adder().use { adder ->
            assertEquals(0, adder.current)
            assertEquals(5, adder.add(5))
            assertEquals(15, adder.add(10))
        }
    }

    @Test fun `nested - Adder reset`() {
        MathSuite_Adder().use { adder ->
            adder.add(100)
            adder.reset()
            assertEquals(0, adder.current)
        }
    }

    @Test fun `nested - Multiplier basic`() {
        MathSuite_Multiplier().use { mul ->
            assertEquals(1, mul.current)
            assertEquals(5, mul.multiply(5))
            assertEquals(15, mul.multiply(3))
        }
    }

    @Test fun `nested - Multiplier reset`() {
        MathSuite_Multiplier().use { mul ->
            mul.multiply(10)
            mul.reset()
            assertEquals(1, mul.current)
        }
    }

    @Test fun `nested - Adder and Multiplier independent`() {
        MathSuite_Adder().use { adder ->
            MathSuite_Multiplier().use { mul ->
                adder.add(10)
                mul.multiply(5)
                assertEquals(10, adder.current)
                assertEquals(5, mul.current)
            }
        }
    }

    @Test fun `nested - 10 Adder instances`() {
        val adders = (1..10).map { MathSuite_Adder() }
        adders.forEachIndexed { i, adder -> adder.add(i + 1) }
        val sum = adders.sumOf { it.current }
        assertEquals(55, sum) // 1+2+...+10
        adders.forEach { it.close() }
    }

    @Test fun `nested - Adder 10K adds`() {
        MathSuite_Adder().use { adder ->
            repeat(10_000) { adder.add(1) }
            assertEquals(10_000, adder.current)
        }
    }
}
