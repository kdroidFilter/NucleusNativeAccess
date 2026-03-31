package com.example.rustcalculator

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowTest {
    @Test fun `count_up emits correct sequence`() = runBlocking {
        Calculator(0).use { calc ->
            val items = calc.count_up(5, 10).toList()
            assertEquals(5, items.size)
            assertEquals(listOf(1, 2, 3, 4, 5), items)
        }
    }

    @Test fun `count_up with non-zero accumulator`() = runBlocking {
        Calculator(10).use { calc ->
            val items = calc.count_up(3, 10).toList()
            assertEquals(listOf(11, 12, 13), items)
        }
    }

    @Test fun `score_labels emits strings`() = runBlocking {
        Calculator(5).use { calc ->
            val items = calc.score_labels(3).toList()
            assertEquals(3, items.size)
            assertEquals("Score #1: 5", items[0])
            assertEquals("Score #2: 10", items[1])
            assertEquals("Score #3: 15", items[2])
        }
    }

    @Test fun `flow with single element`() = runBlocking {
        Calculator(0).use { calc ->
            val items = calc.count_up(1, 10).toList()
            assertEquals(1, items.size)
        }
    }
}
