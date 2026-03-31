package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class NestedCollectionTest {

    // ══════════════════════════════════════════════════════════════════════════
    // NESTED COLLECTIONS — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    // ── List<List<Int>> return ──────────────────────────────────────────────

    @Test
    fun `nested coll - getMatrix basic`() {
        Calculator(5).use { calc ->
            val matrix = calc.getMatrix()
            assertEquals(2, matrix.size)
            assertEquals(listOf(5, 6), matrix[0])
            assertEquals(listOf(10, 11), matrix[1])
        }
    }

    @Test
    fun `nested coll - getMatrix zero`() {
        Calculator(0).use { calc ->
            val matrix = calc.getMatrix()
            assertEquals(listOf(0, 1), matrix[0])
            assertEquals(listOf(0, 1), matrix[1])
        }
    }

    // ── List<List<Int>> param ──────────────────────────────────────────────

    @Test
    fun `nested coll - sumMatrix basic`() {
        Calculator(0).use { calc ->
            val matrix = listOf(listOf(1, 2, 3), listOf(4, 5, 6))
            assertEquals(21, calc.sumMatrix(matrix))
        }
    }

    @Test
    fun `nested coll - sumMatrix single row`() {
        Calculator(0).use { calc ->
            assertEquals(10, calc.sumMatrix(listOf(listOf(10))))
        }
    }

    @Test
    fun `nested coll - sumMatrix empty`() {
        Calculator(0).use { calc ->
            assertEquals(0, calc.sumMatrix(emptyList()))
        }
    }

    @Test
    fun `nested coll - sumMatrix empty inner`() {
        Calculator(0).use { calc ->
            assertEquals(0, calc.sumMatrix(listOf(emptyList(), emptyList())))
        }
    }

    // ── List<List<String>> return ──────────────────────────────────────────

    @Test
    fun `nested coll - getTagGrid basic`() {
        Calculator(3).use { calc ->
            val grid = calc.getTagGrid()
            assertEquals(2, grid.size)
            assertEquals(listOf("a_3", "b_3"), grid[0])
            assertEquals(listOf("c_3"), grid[1])
        }
    }

    // ── Roundtrip ──────────────────────────────────────────────────────────

    @Test
    fun `nested coll - roundtrip getMatrix then sumMatrix`() {
        Calculator(5).use { calc ->
            val matrix = calc.getMatrix()
            val sum = calc.sumMatrix(matrix)
            // [5,6] + [10,11] = 32
            assertEquals(32, sum)
        }
    }

    // ── Concurrent ─────────────────────────────────────────────────────────

    @Test
    fun `nested coll - concurrent getMatrix`() = runBlocking {
        Calculator(2).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.getMatrix() }
            }.awaitAll()
            assertEquals(10, results.size)
            results.forEach {
                assertEquals(listOf(2, 3), it[0])
                assertEquals(listOf(4, 5), it[1])
            }
        }
    }

    @Test
    fun `nested coll - concurrent sumMatrix separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.sumMatrix(listOf(listOf(i, i), listOf(i)))
                }
            }
        }.awaitAll()
        results.forEachIndexed { idx, sum ->
            assertEquals((idx + 1) * 3, sum)
        }
    }

    // ── Sequential stress ──────────────────────────────────────────────────

    @Test
    fun `nested coll - 100 sequential getMatrix`() {
        Calculator(1).use { calc ->
            repeat(100) {
                val matrix = calc.getMatrix()
                assertEquals(2, matrix.size)
            }
        }
        System.gc()
    }

    @Test
    fun `nested coll - 100 sequential sumMatrix`() {
        Calculator(0).use { calc ->
            repeat(100) { i ->
                assertEquals(i * 2, calc.sumMatrix(listOf(listOf(i), listOf(i))))
            }
        }
        System.gc()
    }
}
