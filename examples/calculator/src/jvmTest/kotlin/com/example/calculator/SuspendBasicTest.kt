package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class SuspendBasicTest {

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

    // ── Edge return types ───────────────────────────────────────────────────

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
}
