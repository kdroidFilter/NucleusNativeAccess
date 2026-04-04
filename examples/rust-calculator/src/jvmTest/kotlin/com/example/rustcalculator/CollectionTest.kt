package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

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

    @Test fun `edge coll - get_recent_scores with negative`() {
        Calculator(-5).use { calc ->
            assertEquals(listOf(-5, -10, -15), calc.get_recent_scores())
        }
    }

    @Test fun `edge coll - get_recent_scores with MAX_VALUE`() {
        Calculator(Int.MAX_VALUE).use { calc ->
            val scores = calc.get_recent_scores()
            assertEquals(Int.MAX_VALUE, scores[0])
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // List<String> return
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `get_score_names returns formatted strings`() {
        Calculator(5).use { calc ->
            val names = calc.get_score_names()
            assertEquals(3, names.size)
            assertEquals("score_5", names[0])
            assertEquals("score_10", names[1])
            assertEquals("score_15", names[2])
        }
    }

    @Test fun `get_score_names with zero`() {
        Calculator(0).use { calc ->
            val names = calc.get_score_names()
            assertEquals(listOf("score_0", "score_0", "score_0"), names)
        }
    }

    @Test fun `get_score_names with negative`() {
        Calculator(-3).use { calc ->
            val names = calc.get_score_names()
            assertEquals("score_-3", names[0])
            assertEquals("score_-6", names[1])
            assertEquals("score_-9", names[2])
        }
    }

    @Test fun `edge coll - get_score_names with MAX_VALUE`() {
        Calculator(Int.MAX_VALUE).use { calc ->
            val names = calc.get_score_names()
            assertTrue(names[0].contains("${Int.MAX_VALUE}"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // List<Double> return
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `get_ratios returns doubles`() {
        Calculator(12).use { calc ->
            val ratios = calc.get_ratios()
            assertEquals(3, ratios.size)
            assertEquals(12.0, ratios[0], 0.001)
            assertEquals(6.0, ratios[1], 0.001)
            assertEquals(4.0, ratios[2], 0.001)
        }
    }

    @Test fun `get_ratios with zero`() {
        Calculator(0).use { calc ->
            val ratios = calc.get_ratios()
            assertEquals(0.0, ratios[0], 0.001)
            assertEquals(0.0, ratios[1], 0.001)
            assertEquals(0.0, ratios[2], 0.001)
        }
    }

    @Test fun `get_ratios with negative`() {
        Calculator(-6).use { calc ->
            val ratios = calc.get_ratios()
            assertEquals(-6.0, ratios[0], 0.001)
            assertEquals(-3.0, ratios[1], 0.001)
            assertEquals(-2.0, ratios[2], 0.001)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // List<Boolean> return
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `get_flags returns booleans`() {
        Calculator(50).use { calc ->
            val flags = calc.get_flags()
            assertEquals(3, flags.size)
            assertTrue(flags[0])   // 50 > 0
            assertTrue(flags[1])   // 50 > 10
            assertFalse(flags[2])  // 50 > 100
        }
    }

    @Test fun `get_flags with zero`() {
        Calculator(0).use { calc ->
            val flags = calc.get_flags()
            assertFalse(flags[0])  // 0 > 0
            assertFalse(flags[1])  // 0 > 10
            assertFalse(flags[2])  // 0 > 100
        }
    }

    @Test fun `get_flags with 200`() {
        Calculator(200).use { calc ->
            val flags = calc.get_flags()
            assertTrue(flags[0])
            assertTrue(flags[1])
            assertTrue(flags[2])
        }
    }

    @Test fun `get_flags with negative`() {
        Calculator(-1).use { calc ->
            val flags = calc.get_flags()
            assertFalse(flags[0])
            assertFalse(flags[1])
            assertFalse(flags[2])
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

    @Test fun `to_bytes with negative`() {
        Calculator(-123).use { calc ->
            assertEquals("-123", String(calc.to_bytes()))
        }
    }

    @Test fun `sum_bytes sums all byte values`() {
        Calculator(0).use { calc ->
            val result = calc.sum_bytes(byteArrayOf(1, 2, 3, 4, 5))
            assertEquals(15, result)
            assertEquals(15, calc.current)
        }
    }

    @Test fun `sum_bytes with empty array`() {
        Calculator(0).use { calc ->
            val result = calc.sum_bytes(byteArrayOf())
            assertEquals(0, result)
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

    @Test fun `reverse_bytes single element`() {
        Calculator(0).use { calc ->
            val reversed = calc.reverse_bytes(byteArrayOf(42))
            assertEquals(1, reversed.size)
            assertEquals(42.toByte(), reversed[0])
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

    @Test fun `sum_all single element`() {
        assertEquals(42, Rustcalc.sum_all(listOf(42)))
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

    @Test fun `find_max with negatives`() {
        assertEquals(-1, Rustcalc.find_max(listOf(-3, -1, -5)))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Option<Vec<T>> return
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `get_optional_scores returns Some when accumulator is positive`() {
        Calculator(5).use { calc ->
            val scores = calc.get_optional_scores()
            assertEquals(listOf(5, 10, 15), scores)
        }
    }

    @Test fun `get_optional_scores returns null when accumulator is zero`() {
        Calculator(0).use { calc ->
            assertEquals(null, calc.get_optional_scores())
        }
    }

    @Test fun `get_optional_tags returns Some when label is set`() {
        Calculator(1).use { calc ->
            calc.label = "test"
            val tags = calc.get_optional_tags()
            assertEquals(listOf("test", "scale:1"), tags)
        }
    }

    @Test fun `get_optional_tags returns null when label is empty`() {
        Calculator(1).use { calc ->
            assertEquals(null, calc.get_optional_tags())
        }
    }

    @Test fun `get_optional_metadata returns Some when accumulator is not zero`() {
        Calculator(10).use { calc ->
            val metadata = calc.get_optional_metadata()
            assertEquals(mapOf("current" to 10, "scale" to 1), metadata)
        }
    }

    @Test fun `get_optional_metadata returns null when accumulator is zero`() {
        Calculator(0).use { calc ->
            assertEquals(null, calc.get_optional_metadata())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K get_recent_scores calls`() {
        Calculator(1).use { calc ->
            repeat(100_000) {
                val scores = calc.get_recent_scores()
                assertEquals(3, scores.size)
            }
        }
    }

    @Test fun `load - 100K get_score_names calls`() {
        Calculator(1).use { calc ->
            repeat(100_000) {
                val names = calc.get_score_names()
                assertEquals(3, names.size)
            }
        }
    }

    @Test fun `load - 100K get_ratios calls`() {
        Calculator(1).use { calc ->
            repeat(100_000) {
                val ratios = calc.get_ratios()
                assertEquals(3, ratios.size)
            }
        }
    }

    @Test fun `load - 100K get_flags calls`() {
        Calculator(1).use { calc ->
            repeat(100_000) {
                val flags = calc.get_flags()
                assertEquals(3, flags.size)
            }
        }
    }

    @Test fun `load - 100K to_bytes calls`() {
        Calculator(42).use { calc ->
            repeat(100_000) {
                val bytes = calc.to_bytes()
                assertEquals("42", String(bytes))
            }
        }
    }

    @Test fun `load - 100K sum_all calls`() {
        repeat(100_000) {
            assertEquals(6, Rustcalc.sum_all(listOf(1, 2, 3)))
        }
    }

    @Test fun `load - 100K get_optional_scores calls`() {
        Calculator(1).use { calc ->
            repeat(100_000) {
                val scores = calc.get_optional_scores()
                assertEquals(3, scores!!.size)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrency tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 10K get_recent_scores`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid).use { calc ->
                    repeat(10_000) {
                        val scores = calc.get_recent_scores()
                        assertEquals(tid, scores[0])
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K get_score_names`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid).use { calc ->
                    repeat(10_000) {
                        val names = calc.get_score_names()
                        assertTrue(names[0].contains("$tid"))
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K get_ratios`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid).use { calc ->
                    repeat(10_000) {
                        val ratios = calc.get_ratios()
                        assertEquals(tid.toDouble(), ratios[0], 0.001)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K get_flags`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid * 100).use { calc ->
                    repeat(10_000) {
                        val flags = calc.get_flags()
                        assertTrue(flags[0]) // always > 0
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K sum_all`() {
        val threads = (1..10).map { tid ->
            Thread {
                repeat(10_000) {
                    val result = Rustcalc.sum_all(listOf(tid, tid))
                    assertEquals(tid * 2, result)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
