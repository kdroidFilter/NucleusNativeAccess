package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals

class TopLevelFunctionTest {

    @Test fun `compute Add`() {
        assertEquals(7, Rustcalc.compute(3, 4, Operation.Add))
    }

    @Test fun `compute Subtract`() {
        assertEquals(7, Rustcalc.compute(10, 3, Operation.Subtract))
    }

    @Test fun `compute Multiply`() {
        assertEquals(12, Rustcalc.compute(3, 4, Operation.Multiply))
    }

    @Test fun `greet returns formatted message`() {
        assertEquals("Hello, World!", Rustcalc.greet("World"))
    }

    @Test fun `greet with unicode`() {
        assertEquals("Hello, 世界!", Rustcalc.greet("世界"))
    }

    @Test fun `greet with empty string`() {
        assertEquals("Hello, !", Rustcalc.greet(""))
    }
}
