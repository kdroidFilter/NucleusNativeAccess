package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class ByteArrayEverywhereTest {

    // ══════════════════════════════════════════════════════════════════════════
    // BYTEARRAY CALLBACK PARAMS — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ba callback - onBytesReady receives bytes`() {
        Calculator(5).use { calc ->
            var received: ByteArray? = null
            calc.onBytesReady { received = it }
            assertEquals(5, received!!.size)
            assertContentEquals(byteArrayOf(0, 1, 2, 3, 4), received!!)
        }
    }

    @Test
    fun `ba callback - onBytesReady zero size`() {
        Calculator(0).use { calc ->
            var received: ByteArray? = null
            calc.onBytesReady { received = it }
            assertEquals(0, received!!.size)
        }
    }

    @Test
    fun `ba callback - transformBytes identity`() {
        Calculator(0).use { calc ->
            val input = byteArrayOf(10, 20, 30)
            val result = calc.transformBytes(input) { it }
            assertContentEquals(byteArrayOf(10, 20, 30), result)
        }
    }

    @Test
    fun `ba callback - transformBytes reverse`() {
        Calculator(0).use { calc ->
            val input = byteArrayOf(1, 2, 3, 4, 5)
            val result = calc.transformBytes(input) { it.reversedArray() }
            assertContentEquals(byteArrayOf(5, 4, 3, 2, 1), result)
        }
    }

    @Test
    fun `ba callback - transformBytes double size`() {
        Calculator(0).use { calc ->
            val input = byteArrayOf(1, 2)
            val result = calc.transformBytes(input) { it + it }
            assertContentEquals(byteArrayOf(1, 2, 1, 2), result)
        }
    }

    @Test
    fun `ba callback - transformBytes empty`() {
        Calculator(0).use { calc ->
            val result = calc.transformBytes(byteArrayOf()) { it }
            assertEquals(0, result.size)
        }
    }

    @Test
    fun `ba callback - transformBytes large`() {
        Calculator(0).use { calc ->
            val input = ByteArray(10000) { (it % 256).toByte() }
            val result = calc.transformBytes(input) { it }
            assertEquals(10000, result.size)
            assertContentEquals(input, result)
        }
    }

    @Test
    fun `ba callback - concurrent onBytesReady`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) {
                    var received: ByteArray? = null
                    calc.onBytesReady { received = it }
                    received!!
                }
            }.awaitAll()
            assertEquals(20, results.size)
            results.forEach { assertEquals(3, it.size) }
        }
    }

    @Test
    fun `ba callback - 200 sequential onBytesReady`() {
        Calculator(2).use { calc ->
            repeat(200) {
                var received: ByteArray? = null
                calc.onBytesReady { received = it }
                assertContentEquals(byteArrayOf(0, 1), received!!)
            }
        }
        System.gc()
    }

    @Test
    fun `ba callback - 100 sequential transformBytes`() {
        Calculator(0).use { calc ->
            repeat(100) { i ->
                val input = byteArrayOf(i.toByte())
                val result = calc.transformBytes(input) { it }
                assertContentEquals(input, result)
            }
        }
        System.gc()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BYTEARRAY IN COLLECTIONS — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ba collection - getByteChunks basic`() {
        Calculator(3).use { calc ->
            val chunks = calc.getByteChunks()
            assertEquals(3, chunks.size)
            assertContentEquals(byteArrayOf(1, 2, 3), chunks[0])
            assertContentEquals(byteArrayOf(4, 5, 6, 7), chunks[1])
            assertEquals(3, chunks[2].size) // accumulator-sized
            assertContentEquals(byteArrayOf(0, 1, 2), chunks[2])
        }
    }

    @Test
    fun `ba collection - getByteChunks zero accumulator`() {
        Calculator(0).use { calc ->
            val chunks = calc.getByteChunks()
            assertEquals(3, chunks.size)
            assertEquals(0, chunks[2].size) // empty ByteArray for accumulator=0
        }
    }

    @Test
    fun `ba collection - countByteChunks basic`() {
        Calculator(0).use { calc ->
            val chunks = listOf(byteArrayOf(1, 2), byteArrayOf(3, 4, 5))
            assertEquals(5, calc.countByteChunks(chunks))
        }
    }

    @Test
    fun `ba collection - countByteChunks empty list`() {
        Calculator(0).use { calc ->
            assertEquals(0, calc.countByteChunks(emptyList()))
        }
    }

    @Test
    fun `ba collection - countByteChunks empty arrays`() {
        Calculator(0).use { calc ->
            assertEquals(0, calc.countByteChunks(listOf(byteArrayOf(), byteArrayOf())))
        }
    }

    @Test
    fun `ba collection - countByteChunks large`() {
        Calculator(0).use { calc ->
            val chunks = List(50) { ByteArray(100) { it.toByte() } }
            assertEquals(5000, calc.countByteChunks(chunks))
        }
    }

    @Test
    fun `ba collection - roundtrip getByteChunks then countByteChunks`() {
        Calculator(5).use { calc ->
            val chunks = calc.getByteChunks()
            // chunks: [3 bytes, 4 bytes, 5 bytes] = 12 total
            assertEquals(12, calc.countByteChunks(chunks))
        }
    }

    @Test
    fun `ba collection - concurrent getByteChunks`() = runBlocking {
        Calculator(2).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.getByteChunks() }
            }.awaitAll()
            assertEquals(10, results.size)
            results.forEach {
                assertEquals(3, it.size)
                assertContentEquals(byteArrayOf(1, 2, 3), it[0])
            }
        }
    }

    @Test
    fun `ba collection - concurrent countByteChunks separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.countByteChunks(listOf(ByteArray(i)))
                }
            }
        }.awaitAll()
        results.forEachIndexed { idx, count -> assertEquals(idx + 1, count) }
    }

    @Test
    fun `ba collection - 100 sequential getByteChunks`() {
        Calculator(1).use { calc ->
            repeat(100) {
                val chunks = calc.getByteChunks()
                assertEquals(3, chunks.size)
            }
        }
        System.gc()
    }

    @Test
    fun `ba collection - 100 sequential countByteChunks`() {
        Calculator(0).use { calc ->
            repeat(100) {
                assertEquals(5, calc.countByteChunks(listOf(byteArrayOf(1, 2), byteArrayOf(3, 4, 5))))
            }
        }
        System.gc()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BYTEARRAY DC FIELD — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ba dc field - getBinaryPayload basic`() {
        Calculator(5).use { calc ->
            val bp = calc.getBinaryPayload()
            assertEquals("payload_5", bp.name)
            assertEquals(5, bp.data.size)
            assertContentEquals(byteArrayOf(0, 1, 2, 3, 4), bp.data)
        }
    }

    @Test
    fun `ba dc field - getBinaryPayload zero`() {
        Calculator(0).use { calc ->
            val bp = calc.getBinaryPayload()
            assertEquals("payload_0", bp.name)
            assertEquals(0, bp.data.size)
        }
    }

    @Test
    fun `ba dc field - applyBinaryPayload basic`() {
        Calculator(0).use { calc ->
            val result = calc.applyBinaryPayload(BinaryPayload("test", byteArrayOf(1, 2, 3)))
            assertEquals(3, result)
            assertEquals("test", calc.label)
        }
    }

    @Test
    fun `ba dc field - applyBinaryPayload empty`() {
        Calculator(0).use { calc ->
            assertEquals(0, calc.applyBinaryPayload(BinaryPayload("empty", byteArrayOf())))
        }
    }

    @Test
    fun `ba dc field - applyBinaryPayload large`() {
        Calculator(0).use { calc ->
            val data = ByteArray(5000) { (it % 256).toByte() }
            assertEquals(5000, calc.applyBinaryPayload(BinaryPayload("large", data)))
        }
    }

    @Test
    fun `ba dc field - roundtrip getBinaryPayload then applyBinaryPayload`() {
        Calculator(10).use { calc ->
            val bp = calc.getBinaryPayload()
            assertEquals("payload_10", bp.name)
            assertEquals(10, bp.data.size)
            val result = calc.applyBinaryPayload(bp)
            assertEquals(10, result)
            assertEquals("payload_10", calc.label)
        }
    }

    @Test
    fun `ba dc field - concurrent getBinaryPayload`() = runBlocking {
        Calculator(3).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) { calc.getBinaryPayload() }
            }.awaitAll()
            assertEquals(20, results.size)
            results.forEach {
                assertEquals("payload_3", it.name)
                assertEquals(3, it.data.size)
            }
        }
    }

    @Test
    fun `ba dc field - concurrent separate instances`() = runBlocking {
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.getBinaryPayload() }
            }
        }.awaitAll()
        results.forEachIndexed { idx, bp ->
            assertEquals(idx + 1, bp.data.size)
        }
    }

    @Test
    fun `ba dc field - 200 sequential getBinaryPayload`() {
        Calculator(2).use { calc ->
            repeat(200) {
                val bp = calc.getBinaryPayload()
                assertEquals(2, bp.data.size)
            }
        }
        System.gc()
    }

    @Test
    fun `ba dc field - 100 sequential roundtrips`() {
        Calculator(0).use { calc ->
            repeat(100) { i ->
                calc.add(1)
                val bp = calc.getBinaryPayload()
                assertEquals(i + 1, bp.data.size)
                calc.applyBinaryPayload(bp)
            }
        }
        System.gc()
    }

    @Test
    fun `ba dc field - 50 instances roundtrip`() {
        repeat(50) { i ->
            Calculator(i).use { calc ->
                val bp = calc.getBinaryPayload()
                assertEquals(i, bp.data.size)
                calc.applyBinaryPayload(bp)
            }
        }
        System.gc()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CROSS-FEATURE COMBINATIONS — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ba cross - callback then collection then DC`() {
        Calculator(3).use { calc ->
            // 1. Callback: receive bytes
            var cbBytes: ByteArray? = null
            calc.onBytesReady { cbBytes = it }
            assertContentEquals(byteArrayOf(0, 1, 2), cbBytes!!)

            // 2. Collection: get chunks
            val chunks = calc.getByteChunks()
            assertEquals(3, chunks.size)

            // 3. DC: get payload
            val bp = calc.getBinaryPayload()
            assertEquals(3, bp.data.size)

            // 4. Collection param: count chunks (3 + 4 + 3 = 10)
            assertEquals(10, calc.countByteChunks(chunks))

            // 5. DC param: apply payload
            calc.applyBinaryPayload(bp)
            assertEquals(3, calc.current)
        }
    }

    @Test
    fun `ba cross - concurrent mixed operations`() = runBlocking {
        Calculator(5).use { calc ->
            val cbResults = (1..5).map {
                async(Dispatchers.Default) {
                    var r: ByteArray? = null
                    calc.onBytesReady { r = it }; r!!
                }
            }
            val collResults = (1..5).map {
                async(Dispatchers.Default) { calc.getByteChunks() }
            }
            val dcResults = (1..5).map {
                async(Dispatchers.Default) { calc.getBinaryPayload() }
            }
            cbResults.awaitAll().forEach { assertEquals(5, it.size) }
            collResults.awaitAll().forEach { assertEquals(3, it.size) }
            dcResults.awaitAll().forEach { assertEquals(5, it.data.size) }
        }
    }
}
