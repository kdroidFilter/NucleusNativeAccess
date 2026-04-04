package com.example.rustcalculator

import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallbackTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Synchronous callbacks (fn() params)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `transform_and_sum doubles values`() {
        Calculator(0).use { calc ->
            val result = calc.transform_and_sum(listOf(1, 2, 3)) { it * 2 }
            assertEquals(12, result) // 2 + 4 + 6
        }
    }

    @Test fun `transform_and_sum squares values`() {
        Calculator(0).use { calc ->
            val result = calc.transform_and_sum(listOf(3, 4, 5)) { it * it }
            assertEquals(50, result) // 9 + 16 + 25
        }
    }

    @Test fun `transform_and_sum with empty array`() {
        Calculator(0).use { calc ->
            val result = calc.transform_and_sum(emptyList()) { it }
            assertEquals(0, result)
        }
    }

    @Test fun `for_each_score calls callback N times`() {
        Calculator(10).use { calc ->
            val values = Collections.synchronizedList(mutableListOf<Int>())
            calc.for_each_score(3) { values.add(it) }
            assertEquals(3, values.size)
            assertEquals(listOf(10, 20, 30), values)
        }
    }

    @Test fun `for_each_score with zero count`() {
        Calculator(5).use { calc ->
            val counter = AtomicInteger(0)
            calc.for_each_score(0) { counter.incrementAndGet() }
            assertEquals(0, counter.get())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Suspend + callback combo (event loop pattern)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `run_tick_loop calls on_tick from background thread`() = runTest {
        Calculator(100).use { calc ->
            val values = Collections.synchronizedList(mutableListOf<Int>())
            calc.run_tick_loop(5, 10) { values.add(it) }
            assertEquals(5, values.size)
            // accumulator=100, so values are 101, 102, 103, 104, 105
            assertEquals(listOf(101, 102, 103, 104, 105), values)
        }
    }

    @Test fun `run_tick_loop with accumulator zero`() = runTest {
        Calculator(0).use { calc ->
            val sum = AtomicInteger(0)
            calc.run_tick_loop(3, 10) { sum.addAndGet(it) }
            assertEquals(6, sum.get()) // 1 + 2 + 3
        }
    }

    // ════════════════════════════════════════════════════════════���══════════════
    // Edge cases
    // ═════════���═════════════��═══════════════════════════════════════════════════

    @Test fun `edge cb - transform_and_sum with identity`() {
        Calculator(0).use { calc ->
            val result = calc.transform_and_sum(listOf(1, 2, 3)) { it }
            assertEquals(6, result)
        }
    }

    @Test fun `edge cb - transform_and_sum with negative values`() {
        Calculator(0).use { calc ->
            val result = calc.transform_and_sum(listOf(-1, -2, -3)) { it * 2 }
            assertEquals(-12, result)
        }
    }

    @Test fun `edge cb - transform_and_sum with MAX_VALUE`() {
        Calculator(0).use { calc ->
            val result = calc.transform_and_sum(listOf(Int.MAX_VALUE)) { it }
            assertEquals(Int.MAX_VALUE, result)
        }
    }

    @Test fun `edge cb - for_each_score captures all values`() {
        Calculator(7).use { calc ->
            val values = Collections.synchronizedList(mutableListOf<Int>())
            calc.for_each_score(5) { values.add(it) }
            assertEquals(listOf(7, 14, 21, 28, 35), values)
        }
    }

    @Test fun `edge cb - transform_and_sum with large list`() {
        Calculator(0).use { calc ->
            val big = (1..1000).toList()
            val result = calc.transform_and_sum(big) { it }
            assertEquals(500500, result) // sum 1..1000
        }
    }

    // ═══════════��═══════════════════════════════════════════════════════════════
    // Load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K transform_and_sum calls`() {
        Calculator(0).use { calc ->
            repeat(100_000) {
                val result = calc.transform_and_sum(listOf(1, 2, 3)) { it * 2 }
                assertEquals(12, result)
            }
        }
    }

    @Test fun `load - 100K for_each_score calls`() {
        Calculator(1).use { calc ->
            val counter = AtomicInteger(0)
            repeat(100_000) {
                calc.for_each_score(1) { counter.incrementAndGet() }
            }
            assertEquals(100_000, counter.get())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrency tests
    // ═════════════════════════════════════════════════��═════════════════════════

    @Test fun `concurrent - 10 threads x 10K transform_and_sum`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(0).use { calc ->
                    repeat(10_000) {
                        val result = calc.transform_and_sum(listOf(tid, tid)) { it * 2 }
                        assertEquals(tid * 4, result)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K for_each_score`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid).use { calc ->
                    repeat(10_000) {
                        val values = Collections.synchronizedList(mutableListOf<Int>())
                        calc.for_each_score(1) { values.add(it) }
                        assertEquals(1, values.size)
                        assertEquals(tid, values[0])
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
