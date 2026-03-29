package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitAll

class SuspendFunctionTest {

    // ══════════════════════════════════════════════════════════════════════════
    // SUSPEND FUNCTIONS — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    // ── Basic suspend return types ──────────────────────────────────────────

    @Test fun `suspend - delayedAdd Int return`() = runBlocking {
        Calculator(0).use { calc -> assertEquals(7, calc.delayedAdd(3, 4)) }
    }

    @Test fun `suspend - delayedAdd updates accumulator`() = runBlocking {
        Calculator(0).use { calc ->
            calc.delayedAdd(10, 20)
            assertEquals(30, calc.current)
        }
    }

    @Test fun `suspend - delayedDescribe String return`() = runBlocking {
        Calculator(42).use { calc ->
            assertEquals("Calculator(current=42)", calc.delayedDescribe())
        }
    }

    @Test fun `suspend - instantReturn no delay`() = runBlocking {
        Calculator(99).use { calc -> assertEquals(99, calc.instantReturn()) }
    }

    @Test fun `suspend - delayedIsPositive Boolean return true`() = runBlocking {
        Calculator(5).use { calc -> assertTrue(calc.delayedIsPositive()) }
    }

    @Test fun `suspend - delayedIsPositive Boolean return false`() = runBlocking {
        Calculator(-1).use { calc -> assertFalse(calc.delayedIsPositive()) }
    }

    @Test fun `suspend - delayedGetOp Enum return`() = runBlocking {
        Calculator(0).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 5)
            assertEquals(Operation.MULTIPLY, calc.delayedGetOp())
        }
    }

    @Test fun `suspend - delayedNullable non-null`() = runBlocking {
        Calculator(5).use { calc ->
            assertEquals("positive(5)", calc.delayedNullable())
        }
    }

    @Test fun `suspend - delayedNullable null`() = runBlocking {
        Calculator(0).use { calc ->
            assertNull(calc.delayedNullable())
        }
    }

    @Test fun `suspend - delayedNullable negative`() = runBlocking {
        Calculator(-1).use { calc ->
            assertNull(calc.delayedNullable())
        }
    }

    // ── Error propagation ───────────────────────────────────────────────────

    @Test fun `suspend - failAfterDelay throws`() = runBlocking {
        Calculator(0).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.failAfterDelay() }
        }
    }

    @Test fun `suspend - failAfterDelay message`() = runBlocking {
        Calculator(0).use { calc ->
            val e = assertFailsWith<KotlinNativeException> { calc.failAfterDelay() }
            assertTrue(e.message!!.contains("intentional suspend error"))
        }
    }

    @Test fun `suspend - error then success`() = runBlocking {
        Calculator(0).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.failAfterDelay() }
            assertEquals(10, calc.delayedAdd(4, 6)) // still works
        }
    }

    // ── Cancellation ────────────────────────────────────────────────────────

    @Test fun `suspend - JVM cancel propagates to native`() = runBlocking {
        Calculator(0).use { calc ->
            val start = System.currentTimeMillis()
            val job = launch { calc.longDelay() }
            delay(200)
            job.cancelAndJoin()
            val elapsed = System.currentTimeMillis() - start
            assertTrue(elapsed < 2000, "Should cancel fast, took ${elapsed}ms")
        }
    }

    @Test fun `suspend - cancel throws CancellationException`() = runBlocking {
        Calculator(0).use { calc ->
            val deferred = async { calc.longDelay() }
            delay(200)
            deferred.cancel()
            assertFailsWith<CancellationException> { deferred.await() }
        }
    }

    // ── Concurrency ─────────────────────────────────────────────────────────

    @Test fun `suspend - 50 concurrent delayedAdd`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..50).map { i ->
                async(Dispatchers.Default) { calc.delayedAdd(i, 0) }
            }.awaitAll()
            assertEquals(50, results.size)
        }
    }

    @Test fun `suspend - 100 concurrent on separate instances`() = runBlocking {
        val results = (1..100).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.delayedAdd(0, i) }
            }
        }.awaitAll()
        assertEquals((1..100).toList(), results)
    }

    @Test fun `suspend - 10 concurrent with different return types`() = runBlocking {
        Calculator(5).use { calc ->
            val jobs = listOf(
                async { calc.delayedAdd(1, 2) },
                async { calc.delayedDescribe() },
                async { calc.delayedIsPositive() },
                async { calc.delayedGetOp() },
                async { calc.delayedNullable() },
                async { calc.instantReturn() },
            )
            jobs.forEach { it.await() }
        }
    }

    // ── Load / stress ───────────────────────────────────────────────────────

    @Test fun `suspend - 1K sequential calls`() = runBlocking {
        Calculator(0).use { calc ->
            repeat(1_000) {
                calc.delayedAdd(1, 0)
            }
            // accumulator was overwritten each time to 1
            assertEquals(1, calc.current)
        }
    }

    @Test fun `suspend - 500 instant returns`() = runBlocking {
        Calculator(42).use { calc ->
            repeat(500) {
                assertEquals(42, calc.instantReturn())
            }
        }
    }

    @Test fun `suspend - repeated cancel cycles`() = runBlocking {
        Calculator(0).use { calc ->
            repeat(100) {
                val job = launch { calc.longDelay() }
                delay(5)
                job.cancelAndJoin()
            }
        }
    }

    @Test fun `suspend - mixed success cancel error`() = runBlocking {
        Calculator(0).use { calc ->
            repeat(50) { i ->
                when (i % 3) {
                    0 -> assertEquals(3, calc.delayedAdd(1, 2))
                    1 -> {
                        val j = launch { calc.longDelay() }
                        delay(5)
                        j.cancelAndJoin()
                    }
                    2 -> assertFailsWith<KotlinNativeException> { calc.failAfterDelay() }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SUSPEND EDGE CASES — HEAVY CONCURRENCY, LEAKS, ERRORS
    // ══════════════════════════════════════════════════════════════════════════

    // ── Additional return types ─────────────────────────────────────────────

    @Test fun `suspend edge - Double return`() = runBlocking {
        Calculator(0).use { assertEquals(6.28, it.delayedDouble(3.14), 0.001) }
    }

    @Test fun `suspend edge - Long return`() = runBlocking {
        Calculator(10).use { assertEquals(1_000_010L, it.delayedLong(1_000_000L)) }
    }

    @Test fun `suspend edge - Unit return`() = runBlocking {
        Calculator(0).use { calc ->
            calc.delayedUnit()
            assertEquals(1, calc.current)
        }
    }

    @Test fun `suspend edge - Unit multiple calls`() = runBlocking {
        Calculator(0).use { calc ->
            repeat(10) { calc.delayedUnit() }
            assertEquals(10, calc.current)
        }
    }

    @Test fun `suspend edge - nullable Int non-null`() = runBlocking {
        Calculator(42).use { assertEquals(42, it.delayedGetCurrentOrNull()) }
    }

    @Test fun `suspend edge - nullable Int null`() = runBlocking {
        Calculator(0).use { assertNull(it.delayedGetCurrentOrNull()) }
    }

    @Test fun `suspend edge - nullable transition`() = runBlocking {
        Calculator(0).use { calc ->
            assertNull(calc.delayedGetCurrentOrNull())
            calc.add(5)
            assertEquals(5, calc.delayedGetCurrentOrNull())
            calc.reset()
            assertNull(calc.delayedGetCurrentOrNull())
        }
    }

    // ── Chained/sequential suspend ──────────────────────────────────────────

    @Test fun `suspend edge - chainedDelay basic`() = runBlocking {
        Calculator(10).use { assertEquals(15, it.chainedDelay(5)) }
    }

    @Test fun `suspend edge - chainedDelay zero steps`() = runBlocking {
        Calculator(7).use { assertEquals(7, it.chainedDelay(0)) }
    }

    @Test fun `suspend edge - chainedDelay 50 steps`() = runBlocking {
        Calculator(0).use { assertEquals(50, it.chainedDelay(50)) }
    }

    @Test fun `suspend edge - sequential different types`() = runBlocking {
        Calculator(10).use { calc ->
            val a = calc.delayedAdd(5, 5)
            assertEquals(10, a)
            val s = calc.delayedDescribe()
            assertTrue(s.contains("10"))
            val b = calc.delayedIsPositive()
            assertTrue(b)
            val e = calc.delayedGetOp()
            assertEquals(Operation.ADD, e)
            val d = calc.delayedDouble(1.5)
            assertEquals(3.0, d, 0.001)
            val l = calc.delayedLong(100L)
            assertEquals(110L, l)
        }
    }

    // ── Conditional error ────────────────────────────────────────────────────

    @Test fun `suspend edge - multiError success`() = runBlocking {
        Calculator(42).use { assertEquals(42, it.multiError(false)) }
    }

    @Test fun `suspend edge - multiError fail`() = runBlocking {
        Calculator(0).use { assertFailsWith<KotlinNativeException> { it.multiError(true) } }
    }

    @Test fun `suspend edge - alternating success and error`() = runBlocking {
        Calculator(5).use { calc ->
            repeat(20) { i ->
                if (i % 2 == 0) {
                    assertEquals(5, calc.multiError(false))
                } else {
                    assertFailsWith<KotlinNativeException> { calc.multiError(true) }
                }
            }
        }
    }

    @Test fun `suspend edge - 5 errors then success`() = runBlocking {
        Calculator(7).use { calc ->
            repeat(5) { assertFailsWith<KotlinNativeException> { calc.multiError(true) } }
            assertEquals(7, calc.multiError(false))
        }
    }

    // ── Heavy concurrency ───────────────────────────────────────────────────

    @Test fun `suspend heavy - 200 concurrent delayedAdd`() = runBlocking {
        val results = (1..200).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { it.delayedAdd(0, i) }
            }
        }.awaitAll()
        assertEquals(200, results.size)
        assertEquals((1..200).toList(), results)
    }

    @Test fun `suspend heavy - 50 concurrent on same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..50).map { async(Dispatchers.Default) { calc.instantReturn() } }.awaitAll()
            assertEquals(50, results.size)
        }
    }

    @Test fun `suspend heavy - 10 threads x 100 calls`() = runBlocking {
        val jobs = (1..10).map { t ->
            async(Dispatchers.Default) {
                Calculator(t).use { calc ->
                    repeat(100) { calc.instantReturn() }
                    calc.current
                }
            }
        }
        val results = jobs.awaitAll()
        assertEquals((1..10).toList(), results)
    }

    @Test fun `suspend heavy - concurrent different return types`() = runBlocking {
        Calculator(5).use { calc ->
            val jobs = (1..20).map { i ->
                async(Dispatchers.Default) {
                    when (i % 5) {
                        0 -> calc.delayedAdd(1, 1).toString()
                        1 -> calc.delayedDescribe()
                        2 -> calc.delayedIsPositive().toString()
                        3 -> calc.delayedDouble(1.0).toString()
                        else -> calc.delayedLong(1L).toString()
                    }
                }
            }
            val results = jobs.awaitAll()
            assertEquals(20, results.size)
        }
    }

    // ── Cancellation edge cases ─────────────────────────────────────────────

    @Test fun `suspend cancel - immediate cancel`() = runBlocking {
        Calculator(0).use { calc ->
            val start = System.currentTimeMillis()
            val job = launch { calc.longDelay() } // 5s delay
            job.cancelAndJoin()
            val elapsed = System.currentTimeMillis() - start
            assertTrue(elapsed < 3000, "Immediate cancel should not wait for longDelay, took ${elapsed}ms")
        }
    }

    @Test fun `suspend cancel - 200 rapid cancel cycles`() = runBlocking {
        Calculator(0).use { calc ->
            repeat(200) {
                val job = launch { calc.longDelay() }
                delay(1)
                job.cancelAndJoin()
            }
        }
    }

    @Test fun `suspend cancel - concurrent cancels`() = runBlocking {
        Calculator(0).use { calc ->
            val jobs = (1..20).map { launch(Dispatchers.Default) { calc.longDelay() } }
            delay(50)
            jobs.forEach { it.cancelAndJoin() }
        }
    }

    @Test fun `suspend cancel - cancel then success`() = runBlocking {
        Calculator(0).use { calc ->
            val job = launch { calc.longDelay() }
            delay(10)
            job.cancelAndJoin()
            // Now do a successful call
            assertEquals(3, calc.delayedAdd(1, 2))
        }
    }

    @Test fun `suspend cancel - interleaved cancel and success`() = runBlocking {
        Calculator(0).use { calc ->
            repeat(30) { i ->
                if (i % 2 == 0) {
                    val job = launch { calc.longDelay() }
                    delay(2)
                    job.cancelAndJoin()
                } else {
                    assertEquals(2, calc.delayedAdd(1, 1))
                }
            }
        }
    }

    // ── Memory / leak detection ─────────────────────────────────────────────

    @Test fun `suspend mem - 2K create-call-close no leak`() = runBlocking {
        repeat(2_000) { i ->
            Calculator(i).use { calc ->
                assertEquals(i, calc.instantReturn())
            }
        }
        System.gc()
    }

    @Test fun `suspend mem - 500 cancel cycles no leak`() = runBlocking {
        repeat(500) {
            Calculator(0).use { calc ->
                val job = launch { calc.longDelay() }
                delay(1)
                job.cancelAndJoin()
            }
        }
        System.gc()
    }

    @Test fun `suspend mem - 1K error cycles no leak`() = runBlocking {
        repeat(1_000) {
            Calculator(0).use { calc ->
                assertFailsWith<KotlinNativeException> { calc.failAfterDelay() }
            }
        }
        System.gc()
    }

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

    @Test fun `suspend cross - suspend with collections`() = runBlocking {
        Calculator(5).use { calc ->
            calc.delayedAdd(0, 5)
            val scores = calc.getScores() // sync collection return
            assertEquals(listOf(5, 10, 15), scores)
        }
    }
}
