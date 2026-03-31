package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.first

class FlowCollectionTest {

    // ══════════════════════════════════════════════════════════════════════════
    // FLOW<COLLECTION> — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    // ── Flow<List<Int>> ────────────────────────────────────────────────────

    @Test
    fun `flow list - scoresFlow basic`() = runBlocking {
        Calculator(0).use { calc ->
            val all = calc.scoresFlow(3).toList()
            assertEquals(3, all.size)
            assertEquals(listOf(0, 0, 0), all[0])
            assertEquals(listOf(1, 2, 3), all[1])
            assertEquals(listOf(2, 4, 6), all[2])
        }
    }

    @Test
    fun `flow list - scoresFlow first`() = runBlocking {
        Calculator(0).use { calc ->
            val first = calc.scoresFlow(5).first()
            assertEquals(listOf(0, 0, 0), first)
        }
    }

    @Test
    fun `flow list - scoresFlow take`() = runBlocking {
        Calculator(0).use { calc ->
            val taken = calc.scoresFlow(10).take(2).toList()
            assertEquals(2, taken.size)
        }
    }

    @Test
    fun `flow list - scoresFlow empty`() = runBlocking {
        Calculator(0).use { calc ->
            val all = calc.scoresFlow(0).toList()
            assertEquals(0, all.size)
        }
    }

    // ── Flow<List<String>> ─────────────────────────────────────────────────

    @Test
    fun `flow list - labelsFlow basic`() = runBlocking {
        Calculator(0).use { calc ->
            val all = calc.labelsFlow().toList()
            assertEquals(2, all.size)
            assertEquals(listOf("a", "b"), all[0])
            assertEquals(listOf("c", "d", "e"), all[1])
        }
    }

    // ── Flow<Map<String, Int>> ─────────────────────────────────────────────

    @Test
    fun `flow map - metadataFlow basic`() = runBlocking {
        Calculator(10).use { calc ->
            val all = calc.metadataFlow(3).toList()
            assertEquals(3, all.size)
            assertEquals(0, all[0]["step"])
            assertEquals(10, all[0]["value"])
            assertEquals(1, all[1]["step"])
            assertEquals(11, all[1]["value"])
            assertEquals(2, all[2]["step"])
            assertEquals(12, all[2]["value"])
        }
    }

    @Test
    fun `flow map - metadataFlow first`() = runBlocking {
        Calculator(5).use { calc ->
            val first = calc.metadataFlow(3).first()
            assertEquals(0, first["step"])
            assertEquals(5, first["value"])
        }
    }

    // ── Concurrent ─────────────────────────────────────────────────────────

    @Test
    fun `flow list - concurrent collectors`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..5).map {
                async(Dispatchers.Default) { calc.scoresFlow(3).toList() }
            }.awaitAll()
            assertEquals(5, results.size)
            results.forEach { assertEquals(3, it.size) }
        }
    }

    @Test
    fun `flow list - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.scoresFlow(2).toList() }
            }
        }.awaitAll()
        assertEquals(10, results.size)
        results.forEach { assertEquals(2, it.size) }
    }

    // ── Sequential stress ──────────────────────────────────────────────────

    @Test
    fun `flow list - 50 sequential collections`() = runBlocking {
        Calculator(1).use { calc ->
            repeat(50) {
                val all = calc.scoresFlow(2).toList()
                assertEquals(2, all.size)
            }
        }
        System.gc()
    }
}
