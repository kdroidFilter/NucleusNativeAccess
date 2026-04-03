package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals

class TupleTest {

    @Test
    fun `get_coordinates returns correct tuple`() {
        Calculator(5).use { calc ->
            val coords = calc.get_coordinates()
            assertEquals(5, coords._0)
            assertEquals(10, coords._1)
        }
    }

    @Test
    fun `get_coordinates with zero accumulator`() {
        Calculator(0).use { calc ->
            val coords = calc.get_coordinates()
            assertEquals(0, coords._0)
            assertEquals(0, coords._1)
        }
    }

    @Test
    fun `get_triple returns correct tuple`() {
        Calculator(5).use { calc ->
            calc.label = "test"
            val triple = calc.get_triple()
            assertEquals(5, triple._0)
            assertEquals("test", triple._1)
            assertEquals(true, triple._2)
        }
    }

    @Test
    fun `get_triple with empty label`() {
        Calculator(5).use { calc ->
            val triple = calc.get_triple()
            assertEquals(5, triple._0)
            assertEquals("", triple._1)
            assertEquals(true, triple._2)
        }
    }

    @Test
    fun `sum_tuple adds coordinates to accumulator`() {
        Calculator(5).use { calc ->
            val result = calc.sum_tuple(KneTuple2_TII(1, 2))
            assertEquals(8, result)
        }
    }

    @Test
    fun `sum_tuple with zero coordinates`() {
        Calculator(10).use { calc ->
            val result = calc.sum_tuple(KneTuple2_TII(0, 0))
            assertEquals(10, result)
        }
    }

    @Test
    fun `get_nested_tuple returns correct nested tuple`() {
        Calculator(5).use { calc ->
            calc.label = "nested"
            val result = calc.get_nested_tuple()
            assertEquals(5, result._0)
            assertEquals("nested", result._1._0)
            assertEquals(true, result._1._1)
        }
    }

    @Test
    fun `get_nested_tuple with empty label`() {
        Calculator(5).use { calc ->
            val result = calc.get_nested_tuple()
            assertEquals(5, result._0)
            assertEquals("", result._1._0)
            assertEquals(true, result._1._1)
        }
    }
}
