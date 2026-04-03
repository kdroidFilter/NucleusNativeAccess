package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.concurrent.thread

class ImplTraitTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. impl Iterator<Item = i32> — basic
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `iter_scores returns collected list of 3 elements`() {
        Calculator(5).use { calc ->
            assertEquals(listOf(5, 10, 15), calc.iter_scores())
        }
    }

    @Test fun `iter_scores with zero accumulator`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(0, 0, 0), calc.iter_scores())
        }
    }

    @Test fun `iter_scores with negative accumulator`() {
        Calculator(-3).use { calc ->
            assertEquals(listOf(-3, -6, -9), calc.iter_scores())
        }
    }

    @Test fun `iter_scores with MAX_VALUE boundary`() {
        Calculator(Int.MAX_VALUE).use { calc ->
            val scores = calc.iter_scores()
            assertEquals(3, scores.size)
            assertEquals(Int.MAX_VALUE, scores[0])
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. impl Iterator<Item = String>
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `iter_labels returns collected list of strings`() {
        Calculator(2).use { calc ->
            assertEquals(listOf("score_2", "score_4", "score_6"), calc.iter_labels())
        }
    }

    @Test fun `iter_labels with zero`() {
        Calculator(0).use { calc ->
            assertEquals(listOf("score_0", "score_0", "score_0"), calc.iter_labels())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. impl Iterator — empty (zero elements)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `iter_empty returns empty list`() {
        Calculator(1).use { calc ->
            val empty = calc.iter_empty()
            assertTrue(empty.isEmpty())
            assertEquals(0, empty.size)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 4. impl Iterator<Item = bool> — boolean element type
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `iter_flags with positive accumulator`() {
        Calculator(50).use { calc ->
            assertEquals(listOf(true, true, false), calc.iter_flags())
        }
    }

    @Test fun `iter_flags with zero`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(false, false, false), calc.iter_flags())
        }
    }

    @Test fun `iter_flags with large value`() {
        Calculator(200).use { calc ->
            assertEquals(listOf(true, true, true), calc.iter_flags())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5. impl Iterator<Item = f64> — float element type
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `iter_ratios with 12`() {
        Calculator(12).use { calc ->
            val ratios = calc.iter_ratios()
            assertEquals(3, ratios.size)
            assertEquals(6.0, ratios[0])
            assertEquals(4.0, ratios[1])
            assertEquals(3.0, ratios[2])
        }
    }

    @Test fun `iter_ratios with zero avoids NaN`() {
        Calculator(0).use { calc ->
            val ratios = calc.iter_ratios()
            assertEquals(listOf(0.0, 0.0, 0.0), ratios)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 6. impl Iterator<Item = i64> — Long element type
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `iter_big_values returns longs`() {
        Calculator(7).use { calc ->
            assertEquals(listOf(7_000_000L, 14_000_000L), calc.iter_big_values())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 7. Large iterator — buffer overflow / retry
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `iter_large with 10 elements`() {
        Calculator(0).use { calc ->
            val result = calc.iter_large(10)
            assertEquals((0 until 10).toList(), result)
        }
    }

    @Test fun `iter_large with 5000 elements exceeds default buffer`() {
        Calculator(1).use { calc ->
            val result = calc.iter_large(5000)
            assertEquals(5000, result.size)
            assertEquals(1, result[0])
            assertEquals(5000, result[4999])
        }
    }

    @Test fun `iter_large with zero elements`() {
        Calculator(0).use { calc ->
            assertTrue(calc.iter_large(0).isEmpty())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 8. impl ExactSizeIterator
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `exact_scores returns collected list`() {
        Calculator(4).use { calc ->
            assertEquals(listOf(4, 8, 12), calc.exact_scores())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 9. impl DoubleEndedIterator
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `iter_reversed returns list`() {
        Calculator(3).use { calc ->
            assertEquals(listOf(3, 6, 9), calc.iter_reversed())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 10. impl IntoIterator
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `iter_into returns list`() {
        Calculator(10).use { calc ->
            assertEquals(listOf(10, 11, 12), calc.iter_into())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 11. impl Iterator + Send (multiple bounds)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `iter_sendable with multiple bounds`() {
        Calculator(7).use { calc ->
            assertEquals(listOf(7, 70), calc.iter_sendable())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 12. impl Display — basic + edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `display_value returns string representation`() {
        Calculator(42).use { calc ->
            assertEquals("Calc(42)", calc.display_value())
        }
    }

    @Test fun `display_value with zero`() {
        Calculator(0).use { calc ->
            assertEquals("Calc(0)", calc.display_value())
        }
    }

    @Test fun `display_value with negative`() {
        Calculator(-999).use { calc ->
            assertEquals("Calc(-999)", calc.display_value())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 13. impl Display with unicode
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `display_unicode returns CJK string`() {
        Calculator(42).use { calc ->
            assertEquals("計算機(42)", calc.display_unicode())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 14. impl Display with long string (>8192 bytes, triggers buffer retry)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `display_long returns string longer than buffer`() {
        Calculator(0).use { calc ->
            val result = calc.display_long()
            assertTrue(result.length > 10_000, "Expected >10000 chars, got ${result.length}")
            assertTrue(result.startsWith("x"))
            assertTrue(result.endsWith("A"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 15. impl ToString
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `as_string_repr returns string of accumulator`() {
        Calculator(99).use { calc ->
            assertEquals("99", calc.as_string_repr())
        }
    }

    @Test fun `as_string_repr with negative`() {
        Calculator(-42).use { calc ->
            assertEquals("-42", calc.as_string_repr())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 16. &mut self + impl Trait
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `drain_and_iter mutates then returns iterator`() {
        Calculator(10).use { calc ->
            val result = calc.drain_and_iter(5)
            // accumulator was 10 + 5 = 15
            assertEquals(listOf(15, 30, 45), result)
            // verify state persists
            assertEquals(15, calc.current)
        }
    }

    @Test fun `drain_and_iter called twice accumulates`() {
        Calculator(0).use { calc ->
            val first = calc.drain_and_iter(10)
            assertEquals(listOf(10, 20, 30), first)
            val second = calc.drain_and_iter(5)
            // accumulator was 10 + 5 = 15
            assertEquals(listOf(15, 30, 45), second)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 17. Result<impl Iterator, String> — fallible + impl Trait
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `try_iter_scores succeeds for positive accumulator`() {
        Calculator(5).use { calc ->
            assertEquals(listOf(5, 10, 15), calc.try_iter_scores())
        }
    }

    @Test fun `try_iter_scores succeeds for zero`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(0, 0, 0), calc.try_iter_scores())
        }
    }

    @Test fun `try_iter_scores throws for negative accumulator`() {
        Calculator(-1).use { calc ->
            assertFailsWith<RuntimeException> {
                calc.try_iter_scores()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 18. Result<impl Display, String> — fallible + Display
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `try_display succeeds for non-negative`() {
        Calculator(42).use { calc ->
            assertEquals("OK(42)", calc.try_display())
        }
    }

    @Test fun `try_display throws for negative`() {
        Calculator(-1).use { calc ->
            assertFailsWith<RuntimeException> {
                calc.try_display()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 19. Panic during collect — error propagation
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `iter_panicking succeeds for positive accumulator`() {
        Calculator(5).use { calc ->
            assertEquals(listOf(5, 6, 7), calc.iter_panicking())
        }
    }

    @Test fun `iter_panicking panics at index 2 for negative accumulator`() {
        Calculator(-1).use { calc ->
            assertFailsWith<RuntimeException> {
                calc.iter_panicking()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 20. Companion/static method returning impl Trait
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `fibonacci_iter returns first 6 fibonacci numbers`() {
        val fibs = Calculator.fibonacci_iter(6)
        assertEquals(listOf(0, 1, 1, 2, 3, 5), fibs)
    }

    @Test fun `fibonacci_iter with zero count`() {
        assertTrue(Calculator.fibonacci_iter(0).isEmpty())
    }

    @Test fun `fibonacci_iter with 1`() {
        assertEquals(listOf(0), Calculator.fibonacci_iter(1))
    }

    @Test fun `static_label returns formatted string`() {
        assertEquals("count=42", Calculator.static_label("count", 42))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 21. Top-level impl Iterator
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `generate_range returns collected list`() {
        assertEquals(listOf(1, 2, 3), Rustcalc.generate_range(1, 4))
    }

    @Test fun `generate_range empty when start equals end`() {
        assertTrue(Rustcalc.generate_range(5, 5).isEmpty())
    }

    @Test fun `generate_range single element`() {
        assertEquals(listOf(0), Rustcalc.generate_range(0, 1))
    }

    @Test fun `generate_range negative values`() {
        assertEquals(listOf(-3, -2, -1), Rustcalc.generate_range(-3, 0))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 22. Top-level impl Display
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `format_pair returns display string`() {
        assertEquals("3 + 4 = 7", Rustcalc.format_pair(3, 4))
    }

    @Test fun `format_pair with negatives`() {
        assertEquals("-1 + -2 = -3", Rustcalc.format_pair(-1, -2))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 23. Top-level impl Into<String>
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `into_greeting returns converted string`() {
        assertEquals("Hi, World!", Rustcalc.into_greeting("World"))
    }

    @Test fun `into_greeting with unicode`() {
        assertEquals("Hi, 日本!", Rustcalc.into_greeting("日本"))
    }

    @Test fun `into_greeting with empty`() {
        assertEquals("Hi, !", Rustcalc.into_greeting(""))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 24. Top-level impl AsRef<str>
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `as_ref_label returns string`() {
        assertEquals("label_42", Rustcalc.as_ref_label(42))
    }

    @Test fun `as_ref_label with zero`() {
        assertEquals("label_0", Rustcalc.as_ref_label(0))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 25. Top-level impl Iterator<Item = String> with String param
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `repeat_str returns repeated strings`() {
        assertEquals(listOf("abc", "abc", "abc"), Rustcalc.repeat_str("abc", 3))
    }

    @Test fun `repeat_str with zero count`() {
        assertTrue(Rustcalc.repeat_str("x", 0).isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 26. Top-level Result<impl Display, String>
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `try_format succeeds for non-zero divisor`() {
        assertEquals("10 / 2 = 5", Rustcalc.try_format(10, 2))
    }

    @Test fun `try_format throws for zero divisor`() {
        assertFailsWith<RuntimeException> {
            Rustcalc.try_format(10, 0)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 27. Concurrent calls with impl Trait
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent iter_scores from multiple threads`() {
        Calculator(10).use { calc ->
            val errors = mutableListOf<Throwable>()
            val threads = (1..10).map {
                thread {
                    try {
                        repeat(100) {
                            val result = calc.iter_scores()
                            assertEquals(listOf(10, 20, 30), result)
                        }
                    } catch (e: Throwable) {
                        synchronized(errors) { errors.add(e) }
                    }
                }
            }
            threads.forEach { it.join() }
            assertTrue(errors.isEmpty(), "Concurrent errors: ${errors.map { it.message }}")
        }
    }

    @Test fun `concurrent display_value from multiple threads`() {
        Calculator(7).use { calc ->
            val errors = mutableListOf<Throwable>()
            val threads = (1..10).map {
                thread {
                    try {
                        repeat(100) {
                            assertEquals("Calc(7)", calc.display_value())
                        }
                    } catch (e: Throwable) {
                        synchronized(errors) { errors.add(e) }
                    }
                }
            }
            threads.forEach { it.join() }
            assertTrue(errors.isEmpty(), "Concurrent errors: ${errors.map { it.message }}")
        }
    }

    @Test fun `concurrent generate_range from multiple threads`() {
        val expected = (0 until 100).toList()
        val errors = mutableListOf<Throwable>()
        val threads = (1..8).map {
            thread {
                try {
                    repeat(50) {
                        assertEquals(expected, Rustcalc.generate_range(0, 100))
                    }
                } catch (e: Throwable) {
                    synchronized(errors) { errors.add(e) }
                }
            }
        }
        threads.forEach { it.join() }
        assertTrue(errors.isEmpty(), "Concurrent errors: ${errors.map { it.message }}")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 28. Sequential calls — verify no state leaks between calls
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `sequential impl trait calls dont leak state`() {
        Calculator(5).use { calc ->
            // Call different impl Trait methods in sequence
            assertEquals(listOf(5, 10, 15), calc.iter_scores())
            assertEquals("Calc(5)", calc.display_value())
            assertEquals(listOf("score_5", "score_10", "score_15"), calc.iter_labels())
            assertEquals("5", calc.as_string_repr())
            assertEquals(listOf(5, 10, 15), calc.exact_scores())
            // Call again to ensure repeatable
            assertEquals(listOf(5, 10, 15), calc.iter_scores())
            assertEquals("Calc(5)", calc.display_value())
        }
    }
}
