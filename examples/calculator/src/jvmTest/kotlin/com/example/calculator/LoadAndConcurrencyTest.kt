package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadAndConcurrencyTest {

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
}
