package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertContentEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take

class AsyncSuspendFlowTest {

    // ══════════════════════════════════════════════════════════════════════════
    // SUSPEND — BASIC TYPES (concurrent same instance + separate instances)
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent delayedAdd same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..20).map { i -> async(Dispatchers.Default) { calc.delayedAdd(i, i) } }.awaitAll()
            assertEquals(20, results.size)
            results.forEach { assertTrue(it > 0) }
        }
    }

    @Test fun `concurrent delayedAdd separate instances`() = runBlocking {
        val results = (1..20).map { i -> async(Dispatchers.Default) { Calculator(0).use { it.delayedAdd(i, i) } } }.awaitAll()
        assertEquals((1..20).map { it * 2 }, results)
    }

    @Test fun `concurrent delayedDescribe same instance`() = runBlocking {
        Calculator(42).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedDescribe() } }.awaitAll()
            results.forEach { assertTrue(it.contains("Calculator")) }
        }
    }

    @Test fun `concurrent delayedDescribe separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedDescribe() } } }.awaitAll()
        results.forEachIndexed { idx, s -> assertTrue(s.contains("${idx + 1}")) }
    }

    @Test fun `concurrent delayedIsPositive same instance`() = runBlocking {
        Calculator(10).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedIsPositive() } }.awaitAll()
            results.forEach { assertTrue(it) }
        }
    }

    @Test fun `concurrent delayedIsPositive separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedIsPositive() } } }.awaitAll()
        results.forEach { assertTrue(it) }
    }

    @Test fun `concurrent delayedGetOp same instance`() = runBlocking {
        Calculator(0).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 5)
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetOp() } }.awaitAll()
            assertEquals(15, results.size)
        }
    }

    @Test fun `concurrent delayedGetOp separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.delayedGetOp() } } }.awaitAll()
        results.forEach { assertEquals(Operation.ADD, it) }
    }

    // ── Nullable, Double, Long, Unit ──────────────────────────────────────

    @Test fun `concurrent delayedNullable same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedNullable() } }.awaitAll()
            results.forEach { assertNotNull(it) }
        }
    }

    @Test fun `concurrent delayedNullable separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(if (i % 2 == 0) i else 0).use { it.delayedNullable() } } }.awaitAll()
        assertEquals(15, results.size)
    }

    @Test fun `concurrent delayedDouble same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { i -> async(Dispatchers.Default) { calc.delayedDouble(i.toDouble()) } }.awaitAll()
            results.forEachIndexed { idx, d -> assertEquals((idx + 1) * 2.0, d, 0.001) }
        }
    }

    @Test fun `concurrent delayedDouble separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(0).use { it.delayedDouble(i.toDouble()) } } }.awaitAll()
        results.forEachIndexed { idx, d -> assertEquals((idx + 1) * 2.0, d, 0.001) }
    }

    @Test fun `concurrent delayedLong same instance`() = runBlocking {
        Calculator(10).use { calc ->
            val results = (1..15).map { i -> async(Dispatchers.Default) { calc.delayedLong(i.toLong()) } }.awaitAll()
            assertEquals(15, results.size)
        }
    }

    @Test fun `concurrent delayedLong separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedLong(100L) } } }.awaitAll()
        results.forEachIndexed { idx, v -> assertEquals(100L + idx + 1, v) }
    }

    @Test fun `concurrent delayedUnit same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..15).map { async(Dispatchers.Default) { calc.delayedUnit() } }.awaitAll()
            assertTrue(calc.current > 0)
        }
    }

    @Test fun `concurrent delayedUnit separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { c -> c.delayedUnit(); c.current } } }.awaitAll()
        results.forEach { assertEquals(1, it) }
    }

    // ── Nullable Int, ByteArray ───────────────────────────────────────────

    @Test fun `concurrent delayedGetCurrentOrNull same instance`() = runBlocking {
        Calculator(42).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetCurrentOrNull() } }.awaitAll()
            results.forEach { assertNotNull(it) }
        }
    }

    @Test fun `concurrent delayedGetCurrentOrNull separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(if (i % 2 == 0) i else 0).use { it.delayedGetCurrentOrNull() } } }.awaitAll()
        assertEquals(15, results.size)
    }

    @Test fun `concurrent delayedByteArray same instance`() = runBlocking {
        Calculator(7).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedByteArray() } }.awaitAll()
            results.forEach { assertTrue(it.decodeToString().contains("acc=")) }
        }
    }

    @Test fun `concurrent delayedByteArray separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedByteArray() } } }.awaitAll()
        results.forEachIndexed { idx, b -> assertEquals("acc=${idx + 1}", b.decodeToString()) }
    }

    @Test fun `concurrent delayedByteArrayNullable same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedByteArrayNullable() } }.awaitAll()
            results.forEach { assertNotNull(it) }
        }
    }

    @Test fun `concurrent delayedByteArrayNullable separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(if (i % 2 == 0) i else 0).use { it.delayedByteArrayNullable() } } }.awaitAll()
        assertEquals(15, results.size)
    }

    @Test fun `concurrent delayedLargeByteArray same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.delayedLargeByteArray(100) } }.awaitAll()
            results.forEach { assertEquals(100, it.size) }
        }
    }

    @Test fun `concurrent delayedLargeByteArray separate instances`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.delayedLargeByteArray(100) } } }.awaitAll()
        results.forEach { arr -> assertEquals(100, arr.size); assertEquals(0.toByte(), arr[0]); assertEquals(99.toByte(), arr[99]) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SUSPEND — DATA CLASS RETURNS
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent delayedGetPoint same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetPoint() } }.awaitAll()
            results.forEach { assertEquals(Point::class, it::class) }
        }
    }

    @Test fun `concurrent delayedGetPoint separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetPoint() } } }.awaitAll()
        results.forEachIndexed { idx, p -> assertEquals(idx + 1, p.x); assertEquals((idx + 1) * 2, p.y) }
    }

    @Test fun `concurrent delayedGetPointOrNull non-null same instance`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetPointOrNull() } }.awaitAll()
            results.forEach { assertNotNull(it) }
        }
    }

    @Test fun `concurrent delayedGetPointOrNull non-null separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetPointOrNull() } } }.awaitAll()
        results.forEachIndexed { idx, p -> assertNotNull(p); assertEquals(idx + 1, p.x) }
    }

    @Test fun `concurrent delayedGetPointOrNull null same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetPointOrNull() } }.awaitAll()
            results.forEach { assertNull(it) }
        }
    }

    @Test fun `concurrent delayedGetPointOrNull null separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.delayedGetPointOrNull() } } }.awaitAll()
        results.forEach { assertNull(it) }
    }

    @Test fun `concurrent delayedGetNamedValue same instance`() = runBlocking {
        Calculator(7).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetNamedValue() } }.awaitAll()
            results.forEach { assertEquals(7, it.value) }
        }
    }

    @Test fun `concurrent delayedGetNamedValue separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetNamedValue() } } }.awaitAll()
        results.forEachIndexed { idx, nv -> assertEquals(idx + 1, nv.value); assertEquals("default", nv.name) }
    }

    @Test fun `concurrent delayedGetTaggedPoint same instance`() = runBlocking {
        Calculator(4).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetTaggedPoint() } }.awaitAll()
            results.forEach { assertEquals(4, it.point.x) }
        }
    }

    @Test fun `concurrent delayedGetTaggedPoint separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetTaggedPoint() } } }.awaitAll()
        results.forEachIndexed { idx, tp -> assertEquals(idx + 1, tp.point.x) }
    }

    @Test fun `concurrent delayedGetRect same instance`() = runBlocking {
        Calculator(6).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetRect() } }.awaitAll()
            results.forEach { assertEquals(Point(0, 0), it.topLeft) }
        }
    }

    @Test fun `concurrent delayedGetRect separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetRect() } } }.awaitAll()
        results.forEachIndexed { idx, r -> assertEquals(Point(idx + 1, idx + 1), r.bottomRight) }
    }

    @Test fun `concurrent delayedGetCalcResult same instance`() = runBlocking {
        Calculator(9).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetCalcResult() } }.awaitAll()
            results.forEach { assertTrue(it.description.contains("delayed")) }
        }
    }

    @Test fun `concurrent delayedGetCalcResult separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetCalcResult() } } }.awaitAll()
        results.forEachIndexed { idx, cr -> assertEquals(idx + 1, cr.value) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SUSPEND — LIST/SET/MAP COLLECTIONS
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent delayedGetScores same instance`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetScores() } }.awaitAll()
            results.forEach { assertEquals(3, it.size) }
        }
    }

    @Test fun `concurrent delayedGetScores separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetScores() } } }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals(listOf(idx + 1, (idx + 1) * 2, (idx + 1) * 3), s) }
    }

    @Test fun `concurrent delayedGetScoresOrNull same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetScoresOrNull() } }.awaitAll()
            results.forEach { assertNotNull(it) }
        }
    }

    @Test fun `concurrent delayedGetScoresOrNull separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(if (i % 2 == 0) i else 0).use { it.delayedGetScoresOrNull() } } }.awaitAll()
        assertEquals(15, results.size)
    }

    @Test fun `concurrent delayedGetLabels same instance`() = runBlocking {
        Calculator(1).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetLabels() } }.awaitAll()
            results.forEach { assertEquals(2, it.size) }
        }
    }

    @Test fun `concurrent delayedGetLabels separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetLabels() } } }.awaitAll()
        results.forEachIndexed { idx, l -> assertTrue(l[1].contains("${idx + 1}")) }
    }

    @Test fun `concurrent delayedGetPoints same instance`() = runBlocking {
        Calculator(2).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetPoints() } }.awaitAll()
            results.forEach { assertEquals(2, it.size) }
        }
    }

    @Test fun `concurrent delayedGetPoints separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetPoints() } } }.awaitAll()
        results.forEachIndexed { idx, pts -> assertEquals(Point(idx + 1, 0), pts[0]) }
    }

    @Test fun `concurrent delayedGetPointsOrNull same instance`() = runBlocking {
        Calculator(4).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetPointsOrNull() } }.awaitAll()
            results.forEach { assertNotNull(it) }
        }
    }

    @Test fun `concurrent delayedGetPointsOrNull separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.delayedGetPointsOrNull() } } }.awaitAll()
        results.forEach { assertNull(it) }
    }

    @Test fun `concurrent delayedGetTaggedPoints same instance`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetTaggedPoints() } }.awaitAll()
            results.forEach { assertEquals(2, it.size) }
        }
    }

    @Test fun `concurrent delayedGetTaggedPoints separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetTaggedPoints() } } }.awaitAll()
        results.forEachIndexed { idx, tps -> assertEquals(idx + 1, tps[0].point.x) }
    }

    @Test fun `concurrent delayedGetRects same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetRects() } }.awaitAll()
            results.forEach { assertEquals(2, it.size) }
        }
    }

    @Test fun `concurrent delayedGetRects separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetRects() } } }.awaitAll()
        results.forEachIndexed { idx, rs -> assertEquals(Point(idx + 1, idx + 1), rs[0].bottomRight) }
    }

    @Test fun `concurrent delayedGetOperations same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetOperations() } }.awaitAll()
            results.forEach { assertEquals(Operation.entries.toList(), it) }
        }
    }

    @Test fun `concurrent delayedGetOperations separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.delayedGetOperations() } } }.awaitAll()
        results.forEach { assertEquals(Operation.entries.toList(), it) }
    }

    @Test fun `concurrent delayedGetWeights same instance`() = runBlocking {
        Calculator(4).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetWeights() } }.awaitAll()
            results.forEach { assertEquals(2, it.size) }
        }
    }

    @Test fun `concurrent delayedGetWeights separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetWeights() } } }.awaitAll()
        results.forEachIndexed { idx, w -> assertEquals((idx + 1).toDouble(), w[0], 0.001) }
    }

    @Test fun `concurrent delayedGetFlags same instance`() = runBlocking {
        Calculator(4).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetFlags() } }.awaitAll()
            results.forEach { assertEquals(2, it.size) }
        }
    }

    @Test fun `concurrent delayedGetFlags separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetFlags() } } }.awaitAll()
        results.forEach { assertEquals(2, it.size) }
    }

    // ── Sets ──────────────────────────────────────────────────────────────

    @Test fun `concurrent delayedGetUniqueScores same instance`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetUniqueScores() } }.awaitAll()
            results.forEach { assertTrue(it.contains(3)) }
        }
    }

    @Test fun `concurrent delayedGetUniqueScores separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetUniqueScores() } } }.awaitAll()
        results.forEachIndexed { idx, s -> assertTrue(s.contains(idx + 1)) }
    }

    @Test fun `concurrent delayedGetUniqueLabels same instance`() = runBlocking {
        Calculator(2).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetUniqueLabels() } }.awaitAll()
            results.forEach { assertTrue(it.contains("default")) }
        }
    }

    @Test fun `concurrent delayedGetUniqueLabels separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetUniqueLabels() } } }.awaitAll()
        results.forEach { assertTrue(it.contains("default")) }
    }

    @Test fun `concurrent delayedGetUsedOps same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetUsedOps() } }.awaitAll()
            results.forEach { assertTrue(it.contains(Operation.ADD)) }
        }
    }

    @Test fun `concurrent delayedGetUsedOps separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.delayedGetUsedOps() } } }.awaitAll()
        results.forEach { assertTrue(it.contains(Operation.ADD)) }
    }

    @Test fun `concurrent delayedGetUsedOpsOrNull same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetUsedOpsOrNull() } }.awaitAll()
            results.forEach { assertNotNull(it) }
        }
    }

    @Test fun `concurrent delayedGetUsedOpsOrNull separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.delayedGetUsedOpsOrNull() } } }.awaitAll()
        results.forEach { assertNull(it) }
    }

    // ── Maps ──────────────────────────────────────────────────────────────

    @Test fun `concurrent delayedGetMetadata same instance`() = runBlocking {
        Calculator(8).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetMetadata() } }.awaitAll()
            results.forEach { assertTrue(it.containsKey("current")) }
        }
    }

    @Test fun `concurrent delayedGetMetadata separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetMetadata() } } }.awaitAll()
        results.forEachIndexed { idx, m -> assertEquals(idx + 1, m["current"]) }
    }

    @Test fun `concurrent delayedGetMetadataOrNull same instance`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetMetadataOrNull() } }.awaitAll()
            results.forEach { assertNotNull(it) }
        }
    }

    @Test fun `concurrent delayedGetMetadataOrNull separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.delayedGetMetadataOrNull() } } }.awaitAll()
        results.forEach { assertNull(it) }
    }

    @Test fun `concurrent delayedGetSquares same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetSquares() } }.awaitAll()
            results.forEach { assertEquals(25, it[5]) }
        }
    }

    @Test fun `concurrent delayedGetSquares separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetSquares() } } }.awaitAll()
        results.forEachIndexed { idx, m -> assertEquals((idx + 1) * (idx + 1), m[idx + 1]) }
    }

    // ── Nested collections ────────────────────────────────────────────────

    @Test fun `concurrent delayedGetByteChunks same instance`() = runBlocking {
        Calculator(2).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.delayedGetByteChunks() } }.awaitAll()
            results.forEach { assertEquals(2, it.size); assertContentEquals(byteArrayOf(1, 2, 3), it[0]) }
        }
    }

    @Test fun `concurrent delayedGetByteChunks separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetByteChunks() } } }.awaitAll()
        results.forEachIndexed { idx, chunks -> assertEquals(idx + 1, chunks[1].size) }
    }

    @Test fun `concurrent delayedGetMatrix same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.delayedGetMatrix() } }.awaitAll()
            results.forEach { assertEquals(2, it.size) }
        }
    }

    @Test fun `concurrent delayedGetMatrix separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.delayedGetMatrix() } } }.awaitAll()
        results.forEachIndexed { idx, m -> assertEquals(listOf(idx + 1, idx + 2), m[0]) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FLOW — BASIC TYPES (concurrent same instance + separate instances)
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent countUp same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.countUp(5).toList() } }.awaitAll()
            results.forEach { assertEquals(listOf(1, 2, 3, 4, 5), it) }
        }
    }

    @Test fun `concurrent countUp separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.countUp(5).toList() } } }.awaitAll()
        results.forEach { assertEquals(listOf(1, 2, 3, 4, 5), it) }
    }

    @Test fun `concurrent tickStrings same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.tickStrings(3).toList() } }.awaitAll()
            results.forEach { assertEquals(listOf("tick_0", "tick_1", "tick_2"), it) }
        }
    }

    @Test fun `concurrent tickStrings separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.tickStrings(3).toList() } } }.awaitAll()
        results.forEach { assertEquals(listOf("tick_0", "tick_1", "tick_2"), it) }
    }

    @Test fun `concurrent infiniteFlow take same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.infiniteFlow().take(3).toList() } }.awaitAll()
            results.forEach { assertEquals(listOf(0, 1, 2), it) }
        }
    }

    @Test fun `concurrent infiniteFlow take separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.infiniteFlow().take(3).toList() } } }.awaitAll()
        results.forEach { assertEquals(listOf(0, 1, 2), it) }
    }

    @Test fun `concurrent emptyIntFlow same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.emptyIntFlow().toList() } }.awaitAll()
            results.forEach { assertTrue(it.isEmpty()) }
        }
    }

    @Test fun `concurrent emptyIntFlow separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.emptyIntFlow().toList() } } }.awaitAll()
        results.forEach { assertTrue(it.isEmpty()) }
    }

    @Test fun `concurrent singleFlow same instance`() = runBlocking {
        Calculator(42).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.singleFlow().first() } }.awaitAll()
            results.forEach { assertEquals(42, it) }
        }
    }

    @Test fun `concurrent singleFlow separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.singleFlow().first() } } }.awaitAll()
        assertEquals((1..15).toList(), results)
    }

    @Test fun `concurrent enumFlow same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.enumFlow().toList() } }.awaitAll()
            results.forEach { assertEquals(Operation.entries.toList(), it) }
        }
    }

    @Test fun `concurrent enumFlow separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.enumFlow().toList() } } }.awaitAll()
        results.forEach { assertEquals(Operation.entries.toList(), it) }
    }

    @Test fun `concurrent boolFlow same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.boolFlow().toList() } }.awaitAll()
            results.forEach { assertEquals(listOf(true, false, true), it) }
        }
    }

    @Test fun `concurrent boolFlow separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.boolFlow().toList() } } }.awaitAll()
        results.forEach { assertEquals(listOf(true, false, false), it) }
    }

    // ── Flow<ByteArray> ──────────────────────────────────────────────────

    @Test fun `concurrent byteChunks same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.byteChunks(3, 4).toList() } }.awaitAll()
            results.forEach { assertEquals(3, it.size); it.forEach { chunk -> assertEquals(4, chunk.size) } }
        }
    }

    @Test fun `concurrent byteChunks separate instances`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.byteChunks(2, 5).toList() } } }.awaitAll()
        results.forEach { assertEquals(2, it.size) }
    }

    @Test fun `concurrent singleByteFlow same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.singleByteFlow().first() } }.awaitAll()
            results.forEach { assertContentEquals(byteArrayOf(1, 2, 3), it) }
        }
    }

    @Test fun `concurrent singleByteFlow separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.singleByteFlow().first() } } }.awaitAll()
        results.forEach { assertContentEquals(byteArrayOf(1, 2, 3), it) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FLOW — COLLECTION TYPES
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent scoresFlow same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.scoresFlow(3).toList() } }.awaitAll()
            results.forEach { assertEquals(3, it.size); assertEquals(listOf(0, 0, 0), it[0]) }
        }
    }

    @Test fun `concurrent scoresFlow separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.scoresFlow(2).toList() } } }.awaitAll()
        results.forEach { assertEquals(2, it.size) }
    }

    @Test fun `concurrent labelsFlow same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.labelsFlow().toList() } }.awaitAll()
            results.forEach { assertEquals(2, it.size); assertEquals(listOf("a", "b"), it[0]) }
        }
    }

    @Test fun `concurrent labelsFlow separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.labelsFlow().toList() } } }.awaitAll()
        results.forEach { assertEquals(listOf("c", "d", "e"), it[1]) }
    }

    @Test fun `concurrent metadataFlow same instance`() = runBlocking {
        Calculator(10).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.metadataFlow(2).toList() } }.awaitAll()
            results.forEach { assertEquals(2, it.size); assertTrue(it[0].containsKey("step")) }
        }
    }

    @Test fun `concurrent metadataFlow separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.metadataFlow(2).toList() } } }.awaitAll()
        results.forEach { assertEquals(2, it.size) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FLOW — DATA CLASS TYPES
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent pointFlow same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.pointFlow(3).toList() } }.awaitAll()
            results.forEach { assertEquals(3, it.size); assertEquals(Point(0, 0), it[0]) }
        }
    }

    @Test fun `concurrent pointFlow separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.pointFlow(3).toList() } } }.awaitAll()
        results.forEach { assertEquals(listOf(Point(0, 0), Point(1, 2), Point(2, 4)), it) }
    }

    @Test fun `concurrent namedValueFlow same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.namedValueFlow().toList() } }.awaitAll()
            results.forEach { assertEquals(2, it.size); assertEquals(10, it[1].value) }
        }
    }

    @Test fun `concurrent namedValueFlow separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.namedValueFlow().toList() } } }.awaitAll()
        results.forEachIndexed { idx, nvs -> assertEquals(idx + 1, nvs[0].value) }
    }

    @Test fun `concurrent taggedPointFlow same instance`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.taggedPointFlow().toList() } }.awaitAll()
            results.forEach { assertEquals(Operation.entries.size, it.size) }
        }
    }

    @Test fun `concurrent taggedPointFlow separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.taggedPointFlow().toList() } } }.awaitAll()
        results.forEachIndexed { idx, tps -> assertEquals(idx + 1, tps[0].point.x) }
    }

    @Test fun `concurrent emptyPointFlow same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.emptyPointFlow().toList() } }.awaitAll()
            results.forEach { assertTrue(it.isEmpty()) }
        }
    }

    @Test fun `concurrent emptyPointFlow separate instances`() = runBlocking {
        val results = (1..15).map { async(Dispatchers.Default) { Calculator(0).use { it.emptyPointFlow().toList() } } }.awaitAll()
        results.forEach { assertTrue(it.isEmpty()) }
    }

    @Test fun `concurrent singlePointFlow same instance`() = runBlocking {
        Calculator(7).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.singlePointFlow().first() } }.awaitAll()
            results.forEach { assertEquals(Point(7, 14), it) }
        }
    }

    @Test fun `concurrent singlePointFlow separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.singlePointFlow().first() } } }.awaitAll()
        results.forEachIndexed { idx, p -> assertEquals(Point(idx + 1, (idx + 1) * 2), p) }
    }

    @Test fun `concurrent calcResultFlow same instance`() = runBlocking {
        Calculator(10).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.calcResultFlow(3).toList() } }.awaitAll()
            results.forEach { assertEquals(3, it.size); assertTrue(it[0].description.contains("step_0")) }
        }
    }

    @Test fun `concurrent calcResultFlow separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.calcResultFlow(2).toList() } } }.awaitAll()
        results.forEachIndexed { idx, crs -> assertEquals(idx + 1, crs[0].value) }
    }

    @Test fun `concurrent rectFlow same instance`() = runBlocking {
        Calculator(4).use { calc ->
            val results = (1..15).map { async(Dispatchers.Default) { calc.rectFlow().toList() } }.awaitAll()
            results.forEach { assertEquals(2, it.size); assertEquals(Point(0, 0), it[0].topLeft) }
        }
    }

    @Test fun `concurrent rectFlow separate instances`() = runBlocking {
        val results = (1..15).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.rectFlow().toList() } } }.awaitAll()
        results.forEachIndexed { idx, rs -> assertEquals(Point(idx + 1, idx + 1), rs[0].bottomRight) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HEAVY MIXED PRESSURE — suspend + flow interleaved
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `heavy mixed suspend types same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..20).map { i ->
                async(Dispatchers.Default) {
                    when (i % 4) {
                        0 -> calc.delayedAdd(i, 0).toString()
                        1 -> calc.delayedDescribe()
                        2 -> calc.delayedIsPositive().toString()
                        else -> calc.delayedNullable() ?: "null"
                    }
                }
            }.awaitAll()
            assertEquals(20, results.size)
        }
    }

    @Test fun `heavy mixed suspend types separate instances`() = runBlocking {
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    when (i % 4) {
                        0 -> calc.delayedAdd(i, 0).toString()
                        1 -> calc.delayedDescribe()
                        2 -> calc.delayedIsPositive().toString()
                        else -> calc.delayedNullable() ?: "null"
                    }
                }
            }
        }.awaitAll()
        assertEquals(20, results.size)
    }

    @Test fun `heavy mixed flow types same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..20).map { i ->
                async(Dispatchers.Default) {
                    when (i % 4) {
                        0 -> calc.countUp(3).toList().size
                        1 -> calc.tickStrings(2).toList().size
                        2 -> calc.enumFlow().toList().size
                        else -> calc.boolFlow().toList().size
                    }
                }
            }.awaitAll()
            assertEquals(20, results.size)
            results.forEach { assertTrue(it > 0) }
        }
    }

    @Test fun `heavy mixed flow types separate instances`() = runBlocking {
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    when (i % 4) {
                        0 -> calc.countUp(3).toList().size
                        1 -> calc.tickStrings(2).toList().size
                        2 -> calc.enumFlow().toList().size
                        else -> calc.boolFlow().toList().size
                    }
                }
            }
        }.awaitAll()
        assertEquals(20, results.size)
    }

    @Test fun `heavy suspend and flow interleaved same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..20).map { i ->
                async(Dispatchers.Default) {
                    if (i % 2 == 0) calc.delayedAdd(i, 0).toString()
                    else calc.countUp(3).toList().joinToString()
                }
            }.awaitAll()
            assertEquals(20, results.size)
        }
    }

    @Test fun `heavy suspend and flow interleaved separate instances`() = runBlocking {
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    if (i % 2 == 0) calc.delayedGetPoint().toString()
                    else calc.pointFlow(2).toList().joinToString()
                }
            }
        }.awaitAll()
        assertEquals(20, results.size)
    }

    @Test fun `heavy suspend collections mixed same instance`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..20).map { i ->
                async(Dispatchers.Default) {
                    when (i % 5) {
                        0 -> calc.delayedGetScores().size
                        1 -> calc.delayedGetLabels().size
                        2 -> calc.delayedGetUniqueScores().size
                        3 -> calc.delayedGetMetadata().size
                        else -> calc.delayedGetMatrix().size
                    }
                }
            }.awaitAll()
            assertEquals(20, results.size)
            results.forEach { assertTrue(it > 0) }
        }
    }

    @Test fun `heavy suspend collections mixed separate instances`() = runBlocking {
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    when (i % 5) {
                        0 -> calc.delayedGetScores().size
                        1 -> calc.delayedGetLabels().size
                        2 -> calc.delayedGetUniqueScores().size
                        3 -> calc.delayedGetMetadata().size
                        else -> calc.delayedGetMatrix().size
                    }
                }
            }
        }.awaitAll()
        assertEquals(20, results.size)
    }

    @Test fun `heavy flow dataclass mixed same instance`() = runBlocking {
        Calculator(4).use { calc ->
            val results = (1..20).map { i ->
                async(Dispatchers.Default) {
                    when (i % 4) {
                        0 -> calc.pointFlow(2).toList().size
                        1 -> calc.namedValueFlow().toList().size
                        2 -> calc.calcResultFlow(2).toList().size
                        else -> calc.rectFlow().toList().size
                    }
                }
            }.awaitAll()
            assertEquals(20, results.size)
            results.forEach { assertEquals(2, it) }
        }
    }

    @Test fun `heavy flow dataclass mixed separate instances`() = runBlocking {
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    when (i % 4) {
                        0 -> calc.pointFlow(2).toList().size
                        1 -> calc.namedValueFlow().toList().size
                        2 -> calc.calcResultFlow(2).toList().size
                        else -> calc.rectFlow().toList().size
                    }
                }
            }
        }.awaitAll()
        assertEquals(20, results.size)
        results.forEach { assertEquals(2, it) }
    }

    @Test fun `heavy flow collection mixed same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..15).map { i ->
                async(Dispatchers.Default) {
                    when (i % 3) {
                        0 -> calc.scoresFlow(2).toList().size
                        1 -> calc.labelsFlow().toList().size
                        else -> calc.metadataFlow(2).toList().size
                    }
                }
            }.awaitAll()
            assertEquals(15, results.size)
            results.forEach { assertEquals(2, it) }
        }
    }

    @Test fun `heavy flow collection mixed separate instances`() = runBlocking {
        val results = (1..15).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    when (i % 3) {
                        0 -> calc.scoresFlow(2).toList().size
                        1 -> calc.labelsFlow().toList().size
                        else -> calc.metadataFlow(2).toList().size
                    }
                }
            }
        }.awaitAll()
        assertEquals(15, results.size)
        results.forEach { assertEquals(2, it) }
    }
}
