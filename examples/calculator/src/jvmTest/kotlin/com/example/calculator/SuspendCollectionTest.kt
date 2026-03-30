package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class SuspendCollectionTest {

    // ══════════════════════════════════════════════════════════════════════════
    // SUSPEND COLLECTION RETURNS — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    // ── List<Int> ──────────────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetScores List of Int`() = runBlocking {
        Calculator(5).use { calc ->
            val scores = calc.delayedGetScores()
            assertEquals(listOf(5, 10, 15), scores)
        }
    }

    @Test
    fun `suspend - delayedGetScores zero accumulator`() = runBlocking {
        Calculator(0).use { calc ->
            val scores = calc.delayedGetScores()
            assertEquals(listOf(0, 0, 0), scores)
        }
    }

    // ── List<Int>? ─────────────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetScoresOrNull non-null`() = runBlocking {
        Calculator(3).use { calc ->
            val scores = calc.delayedGetScoresOrNull()
            assertNotNull(scores)
            assertEquals(listOf(3, 6), scores)
        }
    }

    @Test
    fun `suspend - delayedGetScoresOrNull null`() = runBlocking {
        Calculator(0).use { calc ->
            val scores = calc.delayedGetScoresOrNull()
            assertNull(scores)
        }
    }

    // ── List<String> ───────────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetLabels List of String`() = runBlocking {
        Calculator(7).use { calc ->
            val labels = calc.delayedGetLabels()
            assertEquals(listOf("default", "item_7"), labels)
        }
    }

    @Test
    fun `suspend - delayedGetLabels custom label`() = runBlocking {
        Calculator(3).use { calc ->
            calc.label = "custom"
            val labels = calc.delayedGetLabels()
            assertEquals(listOf("custom", "item_3"), labels)
        }
    }

    // ── List<Double> ───────────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetWeights List of Double`() = runBlocking {
        Calculator(4).use { calc ->
            val weights = calc.delayedGetWeights()
            assertEquals(2, weights.size)
            assertEquals(4.0, weights[0], 0.001)
            assertEquals(6.0, weights[1], 0.001)
        }
    }

    // ── List<Boolean> ──────────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetFlags List of Boolean positive`() = runBlocking {
        Calculator(4).use { calc ->
            val flags = calc.delayedGetFlags()
            assertEquals(listOf(true, true), flags) // 4 > 0, 4 % 2 == 0
        }
    }

    @Test
    fun `suspend - delayedGetFlags List of Boolean negative`() = runBlocking {
        Calculator(-3).use { calc ->
            val flags = calc.delayedGetFlags()
            assertEquals(listOf(false, false), flags) // -3 not > 0, -3 % 2 != 0
        }
    }

    // ── List<Enum> ─────────────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetOperations List of Enum`() = runBlocking {
        Calculator(0).use { calc ->
            val ops = calc.delayedGetOperations()
            assertEquals(Operation.entries.toList(), ops)
        }
    }

    // ── List<DataClass> ────────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetPoints List of DC`() = runBlocking {
        Calculator(5).use { calc ->
            val points = calc.delayedGetPoints()
            assertEquals(2, points.size)
            assertEquals(Point(5, 0), points[0])
            assertEquals(Point(0, 5), points[1])
        }
    }

    @Test
    fun `suspend - delayedGetPoints zero accumulator`() = runBlocking {
        Calculator(0).use { calc ->
            val points = calc.delayedGetPoints()
            assertEquals(2, points.size)
            assertEquals(Point(0, 0), points[0])
            assertEquals(Point(0, 0), points[1])
        }
    }

    // ── List<DataClass> with nested DC ───────────────────────────────────

    @Test
    fun `suspend - delayedGetTaggedPoints List of nested DC + Enum`() = runBlocking {
        Calculator(5).use { calc ->
            val points = calc.delayedGetTaggedPoints()
            assertEquals(2, points.size)
            assertEquals(Point(5, 0), points[0].point)
            assertEquals(Operation.ADD, points[0].tag)
            assertEquals(Point(0, 5), points[1].point)
            assertEquals(Operation.ADD, points[1].tag) // lastOperation defaults to ADD
        }
    }

    @Test
    fun `suspend - delayedGetRects List of deeply nested DC`() = runBlocking {
        Calculator(3).use { calc ->
            val rects = calc.delayedGetRects()
            assertEquals(2, rects.size)
            assertEquals(Point(0, 0), rects[0].topLeft)
            assertEquals(Point(3, 3), rects[0].bottomRight)
            assertEquals(Point(3, 0), rects[1].topLeft)
            assertEquals(Point(0, 3), rects[1].bottomRight)
        }
    }

    @Test
    fun `suspend - concurrent List nested DC returns`() = runBlocking {
        Calculator(7).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.delayedGetTaggedPoints() }
            }.awaitAll()
            assertEquals(10, results.size)
            results.forEach { tps ->
                assertEquals(2, tps.size)
                assertEquals(7, tps[0].point.x)
            }
        }
    }

    @Test
    fun `suspend - 100 sequential List nested DC no leak`() = runBlocking {
        Calculator(1).use { calc ->
            repeat(100) {
                val tps = calc.delayedGetTaggedPoints()
                assertEquals(2, tps.size)
            }
        }
        System.gc()
    }

    // ── List<DataClass>? ───────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetPointsOrNull non-null`() = runBlocking {
        Calculator(3).use { calc ->
            val points = calc.delayedGetPointsOrNull()
            assertNotNull(points)
            assertEquals(1, points.size)
            assertEquals(Point(3, 3), points[0])
        }
    }

    @Test
    fun `suspend - delayedGetPointsOrNull null`() = runBlocking {
        Calculator(0).use { calc ->
            val points = calc.delayedGetPointsOrNull()
            assertNull(points)
        }
    }

    // ── Set<Int> ───────────────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetUniqueScores Set of Int`() = runBlocking {
        Calculator(5).use { calc ->
            val scores = calc.delayedGetUniqueScores()
            assertEquals(setOf(5, 10, 15), scores)
        }
    }

    @Test
    fun `suspend - delayedGetUniqueScores zero produces single element`() = runBlocking {
        Calculator(0).use { calc ->
            val scores = calc.delayedGetUniqueScores()
            assertEquals(setOf(0), scores) // 0, 0, 0 -> {0}
        }
    }

    // ── Set<String> ────────────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetUniqueLabels Set of String`() = runBlocking {
        Calculator(3).use { calc ->
            val labels = calc.delayedGetUniqueLabels()
            assertEquals(setOf("default", "item_3"), labels)
        }
    }

    // ── Set<Enum> ──────────────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetUsedOps Set of Enum`() = runBlocking {
        Calculator(0).use { calc ->
            val ops = calc.delayedGetUsedOps()
            // lastOperation defaults to ADD, so {ADD, ADD} -> {ADD}
            assertEquals(setOf(Operation.ADD), ops)
        }
    }

    @Test
    fun `suspend - delayedGetUsedOps after multiply`() = runBlocking {
        Calculator(5).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 2)
            val ops = calc.delayedGetUsedOps()
            assertEquals(setOf(Operation.MULTIPLY, Operation.ADD), ops)
        }
    }

    // ── Set<Enum>? ─────────────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetUsedOpsOrNull non-null`() = runBlocking {
        Calculator(1).use { calc ->
            val ops = calc.delayedGetUsedOpsOrNull()
            assertNotNull(ops)
            assertTrue(ops.contains(Operation.ADD))
        }
    }

    @Test
    fun `suspend - delayedGetUsedOpsOrNull null`() = runBlocking {
        Calculator(0).use { calc ->
            val ops = calc.delayedGetUsedOpsOrNull()
            assertNull(ops)
        }
    }

    // ── Map<String, Int> ───────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetMetadata Map String to Int`() = runBlocking {
        Calculator(42).use { calc ->
            val meta = calc.delayedGetMetadata()
            assertEquals(42, meta["current"])
            assertEquals(1, meta["scale"]) // scale defaults to 1.0
        }
    }

    // ── Map<String, Int>? ──────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetMetadataOrNull non-null`() = runBlocking {
        Calculator(10).use { calc ->
            val meta = calc.delayedGetMetadataOrNull()
            assertNotNull(meta)
            assertEquals(mapOf("val" to 10), meta)
        }
    }

    @Test
    fun `suspend - delayedGetMetadataOrNull null`() = runBlocking {
        Calculator(0).use { calc ->
            val meta = calc.delayedGetMetadataOrNull()
            assertNull(meta)
        }
    }

    // ── Map<Int, Int> ──────────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetSquares Map Int to Int`() = runBlocking {
        Calculator(3).use { calc ->
            val sq = calc.delayedGetSquares()
            assertEquals(1, sq[1])
            assertEquals(4, sq[2])
            assertEquals(9, sq[3])
        }
    }

    // ── Concurrent collection returns ──────────────────────────────────────

    @Test
    fun `suspend - concurrent List Int returns`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) { calc.delayedGetScores() }
            }.awaitAll()
            assertEquals(20, results.size)
            results.forEach { assertEquals(listOf(5, 10, 15), it) }
        }
    }

    @Test
    fun `suspend - concurrent mixed collection types`() = runBlocking {
        Calculator(5).use { calc ->
            val scores = async(Dispatchers.Default) { calc.delayedGetScores() }
            val labels = async(Dispatchers.Default) { calc.delayedGetLabels() }
            val points = async(Dispatchers.Default) { calc.delayedGetPoints() }
            val meta = async(Dispatchers.Default) { calc.delayedGetMetadata() }

            assertEquals(3, scores.await().size)
            assertEquals(2, labels.await().size)
            assertEquals(2, points.await().size)
            assertEquals(5, meta.await()["current"])
        }
    }

    @Test
    fun `suspend - concurrent collection separate instances`() = runBlocking {
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.delayedGetScores() }
            }
        }.awaitAll()
        results.forEachIndexed { idx, scores ->
            val v = idx + 1
            assertEquals(listOf(v, v * 2, v * 3), scores)
        }
    }

    // ── Sequential stress / leak detection ─────────────────────────────────

    @Test
    fun `suspend - 200 sequential List Int returns`() = runBlocking {
        Calculator(1).use { calc ->
            repeat(200) {
                assertEquals(listOf(1, 2, 3), calc.delayedGetScores())
            }
        }
        System.gc()
    }

    @Test
    fun `suspend - 100 sequential Map returns`() = runBlocking {
        Calculator(1).use { calc ->
            repeat(100) {
                val meta = calc.delayedGetMetadata()
                assertEquals(1, meta["current"])
            }
        }
        System.gc()
    }

    @Test
    fun `suspend - 100 instances List DC returns`() = runBlocking {
        repeat(100) { i ->
            Calculator(i).use { calc ->
                val points = calc.delayedGetPoints()
                assertEquals(Point(i, 0), points[0])
            }
        }
        System.gc()
    }

    // ── Mixed suspend collection + sync ────────────────────────────────────

    @Test
    fun `suspend - collection interleaved with sync mutations`() = runBlocking {
        Calculator(0).use { calc ->
            calc.add(3)
            assertEquals(listOf(3, 6, 9), calc.delayedGetScores())
            calc.add(2)
            assertEquals(listOf(5, 10, 15), calc.delayedGetScores())
        }
    }

    @Test
    fun `suspend - collection after error recovery`() = runBlocking {
        Calculator(5).use { calc ->
            try { calc.failAfterDelay() } catch (_: Exception) {}
            val scores = calc.delayedGetScores()
            assertEquals(listOf(5, 10, 15), scores)
        }
    }
}
