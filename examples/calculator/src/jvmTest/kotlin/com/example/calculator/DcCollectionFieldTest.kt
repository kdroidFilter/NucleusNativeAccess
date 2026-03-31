package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class DcCollectionFieldTest {

    // ══════════════════════════════════════════════════════════════════════════
    // DATACLASS WITH COLLECTION FIELDS — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    // ── DC return with List<Int> field ──────────────────────────────────────

    @Test
    fun `dc coll field - getTaggedList basic`() {
        Calculator(5).use { calc ->
            val tl = calc.getTaggedList()
            assertEquals("default", tl.label)
            assertEquals(listOf(5, 10, 15), tl.scores)
        }
    }

    @Test
    fun `dc coll field - getTaggedList custom label`() {
        Calculator(3).use { calc ->
            calc.label = "test"
            val tl = calc.getTaggedList()
            assertEquals("test", tl.label)
            assertEquals(listOf(3, 6, 9), tl.scores)
        }
    }

    @Test
    fun `dc coll field - getTaggedList zero`() {
        Calculator(0).use { calc ->
            val tl = calc.getTaggedList()
            assertEquals(listOf(0, 0, 0), tl.scores)
        }
    }

    // ── DC param with List<Int> field ──────────────────────────────────────

    @Test
    fun `dc coll field - applyTaggedList basic`() {
        Calculator(0).use { calc ->
            val result = calc.applyTaggedList(TaggedList("hello", listOf(1, 2, 3)))
            assertEquals(6, result)
            assertEquals("hello", calc.label)
        }
    }

    @Test
    fun `dc coll field - applyTaggedList empty scores`() {
        Calculator(0).use { calc ->
            val result = calc.applyTaggedList(TaggedList("empty", emptyList()))
            assertEquals(0, result)
        }
    }

    // ── DC return with Map<String, Int> field ──────────────────────────────

    @Test
    fun `dc coll field - getMetadataHolder basic`() {
        Calculator(42).use { calc ->
            val mh = calc.getMetadataHolder()
            assertEquals("calc", mh.name)
            assertEquals(42, mh.metadata["current"])
            assertEquals(1, mh.metadata["scale"])
        }
    }

    @Test
    fun `dc coll field - getMetadataHolder custom label`() {
        Calculator(10).use { calc ->
            calc.label = "my-calc"
            val mh = calc.getMetadataHolder()
            assertEquals("my-calc", mh.name)
            assertEquals(10, mh.metadata["current"])
        }
    }

    // ── DC param with Map<String, Int> field ──────────────────────────────

    @Test
    fun `dc coll field - applyMetadataHolder basic`() {
        Calculator(0).use { calc ->
            val result = calc.applyMetadataHolder(MetadataHolder("new-name", mapOf("a" to 10, "b" to 20)))
            assertEquals("new-name:30", result)
        }
    }

    // ── DC with multiple collection fields ─────────────────────────────────

    @Test
    fun `dc coll field - getMultiCollDC positive`() {
        Calculator(5).use { calc ->
            val mc = calc.getMultiCollDC()
            assertEquals(listOf("default", "item_5"), mc.tags)
            assertEquals(listOf(true, false), mc.flags)  // 5 > 0, 5 % 2 != 0
            assertEquals(listOf(5, 6, 7), mc.counts)
        }
    }

    @Test
    fun `dc coll field - getMultiCollDC zero`() {
        Calculator(0).use { calc ->
            val mc = calc.getMultiCollDC()
            assertEquals(listOf("default", "item_0"), mc.tags)
            assertEquals(listOf(false, true), mc.flags)  // 0 not > 0, 0 % 2 == 0
            assertEquals(listOf(0, 1, 2), mc.counts)
        }
    }

    @Test
    fun `dc coll field - getMultiCollDC custom label`() {
        Calculator(4).use { calc ->
            calc.label = "custom"
            val mc = calc.getMultiCollDC()
            assertEquals(listOf("custom", "item_4"), mc.tags)
            assertEquals(listOf(true, true), mc.flags)  // 4 > 0, 4 % 2 == 0
        }
    }

    // ── Roundtrip: get DC → pass it back ───────────────────────────────────

    @Test
    fun `dc coll field - roundtrip TaggedList`() {
        Calculator(5).use { calc ->
            val tl = calc.getTaggedList()
            assertEquals(listOf(5, 10, 15), tl.scores)
            val sum = calc.applyTaggedList(tl)
            assertEquals(30, sum) // 5 + 10 + 15
        }
    }

    @Test
    fun `dc coll field - roundtrip MetadataHolder`() {
        Calculator(10).use { calc ->
            val mh = calc.getMetadataHolder()
            val result = calc.applyMetadataHolder(mh)
            // metadata = {current=10, scale=1}, sum = 11
            assertEquals("calc:11", result)
        }
    }

    // ── Concurrent ─────────────────────────────────────────────────────────

    @Test
    fun `dc coll field - concurrent getTaggedList`() = runBlocking {
        Calculator(5).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) { calc.getTaggedList() }
            }.awaitAll()
            assertEquals(20, results.size)
            results.forEach { assertEquals(listOf(5, 10, 15), it.scores) }
        }
    }

    @Test
    fun `dc coll field - concurrent separate instances`() = runBlocking {
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.getTaggedList() }
            }
        }.awaitAll()
        results.forEachIndexed { idx, tl ->
            val v = idx + 1
            assertEquals(listOf(v, v * 2, v * 3), tl.scores)
        }
    }

    // ── Sequential stress / leak detection ─────────────────────────────────

    @Test
    fun `dc coll field - 200 sequential getTaggedList no leak`() {
        Calculator(1).use { calc ->
            repeat(200) {
                val tl = calc.getTaggedList()
                assertEquals(listOf(1, 2, 3), tl.scores)
            }
        }
        System.gc()
    }

    @Test
    fun `dc coll field - 100 sequential roundtrips`() {
        Calculator(0).use { calc ->
            repeat(100) { i ->
                calc.add(1)
                val tl = calc.getTaggedList()
                assertEquals(i + 1, tl.scores[0])
            }
        }
        System.gc()
    }

    @Test
    fun `dc coll field - 100 instances getMetadataHolder`() {
        repeat(100) { i ->
            Calculator(i).use { calc ->
                val mh = calc.getMetadataHolder()
                assertEquals(i, mh.metadata["current"])
            }
        }
        System.gc()
    }
}
