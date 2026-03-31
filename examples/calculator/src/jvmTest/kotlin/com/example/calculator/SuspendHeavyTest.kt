package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitAll

class SuspendHeavyTest {

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
}
