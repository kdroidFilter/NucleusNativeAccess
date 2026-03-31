package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // List<Int> return
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `get_recent_scores returns 3 elements`() {
        Calculator(5).use { calc ->
            val scores = calc.get_recent_scores()
            assertEquals(3, scores.size)
            assertEquals(5, scores[0])
            assertEquals(10, scores[1])
            assertEquals(15, scores[2])
        }
    }

    @Test fun `get_recent_scores with zero`() {
        Calculator(0).use { calc ->
            val scores = calc.get_recent_scores()
            assertEquals(listOf(0, 0, 0), scores)
        }
    }

    @Test fun `get_recent_scores after mutation`() {
        Calculator(0).use { calc ->
            calc.add(10)
            val scores = calc.get_recent_scores()
            assertEquals(listOf(10, 20, 30), scores)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ByteArray return + param
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `to_bytes returns accumulator as byte string`() {
        Calculator(42).use { calc ->
            val bytes = calc.to_bytes()
            assertEquals("42", String(bytes))
        }
    }

    @Test fun `to_bytes with zero`() {
        Calculator(0).use { calc ->
            assertEquals("0", String(calc.to_bytes()))
        }
    }

    @Test fun `sum_bytes sums all byte values`() {
        Calculator(0).use { calc ->
            val result = calc.sum_bytes(byteArrayOf(1, 2, 3, 4, 5))
            assertEquals(15, result)
            assertEquals(15, calc.current)
        }
    }

    @Test fun `reverse_bytes reverses array`() {
        Calculator(0).use { calc ->
            val reversed = calc.reverse_bytes(byteArrayOf(1, 2, 3, 4, 5))
            assertEquals(listOf<Byte>(5, 4, 3, 2, 1), reversed.toList())
        }
    }

    @Test fun `reverse_bytes empty array`() {
        Calculator(0).use { calc ->
            val reversed = calc.reverse_bytes(byteArrayOf())
            assertTrue(reversed.isEmpty())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Top-level functions with collections
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `sum_all adds all elements`() {
        assertEquals(15, Rustcalc.sum_all(listOf(1, 2, 3, 4, 5)))
    }

    @Test fun `sum_all empty list`() {
        assertEquals(0, Rustcalc.sum_all(emptyList()))
    }

    @Test fun `find_max returns max element`() {
        assertEquals(5, Rustcalc.find_max(listOf(1, 5, 3)))
    }

    @Test fun `find_max empty list returns null`() {
        assertEquals(null, Rustcalc.find_max(emptyList()))
    }

    @Test fun `find_max single element`() {
        assertEquals(42, Rustcalc.find_max(listOf(42)))
    }
}
