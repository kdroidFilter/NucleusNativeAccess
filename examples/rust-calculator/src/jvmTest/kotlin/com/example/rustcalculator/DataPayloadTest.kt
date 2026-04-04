package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataPayloadTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Scores variant (LIST field)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `factory Scores creates variant with LIST field`() {
        DataPayload.scores(listOf(1, 2, 3)).use { result ->
            assertTrue(result is DataPayload.Scores)
            assertEquals(listOf(1, 2, 3), result.value)
            assertEquals(DataPayload.Tag.Scores, result.tag)
        }
    }

    @Test fun `factory Scores with empty list`() {
        DataPayload.scores(emptyList()).use { result ->
            assertTrue(result is DataPayload.Scores)
            assertEquals(emptyList(), result.value)
        }
    }

    @Test fun `factory Scores with single element`() {
        DataPayload.scores(listOf(42)).use { result ->
            assertEquals(listOf(42), (result as DataPayload.Scores).value)
        }
    }

    @Test fun `factory Scores with negative values`() {
        DataPayload.scores(listOf(-1, -2, -3)).use { result ->
            assertEquals(listOf(-1, -2, -3), (result as DataPayload.Scores).value)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UniqueIds variant (SET field)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `factory UniqueIds creates variant with SET field`() {
        DataPayload.uniqueIds(setOf(10, 20, 30)).use { result ->
            assertTrue(result is DataPayload.UniqueIds)
            assertEquals(setOf(10, 20, 30), result.value)
            assertEquals(DataPayload.Tag.UniqueIds, result.tag)
        }
    }

    @Test fun `factory UniqueIds with empty set`() {
        DataPayload.uniqueIds(emptySet()).use { result ->
            assertTrue(result is DataPayload.UniqueIds)
            assertEquals(emptySet(), result.value)
        }
    }

    @Test fun `factory UniqueIds with single element`() {
        DataPayload.uniqueIds(setOf(99)).use { result ->
            assertEquals(setOf(99), (result as DataPayload.UniqueIds).value)
        }
    }

    @Test fun `factory UniqueIds preserves uniqueness`() {
        // Pass duplicates — SET on Rust side deduplicates
        DataPayload.uniqueIds(setOf(1, 2, 3)).use { result ->
            assertEquals(3, (result as DataPayload.UniqueIds).value.size)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Mapping variant (MAP field)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `factory Mapping creates variant with MAP field`() {
        DataPayload.mapping(mapOf(1 to 10, 2 to 20)).use { result ->
            assertTrue(result is DataPayload.Mapping)
            assertEquals(mapOf(1 to 10, 2 to 20), result.value)
            assertEquals(DataPayload.Tag.Mapping, result.tag)
        }
    }

    @Test fun `factory Mapping with empty map`() {
        DataPayload.mapping(emptyMap()).use { result ->
            assertTrue(result is DataPayload.Mapping)
            assertEquals(emptyMap(), result.value)
        }
    }

    @Test fun `factory Mapping with single entry`() {
        DataPayload.mapping(mapOf(42 to 99)).use { result ->
            assertEquals(mapOf(42 to 99), (result as DataPayload.Mapping).value)
        }
    }

    @Test fun `factory Mapping with negative keys and values`() {
        DataPayload.mapping(mapOf(-1 to -10, -2 to -20)).use { result ->
            assertEquals(mapOf(-1 to -10, -2 to -20), (result as DataPayload.Mapping).value)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Empty variant
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `factory Empty creates variant`() {
        DataPayload.empty().use { result ->
            assertTrue(result is DataPayload.Empty)
            assertEquals(DataPayload.Tag.Empty, result.tag)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Return from Rust functions
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `create_scores_payload returns Scores`() {
        Rustcalc.create_scores_payload(listOf(1, 2, 3)).use { result ->
            assertTrue(result is DataPayload.Scores)
            assertEquals(listOf(1, 2, 3), result.value)
        }
    }

    @Test fun `create_unique_ids_payload returns UniqueIds`() {
        Rustcalc.create_unique_ids_payload(listOf(10, 20, 10)).use { result ->
            assertTrue(result is DataPayload.UniqueIds)
            // Rust HashSet deduplicates
            assertTrue(result.value.contains(10))
            assertTrue(result.value.contains(20))
        }
    }

    @Test fun `create_mapping_payload returns Mapping`() {
        Rustcalc.create_mapping_payload(listOf(1, 2), listOf(10, 20)).use { result ->
            assertTrue(result is DataPayload.Mapping)
            assertEquals(10, result.value[1])
            assertEquals(20, result.value[2])
        }
    }

    @Test fun `create_empty_payload returns Empty`() {
        Rustcalc.create_empty_payload().use { result ->
            assertTrue(result is DataPayload.Empty)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge - Scores with MAX_VALUE`() {
        DataPayload.scores(listOf(Int.MAX_VALUE)).use { result ->
            assertEquals(listOf(Int.MAX_VALUE), (result as DataPayload.Scores).value)
        }
    }

    @Test fun `edge - Scores with MIN_VALUE`() {
        DataPayload.scores(listOf(Int.MIN_VALUE)).use { result ->
            assertEquals(listOf(Int.MIN_VALUE), (result as DataPayload.Scores).value)
        }
    }

    @Test fun `edge - UniqueIds with MAX_VALUE`() {
        DataPayload.uniqueIds(setOf(Int.MAX_VALUE, Int.MIN_VALUE)).use { result ->
            val s = (result as DataPayload.UniqueIds).value
            assertTrue(s.contains(Int.MAX_VALUE))
            assertTrue(s.contains(Int.MIN_VALUE))
        }
    }

    @Test fun `edge - Mapping with zero key`() {
        DataPayload.mapping(mapOf(0 to 0)).use { result ->
            assertEquals(mapOf(0 to 0), (result as DataPayload.Mapping).value)
        }
    }

    @Test fun `edge - all tags cycle`() {
        DataPayload.scores(listOf(1)).use { assertEquals(DataPayload.Tag.Scores, it.tag) }
        DataPayload.uniqueIds(setOf(1)).use { assertEquals(DataPayload.Tag.UniqueIds, it.tag) }
        DataPayload.mapping(mapOf(1 to 1)).use { assertEquals(DataPayload.Tag.Mapping, it.tag) }
        DataPayload.empty().use { assertEquals(DataPayload.Tag.Empty, it.tag) }
    }

    @Test fun `edge - lifecycle create and close many`() {
        repeat(50) { i ->
            DataPayload.scores(listOf(i)).use { result ->
                assertEquals(listOf(i), (result as DataPayload.Scores).value)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K Scores factory calls`() {
        repeat(100_000) {
            DataPayload.scores(listOf(1, 2, 3)).use { result ->
                assertEquals(3, (result as DataPayload.Scores).value.size)
            }
        }
    }

    @Test fun `load - 100K UniqueIds factory calls`() {
        repeat(100_000) {
            DataPayload.uniqueIds(setOf(1, 2, 3)).use { result ->
                assertEquals(3, (result as DataPayload.UniqueIds).value.size)
            }
        }
    }

    @Test fun `load - 100K Mapping factory calls`() {
        repeat(100_000) {
            DataPayload.mapping(mapOf(1 to 10)).use { result ->
                assertEquals(1, (result as DataPayload.Mapping).value.size)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrency tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 10K Scores factory`() {
        val threads = (1..10).map { tid ->
            Thread {
                repeat(10_000) {
                    DataPayload.scores(listOf(tid, tid * 2)).use { result ->
                        assertEquals(listOf(tid, tid * 2), (result as DataPayload.Scores).value)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K UniqueIds factory`() {
        val threads = (1..10).map { tid ->
            Thread {
                repeat(10_000) {
                    DataPayload.uniqueIds(setOf(tid)).use { result ->
                        assertTrue((result as DataPayload.UniqueIds).value.contains(tid))
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K Mapping factory`() {
        val threads = (1..10).map { tid ->
            Thread {
                repeat(10_000) {
                    DataPayload.mapping(mapOf(tid to tid * 10)).use { result ->
                        assertEquals(tid * 10, (result as DataPayload.Mapping).value[tid])
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
