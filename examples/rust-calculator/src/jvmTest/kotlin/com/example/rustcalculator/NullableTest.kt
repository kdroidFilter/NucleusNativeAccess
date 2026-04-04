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

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge null - divide_or_null with 1`() {
        Calculator(42).use { calc -> assertEquals(42, calc.divide_or_null(1)) }
    }

    @Test fun `edge null - divide_or_null with negative divisor`() {
        Calculator(10).use { calc -> assertEquals(-5, calc.divide_or_null(-2)) }
    }

    @Test fun `edge null - describe_or_null with negative`() {
        Calculator(-5).use { calc -> assertNull(calc.describe_or_null()) }
    }

    @Test fun `edge null - describe_or_null with MAX_VALUE`() {
        Calculator(Int.MAX_VALUE).use { calc ->
            val desc = calc.describe_or_null()
            assertTrue(desc!!.contains("${Int.MAX_VALUE}"))
        }
    }

    @Test fun `edge null - to_long_or_null with large value`() {
        Calculator(Int.MAX_VALUE).use { calc ->
            assertEquals(Int.MAX_VALUE.toLong(), calc.to_long_or_null())
        }
    }

    @Test fun `edge null - nullable transitions`() {
        Calculator(0).use { calc ->
            assertNull(calc.get_nickname())
            calc.set_nickname("a")
            assertEquals("a", calc.get_nickname())
            calc.set_nickname(null)
            assertNull(calc.get_nickname())
            calc.set_nickname("b")
            assertEquals("b", calc.get_nickname())
        }
    }

    @Test fun `edge null - nullable DataClass param non-null`() {
        Calculator(0).use { calc ->
            val result = calc.add_point_or_null(Point(3, 7))
            assertEquals(10, result)
        }
    }

    @Test fun `edge null - nullable DataClass param null`() {
        Calculator(5).use { calc ->
            val result = calc.add_point_or_null(null)
            assertEquals(5, result)
        }
    }

    @Test fun `edge null - nickname with emoji`() {
        Calculator(0).use { calc ->
            calc.set_nickname("🔥💯🚀")
            assertEquals("🔥💯🚀", calc.get_nickname())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K divide_or_null calls`() {
        Calculator(100).use { calc ->
            repeat(100_000) {
                assertEquals(50, calc.divide_or_null(2))
            }
        }
    }

    @Test fun `load - 100K describe_or_null calls`() {
        Calculator(1).use { calc ->
            repeat(100_000) {
                val desc = calc.describe_or_null()
                assertTrue(desc!!.contains("1"))
            }
        }
    }

    @Test fun `load - 100K nullable String transitions`() {
        Calculator(0).use { calc ->
            repeat(100_000) { i ->
                if (i % 2 == 0) {
                    calc.set_nickname("n$i")
                } else {
                    calc.set_nickname(null)
                    assertNull(calc.get_nickname())
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrency tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 10K divide_or_null`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid * 100).use { calc ->
                    repeat(10_000) {
                        val result = calc.divide_or_null(2)
                        assertEquals(tid * 50, result)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K nullable String`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(0).use { calc ->
                    repeat(10_000) { i ->
                        if (i % 2 == 0) {
                            calc.set_nickname("t$tid")
                            assertEquals("t$tid", calc.get_nickname())
                        } else {
                            calc.set_nickname(null)
                            assertNull(calc.get_nickname())
                        }
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
