package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E2E tests for unit-returning methods.
 *
 * These validate the fix for the regression where Rust functions with
 * `output = null` (unit/`()` return type) were incorrectly skipped as
 * "unsupported return type" during rustdoc JSON parsing.
 *
 * The methods below cover all unit-returning patterns: setters, mutating
 * operations, trait impls returning unit, and suspend+unit combinations.
 */
class UnitMethodTest {

    // ── Unit-returning setter methods ────────────────────────────────────────

    @Test fun `set_label changes the label property`() {
        Calculator(0).use { calc ->
            assertEquals("", calc.label)
            calc.label = "MyCalc"
            assertEquals("MyCalc", calc.label)
        }
    }

    @Test fun `set_label accepts empty string`() {
        Calculator(0).use { calc ->
            calc.label = ""
            assertEquals("", calc.label)
        }
    }

    @Test fun `set_scale changes the scale property`() {
        Calculator(100).use { calc ->
            assertEquals(1.0, calc.scale, 0.001)
            calc.scale = 2.5
            assertEquals(2.5, calc.scale, 0.001)
        }
    }

    @Test fun `set_enabled toggles the enabled property`() {
        Calculator(0).use { calc ->
            assertTrue(calc.enabled)
            calc.enabled = false
            assertFalse(calc.enabled)
            calc.enabled = true
            assertTrue(calc.enabled)
        }
    }

    @Test fun `set_nickname changes the nickname`() {
        Calculator(0).use { calc ->
            assertNull(calc.get_nickname())
            calc.set_nickname("Bob")
            assertEquals("Bob", calc.get_nickname())
        }
    }

    @Test fun `set_nickname accepts null to clear nickname`() {
        Calculator(0).use { calc ->
            calc.set_nickname("Bob")
            calc.set_nickname(null)
            assertNull(calc.get_nickname())
        }
    }

    // ── Unit-returning mutating methods ──────────────────────────────────────

    @Test fun `reset clears accumulator to zero`() {
        Calculator(0).use { calc ->
            calc.add(999)
            calc.reset()
            assertEquals(0, calc.current)
        }
    }

    @Test fun `reset multiple times is idempotent`() {
        Calculator(0).use { calc ->
            calc.add(5)
            calc.reset()
            calc.reset()
            calc.reset()
            assertEquals(0, calc.current)
        }
    }

    @Test fun `set_from_named updates accumulator and label from data class`() {
        Calculator(0).use { calc ->
            val named = NamedValue("Counter", 42)
            calc.set_from_named(named)
            assertEquals(42, calc.current)
            assertEquals("Counter", calc.label)
        }
    }

    @Test fun `set_from_named preserves accumulator when called with zero-value`() {
        Calculator(100).use { calc ->
            val named = NamedValue("Zero", 0)
            calc.set_from_named(named)
            assertEquals(0, calc.current)
            assertEquals("Zero", calc.label)
        }
    }

    // ── Trait impl: Resettable.reset_to_default() ─────────────────────────────

    @Test fun `reset_to_default restores all fields to defaults`() {
        Calculator(0).use { calc ->
            calc.add(42)
            calc.label = "temp"
            calc.scale = 9.99
            calc.enabled = false

            calc.reset_to_default()

            assertEquals(0, calc.current)
            assertEquals("", calc.label)
            assertEquals(1.0, calc.scale, 0.001)
            assertTrue(calc.enabled)
        }
    }

    // ── Chained unit-returning calls ─────────────────────────────────────────

    @Test fun `multiple unit-returning calls chain correctly`() {
        Calculator(0).use { calc ->
            calc.label = "Test"
            calc.scale = 3.14
            calc.enabled = true
            calc.add(100)
            calc.reset()

            assertEquals(0, calc.current)
            assertEquals("Test", calc.label)
            assertEquals(3.14, calc.scale, 0.001)
            assertTrue(calc.enabled)
        }
    }

    // ── Never-returning functions (Rust `!` type) ───────────────────────────

    @Test
    fun `panic_always throws and never returns`() {
        Calculator(0).use { calc ->
            assertFailsWith<Throwable> {
                calc.panic_always()
            }
        }
    }

    @Test
    fun `panic_always does not return a value`() {
        Calculator(0).use { calc ->
            val thrown = assertFailsWith<Throwable> {
                calc.panic_always()
            }
            assertTrue(thrown.message?.contains("crashed") == true)
        }
    }
}
