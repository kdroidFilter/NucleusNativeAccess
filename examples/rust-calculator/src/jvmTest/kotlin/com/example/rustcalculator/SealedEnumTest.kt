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
}
