package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class EdgeCaseTest {

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

}
