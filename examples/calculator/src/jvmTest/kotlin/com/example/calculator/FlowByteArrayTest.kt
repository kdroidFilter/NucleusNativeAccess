package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take

class FlowByteArrayTest {

    // ══════════════════════════════════════════════════════════════════════════
    // FLOW<BYTEARRAY> — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `flow bytearray - singleByteFlow`() = runBlocking {
        Calculator(0).use { calc ->
            val first = calc.singleByteFlow().first()
            assertContentEquals(byteArrayOf(1, 2, 3), first)
        }
    }

    @Test
    fun `flow bytearray - byteChunks basic`() = runBlocking {
        Calculator(0).use { calc ->
            val all = calc.byteChunks(3, 4).toList()
            assertEquals(3, all.size)
            assertEquals(4, all[0].size)
            assertEquals(4, all[1].size)
            assertEquals(4, all[2].size)
            // First chunk: bytes 0,1,2,3
            assertContentEquals(byteArrayOf(0, 1, 2, 3), all[0])
            // Second chunk: bytes 4,5,6,7
            assertContentEquals(byteArrayOf(4, 5, 6, 7), all[1])
        }
    }

    @Test
    fun `flow bytearray - byteChunks first only`() = runBlocking {
        Calculator(0).use { calc ->
            val first = calc.byteChunks(10, 2).first()
            assertEquals(2, first.size)
            assertContentEquals(byteArrayOf(0, 1), first)
        }
    }

    @Test
    fun `flow bytearray - byteChunks take`() = runBlocking {
        Calculator(0).use { calc ->
            val taken = calc.byteChunks(10, 2).take(3).toList()
            assertEquals(3, taken.size)
        }
    }

    @Test
    fun `flow bytearray - byteChunks empty`() = runBlocking {
        Calculator(0).use { calc ->
            val all = calc.byteChunks(0, 4).toList()
            assertEquals(0, all.size)
        }
    }

    @Test
    fun `flow bytearray - byteChunks large chunk`() = runBlocking {
        Calculator(0).use { calc ->
            val all = calc.byteChunks(2, 1000).toList()
            assertEquals(2, all.size)
            assertEquals(1000, all[0].size)
            assertEquals(1000, all[1].size)
        }
    }

    // ── Concurrent ─────────────────────────────────────────────────────────

    @Test
    fun `flow bytearray - concurrent collectors`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..5).map {
                async(Dispatchers.Default) { calc.byteChunks(2, 3).toList() }
            }.awaitAll()
            assertEquals(5, results.size)
            results.forEach { assertEquals(2, it.size) }
        }
    }

    // ── Sequential stress ──────────────────────────────────────────────────

    @Test
    fun `flow bytearray - 50 sequential collections`() = runBlocking {
        Calculator(0).use { calc ->
            repeat(50) {
                val all = calc.byteChunks(2, 4).toList()
                assertEquals(2, all.size)
            }
        }
        System.gc()
    }
}
