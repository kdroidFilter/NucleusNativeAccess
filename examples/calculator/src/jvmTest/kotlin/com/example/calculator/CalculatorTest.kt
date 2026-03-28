package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals

class CalculatorTest {

    @Test
    fun `add accumulates`() {
        Calculator(0).use { calc ->
            assertEquals(5, calc.add(5))
            assertEquals(8, calc.add(3))
            assertEquals(8, calc.current)
        }
    }

    @Test
    fun `subtract works`() {
        Calculator(0).use { calc ->
            calc.add(10)
            assertEquals(7, calc.subtract(3))
        }
    }

    @Test
    fun `multiply works`() {
        Calculator(0).use { calc ->
            calc.add(4)
            assertEquals(12, calc.multiply(3))
        }
    }

    @Test
    fun `reset clears`() {
        Calculator(0).use { calc ->
            calc.add(42)
            calc.reset()
            assertEquals(0, calc.current)
        }
    }

    @Test
    fun `describe returns native string`() {
        Calculator(7).use { calc ->
            assertEquals("Calculator(current=7)", calc.describe())
        }
    }
}
