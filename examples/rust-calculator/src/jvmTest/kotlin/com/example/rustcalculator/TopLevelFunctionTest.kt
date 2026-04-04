package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopLevelFunctionTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // compute
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `compute Add`() {
        assertEquals(7, Rustcalc.compute(3, 4, Operation.Add))
    }

    @Test fun `compute Subtract`() {
        assertEquals(7, Rustcalc.compute(10, 3, Operation.Subtract))
    }

    @Test fun `compute Multiply`() {
        assertEquals(12, Rustcalc.compute(3, 4, Operation.Multiply))
    }

    @Test fun `compute with zero`() {
        assertEquals(5, Rustcalc.compute(0, 5, Operation.Add))
    }

    @Test fun `compute with negative`() {
        assertEquals(-7, Rustcalc.compute(-3, -4, Operation.Add))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // greet
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `greet returns formatted message`() {
        assertEquals("Hello, World!", Rustcalc.greet("World"))
    }

    @Test fun `greet with unicode`() {
        assertEquals("Hello, 世界!", Rustcalc.greet("世界"))
    }

    @Test fun `greet with empty string`() {
        assertEquals("Hello, !", Rustcalc.greet(""))
    }

    @Test fun `greet with emoji`() {
        assertEquals("Hello, 🚀!", Rustcalc.greet("🚀"))
    }

    @Test fun `greet with long string`() {
        val name = "A".repeat(5000)
        assertEquals("Hello, $name!", Rustcalc.greet(name))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge top - compute MAX_VALUE`() {
        assertEquals(Int.MAX_VALUE, Rustcalc.compute(Int.MAX_VALUE, 0, Operation.Add))
    }

    @Test fun `edge top - compute Multiply by zero`() {
        assertEquals(0, Rustcalc.compute(Int.MAX_VALUE, 0, Operation.Multiply))
    }

    @Test fun `edge top - compute all operations`() {
        for (op in Operation.entries) {
            Rustcalc.compute(10, 5, op) // should not throw
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K compute calls`() {
        repeat(100_000) {
            assertEquals(7, Rustcalc.compute(3, 4, Operation.Add))
        }
    }

    @Test fun `load - 100K greet calls`() {
        repeat(100_000) {
            assertEquals("Hello, World!", Rustcalc.greet("World"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrency tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 10K compute`() {
        val threads = (1..10).map { tid ->
            Thread {
                repeat(10_000) {
                    val result = Rustcalc.compute(tid, tid, Operation.Add)
                    assertEquals(tid * 2, result)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K greet`() {
        val threads = (1..10).map { tid ->
            Thread {
                repeat(10_000) {
                    val result = Rustcalc.greet("t$tid")
                    assertEquals("Hello, t$tid!", result)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
