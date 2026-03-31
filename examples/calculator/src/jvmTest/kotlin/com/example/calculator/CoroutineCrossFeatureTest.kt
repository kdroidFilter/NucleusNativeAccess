package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.first

class CoroutineCrossFeatureTest {

    // ══════════════════════════════════════════════════════════════════════════
    // CROSS-FEATURE COROUTINE BATTLE TESTS
    // Every feature tested under concurrent coroutine pressure
    // ══════════════════════════════════════════════════════════════════════════

    // ── ByteArray everywhere in coroutines ──────────────────────────────────

    @Test
    fun `coroutine - ByteArray callback concurrent`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) {
                    val transformed = calc.transformBytes(byteArrayOf(1, 2, 3)) { it.reversedArray() }
                    transformed
                }
            }.awaitAll()
            results.forEach { assertContentEquals(byteArrayOf(3, 2, 1), it) }
        }
    }

    @Test
    fun `coroutine - ByteArray DC field concurrent`() = runBlocking {
        Calculator(4).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) { calc.getBinaryPayload() }
            }.awaitAll()
            results.forEach {
                assertEquals("payload_4", it.name)
                assertEquals(4, it.data.size)
            }
        }
    }

    @Test
    fun `coroutine - ByteArray DC field roundtrip concurrent`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val bp = calc.getBinaryPayload()
                    calc.applyBinaryPayload(bp)
                }
            }
        }.awaitAll()
        results.forEachIndexed { idx, r -> assertEquals(idx + 1, r) }
    }

    @Test
    fun `coroutine - List ByteArray concurrent`() = runBlocking {
        Calculator(2).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.getByteChunks() }
            }.awaitAll()
            results.forEach { assertEquals(3, it.size) }
        }
    }

    // ── List<DataClass> param in coroutines ─────────────────────────────────

    @Test
    fun `coroutine - List DC param concurrent`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..20).map { i ->
                async(Dispatchers.Default) {
                    calc.sumPoints(listOf(Point(i, i), Point(i * 2, 0)))
                }
            }.awaitAll()
            assertEquals(20, results.size)
            results.forEach { assertTrue(it > 0) }
        }
    }

    @Test
    fun `coroutine - List DC param separate instances`() = runBlocking {
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.describeNamedValues(listOf(NamedValue("test_$i", i)))
                }
            }
        }.awaitAll()
        results.forEachIndexed { idx, r -> assertTrue(r.contains("test_${idx + 1}")) }
    }

    // ── Collection properties in coroutines ─────────────────────────────────

    @Test
    fun `coroutine - collection property read concurrent`() = runBlocking {
        Calculator(7).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) { calc.recentScores }
            }.awaitAll()
            results.forEach { assertEquals(listOf(7, 14, 21), it) }
        }
    }

    @Test
    fun `coroutine - collection property write-read concurrent`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { i ->
                async(Dispatchers.Default) {
                    calc.tags = listOf("tag_$i")
                    delay(1)
                    calc.tags
                }
            }.awaitAll()
            assertEquals(10, results.size)
        }
    }

    @Test
    fun `coroutine - map property concurrent`() = runBlocking {
        Calculator(42).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) { calc.info }
            }.awaitAll()
            results.forEach { assertEquals(42, it["current"]) }
        }
    }

    // ── Nullable callbacks in coroutines ────────────────────────────────────

    @Test
    fun `coroutine - nullable callback mixed null-nonnull`() = runBlocking {
        Calculator(10).use { calc ->
            val results = (1..20).map { i ->
                async(Dispatchers.Default) {
                    if (i % 2 == 0) calc.formatOrNull { "v=$it" }
                    else calc.formatOrNull(null)
                }
            }.awaitAll()
            results.forEachIndexed { idx, r ->
                if ((idx + 1) % 2 == 0) assertEquals("v=10", r)
                else assertEquals("null", r)
            }
        }
    }

    // ── Lambda return type in coroutines ────────────────────────────────────

    @Test
    fun `coroutine - lambda return concurrent create`() = runBlocking {
        Calculator(0).use { calc ->
            val adders = (1..20).map { i ->
                async(Dispatchers.Default) { calc.getAdder(i) }
            }.awaitAll()
            adders.forEachIndexed { idx, adder -> assertEquals(100 + idx + 1, adder(100)) }
        }
    }

    @Test
    fun `coroutine - lambda return concurrent invoke`() = runBlocking {
        Calculator(0).use { calc ->
            val adder = calc.getAdder(5)
            val results = (1..20).map { i ->
                async(Dispatchers.Default) { adder(i) }
            }.awaitAll()
            results.forEachIndexed { idx, r -> assertEquals(idx + 1 + 5, r) }
        }
    }

    // ── Flow<Collection> in coroutines ──────────────────────────────────────

    @Test
    fun `coroutine - Flow List concurrent collectors`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..5).map {
                async(Dispatchers.Default) { calc.scoresFlow(3).toList() }
            }.awaitAll()
            results.forEach { assertEquals(3, it.size) }
        }
    }

    @Test
    fun `coroutine - Flow Map concurrent`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..5).map {
                async(Dispatchers.Default) { calc.metadataFlow(2).toList() }
            }.awaitAll()
            results.forEach {
                assertEquals(2, it.size)
                assertEquals(5, it[0]["value"])
            }
        }
    }

    @Test
    fun `coroutine - Flow ByteArray concurrent`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..5).map {
                async(Dispatchers.Default) { calc.byteChunks(2, 4).toList() }
            }.awaitAll()
            results.forEach {
                assertEquals(2, it.size)
                assertEquals(4, it[0].size)
            }
        }
    }

    // ── Nested collections in coroutines ────────────────────────────────────

    @Test
    fun `coroutine - nested List return concurrent`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) { calc.getMatrix() }
            }.awaitAll()
            results.forEach {
                assertEquals(listOf(3, 4), it[0])
                assertEquals(listOf(6, 7), it[1])
            }
        }
    }

    @Test
    fun `coroutine - nested List param concurrent`() = runBlocking {
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.sumMatrix(listOf(listOf(i, i), listOf(i)))
                }
            }
        }.awaitAll()
        results.forEachIndexed { idx, sum -> assertEquals((idx + 1) * 3, sum) }
    }

    // ── Suspend with ByteArray/nested collections in coroutines ─────────────

    @Test
    fun `coroutine - suspend List ByteArray concurrent`() = runBlocking {
        Calculator(2).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.delayedGetByteChunks() }
            }.awaitAll()
            results.forEach {
                assertEquals(2, it.size)
                assertContentEquals(byteArrayOf(1, 2, 3), it[0])
            }
        }
    }

    @Test
    fun `coroutine - suspend nested List concurrent`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.delayedGetMatrix() }
            }.awaitAll()
            results.forEach {
                assertEquals(listOf(5, 6), it[0])
                assertEquals(listOf(10), it[1])
            }
        }
    }

    // ── DC with collection fields in coroutines ─────────────────────────────

    @Test
    fun `coroutine - DC with List field concurrent`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) { calc.getTaggedList() }
            }.awaitAll()
            results.forEach { assertEquals(listOf(5, 10, 15), it.scores) }
        }
    }

    @Test
    fun `coroutine - DC with Map field concurrent`() = runBlocking {
        Calculator(10).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) { calc.getMetadataHolder() }
            }.awaitAll()
            results.forEach { assertEquals(10, it.metadata["current"]) }
        }
    }

    @Test
    fun `coroutine - DC with collection field roundtrip concurrent`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val tl = calc.getTaggedList()
                    calc.applyTaggedList(tl)
                }
            }
        }.awaitAll()
        results.forEachIndexed { idx, sum ->
            val v = idx + 1
            assertEquals(v + v * 2 + v * 3, sum)
        }
    }

    // ── Suspend DC return in coroutines ──────────────────────────────────────

    @Test
    fun `coroutine - suspend DC return concurrent`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.delayedGetPoint() }
            }.awaitAll()
            results.forEach { assertEquals(Point(5, 10), it) }
        }
    }

    @Test
    fun `coroutine - suspend List return concurrent`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.delayedGetScores() }
            }.awaitAll()
            results.forEach { assertEquals(listOf(5, 10, 15), it) }
        }
    }

    @Test
    fun `coroutine - suspend Map return concurrent`() = runBlocking {
        Calculator(42).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.delayedGetMetadata() }
            }.awaitAll()
            results.forEach { assertEquals(42, it["current"]) }
        }
    }

    // ── Full cross-feature stress: all features in one coroutine ─────────────

    @Test
    fun `coroutine - all features interleaved`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..10).map { i ->
                async(Dispatchers.Default) {
                    // Suspend DC
                    val p = calc.delayedGetPoint()
                    // List<DC> param
                    calc.sumPoints(listOf(p, Point(i, i)))
                    // Collection property
                    val scores = calc.recentScores
                    // DC with collection field
                    val tl = calc.getTaggedList()
                    // Nullable callback
                    val fmt = if (i % 2 == 0) calc.formatOrNull { "v=$it" } else calc.formatOrNull(null)
                    // Lambda return
                    val adder = calc.getAdder(i)
                    // Nested collection
                    val matrix = calc.getMatrix()
                    // Flow first
                    val firstScores = calc.scoresFlow(2).first()
                    // ByteArray callback
                    var ba: ByteArray? = null
                    calc.onBytesReady { ba = it }

                    listOf(p.x, scores.size, tl.scores.size, adder(0), matrix.size, firstScores.size, ba!!.size)
                }
            }.awaitAll()

            assertEquals(10, results.size)
            results.forEach { vals ->
                assertEquals(5, vals[0])       // point.x
                assertEquals(3, vals[1])       // scores.size
                assertEquals(3, vals[2])       // taggedList.scores.size
                assertTrue(vals[3] > 0)        // adder(0) > 0
                assertEquals(2, vals[4])       // matrix.size
                assertEquals(3, vals[5])       // firstScores.size
                assertTrue(vals[6] >= 0)       // ba.size
            }
        }
    }
}
