package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class NullableTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Nullable returns (Option<T>)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `nullable Int return - non-null`() {
        Calculator(10).use { calc -> assertEquals(5, calc.divide_or_null(2)) }
    }

    @Test fun `nullable Int return - null`() {
        Calculator(10).use { calc -> assertNull(calc.divide_or_null(0)) }
    }

    @Test fun `nullable String return - non-null`() {
        Calculator(5).use { calc -> assertEquals("Positive(5)", calc.describe_or_null()) }
    }

    @Test fun `nullable String return - null`() {
        Calculator(0).use { calc -> assertNull(calc.describe_or_null()) }
    }

    @Test fun `nullable Boolean return - true`() {
        Calculator(5).use { calc -> assertEquals(true, calc.is_positive_or_null()) }
    }

    @Test fun `nullable Boolean return - false`() {
        Calculator(-1).use { calc -> assertEquals(false, calc.is_positive_or_null()) }
    }

    @Test fun `nullable Boolean return - null`() {
        Calculator(0).use { calc -> assertNull(calc.is_positive_or_null()) }
    }

    @Test fun `nullable Long return - non-null`() {
        Calculator(42).use { calc -> assertEquals(42L, calc.to_long_or_null()) }
    }

    @Test fun `nullable Long return - null`() {
        Calculator(0).use { calc -> assertNull(calc.to_long_or_null()) }
    }

    @Test fun `nullable Double return - non-null`() {
        Calculator(7).use { calc -> assertEquals(7.0, calc.to_double_or_null()!!, 0.001) }
    }

    @Test fun `nullable Double return - null`() {
        Calculator(0).use { calc -> assertNull(calc.to_double_or_null()) }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Nullable params (Option<T>)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `nullable Int param - non-null`() {
        Calculator(10).use { calc -> assertEquals(15, calc.add_optional(5)) }
    }

    @Test fun `nullable Int param - null`() {
        Calculator(10).use { calc -> assertEquals(10, calc.add_optional(null)) }
    }

    @Test fun `nullable String param - set and get`() {
        Calculator(0).use { calc ->
            assertNull(calc.get_nickname())
            calc.set_nickname("Rusty")
            assertEquals("Rusty", calc.get_nickname())
            calc.set_nickname(null)
            assertNull(calc.get_nickname())
        }
    }

    @Test fun `nullable String param - unicode`() {
        Calculator(0).use { calc ->
            calc.set_nickname("こんにちは")
            assertEquals("こんにちは", calc.get_nickname())
        }
    }
}
