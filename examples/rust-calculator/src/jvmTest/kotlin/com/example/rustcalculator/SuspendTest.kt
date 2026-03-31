package com.example.rustcalculator

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuspendTest {

    @Test fun `suspend delayed_add returns correct result`() = runBlocking {
        Calculator(10).use { calc ->
            val result = calc.delayed_add(5, 50)
            assertEquals(15, result)
        }
    }

    @Test fun `suspend delayed_describe returns string`() = runBlocking {
        Calculator(42).use { calc ->
            val result = calc.delayed_describe(50)
            assertEquals("Calculator(current=42)", result)
        }
    }

    @Test fun `suspend fail_after_delay throws exception`() {
        runBlocking {
            Calculator(0).use { calc ->
                var threw = false
                try {
                    calc.fail_after_delay(50)
                } catch (e: KotlinNativeException) {
                    threw = true
                }
                assertTrue(threw, "Expected KotlinNativeException")
            }
        }
    }

    @Test fun `suspend delayed_noop completes`() = runBlocking {
        Calculator(0).use { calc ->
            calc.delayed_noop(50)
        }
    }

    @Test fun `suspend delayed_is_positive returns bool`() = runBlocking {
        Calculator(5).use { calc ->
            assertTrue(calc.delayed_is_positive(50))
        }
    }
}
