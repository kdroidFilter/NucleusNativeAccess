package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals

class EnumTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Enum structure
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `enum has correct entries`() {
        assertEquals(3, Operation.entries.size)
        assertEquals("Add", Operation.Add.name)
        assertEquals("Subtract", Operation.Subtract.name)
        assertEquals("Multiply", Operation.Multiply.name)
    }

    @Test fun `enum ordinals match`() {
        assertEquals(0, Operation.Add.ordinal)
        assertEquals(1, Operation.Subtract.ordinal)
        assertEquals(2, Operation.Multiply.ordinal)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Enum as parameter
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `enum as parameter - Add`() {
        Calculator(0).use { calc -> assertEquals(5, calc.apply_op(Operation.Add, 5)) }
    }

    @Test fun `enum as parameter - Subtract`() {
        Calculator(10).use { calc -> assertEquals(7, calc.apply_op(Operation.Subtract, 3)) }
    }

    @Test fun `enum as parameter - Multiply`() {
        Calculator(4).use { calc -> assertEquals(12, calc.apply_op(Operation.Multiply, 3)) }
    }

    @Test fun `enum roundtrip through all values`() {
        Calculator(1).use { calc ->
            for (op in Operation.entries) {
                calc.apply_op(op, 1)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge enum - Add with zero`() {
        Calculator(10).use { calc -> assertEquals(10, calc.apply_op(Operation.Add, 0)) }
    }

    @Test fun `edge enum - Subtract with zero`() {
        Calculator(10).use { calc -> assertEquals(10, calc.apply_op(Operation.Subtract, 0)) }
    }

    @Test fun `edge enum - Multiply with zero`() {
        Calculator(10).use { calc -> assertEquals(0, calc.apply_op(Operation.Multiply, 0)) }
    }

    @Test fun `edge enum - Multiply with one`() {
        Calculator(42).use { calc -> assertEquals(42, calc.apply_op(Operation.Multiply, 1)) }
    }

    @Test fun `edge enum - Add with negative`() {
        Calculator(10).use { calc -> assertEquals(5, calc.apply_op(Operation.Add, -5)) }
    }

    @Test fun `edge enum - Subtract with negative`() {
        Calculator(10).use { calc -> assertEquals(15, calc.apply_op(Operation.Subtract, -5)) }
    }

    @Test fun `edge enum - Add with MAX_VALUE`() {
        Calculator(0).use { calc -> assertEquals(Int.MAX_VALUE, calc.apply_op(Operation.Add, Int.MAX_VALUE)) }
    }

    @Test fun `edge enum - sequential operations`() {
        Calculator(0).use { calc ->
            calc.apply_op(Operation.Add, 10)       // 10
            calc.apply_op(Operation.Multiply, 3)    // 30
            calc.apply_op(Operation.Subtract, 5)    // 25
            calc.apply_op(Operation.Add, 75)        // 100
            assertEquals(100, calc.current)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K apply_op calls`() {
        Calculator(0).use { calc ->
            repeat(100_000) {
                calc.apply_op(Operation.Add, 1)
            }
            assertEquals(100_000, calc.current)
        }
    }

    @Test fun `load - 100K enum param roundtrip`() {
        Calculator(0).use { calc ->
            repeat(100_000) { i ->
                val op = Operation.entries[i % 3]
                calc.apply_op(op, 0)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrency tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 10K apply_op`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(0).use { calc ->
                    repeat(10_000) {
                        val op = Operation.entries[tid % 3]
                        calc.apply_op(op, 1)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
