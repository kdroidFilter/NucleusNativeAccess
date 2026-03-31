package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals

class EnumTest {

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
}
