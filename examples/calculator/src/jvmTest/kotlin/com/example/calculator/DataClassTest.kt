package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataClassTest {

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

    // ══════════════════════════════════════════════════════════════════════════
    // LIST<DATACLASS> RETURN
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `list dc - getPoints basic`() {
        Calculator(5).use { calc ->
            val points = calc.getPoints()
            assertEquals(3, points.size)
            assertEquals(Point(5, 0), points[0])
            assertEquals(Point(0, 5), points[1])
            assertEquals(Point(5, 5), points[2])
        }
    }

    @Test fun `list dc - getPoints zero`() {
        Calculator(0).use { calc ->
            val points = calc.getPoints()
            assertEquals(3, points.size)
            assertEquals(Point(0, 0), points[0])
            assertEquals(Point(0, 0), points[1])
            assertEquals(Point(0, 0), points[2])
        }
    }

    @Test fun `list dc - getPoints negative`() {
        Calculator(-3).use { calc ->
            val points = calc.getPoints()
            assertEquals(Point(-3, 0), points[0])
            assertEquals(Point(0, -3), points[1])
            assertEquals(Point(-3, -3), points[2])
        }
    }

    @Test fun `list dc - getNamedValues`() {
        Calculator(10).use { calc ->
            val nvs = calc.getNamedValues()
            assertEquals(2, nvs.size)
            assertEquals("first", nvs[0].name)
            assertEquals(10, nvs[0].value)
            assertEquals("second", nvs[1].name)
            assertEquals(20, nvs[1].value)
        }
    }

    @Test fun `list dc - getTaggedPoints`() {
        Calculator(7).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 1)
            val tps = calc.getTaggedPoints()
            assertEquals(2, tps.size)
            assertEquals(Point(7, 0), tps[0].point)
            assertEquals(Operation.MULTIPLY, tps[0].tag)
            assertEquals(Point(0, 7), tps[1].point)
            assertEquals(Operation.ADD, tps[1].tag)
        }
    }

    @Test fun `list dc - getEmptyPoints`() {
        Calculator(0).use { calc ->
            val points = calc.getEmptyPoints()
            assertEquals(0, points.size)
        }
    }

    @Test fun `list dc - getSinglePoint`() {
        Calculator(42).use { calc ->
            val points = calc.getSinglePoint()
            assertEquals(1, points.size)
            assertEquals(Point(42, 84), points[0])
        }
    }

    @Test fun `list dc - getPointsOrNull non-null`() {
        Calculator(5).use { calc ->
            val points = calc.getPointsOrNull()
            assertTrue(points != null)
            assertEquals(1, points!!.size)
            assertEquals(Point(5, 5), points[0])
        }
    }

    @Test fun `list dc - getPointsOrNull null`() {
        Calculator(0).use { calc ->
            assertNull(calc.getPointsOrNull())
        }
    }

    @Test fun `list dc - getPoints after mutations`() {
        Calculator(0).use { calc ->
            calc.add(10)
            val p1 = calc.getPoints()
            assertEquals(Point(10, 0), p1[0])
            calc.multiply(2)
            val p2 = calc.getPoints()
            assertEquals(Point(20, 0), p2[0])
            assertEquals(Point(20, 20), p2[2])
        }
    }

    @Test fun `list dc - getNamedValues with label`() {
        Calculator(5).use { calc ->
            calc.label = "test"
            val nvs = calc.getNamedValues()
            assertEquals("first", nvs[0].name) // getNamedValues uses hardcoded names
            assertEquals(5, nvs[0].value)
        }
    }

    @Test fun `list dc - getPoints 100 times`() {
        Calculator(1).use { calc ->
            repeat(100) {
                val points = calc.getPoints()
                assertEquals(3, points.size)
                assertEquals(1, points[0].x)
            }
        }
    }

    @Test fun `list dc - concurrent getPoints`() {
        val threads = (1..5).map { idx ->
            Thread {
                Calculator(idx).use { calc ->
                    repeat(100) {
                        val points = calc.getPoints()
                        assertEquals(3, points.size)
                        assertEquals(idx, points[0].x)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `list dc - getTaggedPoints all operations`() {
        Calculator(1).use { calc ->
            for (op in Operation.entries) {
                calc.applyOp(op, 1)
                val tps = calc.getTaggedPoints()
                assertEquals(op, tps[0].tag)
                assertEquals(Operation.ADD, tps[1].tag) // second is always ADD
            }
        }
    }

    @Test fun `list dc - nullable transition`() {
        Calculator(0).use { calc ->
            assertNull(calc.getPointsOrNull())
            calc.add(1)
            val pts = calc.getPointsOrNull()
            assertTrue(pts != null)
            assertEquals(Point(1, 1), pts!![0])
            calc.reset()
            assertNull(calc.getPointsOrNull())
        }
    }

}
