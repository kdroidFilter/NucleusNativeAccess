package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class SuspendEdgeCaseTest {

    // ══════════════════════════════════════════════════════════════════════════
    // SUSPEND WITH BYTEARRAY/NESTED COLLECTIONS — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    // ── suspend fun(): List<ByteArray> ─────────────────────────────────────

    @Test
    fun `suspend - delayedGetByteChunks basic`() = runBlocking {
        Calculator(3).use { calc ->
            val chunks = calc.delayedGetByteChunks()
            assertEquals(2, chunks.size)
            assertContentEquals(byteArrayOf(1, 2, 3), chunks[0])
            assertEquals(3, chunks[1].size)
            assertContentEquals(byteArrayOf(0, 1, 2), chunks[1])
        }
    }

    @Test
    fun `suspend - delayedGetByteChunks zero`() = runBlocking {
        Calculator(0).use { calc ->
            val chunks = calc.delayedGetByteChunks()
            assertEquals(2, chunks.size)
            assertEquals(0, chunks[1].size)
        }
    }

    @Test
    fun `suspend - delayedGetByteChunks concurrent`() = runBlocking {
        Calculator(2).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.delayedGetByteChunks() }
            }.awaitAll()
            assertEquals(10, results.size)
            results.forEach { assertEquals(2, it.size) }
        }
    }

    @Test
    fun `suspend - delayedGetByteChunks stress`() = runBlocking {
        Calculator(1).use { calc ->
            repeat(50) {
                val chunks = calc.delayedGetByteChunks()
                assertEquals(2, chunks.size)
            }
        }
        System.gc()
    }

    // ── suspend fun(): List<List<Int>> ─────────────────────────────────────

    @Test
    fun `suspend - delayedGetMatrix basic`() = runBlocking {
        Calculator(5).use { calc ->
            val matrix = calc.delayedGetMatrix()
            assertEquals(2, matrix.size)
            assertEquals(listOf(5, 6), matrix[0])
            assertEquals(listOf(10), matrix[1])
        }
    }

    @Test
    fun `suspend - delayedGetMatrix zero`() = runBlocking {
        Calculator(0).use { calc ->
            val matrix = calc.delayedGetMatrix()
            assertEquals(listOf(0, 1), matrix[0])
            assertEquals(listOf(0), matrix[1])
        }
    }

    @Test
    fun `suspend - delayedGetMatrix concurrent`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.delayedGetMatrix() }
            }.awaitAll()
            assertEquals(10, results.size)
            results.forEach {
                assertEquals(listOf(3, 4), it[0])
                assertEquals(listOf(6), it[1])
            }
        }
    }

    @Test
    fun `suspend - delayedGetMatrix stress`() = runBlocking {
        Calculator(1).use { calc ->
            repeat(50) {
                val matrix = calc.delayedGetMatrix()
                assertEquals(2, matrix.size)
            }
        }
        System.gc()
    }

    @Test
    fun `suspend - delayedGetMatrix after mutation`() = runBlocking {
        Calculator(0).use { calc ->
            calc.add(7)
            val matrix = calc.delayedGetMatrix()
            assertEquals(listOf(7, 8), matrix[0])
            assertEquals(listOf(14), matrix[1])
        }
    }
}
