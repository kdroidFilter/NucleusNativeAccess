package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class SuspendDataClassTest {

    // ══════════════════════════════════════════════════════════════════════════
    // SUSPEND DATACLASS RETURNS — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    // ── Basic DataClass returns ────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetPoint basic`() = runBlocking {
        Calculator(5).use { calc ->
            val p = calc.delayedGetPoint()
            assertEquals(5, p.x)
            assertEquals(10, p.y)
        }
    }

    @Test
    fun `suspend - delayedGetPoint zero accumulator`() = runBlocking {
        Calculator(0).use { calc ->
            val p = calc.delayedGetPoint()
            assertEquals(0, p.x)
            assertEquals(0, p.y)
        }
    }

    @Test
    fun `suspend - delayedGetPoint negative accumulator`() = runBlocking {
        Calculator(-3).use { calc ->
            val p = calc.delayedGetPoint()
            assertEquals(-3, p.x)
            assertEquals(-6, p.y)
        }
    }

    @Test
    fun `suspend - delayedGetPoint after mutation`() = runBlocking {
        Calculator(0).use { calc ->
            calc.add(7)
            val p = calc.delayedGetPoint()
            assertEquals(7, p.x)
            assertEquals(14, p.y)
        }
    }

    // ── Nullable DataClass ─────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetPointOrNull non-null`() = runBlocking {
        Calculator(3).use { calc ->
            val p = calc.delayedGetPointOrNull()
            assertNotNull(p)
            assertEquals(3, p.x)
            assertEquals(6, p.y)
        }
    }

    @Test
    fun `suspend - delayedGetPointOrNull null`() = runBlocking {
        Calculator(0).use { calc ->
            val p = calc.delayedGetPointOrNull()
            assertNull(p)
        }
    }

    @Test
    fun `suspend - delayedGetPointOrNull toggle null and non-null`() = runBlocking {
        Calculator(0).use { calc ->
            assertNull(calc.delayedGetPointOrNull())
            calc.add(1)
            assertNotNull(calc.delayedGetPointOrNull())
            calc.subtract(1)
            assertNull(calc.delayedGetPointOrNull())
        }
    }

    // ── DC with String field ───────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetNamedValue default label`() = runBlocking {
        Calculator(42).use { calc ->
            val nv = calc.delayedGetNamedValue()
            assertEquals("default", nv.name)
            assertEquals(42, nv.value)
        }
    }

    @Test
    fun `suspend - delayedGetNamedValue custom label`() = runBlocking {
        Calculator(10).use { calc ->
            calc.label = "test"
            val nv = calc.delayedGetNamedValue()
            assertEquals("test", nv.name)
            assertEquals(10, nv.value)
        }
    }

    // ── Nested DC + Enum ───────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetTaggedPoint nested DC with enum`() = runBlocking {
        Calculator(5).use { calc ->
            val tp = calc.delayedGetTaggedPoint()
            assertEquals(5, tp.point.x)
            assertEquals(10, tp.point.y)
            assertEquals(Operation.ADD, tp.tag)
        }
    }

    @Test
    fun `suspend - delayedGetTaggedPoint after operation change`() = runBlocking {
        Calculator(5).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 2)
            val tp = calc.delayedGetTaggedPoint()
            assertEquals(10, tp.point.x)
            assertEquals(20, tp.point.y)
            assertEquals(Operation.MULTIPLY, tp.tag)
        }
    }

    // ── Deeply nested DC ───────────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetRect deeply nested`() = runBlocking {
        Calculator(7).use { calc ->
            val r = calc.delayedGetRect()
            assertEquals(0, r.topLeft.x)
            assertEquals(0, r.topLeft.y)
            assertEquals(7, r.bottomRight.x)
            assertEquals(7, r.bottomRight.y)
        }
    }

    // ── Common DC (commonMain) ─────────────────────────────────────────────

    @Test
    fun `suspend - delayedGetCalcResult common data class`() = runBlocking {
        Calculator(99).use { calc ->
            val cr = calc.delayedGetCalcResult()
            assertEquals(99, cr.value)
            assertEquals("delayed: 99", cr.description)
        }
    }

    // ── Concurrent suspend DC returns ──────────────────────────────────────

    @Test
    fun `suspend - concurrent DC returns same instance`() = runBlocking {
        Calculator(10).use { calc ->
            val results = (1..20).map {
                async(Dispatchers.Default) { calc.delayedGetPoint() }
            }.awaitAll()
            assertEquals(20, results.size)
            results.forEach { p ->
                assertEquals(10, p.x)
                assertEquals(20, p.y)
            }
        }
    }

    @Test
    fun `suspend - concurrent DC returns separate instances`() = runBlocking {
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.delayedGetPoint() }
            }
        }.awaitAll()
        results.forEachIndexed { idx, p ->
            val expected = idx + 1
            assertEquals(expected, p.x)
            assertEquals(expected * 2, p.y)
        }
    }

    @Test
    fun `suspend - concurrent mixed DC types`() = runBlocking {
        Calculator(5).use { calc ->
            val points = async(Dispatchers.Default) { calc.delayedGetPoint() }
            val named = async(Dispatchers.Default) { calc.delayedGetNamedValue() }
            val tagged = async(Dispatchers.Default) { calc.delayedGetTaggedPoint() }
            val rect = async(Dispatchers.Default) { calc.delayedGetRect() }
            val result = async(Dispatchers.Default) { calc.delayedGetCalcResult() }

            assertEquals(5, points.await().x)
            assertEquals(5, named.await().value)
            assertEquals(5, tagged.await().point.x)
            assertEquals(5, rect.await().bottomRight.x)
            assertEquals(5, result.await().value)
        }
    }

    // ── Sequential stress / leak detection ─────────────────────────────────

    @Test
    fun `suspend - 200 sequential DC returns no leak`() = runBlocking {
        Calculator(1).use { calc ->
            repeat(200) {
                val p = calc.delayedGetPoint()
                assertEquals(1, p.x)
            }
        }
        System.gc()
    }

    @Test
    fun `suspend - 100 instances DC returns`() = runBlocking {
        repeat(100) { i ->
            Calculator(i).use { calc ->
                val p = calc.delayedGetPoint()
                assertEquals(i, p.x)
                assertEquals(i * 2, p.y)
            }
        }
        System.gc()
    }

    // ── DC after error recovery ────────────────────────────────────────────

    @Test
    fun `suspend - DC return after error recovery`() = runBlocking {
        Calculator(5).use { calc ->
            try { calc.failAfterDelay() } catch (_: Exception) {}
            val p = calc.delayedGetPoint()
            assertEquals(5, p.x)
            assertEquals(10, p.y)
        }
    }

    // ── Mixed suspend DC + sync operations ─────────────────────────────────

    @Test
    fun `suspend - DC interleaved with sync mutations`() = runBlocking {
        Calculator(0).use { calc ->
            calc.add(3)
            val p1 = calc.delayedGetPoint()
            assertEquals(3, p1.x)

            calc.add(2)
            val p2 = calc.delayedGetPoint()
            assertEquals(5, p2.x)
            assertEquals(10, p2.y)
        }
    }

    @Test
    fun `suspend - multiple DC types sequentially`() = runBlocking {
        Calculator(4).use { calc ->
            val p = calc.delayedGetPoint()
            val nv = calc.delayedGetNamedValue()
            val tp = calc.delayedGetTaggedPoint()
            val r = calc.delayedGetRect()
            val cr = calc.delayedGetCalcResult()

            assertEquals(Point(4, 8), p)
            assertEquals(4, nv.value)
            assertEquals(4, tp.point.x)
            assertEquals(Point(0, 0), r.topLeft)
            assertEquals(4, cr.value)
        }
    }
}
