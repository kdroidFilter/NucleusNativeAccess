package com.example.rustcalculator

import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E2E tests for impl Trait as function PARAMETERS (not return types).
 * Verifies that the bridge resolves well-known traits (ToString, Into, AsRef)
 * to concrete types and passes them correctly.
 */
class ImplTraitParamTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. impl ToString parameter — edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `impl - greet_impl with simple string`() {
        assertEquals("Hello, World!", Rustcalc.greet_impl("World"))
    }

    @Test fun `impl - greet_impl with empty string`() {
        assertEquals("Hello, !", Rustcalc.greet_impl(""))
    }

    @Test fun `str - greet_impl with unicode`() {
        assertEquals("Hello, 日本語!", Rustcalc.greet_impl("日本語"))
    }

    @Test fun `str - greet_impl with emoji`() {
        assertEquals("Hello, 🎉!", Rustcalc.greet_impl("🎉"))
    }

    @Test fun `str - greet_impl with long string`() {
        val long = "x".repeat(10_000)
        assertEquals("Hello, $long!", Rustcalc.greet_impl(long))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. impl Into<String> parameter — edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `impl - into_upper with lowercase`() {
        assertEquals("HELLO", Rustcalc.into_upper("hello"))
    }

    @Test fun `impl - into_upper with empty`() {
        assertEquals("", Rustcalc.into_upper(""))
    }

    @Test fun `impl - into_upper with mixed case`() {
        assertEquals("HELLO WORLD", Rustcalc.into_upper("Hello World"))
    }

    @Test fun `str - into_upper with unicode`() {
        assertEquals("日本", Rustcalc.into_upper("日本"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. impl AsRef<str> parameter — edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `impl - count_chars with ascii`() {
        assertEquals(5, Rustcalc.count_chars("hello"))
    }

    @Test fun `impl - count_chars with empty`() {
        assertEquals(0, Rustcalc.count_chars(""))
    }

    @Test fun `str - count_chars with unicode`() {
        // UTF-8 byte count, not char count
        val text = "日本"
        assertEquals(text.toByteArray(Charsets.UTF_8).size, Rustcalc.count_chars(text))
    }

    @Test fun `edge - count_chars with null byte`() {
        // CStr stops at null byte — this tests boundary behavior
        assertEquals(0, Rustcalc.count_chars("\u0000abc"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 4. Box<dyn Trait> parameter (ownership transfer) — edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `obj - consume_describable takes ownership`() {
        val desc = Rustcalc.create_describable(42) as DynDescribable
        val result = Rustcalc.consume_describable(desc)
        assertEquals("consumed: Calculator(current=42, label=)", result)
    }

    @Test fun `obj - consume_describable with zero`() {
        val desc = Rustcalc.create_describable(0) as DynDescribable
        assertEquals("consumed: Calculator(current=0, label=)", Rustcalc.consume_describable(desc))
    }

    @Test fun `edge - consume_describable with negative`() {
        val desc = Rustcalc.create_describable(-99) as DynDescribable
        assertEquals("consumed: Calculator(current=-99, label=)", Rustcalc.consume_describable(desc))
    }

    @Test fun `edge - consume_describable with MAX_VALUE`() {
        val desc = Rustcalc.create_describable(Int.MAX_VALUE) as DynDescribable
        assertEquals("consumed: Calculator(current=${Int.MAX_VALUE}, label=)", Rustcalc.consume_describable(desc))
    }

    @Test fun `edge - consume_describable with MIN_VALUE`() {
        val desc = Rustcalc.create_describable(Int.MIN_VALUE) as DynDescribable
        assertEquals("consumed: Calculator(current=${Int.MIN_VALUE}, label=)", Rustcalc.consume_describable(desc))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5. Load tests — 100K+ FFM calls
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K greet_impl calls`() {
        repeat(100_000) {
            assertEquals("Hello, x!", Rustcalc.greet_impl("x"))
        }
    }

    @Test fun `load - 100K into_upper calls`() {
        repeat(100_000) {
            assertEquals("ABC", Rustcalc.into_upper("abc"))
        }
    }

    @Test fun `load - 100K count_chars calls`() {
        repeat(100_000) {
            assertEquals(5, Rustcalc.count_chars("hello"))
        }
    }

    @Test fun `load - 10K consume_describable create+consume cycles`() {
        repeat(10_000) {
            val desc = Rustcalc.create_describable(it) as DynDescribable
            Rustcalc.consume_describable(desc)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 6. Concurrency tests — multi-threaded
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 10K greet_impl calls`() {
        val errors = mutableListOf<Throwable>()
        val threads = (1..10).map { i ->
            thread {
                try {
                    repeat(10_000) {
                        assertEquals("Hello, t$i!", Rustcalc.greet_impl("t$i"))
                    }
                } catch (e: Throwable) {
                    synchronized(errors) { errors.add(e) }
                }
            }
        }
        threads.forEach { it.join() }
        assertTrue(errors.isEmpty(), "Concurrent errors: ${errors.map { it.message }}")
    }

    @Test fun `concurrent - 10 threads x 10K into_upper calls`() {
        val errors = mutableListOf<Throwable>()
        val threads = (1..10).map { i ->
            thread {
                try {
                    val input = "thread$i"
                    val expected = input.uppercase()
                    repeat(10_000) {
                        assertEquals(expected, Rustcalc.into_upper(input))
                    }
                } catch (e: Throwable) {
                    synchronized(errors) { errors.add(e) }
                }
            }
        }
        threads.forEach { it.join() }
        assertTrue(errors.isEmpty(), "Concurrent errors: ${errors.map { it.message }}")
    }

    @Test fun `concurrent - 10 threads x 1K consume_describable cycles`() {
        val errors = mutableListOf<Throwable>()
        val threads = (1..10).map { i ->
            thread {
                try {
                    repeat(1_000) { j ->
                        val desc = Rustcalc.create_describable(i * 1000 + j) as DynDescribable
                        val result = Rustcalc.consume_describable(desc)
                        assertTrue(result.startsWith("consumed:"))
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
