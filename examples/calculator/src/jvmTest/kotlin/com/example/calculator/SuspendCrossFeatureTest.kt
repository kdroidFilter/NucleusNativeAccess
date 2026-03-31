package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class SuspendCrossFeatureTest {

    // ── Cross-feature: suspend + other features ─────────────────────────────

    @Test fun `suspend cross - suspend then sync method`() = runBlocking {
        Calculator(0).use { calc ->
            calc.delayedAdd(10, 20)
            assertEquals(30, calc.current) // sync property read
            assertEquals("Calculator(current=30)", calc.describe()) // sync method
        }
    }

    @Test fun `suspend cross - sync then suspend then callback`() = runBlocking {
        Calculator(0).use { calc ->
            calc.add(5) // sync
            val r = calc.delayedAdd(0, calc.current) // suspend
            assertEquals(5, r)
            var cbValue = 0
            calc.onValueChanged { cbValue = it } // callback
            assertEquals(5, cbValue)
        }
    }

    @Test fun `suspend cross - suspend on multiple instances`() = runBlocking {
        val calcs = (1..5).map { Calculator(it * 10) }
        val results = calcs.map { calc ->
            async(Dispatchers.Default) { calc.delayedAdd(0, calc.current) }
        }.awaitAll()
        assertEquals(listOf(10, 20, 30, 40, 50), results)
        calcs.forEach { it.close() }
    }

    // ── Suspend ByteArray ────────────────────────────────────────────────────

    @Test fun `suspend - delayedByteArray basic`() = runBlocking {
        Calculator(42).use { calc ->
            val result = calc.delayedByteArray()
            assertEquals("acc=42", result.decodeToString())
        }
    }

    @Test fun `suspend - delayedByteArray after mutation`() = runBlocking {
        Calculator(0).use { calc ->
            calc.delayedAdd(10, 20)
            val result = calc.delayedByteArray()
            assertEquals("acc=30", result.decodeToString())
        }
    }

    @Test fun `suspend - delayedByteArrayNullable non-null`() = runBlocking {
        Calculator(5).use { calc ->
            val result = calc.delayedByteArrayNullable()
            assertEquals("pos=5", result?.decodeToString())
        }
    }

    @Test fun `suspend - delayedByteArrayNullable null`() = runBlocking {
        Calculator(0).use { calc ->
            assertNull(calc.delayedByteArrayNullable())
        }
    }

    @Test fun `suspend - delayedLargeByteArray small`() = runBlocking {
        Calculator(0).use { calc ->
            val result = calc.delayedLargeByteArray(100)
            assertEquals(100, result.size)
            assertEquals(0.toByte(), result[0])
            assertEquals(99.toByte(), result[99])
        }
    }

    @Test fun `suspend - delayedLargeByteArray exceeds initial buffer`() = runBlocking {
        Calculator(0).use { calc ->
            val size = 16_000 // exceeds 8192 STRING_BUF_SIZE
            val result = calc.delayedLargeByteArray(size)
            assertEquals(size, result.size)
            // Verify contents are correct across the whole array
            for (i in result.indices) {
                assertEquals((i % 256).toByte(), result[i], "Mismatch at index $i")
            }
        }
    }

    @Test fun `suspend - concurrent ByteArray calls`() = runBlocking {
        Calculator(1).use { calc ->
            val results = (1..20).map { i ->
                async(Dispatchers.Default) {
                    calc.delayedLargeByteArray(i * 100)
                }
            }.awaitAll()
            results.forEachIndexed { idx, bytes ->
                assertEquals((idx + 1) * 100, bytes.size)
            }
        }
    }

    @Test fun `suspend cross - suspend with collections`() = runBlocking {
        Calculator(5).use { calc ->
            calc.delayedAdd(0, 5)
            val scores = calc.getScores() // sync collection return
            assertEquals(listOf(5, 10, 15), scores)
        }
    }
}
