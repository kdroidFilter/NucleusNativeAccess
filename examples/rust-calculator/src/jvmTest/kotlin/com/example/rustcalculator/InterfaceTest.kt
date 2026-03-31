package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InterfaceTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Describable trait
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `describe_self returns formatted string`() {
        Calculator(42).use { calc ->
            val desc = calc.describe_self()
            assertTrue(desc.contains("42"), "Should contain accumulator value")
        }
    }

    @Test fun `describe_self with label`() {
        Calculator(0).use { calc ->
            calc.label = "test"
            val desc = calc.describe_self()
            assertTrue(desc.contains("test"), "Should contain label")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Resettable trait
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `reset_to_default clears state`() {
        Calculator(0).use { calc ->
            calc.add(100)
            calc.label = "test"
            calc.scale = 2.5
            calc.enabled = false
            calc.reset_to_default()
            assertEquals(0, calc.current)
            assertEquals("", calc.label)
            assertEquals(1.0, calc.scale, 0.001)
            assertTrue(calc.enabled)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Measurable trait
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `measure returns scaled value`() {
        Calculator(10).use { calc ->
            assertEquals(10.0, calc.measure(), 0.001)
            calc.scale = 2.5
            assertEquals(25.0, calc.measure(), 0.001)
        }
    }

    @Test fun `unit returns constant string`() {
        Calculator(0).use { calc ->
            assertEquals("units", calc.unit())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Polymorphism via interfaces
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `Calculator is Describable`() {
        Calculator(5).use { calc ->
            val describable: Describable = calc
            assertTrue(describable.describe_self().contains("5"))
        }
    }

    @Test fun `Calculator is Measurable`() {
        Calculator(7).use { calc ->
            val measurable: Measurable = calc
            assertEquals(7.0, measurable.measure(), 0.001)
        }
    }

    @Test fun `Calculator is Resettable`() {
        Calculator(99).use { calc ->
            val resettable: Resettable = calc
            resettable.reset_to_default()
            assertEquals(0, calc.current)
        }
    }
}
