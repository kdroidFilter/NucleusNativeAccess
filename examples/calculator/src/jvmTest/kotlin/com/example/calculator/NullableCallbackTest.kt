package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class NullableCallbackTest {

    // ══════════════════════════════════════════════════════════════════════════
    // NULLABLE CALLBACK PARAMS — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    // ── (Int) -> Unit nullable ─────────────────────────────────────────────

    @Test
    fun `nullable cb - onValueChangedOrNull with callback`() {
        Calculator(42).use { calc ->
            var received = -1
            val result = calc.onValueChangedOrNull { received = it }
            assertTrue(result)
            assertEquals(42, received)
        }
    }

    @Test
    fun `nullable cb - onValueChangedOrNull with null`() {
        Calculator(42).use { calc ->
            val result = calc.onValueChangedOrNull(null)
            assertFalse(result)
        }
    }

    // ── (Int) -> Int nullable ──────────────────────────────────────────────

    @Test
    fun `nullable cb - transformOrDefault with callback`() {
        Calculator(5).use { calc ->
            val result = calc.transformOrDefault({ it * 3 }, 99)
            assertEquals(15, result)
        }
    }

    @Test
    fun `nullable cb - transformOrDefault with null uses default`() {
        Calculator(5).use { calc ->
            val result = calc.transformOrDefault(null, 99)
            assertEquals(99, result)
        }
    }

    // ── (Int) -> String nullable ───────────────────────────────────────────

    @Test
    fun `nullable cb - formatOrNull with callback`() {
        Calculator(7).use { calc ->
            val result = calc.formatOrNull { "Value: $it" }
            assertEquals("Value: 7", result)
        }
    }

    @Test
    fun `nullable cb - formatOrNull with null`() {
        Calculator(7).use { calc ->
            val result = calc.formatOrNull(null)
            assertEquals("null", result)
        }
    }

    // ── Alternating null / non-null ────────────────────────────────────────

    @Test
    fun `nullable cb - alternating null and non-null`() {
        Calculator(5).use { calc ->
            assertEquals("Value: 5", calc.formatOrNull { "Value: $it" })
            assertEquals("null", calc.formatOrNull(null))
            assertEquals("X=5", calc.formatOrNull { "X=$it" })
            assertEquals("null", calc.formatOrNull(null))
        }
    }

    // ── Concurrent ─────────────────────────────────────────────────────────

    @Test
    fun `nullable cb - concurrent with non-null callbacks`() = runBlocking {
        Calculator(10).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) {
                    calc.formatOrNull { "v=$it" }
                }
            }.awaitAll()
            assertEquals(20, results.size)
            results.forEach { assertEquals("v=10", it) }
        }
    }

    @Test
    fun `nullable cb - concurrent mixed null and non-null`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..20).map { i ->
                async(Dispatchers.Default) {
                    if (i % 2 == 0) calc.formatOrNull { "v=$it" }
                    else calc.formatOrNull(null)
                }
            }.awaitAll()
            results.forEachIndexed { idx, r ->
                if ((idx + 1) % 2 == 0) assertEquals("v=5", r)
                else assertEquals("null", r)
            }
        }
    }

    // ── Sequential stress ──────────────────────────────────────────────────

    @Test
    fun `nullable cb - 200 sequential calls`() {
        Calculator(1).use { calc ->
            repeat(200) { i ->
                if (i % 2 == 0) {
                    assertTrue(calc.onValueChangedOrNull { })
                } else {
                    assertFalse(calc.onValueChangedOrNull(null))
                }
            }
        }
        System.gc()
    }
}
