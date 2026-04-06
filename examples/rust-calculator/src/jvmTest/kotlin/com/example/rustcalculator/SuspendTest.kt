package com.example.rustcalculator

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SuspendTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Basic suspend functions
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `suspend delayed_add returns correct result`() = runBlocking {
        Calculator(10).use { calc ->
            val result = calc.delayed_add(5, 50)
            assertEquals(15, result)
        }
    }

    @Test fun `suspend delayed_describe returns string`() = runBlocking {
        Calculator(42).use { calc ->
            val result = calc.delayed_describe(50)
            assertEquals("Calculator(current=42)", result)
        }
    }

    @Test fun `suspend fail_after_delay throws exception`() = runBlocking {
        Calculator(0).use { calc ->
            var threw = false
            try {
                calc.fail_after_delay(50)
            } catch (e: KotlinNativeException) {
                threw = true
            }
            assertTrue(threw, "Expected KotlinNativeException")
        }
    }

    @Test fun `suspend delayed_noop completes`() = runBlocking {
        Calculator(0).use { calc ->
            calc.delayed_noop(50)
        }
    }

    @Test fun `suspend delayed_is_positive returns bool`() = runBlocking {
        Calculator(5).use { calc ->
            assertTrue(calc.delayed_is_positive(50))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge suspend - delayed_add with zero`() = runBlocking {
        Calculator(0).use { calc ->
            assertEquals(0, calc.delayed_add(0, 10))
        }
    }

    @Test fun `edge suspend - delayed_add with negative`() = runBlocking {
        Calculator(10).use { calc ->
            assertEquals(5, calc.delayed_add(-5, 10))
        }
    }

    @Test fun `edge suspend - delayed_is_positive false`() = runBlocking {
        Calculator(-1).use { calc ->
            assertFalse(calc.delayed_is_positive(10))
        }
    }

    @Test fun `edge suspend - delayed_describe with zero`() = runBlocking {
        Calculator(0).use { calc ->
            assertEquals("Calculator(current=0)", calc.delayed_describe(10))
        }
    }

    @Test fun `edge suspend - recovery after exception`() = runBlocking {
        Calculator(10).use { calc ->
            try { calc.fail_after_delay(10) } catch (_: KotlinNativeException) {}
            val result = calc.delayed_add(5, 10)
            assertEquals(15, result)
        }
    }

    @Test fun `edge suspend - sequential delayed_add calls`() = runBlocking {
        Calculator(0).use { calc ->
            assertEquals(1, calc.delayed_add(1, 10))
            assertEquals(3, calc.delayed_add(2, 10))
            assertEquals(6, calc.delayed_add(3, 10))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K delayed_add calls`() = runBlocking {
        Calculator(0).use { calc ->
            repeat(100_000) {
                calc.delayed_add(0, 0)
            }
        }
    }

    @Test fun `load - 100K delayed_is_positive calls`() = runBlocking {
        Calculator(1).use { calc ->
            repeat(100_000) {
                assertTrue(calc.delayed_is_positive(0))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrency tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 1K delayed_add`() {
        val threads = (1..10).map { tid ->
            Thread {
                runBlocking {
                    Calculator(tid).use { calc ->
                        repeat(1_000) {
                            calc.delayed_add(0, 0)
                        }
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 1K delayed_describe`() {
        val threads = (1..10).map { tid ->
            Thread {
                runBlocking {
                    Calculator(tid).use { calc ->
                        repeat(1_000) {
                            val desc = calc.delayed_describe(0)
                            assertTrue(desc.contains("$tid"))
                        }
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
