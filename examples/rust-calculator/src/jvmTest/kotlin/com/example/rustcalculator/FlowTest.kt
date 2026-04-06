package com.example.rustcalculator

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Basic Flow<Int> and Flow<String>
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `count_up emits correct sequence`() = runBlocking {
        Calculator(0).use { calc ->
            val items = calc.count_up(5, 10).toList()
            assertEquals(5, items.size)
            assertEquals(listOf(1, 2, 3, 4, 5), items)
        }
    }

    @Test fun `count_up with non-zero accumulator`() = runBlocking {
        Calculator(10).use { calc ->
            val items = calc.count_up(3, 10).toList()
            assertEquals(listOf(11, 12, 13), items)
        }
    }

    @Test fun `score_labels emits strings`() = runBlocking {
        Calculator(5).use { calc ->
            val items = calc.score_labels(3).toList()
            assertEquals(3, items.size)
            assertEquals("Score #1: 5", items[0])
            assertEquals("Score #2: 10", items[1])
            assertEquals("Score #3: 15", items[2])
        }
    }

    @Test fun `flow with single element`() = runBlocking {
        Calculator(0).use { calc ->
            val items = calc.count_up(1, 10).toList()
            assertEquals(1, items.size)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge flow - count_up with zero accumulator`() = runBlocking {
        Calculator(0).use { calc ->
            val items = calc.count_up(3, 10).toList()
            assertEquals(listOf(1, 2, 3), items)
        }
    }

    @Test fun `edge flow - count_up with negative accumulator`() = runBlocking {
        Calculator(-10).use { calc ->
            val items = calc.count_up(3, 10).toList()
            assertEquals(listOf(-9, -8, -7), items)
        }
    }

    @Test fun `edge flow - score_labels with single element`() = runBlocking {
        Calculator(1).use { calc ->
            val items = calc.score_labels(1).toList()
            assertEquals(1, items.size)
            assertEquals("Score #1: 1", items[0])
        }
    }

    @Test fun `edge flow - score_labels with large count`() = runBlocking {
        Calculator(1).use { calc ->
            val items = calc.score_labels(100).toList()
            assertEquals(100, items.size)
            assertEquals("Score #100: 100", items[99])
        }
    }

    @Test fun `edge flow - count_up with MAX_VALUE accumulator`() = runBlocking {
        Calculator(Int.MAX_VALUE).use { calc ->
            val items = calc.count_up(1, 10).toList()
            assertEquals(1, items.size)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 10K count_up flow collections`() = runBlocking {
        Calculator(1).use { calc ->
            repeat(10_000) {
                val items = calc.count_up(1, 0).toList()
                assertEquals(1, items.size)
                assertEquals(2, items[0])
            }
        }
    }

    @Test fun `load - 10K score_labels flow collections`() = runBlocking {
        Calculator(1).use { calc ->
            repeat(10_000) {
                val items = calc.score_labels(1).toList()
                assertEquals(1, items.size)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrency tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 1K count_up flows`() {
        val threads = (1..10).map { tid ->
            Thread {
                runBlocking {
                    Calculator(tid).use { calc ->
                        repeat(1_000) {
                            val items = calc.count_up(2, 0).toList()
                            assertEquals(2, items.size)
                            assertEquals(tid + 1, items[0])
                        }
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 1K score_labels flows`() {
        val threads = (1..10).map { tid ->
            Thread {
                runBlocking {
                    Calculator(tid).use { calc ->
                        repeat(1_000) {
                            val items = calc.score_labels(1).toList()
                            assertEquals(1, items.size)
                            assertTrue(items[0].contains("$tid"))
                        }
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
