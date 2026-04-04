package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SealedEnumTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // CalcResult sealed class basics
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `try_divide exact returns Value`() {
        Calculator(10).use { calc ->
            val result = calc.try_divide(2)
            assertTrue(result is CalcResult.Value, "Expected Value, got ${result::class.simpleName}")
            assertEquals(5, (result as CalcResult.Value).value)
            result.close()
        }
    }

    @Test fun `try_divide by zero returns Error`() {
        Calculator(10).use { calc ->
            val result = calc.try_divide(0)
            assertTrue(result is CalcResult.Error, "Expected Error, got ${result::class.simpleName}")
            assertEquals("Division by zero", (result as CalcResult.Error).value)
            result.close()
        }
    }

    @Test fun `try_divide on zero accumulator returns Nothing`() {
        Calculator(0).use { calc ->
            val result = calc.try_divide(5)
            assertTrue(result is CalcResult.Nothing, "Expected Nothing, got ${result::class.simpleName}")
            result.close()
        }
    }

    @Test fun `try_divide inexact returns Partial`() {
        Calculator(10).use { calc ->
            val result = calc.try_divide(3)
            assertTrue(result is CalcResult.Partial, "Expected Partial, got ${result::class.simpleName}")
            val partial = result as CalcResult.Partial
            assertEquals(3, partial.value) // 10 / 3 = 3
            assertTrue(partial.confidence > 0.0 && partial.confidence < 1.0)
            result.close()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tag enum
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `tag returns correct enum value`() {
        Calculator(10).use { calc ->
            calc.try_divide(2).use { result ->
                assertEquals(CalcResult.Tag.Value, result.tag)
            }
            calc.try_divide(0).use { result ->
                assertEquals(CalcResult.Tag.Error, result.tag)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // last_result
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `last_result positive returns Value`() {
        Calculator(42).use { calc ->
            calc.last_result().use { result ->
                assertTrue(result is CalcResult.Value)
                assertEquals(42, (result as CalcResult.Value).value)
            }
        }
    }

    @Test fun `last_result zero returns Nothing`() {
        Calculator(0).use { calc ->
            calc.last_result().use { result ->
                assertTrue(result is CalcResult.Nothing)
            }
        }
    }

    @Test fun `last_result negative returns Error`() {
        Calculator(-5).use { calc ->
            calc.last_result().use { result ->
                assertTrue(result is CalcResult.Error)
                assertTrue((result as CalcResult.Error).value.contains("Negative"))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Factory methods (construct from Kotlin)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `factory method Value`() {
        CalcResult.value(99).use { result ->
            assertTrue(result is CalcResult.Value)
            assertEquals(99, result.value)
        }
    }

    @Test fun `factory method Error`() {
        CalcResult.error("test error").use { result ->
            assertTrue(result is CalcResult.Error)
            assertEquals("test error", result.value)
        }
    }

    @Test fun `factory method Partial`() {
        CalcResult.partial(50, 0.75).use { result ->
            assertTrue(result is CalcResult.Partial)
            assertEquals(50, result.value)
            assertEquals(0.75, result.confidence, 0.001)
        }
    }

    @Test fun `factory method Nothing`() {
        CalcResult.nothing().use { result ->
            assertTrue(result is CalcResult.Nothing)
            assertEquals(CalcResult.Tag.Nothing, result.tag)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge sealed - try_divide with 1`() {
        Calculator(42).use { calc ->
            calc.try_divide(1).use { result ->
                assertTrue(result is CalcResult.Value)
                assertEquals(42, (result as CalcResult.Value).value)
            }
        }
    }

    @Test fun `edge sealed - try_divide with -1`() {
        Calculator(10).use { calc ->
            calc.try_divide(-1).use { result ->
                assertTrue(result is CalcResult.Value)
                assertEquals(-10, (result as CalcResult.Value).value)
            }
        }
    }

    @Test fun `edge sealed - Error with empty message`() {
        CalcResult.error("").use { result ->
            assertTrue(result is CalcResult.Error)
            assertEquals("", result.value)
        }
    }

    @Test fun `edge sealed - Error with unicode message`() {
        CalcResult.error("エラー 🔥").use { result ->
            assertTrue(result is CalcResult.Error)
            assertEquals("エラー 🔥", result.value)
        }
    }

    @Test fun `edge sealed - Partial with zero confidence`() {
        CalcResult.partial(0, 0.0).use { result ->
            assertTrue(result is CalcResult.Partial)
            assertEquals(0, result.value)
            assertEquals(0.0, result.confidence, 0.001)
        }
    }

    @Test fun `edge sealed - Partial with max confidence`() {
        CalcResult.partial(100, 1.0).use { result ->
            assertTrue(result is CalcResult.Partial)
            assertEquals(1.0, result.confidence, 0.001)
        }
    }

    @Test fun `edge sealed - Value with MAX_VALUE`() {
        CalcResult.value(Int.MAX_VALUE).use { result ->
            assertTrue(result is CalcResult.Value)
            assertEquals(Int.MAX_VALUE, result.value)
        }
    }

    @Test fun `edge sealed - Value with MIN_VALUE`() {
        CalcResult.value(Int.MIN_VALUE).use { result ->
            assertTrue(result is CalcResult.Value)
            assertEquals(Int.MIN_VALUE, result.value)
        }
    }

    @Test fun `edge sealed - all tags cycle`() {
        Calculator(10).use { calc ->
            calc.try_divide(2).use { assertEquals(CalcResult.Tag.Value, it.tag) }
            calc.try_divide(0).use { assertEquals(CalcResult.Tag.Error, it.tag) }
            calc.try_divide(3).use { assertEquals(CalcResult.Tag.Partial, it.tag) }
        }
        Calculator(0).use { calc ->
            calc.try_divide(1).use { assertEquals(CalcResult.Tag.Nothing, it.tag) }
        }
    }

    @Test fun `edge sealed - lifecycle create and close many`() {
        repeat(50) { i ->
            CalcResult.value(i).use { result ->
                assertTrue(result is CalcResult.Value)
                assertEquals(i, result.value)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K try_divide calls`() {
        Calculator(100).use { calc ->
            repeat(100_000) {
                calc.try_divide(2).use { result ->
                    assertTrue(result is CalcResult.Value)
                }
            }
        }
    }

    @Test fun `load - 100K factory Value calls`() {
        repeat(100_000) {
            CalcResult.value(42).use { result ->
                assertTrue(result is CalcResult.Value)
            }
        }
    }

    @Test fun `load - 100K last_result calls`() {
        Calculator(1).use { calc ->
            repeat(100_000) {
                calc.last_result().use { result ->
                    assertTrue(result is CalcResult.Value)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrency tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 10K try_divide`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid * 10).use { calc ->
                    repeat(10_000) {
                        calc.try_divide(2).use { result ->
                            assertTrue(result is CalcResult.Value)
                            assertEquals(tid * 5, (result as CalcResult.Value).value)
                        }
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K factory methods`() {
        val threads = (1..10).map { tid ->
            Thread {
                repeat(10_000) {
                    CalcResult.value(tid).use { result ->
                        assertTrue(result is CalcResult.Value)
                        assertEquals(tid, result.value)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
