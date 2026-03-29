package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.first

class FlowTest {

    // ══════════════════════════════════════════════════════════════════════════
    // FLOW<T> — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    // ── Basic Flow collection ───────────────────────────────────────────────

    @Test fun `flow - countUp basic`() = runBlocking {
        Calculator(0).use { calc ->
            val items = calc.countUp(5).toList()
            assertEquals(listOf(1, 2, 3, 4, 5), items)
        }
    }

    @Test fun `flow - countUp single`() = runBlocking {
        Calculator(0).use { assertEquals(listOf(1), it.countUp(1).toList()) }
    }

    @Test fun `flow - countUp zero`() = runBlocking {
        Calculator(0).use { assertEquals(emptyList<Int>(), it.countUp(0).toList()) }
    }

    @Test fun `flow - countUp 100 elements`() = runBlocking {
        Calculator(0).use { calc ->
            val items = calc.countUp(100).toList()
            assertEquals(100, items.size)
            assertEquals((1..100).toList(), items)
        }
    }

    @Test fun `flow - tickStrings`() = runBlocking {
        Calculator(0).use { calc ->
            val items = calc.tickStrings(3).toList()
            assertEquals(listOf("tick_0", "tick_1", "tick_2"), items)
        }
    }

    @Test fun `flow - tickStrings empty`() = runBlocking {
        Calculator(0).use { assertEquals(emptyList<String>(), it.tickStrings(0).toList()) }
    }

    @Test fun `flow - emptyIntFlow`() = runBlocking {
        Calculator(0).use { assertEquals(emptyList<Int>(), it.emptyIntFlow().toList()) }
    }

    @Test fun `flow - singleFlow`() = runBlocking {
        Calculator(42).use { assertEquals(listOf(42), it.singleFlow().toList()) }
    }

    @Test fun `flow - enumFlow`() = runBlocking {
        Calculator(0).use { calc ->
            val ops = calc.enumFlow().toList()
            assertEquals(Operation.entries.toList(), ops)
        }
    }

    @Test fun `flow - boolFlow positive`() = runBlocking {
        Calculator(5).use { calc ->
            assertEquals(listOf(true, false, true), calc.boolFlow().toList())
        }
    }

    @Test fun `flow - boolFlow zero`() = runBlocking {
        Calculator(0).use { calc ->
            assertEquals(listOf(true, false, false), calc.boolFlow().toList())
        }
    }

    // ── Flow error handling ─────────────────────────────────────────────────

    @Test fun `flow - failingFlow throws after partial`() = runBlocking {
        Calculator(0).use { calc ->
            val items = mutableListOf<Int>()
            assertFailsWith<KotlinNativeException> {
                calc.failingFlow().collect { items.add(it) }
            }
            assertEquals(listOf(1, 2), items) // got 2 elements before error
        }
    }

    // ── Flow cancellation ───────────────────────────────────────────────────

    @Test fun `flow - take first 3 from infinite`() = runBlocking {
        Calculator(0).use { calc ->
            val items = calc.infiniteFlow().take(3).toList()
            assertEquals(listOf(0, 1, 2), items)
        }
    }

    @Test fun `flow - take first from infinite`() = runBlocking {
        Calculator(0).use { calc ->
            assertEquals(0, calc.infiniteFlow().first())
        }
    }

    @Test fun `flow - cancel infinite flow`() = runBlocking {
        Calculator(0).use { calc ->
            val start = System.currentTimeMillis()
            val job = launch {
                calc.infiniteFlow().collect { /* consume forever */ }
            }
            delay(100)
            job.cancelAndJoin()
            val elapsed = System.currentTimeMillis() - start
            assertTrue(elapsed < 3000, "Should cancel fast, took ${elapsed}ms")
        }
    }

    // ── Flow concurrency ────────────────────────────────────────────────────

    @Test fun `flow - 5 concurrent collectors`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..5).map { i ->
                async(Dispatchers.Default) { calc.countUp(10).toList() }
            }.awaitAll()
            assertEquals(5, results.size)
            results.forEach { assertEquals((1..10).toList(), it) }
        }
    }

    @Test fun `flow - concurrent on separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> calc.countUp(5).toList() }
            }
        }.awaitAll()
        assertEquals(10, results.size)
        results.forEach { assertEquals(listOf(1, 2, 3, 4, 5), it) }
    }

    // ── Flow load ───────────────────────────────────────────────────────────

    @Test fun `flow - 500 elements`() = runBlocking {
        Calculator(0).use { calc ->
            val items = calc.countUp(500).toList()
            assertEquals(500, items.size)
            assertEquals(500, items.last())
        }
    }

    @Test fun `flow - 50 sequential flows`() = runBlocking {
        Calculator(0).use { calc ->
            repeat(50) {
                assertEquals(listOf(1, 2, 3), calc.countUp(3).toList())
            }
        }
    }

    @Test fun `flow - 20 cancel cycles infinite`() = runBlocking {
        Calculator(0).use { calc ->
            repeat(20) {
                val items = calc.infiniteFlow().take(5).toList()
                assertEquals(5, items.size)
            }
        }
    }

    // ── Cross-feature ────────────────────────────────────────────────────────

    @Test fun `flow cross - flow then sync`() = runBlocking {
        Calculator(0).use { calc ->
            calc.countUp(3).toList()
            assertEquals(0, calc.current) // flow didn't modify accumulator
            calc.add(10)
            assertEquals(10, calc.current)
        }
    }

    @Test fun `flow cross - flow then suspend`() = runBlocking {
        Calculator(0).use { calc ->
            val items = calc.countUp(3).toList()
            assertEquals(listOf(1, 2, 3), items)
            val r = calc.delayedAdd(1, 2)
            assertEquals(3, r)
        }
    }

    @Test fun `flow cross - flow then callback`() = runBlocking {
        Calculator(5).use { calc ->
            calc.countUp(2).toList()
            var received = 0
            calc.onValueChanged { received = it }
            assertEquals(5, received)
        }
    }
}
