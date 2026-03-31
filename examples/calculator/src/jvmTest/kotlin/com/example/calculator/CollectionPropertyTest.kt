package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class CollectionPropertyTest {

    // ══════════════════════════════════════════════════════════════════════════
    // COLLECTION PROPERTIES — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    // ── val List<Int> property (getter only) ───────────────────────────────

    @Test
    fun `coll prop - recentScores basic`() {
        Calculator(5).use { calc ->
            assertEquals(listOf(5, 10, 15), calc.recentScores)
        }
    }

    @Test
    fun `coll prop - recentScores after mutation`() {
        Calculator(0).use { calc ->
            calc.add(3)
            assertEquals(listOf(3, 6, 9), calc.recentScores)
            calc.add(2)
            assertEquals(listOf(5, 10, 15), calc.recentScores)
        }
    }

    @Test
    fun `coll prop - recentScores zero`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(0, 0, 0), calc.recentScores)
        }
    }

    // ── var List<String> property (getter + setter) ────────────────────────

    @Test
    fun `coll prop - tags default`() {
        Calculator(0).use { calc ->
            assertEquals(listOf("default"), calc.tags)
        }
    }

    @Test
    fun `coll prop - tags set and get`() {
        Calculator(0).use { calc ->
            calc.tags = listOf("alpha", "beta", "gamma")
            assertEquals(listOf("alpha", "beta", "gamma"), calc.tags)
        }
    }

    @Test
    fun `coll prop - tags set empty`() {
        Calculator(0).use { calc ->
            calc.tags = emptyList()
            assertEquals(emptyList(), calc.tags)
        }
    }

    @Test
    fun `coll prop - tags set multiple times`() {
        Calculator(0).use { calc ->
            calc.tags = listOf("a")
            assertEquals(listOf("a"), calc.tags)
            calc.tags = listOf("x", "y")
            assertEquals(listOf("x", "y"), calc.tags)
        }
    }

    // ── val Map<String, Int> property (getter only) ────────────────────────

    @Test
    fun `coll prop - metadata basic`() {
        Calculator(42).use { calc ->
            val meta = calc.info
            assertEquals(42, meta["current"])
            assertEquals(1, meta["scale"])
        }
    }

    @Test
    fun `coll prop - metadata after mutation`() {
        Calculator(0).use { calc ->
            calc.add(10)
            calc.scale = 3.0
            val meta = calc.info
            assertEquals(10, meta["current"])
            assertEquals(3, meta["scale"])
        }
    }

    // ── Concurrent ─────────────────────────────────────────────────────────

    @Test
    fun `coll prop - concurrent recentScores reads`() = runBlocking {
        Calculator(7).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) { calc.recentScores }
            }.awaitAll()
            assertEquals(20, results.size)
            results.forEach { assertEquals(listOf(7, 14, 21), it) }
        }
    }

    @Test
    fun `coll prop - concurrent metadata reads`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) { calc.info }
            }.awaitAll()
            results.forEach { assertEquals(5, it["current"]) }
        }
    }

    // ── Sequential stress ──────────────────────────────────────────────────

    @Test
    fun `coll prop - 200 sequential recentScores reads`() {
        Calculator(1).use { calc ->
            repeat(200) {
                assertEquals(listOf(1, 2, 3), calc.recentScores)
            }
        }
        System.gc()
    }

    @Test
    fun `coll prop - 100 sequential tags write-read cycles`() {
        Calculator(0).use { calc ->
            repeat(100) { i ->
                calc.tags = listOf("tag_$i")
                assertEquals(listOf("tag_$i"), calc.tags)
            }
        }
        System.gc()
    }
}
