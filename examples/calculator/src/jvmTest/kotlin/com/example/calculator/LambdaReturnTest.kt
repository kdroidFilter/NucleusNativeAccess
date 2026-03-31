package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class LambdaReturnTest {

    // ══════════════════════════════════════════════════════════════════════════
    // LAMBDA AS RETURN TYPE — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    // ── (Int) -> Int return ────────────────────────────────────────────────

    @Test
    fun `lambda return - getAdder basic`() {
        Calculator(0).use { calc ->
            val adder = calc.getAdder(5)
            assertEquals(15, adder(10))
            assertEquals(5, adder(0))
            assertEquals(-5, adder(-10))
        }
    }

    @Test
    fun `lambda return - getAdder zero`() {
        Calculator(0).use { calc ->
            val adder = calc.getAdder(0)
            assertEquals(42, adder(42))
        }
    }

    @Test
    fun `lambda return - getAdder negative`() {
        Calculator(0).use { calc ->
            val adder = calc.getAdder(-3)
            assertEquals(7, adder(10))
        }
    }

    @Test
    fun `lambda return - getAdder call multiple times`() {
        Calculator(0).use { calc ->
            val adder = calc.getAdder(1)
            assertEquals(1, adder(0))
            assertEquals(2, adder(1))
            assertEquals(100, adder(99))
            assertEquals(1, adder(0))
        }
    }

    @Test
    fun `lambda return - multiple adders independent`() {
        Calculator(0).use { calc ->
            val add5 = calc.getAdder(5)
            val add10 = calc.getAdder(10)
            assertEquals(15, add5(10))
            assertEquals(20, add10(10))
            assertEquals(5, add5(0))
            assertEquals(10, add10(0))
        }
    }

    // ── (Int) -> String return ─────────────────────────────────────────────

    @Test
    fun `lambda return - getFormatter basic`() {
        Calculator(42).use { calc ->
            val fmt = calc.getFormatter()
            val result = fmt(7)
            assertEquals("value=7 (acc=42)", result)
        }
    }

    @Test
    fun `lambda return - getFormatter captures accumulator`() {
        Calculator(0).use { calc ->
            calc.add(10)
            val fmt = calc.getFormatter()
            assertEquals("value=5 (acc=10)", fmt(5))
        }
    }

    // ── (Int) -> Unit return ───────────────────────────────────────────────

    @Test
    fun `lambda return - getNotifier basic`() {
        Calculator(0).use { calc ->
            val notifier = calc.getNotifier()
            notifier(42)
            assertEquals(42, calc.current)
        }
    }

    @Test
    fun `lambda return - getNotifier multiple calls`() {
        Calculator(0).use { calc ->
            val notifier = calc.getNotifier()
            notifier(1)
            assertEquals(1, calc.current)
            notifier(99)
            assertEquals(99, calc.current)
        }
    }

    // ── Concurrent ─────────────────────────────────────────────────────────

    @Test
    fun `lambda return - concurrent getAdder`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..20).map { i ->
                async(Dispatchers.Default) {
                    val adder = calc.getAdder(i)
                    adder(100)
                }
            }.awaitAll()
            results.forEachIndexed { idx, r -> assertEquals(100 + idx + 1, r) }
        }
    }

    @Test
    fun `lambda return - concurrent invoke same lambda`() = runBlocking {
        Calculator(0).use { calc ->
            val adder = calc.getAdder(5)
            val results = (1..20).map { i ->
                async(Dispatchers.Default) { adder(i) }
            }.awaitAll()
            results.forEachIndexed { idx, r -> assertEquals(idx + 1 + 5, r) }
        }
    }

    // ── Sequential stress ──────────────────────────────────────────────────

    @Test
    fun `lambda return - 100 sequential getAdder`() {
        Calculator(0).use { calc ->
            repeat(100) { i ->
                val adder = calc.getAdder(i)
                assertEquals(i + 1, adder(1))
            }
        }
        System.gc()
    }

    @Test
    fun `lambda return - 200 sequential invocations`() {
        Calculator(0).use { calc ->
            val adder = calc.getAdder(1)
            repeat(200) { i ->
                assertEquals(i + 1, adder(i))
            }
        }
        System.gc()
    }

    @Test
    fun `lambda return - 50 instances`() {
        repeat(50) { i ->
            Calculator(i).use { calc ->
                val adder = calc.getAdder(i)
                assertEquals(i * 2, adder(i))
            }
        }
        System.gc()
    }
}
