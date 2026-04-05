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

    // ═══════════════════════════════════════════════════════════════════════════
    // Callbacks returning sealed enum (CalcResult)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `cb sealed - map_to_result returns Value variant`() {
        Calculator(0).use { calc ->
            calc.map_to_result(42) { v -> CalcResult.value(v) }.use { result ->
                assertTrue(result is CalcResult.Value)
                assertEquals(42, (result as CalcResult.Value).value)
            }
        }
    }

    @Test fun `cb sealed - map_to_result returns Error variant`() {
        Calculator(0).use { calc ->
            calc.map_to_result(-1) { _ -> CalcResult.error("negative") }.use { result ->
                assertTrue(result is CalcResult.Error)
                assertEquals("negative", (result as CalcResult.Error).value)
            }
        }
    }

    @Test fun `cb sealed - map_to_result returns Partial variant`() {
        Calculator(0).use { calc ->
            calc.map_to_result(7) { v -> CalcResult.partial(v, 0.75) }.use { result ->
                assertTrue(result is CalcResult.Partial)
                val partial = result as CalcResult.Partial
                assertEquals(7, partial.value)
                assertEquals(0.75, partial.confidence, 0.001)
            }
        }
    }

    @Test fun `cb sealed - map_to_result returns Nothing variant`() {
        Calculator(0).use { calc ->
            calc.map_to_result(0) { _ -> CalcResult.nothing() }.use { result ->
                assertTrue(result is CalcResult.Nothing)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Callbacks returning object (Calculator)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `cb obj - create_via_callback returns new Calculator`() {
        Calculator(10).use { calc ->
            calc.create_via_callback { initial -> Calculator(initial * 2) }.use { created ->
                assertEquals(20, created.current)
            }
        }
    }

    @Test fun `cb obj - create_via_callback with zero`() {
        Calculator(0).use { calc ->
            calc.create_via_callback { initial -> Calculator(initial) }.use { created ->
                assertEquals(0, created.current)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Callbacks taking object param (Calculator)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `cb obj param - apply_to_clone reads accumulator`() {
        Calculator(42).use { calc ->
            val result = calc.apply_to_clone { c ->
                c.use { it.current }
            }
            assertEquals(42, result)
        }
    }

    @Test fun `cb obj param - apply_to_clone with mutation`() {
        Calculator(10).use { calc ->
            val result = calc.apply_to_clone { c ->
                c.use {
                    it.add(5)
                    it.current
                }
            }
            assertEquals(15, result)
            // Original calculator should not be affected
            assertEquals(10, calc.current)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Callbacks taking sealed enum param (CalcResult)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `cb sealed param - format_result with Value`() {
        Calculator(0).use { calc ->
            CalcResult.value(99).use { input ->
                val result = calc.format_result(input) { r ->
                    r.use {
                        when (it) {
                            is CalcResult.Value -> "got ${it.value}"
                            else -> "other"
                        }
                    }
                }
                assertEquals("got 99", result)
            }
        }
    }

    @Test fun `cb sealed param - format_result with Error`() {
        Calculator(0).use { calc ->
            CalcResult.error("boom").use { input ->
                val result = calc.format_result(input) { r ->
                    r.use {
                        when (it) {
                            is CalcResult.Error -> "err: ${it.value}"
                            else -> "other"
                        }
                    }
                }
                assertEquals("err: boom", result)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases for handle-backed callbacks
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge cb sealed - map_to_result with MAX_VALUE`() {
        Calculator(0).use { calc ->
            calc.map_to_result(Int.MAX_VALUE) { v -> CalcResult.value(v) }.use { result ->
                assertTrue(result is CalcResult.Value)
                assertEquals(Int.MAX_VALUE, (result as CalcResult.Value).value)
            }
        }
    }

    @Test fun `edge cb sealed - map_to_result with MIN_VALUE`() {
        Calculator(0).use { calc ->
            calc.map_to_result(Int.MIN_VALUE) { v -> CalcResult.value(v) }.use { result ->
                assertTrue(result is CalcResult.Value)
                assertEquals(Int.MIN_VALUE, (result as CalcResult.Value).value)
            }
        }
    }

    @Test fun `edge cb sealed param - format_result with empty error string`() {
        Calculator(0).use { calc ->
            CalcResult.error("").use { input ->
                val result = calc.format_result(input) { r ->
                    r.use {
                        when (it) {
                            is CalcResult.Error -> "err:[${it.value}]"
                            else -> "other"
                        }
                    }
                }
                assertEquals("err:[]", result)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Load tests for handle-backed callbacks
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 1K map_to_result calls`() {
        Calculator(0).use { calc ->
            repeat(1_000) { i ->
                calc.map_to_result(i) { v -> CalcResult.value(v) }.use { result ->
                    assertTrue(result is CalcResult.Value)
                }
            }
        }
    }

    @Test fun `load - 1K create_via_callback calls`() {
        Calculator(1).use { calc ->
            repeat(1_000) {
                calc.create_via_callback { v -> Calculator(v) }.use { created ->
                    assertEquals(1, created.current)
                }
            }
        }
    }

    @Test fun `load - 1K apply_to_clone calls`() {
        Calculator(5).use { calc ->
            repeat(1_000) {
                val result = calc.apply_to_clone { c -> c.use { it.current } }
                assertEquals(5, result)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrency tests for handle-backed callbacks
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 1K map_to_result`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(0).use { calc ->
                    repeat(1_000) {
                        calc.map_to_result(tid) { v -> CalcResult.value(v) }.use { result ->
                            assertTrue(result is CalcResult.Value)
                            assertEquals(tid, (result as CalcResult.Value).value)
                        }
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 1K create_via_callback`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid).use { calc ->
                    repeat(1_000) {
                        calc.create_via_callback { v -> Calculator(v) }.use { created ->
                            assertEquals(tid, created.current)
                        }
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 1K apply_to_clone`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid).use { calc ->
                    repeat(1_000) {
                        val result = calc.apply_to_clone { c -> c.use { it.current } }
                        assertEquals(tid, result)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
