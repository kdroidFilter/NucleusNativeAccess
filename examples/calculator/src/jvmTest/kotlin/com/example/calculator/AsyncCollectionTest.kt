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

class AsyncCollectionTest {

    // ══════════════════════════════════════════════════════════════════════════
    // LIST RETURN
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getScores - concurrent same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getScores() } }.awaitAll()
            results.forEach { assertEquals(listOf(5, 10, 15), it) }
        }
    }

    @Test
    fun `getScores - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getScores() } } }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals(listOf(idx + 1, (idx + 1) * 2, (idx + 1) * 3), s) }
    }

    @Test
    fun `getLabels - concurrent same instance`() = runBlocking {
        Calculator(5).use { calc ->
            calc.label = "lbl"
            val results = (1..10).map { async(Dispatchers.Default) { calc.getLabels() } }.awaitAll()
            results.forEach { assertEquals(listOf("lbl", "item_5"), it) }
        }
    }

    @Test
    fun `getLabels - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.getLabels() } } }.awaitAll()
        results.forEach { assertEquals(listOf("default", "item_0"), it) }
    }

    @Test
    fun `getWeights - concurrent same instance`() = runBlocking {
        Calculator(10).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getWeights() } }.awaitAll()
            results.forEach { assertEquals(10.0, it[0], 0.001); assertEquals(15.0, it[1], 0.001) }
        }
    }

    @Test
    fun `getWeights - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getWeights() } } }.awaitAll()
        results.forEachIndexed { idx, w -> assertEquals((idx + 1).toDouble(), w[0], 0.001) }
    }

    @Test
    fun `getFlags - concurrent same instance`() = runBlocking {
        Calculator(5).use { calc ->
            calc.label = "t"
            val results = (1..10).map { async(Dispatchers.Default) { calc.getFlags() } }.awaitAll()
            results.forEach { assertEquals(listOf(true, false, true), it) }
        }
    }

    @Test
    fun `getFlags - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.getFlags() } } }.awaitAll()
        results.forEach { assertEquals(listOf(false, true, false), it) }
    }

    @Test
    fun `getOperations - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getOperations() } }.awaitAll()
            results.forEach { assertEquals(listOf(Operation.ADD, Operation.SUBTRACT, Operation.MULTIPLY), it) }
        }
    }

    @Test
    fun `getOperations - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.getOperations() } } }.awaitAll()
        results.forEach { assertEquals(3, it.size) }
    }

    @Test
    fun `getLongScores - concurrent same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getLongScores() } }.awaitAll()
            results.forEach { assertEquals(listOf(5L, 500_000L), it) }
        }
    }

    @Test
    fun `getLongScores - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getLongScores() } } }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals((idx + 1).toLong(), s[0]) }
    }

    @Test
    fun `getFloatWeights - concurrent same instance`() = runBlocking {
        Calculator(10).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getFloatWeights() } }.awaitAll()
            results.forEach { assertEquals(10.0f, it[0], 0.001f) }
        }
    }

    @Test
    fun `getFloatWeights - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getFloatWeights() } } }.awaitAll()
        results.forEachIndexed { idx, w -> assertEquals((idx + 1).toFloat(), w[0], 0.001f) }
    }

    @Test
    fun `getShortValues - concurrent same instance`() = runBlocking {
        Calculator(7).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getShortValues() } }.awaitAll()
            results.forEach { assertEquals(listOf(7.toShort(), 14.toShort()), it) }
        }
    }

    @Test
    fun `getShortValues - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getShortValues() } } }.awaitAll()
        results.forEachIndexed { idx, v -> assertEquals((idx + 1).toShort(), v[0]) }
    }

    @Test
    fun `getByteValues - concurrent same instance`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getByteValues() } }.awaitAll()
            results.forEach { assertEquals(listOf(3.toByte(), 4.toByte()), it) }
        }
    }

    @Test
    fun `getByteValues - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getByteValues() } } }.awaitAll()
        results.forEachIndexed { idx, v -> assertEquals((idx + 1).toByte(), v[0]) }
    }

    @Test
    fun `getPoints - concurrent same instance`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getPoints() } }.awaitAll()
            results.forEach { assertEquals(3, it.size); assertEquals(Point(3, 0), it[0]) }
        }
    }

    @Test
    fun `getPoints - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getPoints() } } }.awaitAll()
        results.forEachIndexed { idx, p -> assertEquals(Point(idx + 1, 0), p[0]) }
    }

    @Test
    fun `getNamedValues - concurrent same instance`() = runBlocking {
        Calculator(4).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getNamedValues() } }.awaitAll()
            results.forEach { assertEquals(NamedValue("first", 4), it[0]) }
        }
    }

    @Test
    fun `getNamedValues - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getNamedValues() } } }.awaitAll()
        results.forEachIndexed { idx, nv -> assertEquals(idx + 1, nv[0].value) }
    }

    @Test
    fun `getTaggedPoints - concurrent same instance`() = runBlocking {
        Calculator(2).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getTaggedPoints() } }.awaitAll()
            results.forEach { assertEquals(2, it.size); assertEquals(Point(2, 0), it[0].point) }
        }
    }

    @Test
    fun `getTaggedPoints - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getTaggedPoints() } } }.awaitAll()
        results.forEachIndexed { idx, tp -> assertEquals(Point(idx + 1, 0), tp[0].point) }
    }

    @Test
    fun `getSingletonList - concurrent same instance`() = runBlocking {
        Calculator(42).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getSingletonList() } }.awaitAll()
            results.forEach { assertEquals(listOf(42), it) }
        }
    }

    @Test
    fun `getSingletonList - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getSingletonList() } } }.awaitAll()
        results.forEachIndexed { idx, l -> assertEquals(listOf(idx + 1), l) }
    }

    @Test
    fun `getEmptyList - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getEmptyList() } }.awaitAll()
            results.forEach { assertTrue(it.isEmpty()) }
        }
    }

    @Test
    fun `getEmptyList - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.getEmptyList() } } }.awaitAll()
        results.forEach { assertTrue(it.isEmpty()) }
    }

    @Test
    fun `getLargeList - concurrent same instance`() = runBlocking {
        Calculator(2).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getLargeList(100) } }.awaitAll()
            results.forEach { assertEquals(100, it.size); assertEquals(0, it[0]); assertEquals(198, it[99]) }
        }
    }

    @Test
    fun `getLargeList - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getLargeList(100) } } }.awaitAll()
        results.forEachIndexed { idx, l -> assertEquals(100, l.size); assertEquals(99 * (idx + 1), l[99]) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LIST PARAM
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `sumAll - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.sumAll(listOf(1, 2, 3)) } }.awaitAll()
            results.forEach { assertEquals(6, it) }
        }
    }

    @Test
    fun `sumAll - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(0).use { it.sumAll(listOf(i, i * 2)) } } }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals((idx + 1) * 3, s) }
    }

    @Test
    fun `joinLabels - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.joinLabels(listOf("a", "b")) } }.awaitAll()
            results.forEach { assertEquals("a, b", it) }
        }
    }

    @Test
    fun `joinLabels - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(0).use { it.joinLabels(listOf("x$i")) } } }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals("x${idx + 1}", s) }
    }

    @Test
    fun `countOps - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.countOps(listOf(Operation.ADD, Operation.SUBTRACT)) } }.awaitAll()
            results.forEach { assertEquals(2, it) }
        }
    }

    @Test
    fun `countOps - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.countOps(listOf(Operation.ADD)) } } }.awaitAll()
        results.forEach { assertEquals(1, it) }
    }

    @Test
    fun `sumLongs - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.sumLongs(listOf(100L, 200L)) } }.awaitAll()
            results.forEach { assertEquals(300L, it) }
        }
    }

    @Test
    fun `sumLongs - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(0).use { it.sumLongs(listOf(i.toLong())) } } }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals((idx + 1).toLong(), s) }
    }

    @Test
    fun `reverseList - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.reverseList(listOf(1, 2, 3)) } }.awaitAll()
            results.forEach { assertEquals(listOf(3, 2, 1), it) }
        }
    }

    @Test
    fun `reverseList - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(0).use { it.reverseList(listOf(i, i + 1)) } } }.awaitAll()
        results.forEachIndexed { idx, r -> assertEquals(listOf(idx + 2, idx + 1), r) }
    }

    @Test
    fun `filterPositive - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.filterPositive(listOf(-1, 2, -3, 4)) } }.awaitAll()
            results.forEach { assertEquals(listOf(2, 4), it) }
        }
    }

    @Test
    fun `filterPositive - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.filterPositive(listOf(-5, 10)) } } }.awaitAll()
        results.forEach { assertEquals(listOf(10), it) }
    }

    @Test
    fun `transformStrings - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.transformStrings(listOf("hello")) } }.awaitAll()
            results.forEach { assertEquals(listOf("HELLO"), it) }
        }
    }

    @Test
    fun `transformStrings - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(0).use { it.transformStrings(listOf("a$i")) } } }.awaitAll()
        results.forEachIndexed { idx, r -> assertEquals(listOf("A${idx + 1}"), r) }
    }

    @Test
    fun `sumPoints - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.sumPoints(listOf(Point(1, 2), Point(3, 4))) } }.awaitAll()
            results.forEach { assertEquals(10, it) }
        }
    }

    @Test
    fun `sumPoints - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(0).use { it.sumPoints(listOf(Point(i, i))) } } }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals((idx + 1) * 2, s) }
    }

    @Test
    fun `describeNamedValues - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.describeNamedValues(listOf(NamedValue("k", 1))) } }.awaitAll()
            results.forEach { assertEquals("k=1", it) }
        }
    }

    @Test
    fun `describeNamedValues - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(0).use { it.describeNamedValues(listOf(NamedValue("v", i))) } } }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals("v=${idx + 1}", s) }
    }

    @Test
    fun `countTaggedPoints - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val items = listOf(TaggedPoint(Point(0, 0), Operation.ADD), TaggedPoint(Point(1, 1), Operation.SUBTRACT))
            val results = (1..10).map { async(Dispatchers.Default) { calc.countTaggedPoints(items) } }.awaitAll()
            results.forEach { assertEquals(2, it) }
        }
    }

    @Test
    fun `countTaggedPoints - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) { Calculator(0).use { it.countTaggedPoints(List(i) { TaggedPoint(Point(0, 0), Operation.ADD) }) } }
        }.awaitAll()
        results.forEachIndexed { idx, c -> assertEquals(idx + 1, c) }
    }

    @Test
    fun `sumRects - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val rects = listOf(Rect(Point(1, 2), Point(3, 4)))
            val results = (1..10).map { async(Dispatchers.Default) { calc.sumRects(rects) } }.awaitAll()
            results.forEach { assertEquals(10, it) }
        }
    }

    @Test
    fun `sumRects - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) { Calculator(0).use { it.sumRects(listOf(Rect(Point(i, 0), Point(0, 0)))) } }
        }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals(idx + 1, s) }
    }

    @Test
    fun `describePersons - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val persons = listOf(Person("Alice", 30, Address("Main St", "NY")))
            val results = (1..10).map { async(Dispatchers.Default) { calc.describePersons(persons) } }.awaitAll()
            results.forEach { assertEquals("Alice(30) @ Main St, NY", it) }
        }
    }

    @Test
    fun `describePersons - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) { Calculator(0).use { it.describePersons(listOf(Person("P$i", i, Address("S", "C")))) } }
        }.awaitAll()
        results.forEachIndexed { idx, s -> assertTrue(s.contains("P${idx + 1}")) }
    }

    @Test
    fun `oldestPersonAge - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val persons = listOf(Person("A", 25, Address("S", "C")), Person("B", 40, Address("S", "C")))
            val results = (1..10).map { async(Dispatchers.Default) { calc.oldestPersonAge(persons) } }.awaitAll()
            results.forEach { assertEquals(40, it) }
        }
    }

    @Test
    fun `oldestPersonAge - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) { Calculator(0).use { it.oldestPersonAge(listOf(Person("X", i * 10, Address("S", "C")))) } }
        }.awaitAll()
        results.forEachIndexed { idx, a -> assertEquals((idx + 1) * 10, a) }
    }

    @Test
    fun `countByteChunks - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val chunks = listOf(byteArrayOf(1, 2), byteArrayOf(3))
            val results = (1..10).map { async(Dispatchers.Default) { calc.countByteChunks(chunks) } }.awaitAll()
            results.forEach { assertEquals(3, it) }
        }
    }

    @Test
    fun `countByteChunks - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) { Calculator(0).use { it.countByteChunks(listOf(ByteArray(i))) } }
        }.awaitAll()
        results.forEachIndexed { idx, c -> assertEquals(idx + 1, c) }
    }

    @Test
    fun `sumMatrix - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val matrix = listOf(listOf(1, 2), listOf(3, 4))
            val results = (1..10).map { async(Dispatchers.Default) { calc.sumMatrix(matrix) } }.awaitAll()
            results.forEach { assertEquals(10, it) }
        }
    }

    @Test
    fun `sumMatrix - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) { Calculator(0).use { it.sumMatrix(listOf(listOf(i, i))) } }
        }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals((idx + 1) * 2, s) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SET RETURN / PARAM
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getUniqueDigits - concurrent same instance`() = runBlocking {
        Calculator(123).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getUniqueDigits() } }.awaitAll()
            results.forEach { assertEquals(setOf(1, 2, 3), it) }
        }
    }

    @Test
    fun `getUniqueDigits - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(111).use { it.getUniqueDigits() } } }.awaitAll()
        results.forEach { assertEquals(setOf(1), it) }
    }

    @Test
    fun `sumUnique - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.sumUnique(setOf(10, 20)) } }.awaitAll()
            results.forEach { assertEquals(30, it) }
        }
    }

    @Test
    fun `sumUnique - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(0).use { it.sumUnique(setOf(i)) } } }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals(idx + 1, s) }
    }

    @Test
    fun `getUniqueLabels - concurrent same instance`() = runBlocking {
        Calculator(5).use { calc ->
            calc.label = "lbl"
            val results = (1..10).map { async(Dispatchers.Default) { calc.getUniqueLabels() } }.awaitAll()
            results.forEach { assertTrue(it.contains("lbl")); assertTrue(it.contains("item_5")) }
        }
    }

    @Test
    fun `getUniqueLabels - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.getUniqueLabels() } } }.awaitAll()
        results.forEach { assertTrue(it.contains("default")) }
    }

    @Test
    fun `joinUniqueStrings - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.joinUniqueStrings(setOf("b", "a")) } }.awaitAll()
            results.forEach { assertEquals("a;b", it) }
        }
    }

    @Test
    fun `joinUniqueStrings - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.joinUniqueStrings(setOf("x")) } } }.awaitAll()
        results.forEach { assertEquals("x", it) }
    }

    @Test
    fun `getUsedOps - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getUsedOps() } }.awaitAll()
            results.forEach { assertTrue(it.contains(Operation.ADD)) }
        }
    }

    @Test
    fun `getUsedOps - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.getUsedOps() } } }.awaitAll()
        results.forEach { assertEquals(setOf(Operation.ADD), it) }
    }

    @Test
    fun `getEmptySet - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getEmptySet() } }.awaitAll()
            results.forEach { assertTrue(it.isEmpty()) }
        }
    }

    @Test
    fun `getEmptySet - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.getEmptySet() } } }.awaitAll()
        results.forEach { assertTrue(it.isEmpty()) }
    }

    @Test
    fun `intersectSets - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.intersectSets(setOf(1, 2, 3), setOf(2, 3, 4)) } }.awaitAll()
            results.forEach { assertEquals(setOf(2, 3), it) }
        }
    }

    @Test
    fun `intersectSets - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) { Calculator(0).use { it.intersectSets(setOf(i, i + 1), setOf(i + 1, i + 2)) } }
        }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals(setOf(idx + 2), s) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAP RETURN / PARAM
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getMetadata - concurrent same instance`() = runBlocking {
        Calculator(42).use { calc ->
            calc.scale = 3.0
            val results = (1..10).map { async(Dispatchers.Default) { calc.getMetadata() } }.awaitAll()
            results.forEach { assertEquals(42, it["current"]); assertEquals(3, it["scale"]) }
        }
    }

    @Test
    fun `getMetadata - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getMetadata() } } }.awaitAll()
        results.forEachIndexed { idx, m -> assertEquals(idx + 1, m["current"]) }
    }

    @Test
    fun `sumMap - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.sumMap(mapOf("a" to 5, "b" to 10)) } }.awaitAll()
            results.forEach { assertEquals(15, it) }
        }
    }

    @Test
    fun `sumMap - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(0).use { it.sumMap(mapOf("k" to i)) } } }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals(idx + 1, s) }
    }

    @Test
    fun `getIndexedLabels - concurrent same instance`() = runBlocking {
        Calculator(7).use { calc ->
            calc.label = "hi"
            val results = (1..10).map { async(Dispatchers.Default) { calc.getIndexedLabels() } }.awaitAll()
            results.forEach { assertEquals("hi", it[0]); assertEquals("item_7", it[1]) }
        }
    }

    @Test
    fun `getIndexedLabels - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getIndexedLabels() } } }.awaitAll()
        results.forEachIndexed { idx, m -> assertEquals("item_${idx + 1}", m[1]) }
    }

    @Test
    fun `getSquares - concurrent same instance`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getSquares() } }.awaitAll()
            results.forEach { assertEquals(1, it[1]); assertEquals(4, it[2]); assertEquals(9, it[3]) }
        }
    }

    @Test
    fun `getSquares - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getSquares() } } }.awaitAll()
        results.forEach { assertEquals(1, it[1]) }
    }

    @Test
    fun `sumMapValues - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.sumMapValues(mapOf(1 to 3, 2 to 7)) } }.awaitAll()
            results.forEach { assertEquals(10, it) }
        }
    }

    @Test
    fun `sumMapValues - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(0).use { it.sumMapValues(mapOf(1 to i)) } } }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals(idx + 1, s) }
    }

    @Test
    fun `getStringMap - concurrent same instance`() = runBlocking {
        Calculator(42).use { calc ->
            calc.label = "test"
            val results = (1..10).map { async(Dispatchers.Default) { calc.getStringMap() } }.awaitAll()
            results.forEach { assertEquals("test", it["name"]); assertEquals("42", it["value"]) }
        }
    }

    @Test
    fun `getStringMap - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getStringMap() } } }.awaitAll()
        results.forEachIndexed { idx, m -> assertEquals("${idx + 1}", m["value"]) }
    }

    @Test
    fun `concatMapEntries - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.concatMapEntries(sortedMapOf("a" to "1")) } }.awaitAll()
            results.forEach { assertEquals("a=1", it) }
        }
    }

    @Test
    fun `concatMapEntries - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) { Calculator(0).use { it.concatMapEntries(sortedMapOf("k" to "$i")) } }
        }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals("k=${idx + 1}", s) }
    }

    @Test
    fun `getSingletonMap - concurrent same instance`() = runBlocking {
        Calculator(7).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getSingletonMap() } }.awaitAll()
            results.forEach { assertEquals(mapOf("only" to 7), it) }
        }
    }

    @Test
    fun `getSingletonMap - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getSingletonMap() } } }.awaitAll()
        results.forEachIndexed { idx, m -> assertEquals(idx + 1, m["only"]) }
    }

    @Test
    fun `getEmptyMap - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getEmptyMap() } }.awaitAll()
            results.forEach { assertTrue(it.isEmpty()) }
        }
    }

    @Test
    fun `getEmptyMap - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.getEmptyMap() } } }.awaitAll()
        results.forEach { assertTrue(it.isEmpty()) }
    }

    @Test
    fun `mergeMapValues - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.mergeMapValues(mapOf("a" to 1), mapOf("b" to 2)) }
            }.awaitAll()
            results.forEach { assertEquals(mapOf("a" to 1, "b" to 2), it) }
        }
    }

    @Test
    fun `mergeMapValues - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) { Calculator(0).use { it.mergeMapValues(mapOf("x" to i), mapOf("y" to i * 2)) } }
        }.awaitAll()
        results.forEachIndexed { idx, m -> assertEquals(idx + 1, m["x"]); assertEquals((idx + 1) * 2, m["y"]) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NULLABLE COLLECTIONS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getScoresOrNull - concurrent same instance non-null`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getScoresOrNull() } }.awaitAll()
            results.forEach { assertNotNull(it); assertEquals(listOf(5, 10), it) }
        }
    }

    @Test
    fun `getScoresOrNull - concurrent separate instances null`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.getScoresOrNull() } } }.awaitAll()
        results.forEach { assertNull(it) }
    }

    @Test
    fun `getLabelsOrNull - concurrent same instance non-null`() = runBlocking {
        Calculator(0).use { calc ->
            calc.label = "hi"
            val results = (1..10).map { async(Dispatchers.Default) { calc.getLabelsOrNull() } }.awaitAll()
            results.forEach { assertNotNull(it); assertEquals(listOf("hi", "extra"), it) }
        }
    }

    @Test
    fun `getLabelsOrNull - concurrent separate instances null`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.getLabelsOrNull() } } }.awaitAll()
        results.forEach { assertNull(it) }
    }

    @Test
    fun `sumAllOrNull - concurrent same instance non-null`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.sumAllOrNull(listOf(3, 7)) } }.awaitAll()
            results.forEach { assertEquals(10, it) }
        }
    }

    @Test
    fun `sumAllOrNull - concurrent separate instances null`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.sumAllOrNull(null) } } }.awaitAll()
        results.forEach { assertEquals(-1, it) }
    }

    @Test
    fun `getOpsOrNull - concurrent same instance non-null`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getOpsOrNull() } }.awaitAll()
            results.forEach { assertNotNull(it); assertTrue(it!!.contains(Operation.ADD)) }
        }
    }

    @Test
    fun `getOpsOrNull - concurrent separate instances null`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(-1).use { it.getOpsOrNull() } } }.awaitAll()
        results.forEach { assertNull(it) }
    }

    @Test
    fun `getMetadataOrNull - concurrent same instance non-null`() = runBlocking {
        Calculator(10).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getMetadataOrNull() } }.awaitAll()
            results.forEach { assertNotNull(it); assertEquals(mapOf("val" to 10), it) }
        }
    }

    @Test
    fun `getMetadataOrNull - concurrent separate instances null`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.getMetadataOrNull() } } }.awaitAll()
        results.forEach { assertNull(it) }
    }

    @Test
    fun `getScoresOrNullByLabel - concurrent same instance non-null`() = runBlocking {
        Calculator(0).use { calc ->
            calc.label = "tag"
            val results = (1..10).map { async(Dispatchers.Default) { calc.getScoresOrNullByLabel() } }.awaitAll()
            results.forEach { assertNotNull(it); assertEquals(listOf("tag"), it) }
        }
    }

    @Test
    fun `getScoresOrNullByLabel - concurrent separate instances null`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.getScoresOrNullByLabel() } } }.awaitAll()
        results.forEach { assertNull(it) }
    }

    @Test
    fun `getNullableSetByAccum - concurrent same instance non-null`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getNullableSetByAccum() } }.awaitAll()
            results.forEach { assertNotNull(it); assertEquals(setOf(5, 6), it) }
        }
    }

    @Test
    fun `getNullableSetByAccum - concurrent separate instances null`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(-1).use { it.getNullableSetByAccum() } } }.awaitAll()
        results.forEach { assertNull(it) }
    }

    @Test
    fun `getNullableMapByLabel - concurrent same instance non-null`() = runBlocking {
        Calculator(0).use { calc ->
            calc.label = "lbl"
            val results = (1..10).map { async(Dispatchers.Default) { calc.getNullableMapByLabel() } }.awaitAll()
            results.forEach { assertNotNull(it); assertEquals(mapOf("label" to "lbl"), it) }
        }
    }

    @Test
    fun `getNullableMapByLabel - concurrent separate instances null`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.getNullableMapByLabel() } } }.awaitAll()
        results.forEach { assertNull(it) }
    }

    @Test
    fun `getPointsOrNull - concurrent same instance non-null`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getPointsOrNull() } }.awaitAll()
            results.forEach { assertNotNull(it); assertEquals(listOf(Point(3, 3)), it) }
        }
    }

    @Test
    fun `getPointsOrNull - concurrent separate instances null`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.getPointsOrNull() } } }.awaitAll()
        results.forEach { assertNull(it) }
    }

    @Test
    fun `sumPointsOrNull - concurrent same instance non-null`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.sumPointsOrNull(listOf(Point(1, 2))) } }.awaitAll()
            results.forEach { assertEquals(3, it) }
        }
    }

    @Test
    fun `sumPointsOrNull - concurrent separate instances null`() = runBlocking {
        val results = (1..10).map { async(Dispatchers.Default) { Calculator(0).use { it.sumPointsOrNull(null) } } }.awaitAll()
        results.forEach { assertEquals(-1, it) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NESTED COLLECTIONS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getMatrix - concurrent same instance`() = runBlocking {
        Calculator(2).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getMatrix() } }.awaitAll()
            results.forEach { assertEquals(listOf(2, 3), it[0]); assertEquals(listOf(4, 5), it[1]) }
        }
    }

    @Test
    fun `getMatrix - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getMatrix() } } }.awaitAll()
        results.forEachIndexed { idx, m -> assertEquals(listOf(idx + 1, idx + 2), m[0]) }
    }

    @Test
    fun `getTagGrid - concurrent same instance`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getTagGrid() } }.awaitAll()
            results.forEach { assertEquals(listOf("a_3", "b_3"), it[0]); assertEquals(listOf("c_3"), it[1]) }
        }
    }

    @Test
    fun `getTagGrid - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getTagGrid() } } }.awaitAll()
        results.forEachIndexed { idx, g -> assertEquals(listOf("a_${idx + 1}", "b_${idx + 1}"), g[0]) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BYTEARRAY IN COLLECTIONS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getByteChunks - concurrent same instance`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getByteChunks() } }.awaitAll()
            results.forEach {
                assertEquals(3, it.size)
                assertContentEquals(byteArrayOf(1, 2, 3), it[0])
                assertContentEquals(byteArrayOf(4, 5, 6, 7), it[1])
            }
        }
    }

    @Test
    fun `getByteChunks - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getByteChunks() } } }.awaitAll()
        results.forEachIndexed { idx, chunks -> assertEquals(3, chunks.size); assertEquals(idx + 1, chunks[2].size) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COLLECTION PROPERTIES
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `recentScores - concurrent same instance`() = runBlocking {
        Calculator(7).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.recentScores } }.awaitAll()
            results.forEach { assertEquals(listOf(7, 14, 21), it) }
        }
    }

    @Test
    fun `recentScores - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.recentScores } } }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals(listOf(idx + 1, (idx + 1) * 2, (idx + 1) * 3), s) }
    }

    @Test
    fun `tags read - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            calc.tags = listOf("alpha", "beta")
            val results = (1..10).map { async(Dispatchers.Default) { calc.tags } }.awaitAll()
            results.forEach { assertEquals(listOf("alpha", "beta"), it) }
        }
    }

    @Test
    fun `tags write - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.tags = listOf("t$i")
                    calc.tags
                }
            }
        }.awaitAll()
        results.forEachIndexed { idx, t -> assertEquals(listOf("t${idx + 1}"), t) }
    }

    @Test
    fun `info - concurrent same instance`() = runBlocking {
        Calculator(10).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.info } }.awaitAll()
            results.forEach { assertEquals(10, it["current"]) }
        }
    }

    @Test
    fun `info - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.info } } }.awaitAll()
        results.forEachIndexed { idx, m -> assertEquals(idx + 1, m["current"]) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DC WITH COLLECTION FIELDS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getTaggedList - concurrent same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getTaggedList() } }.awaitAll()
            results.forEach { assertEquals(listOf(5, 10, 15), it.scores); assertEquals("default", it.label) }
        }
    }

    @Test
    fun `getTaggedList - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getTaggedList() } } }.awaitAll()
        results.forEachIndexed { idx, tl -> assertEquals(listOf(idx + 1, (idx + 1) * 2, (idx + 1) * 3), tl.scores) }
    }

    @Test
    fun `applyTaggedList - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.applyTaggedList(TaggedList("t", listOf(1, 2, 3))) } }.awaitAll()
            results.forEach { assertEquals(6, it) }
        }
    }

    @Test
    fun `applyTaggedList - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) { Calculator(0).use { it.applyTaggedList(TaggedList("l$i", listOf(i))) } }
        }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals(idx + 1, s) }
    }

    @Test
    fun `getMetadataHolder - concurrent same instance`() = runBlocking {
        Calculator(42).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getMetadataHolder() } }.awaitAll()
            results.forEach { assertEquals("calc", it.name); assertEquals(42, it.metadata["current"]) }
        }
    }

    @Test
    fun `getMetadataHolder - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getMetadataHolder() } } }.awaitAll()
        results.forEachIndexed { idx, mh -> assertEquals(idx + 1, mh.metadata["current"]) }
    }

    @Test
    fun `applyMetadataHolder - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.applyMetadataHolder(MetadataHolder("n", mapOf("a" to 5, "b" to 10))) }
            }.awaitAll()
            results.forEach { assertEquals("n:15", it) }
        }
    }

    @Test
    fun `applyMetadataHolder - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) { Calculator(0).use { it.applyMetadataHolder(MetadataHolder("m$i", mapOf("v" to i))) } }
        }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals("m${idx + 1}:${idx + 1}", s) }
    }

    @Test
    fun `getMultiCollDC - concurrent same instance`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getMultiCollDC() } }.awaitAll()
            results.forEach { assertEquals(listOf(5, 6, 7), it.counts); assertEquals(listOf(true, false), it.flags) }
        }
    }

    @Test
    fun `getMultiCollDC - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getMultiCollDC() } } }.awaitAll()
        results.forEachIndexed { idx, mc -> assertEquals(listOf(idx + 1, idx + 2, idx + 3), mc.counts) }
    }

    @Test
    fun `getBinaryPayload - concurrent same instance`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..10).map { async(Dispatchers.Default) { calc.getBinaryPayload() } }.awaitAll()
            results.forEach { assertEquals("payload_3", it.name); assertEquals(3, it.data.size) }
        }
    }

    @Test
    fun `getBinaryPayload - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i -> async(Dispatchers.Default) { Calculator(i).use { it.getBinaryPayload() } } }.awaitAll()
        results.forEachIndexed { idx, bp -> assertEquals("payload_${idx + 1}", bp.name); assertEquals(idx + 1, bp.data.size) }
    }

    @Test
    fun `applyBinaryPayload - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.applyBinaryPayload(BinaryPayload("bp", byteArrayOf(1, 2, 3))) }
            }.awaitAll()
            results.forEach { assertEquals(3, it) }
        }
    }

    @Test
    fun `applyBinaryPayload - concurrent separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) { Calculator(0).use { it.applyBinaryPayload(BinaryPayload("p$i", ByteArray(i))) } }
        }.awaitAll()
        results.forEachIndexed { idx, s -> assertEquals(idx + 1, s) }
    }
}
