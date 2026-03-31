package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class AsyncDataClassEnumTest {

    // ══════════════════════════════════════════════════════════════════════════
    // DATA CLASS RETURN — same instance
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `async getPoint same instance`() = runBlocking {
        Calculator(7).use { calc ->
            (1..15).map {
                async(Dispatchers.Default) { calc.getPoint() }
            }.awaitAll().forEach {
                assertEquals(7, it.x)
                assertEquals(14, it.y)
            }
        }
    }

    @Test
    fun `async getPoint separate instances`() = runBlocking {
        (1..15).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.getPoint() }
            }
        }.awaitAll().forEachIndexed { idx, p ->
            val i = idx + 1
            assertEquals(i, p.x)
            assertEquals(i * 2, p.y)
        }
    }

    @Test
    fun `async getNamedValue same instance`() = runBlocking {
        Calculator(42).use { calc ->
            calc.label = "shared"
            (1..12).map {
                async(Dispatchers.Default) { calc.getNamedValue() }
            }.awaitAll().forEach {
                assertEquals("shared", it.name)
                assertEquals(42, it.value)
            }
        }
    }

    @Test
    fun `async getNamedValue separate instances`() = runBlocking {
        (1..12).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    calc.label = "lbl$i"
                    calc.getNamedValue()
                }
            }
        }.awaitAll().forEachIndexed { idx, nv ->
            val i = idx + 1
            assertEquals("lbl$i", nv.name)
            assertEquals(i, nv.value)
        }
    }

    @Test
    fun `async getTaggedPoint same instance`() = runBlocking {
        Calculator(5).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 2)
            (1..10).map {
                async(Dispatchers.Default) { calc.getTaggedPoint() }
            }.awaitAll().forEach {
                assertEquals(Operation.MULTIPLY, it.tag)
                assertEquals(10, it.point.x)
            }
        }
    }

    @Test
    fun `async getTaggedPoint separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    calc.applyOp(Operation.SUBTRACT, 0)
                    calc.getTaggedPoint()
                }
            }
        }.awaitAll().forEachIndexed { idx, tp ->
            val i = idx + 1
            assertEquals(i, tp.point.x)
            assertEquals(Operation.SUBTRACT, tp.tag)
        }
    }

    @Test
    fun `async getRect same instance`() = runBlocking {
        Calculator(8).use { calc ->
            (1..15).map {
                async(Dispatchers.Default) { calc.getRect() }
            }.awaitAll().forEach {
                assertEquals(Point(0, 0), it.topLeft)
                assertEquals(Point(8, 8), it.bottomRight)
            }
        }
    }

    @Test
    fun `async getRect separate instances`() = runBlocking {
        (1..15).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.getRect() }
            }
        }.awaitAll().forEachIndexed { idx, r ->
            val i = idx + 1
            assertEquals(Point(i, i), r.bottomRight)
        }
    }

    @Test
    fun `async getResult same instance`() = runBlocking {
        Calculator(99).use { calc ->
            (1..12).map {
                async(Dispatchers.Default) { calc.getResult() }
            }.awaitAll().forEach {
                assertEquals(99, it.value)
                assertEquals("Result: 99", it.description)
            }
        }
    }

    @Test
    fun `async getResult separate instances`() = runBlocking {
        (1..12).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.getResult() }
            }
        }.awaitAll().forEachIndexed { idx, r ->
            val i = idx + 1
            assertEquals(i, r.value)
            assertEquals("Result: $i", r.description)
        }
    }

    @Test
    fun `async getPointOrNull non-null same instance`() = runBlocking {
        Calculator(5).use { calc ->
            (1..10).map {
                async(Dispatchers.Default) { calc.getPointOrNull() }
            }.awaitAll().forEach {
                assertNotNull(it)
                assertEquals(5, it.x)
                assertEquals(10, it.y)
            }
        }
    }

    @Test
    fun `async getPointOrNull null same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map {
                async(Dispatchers.Default) { calc.getPointOrNull() }
            }.awaitAll().forEach { assertNull(it) }
        }
    }

    @Test
    fun `async getResultOrNull non-null same instance`() = runBlocking {
        Calculator(77).use { calc ->
            (1..10).map {
                async(Dispatchers.Default) { calc.getResultOrNull() }
            }.awaitAll().forEach {
                assertNotNull(it)
                assertEquals(77, it.value)
            }
        }
    }

    @Test
    fun `async getResultOrNull null same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map {
                async(Dispatchers.Default) { calc.getResultOrNull() }
            }.awaitAll().forEach { assertNull(it) }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DATA CLASS PARAM — same instance
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `async addPoint same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..15).map {
                async(Dispatchers.Default) { calc.addPoint(Point(1, 1)) }
            }.awaitAll()
            // Each addPoint adds 2, but concurrent mutations are non-deterministic;
            // just verify the final result is consistent with 15 adds of 2
            assertEquals(30, calc.current)
        }
    }

    @Test
    fun `async addPoint separate instances`() = runBlocking {
        (1..15).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.addPoint(Point(i, i * 2))
                }
            }
        }.awaitAll().forEachIndexed { idx, result ->
            val i = idx + 1
            assertEquals(i + i * 2, result)
        }
    }

    @Test
    fun `async setFromNamed same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map { i ->
                async(Dispatchers.Default) {
                    calc.setFromNamed(NamedValue("n$i", i))
                }
            }.awaitAll()
            // Last write wins; just check state is consistent
            assertNotNull(calc.label)
            assert(calc.current in 1..10)
        }
    }

    @Test
    fun `async setFromNamed separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.setFromNamed(NamedValue("name$i", i * 10))
                    assertEquals("name$i", calc.label)
                    assertEquals(i * 10, calc.current)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async setFromTagged same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map { i ->
                async(Dispatchers.Default) {
                    val op = Operation.entries[i % Operation.entries.size]
                    calc.setFromTagged(TaggedPoint(Point(i, i), op))
                }
            }.awaitAll()
            assert(calc.current > 0)
        }
    }

    @Test
    fun `async setFromTagged separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.setFromTagged(TaggedPoint(Point(i, i * 2), Operation.SUBTRACT))
                    assertEquals(i * 3, calc.current)
                    assertEquals(Operation.SUBTRACT, calc.lastOperation)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async addPointOrNull non-null separate instances`() = runBlocking {
        (1..12).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> calc.addPointOrNull(Point(i, i)) }
            }
        }.awaitAll().forEachIndexed { idx, r ->
            assertEquals((idx + 1) * 2, r)
        }
    }

    @Test
    fun `async addPointOrNull null separate instances`() = runBlocking {
        (1..12).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.addPointOrNull(null) }
            }
        }.awaitAll().forEachIndexed { idx, r ->
            assertEquals(idx + 1, r)
        }
    }

    @Test
    fun `async applyResult same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map { i ->
                async(Dispatchers.Default) {
                    calc.applyResult(CalcResult(i, "res$i"))
                }
            }.awaitAll()
            assert(calc.current in 1..10)
        }
    }

    @Test
    fun `async applyResult separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    val r = calc.applyResult(CalcResult(i * 5, "d$i"))
                    assertEquals(i * 5, r)
                    assertEquals("d$i", calc.label)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async restoreFrom same instance`() = runBlocking {
        Calculator(0).use { target ->
            (1..10).map { i ->
                async(Dispatchers.Default) {
                    Calculator(i * 10).use { source ->
                        source.label = "snap$i"
                        val snap = source.snapshot()
                        target.restoreFrom(snap)
                        snap.calc.close()
                    }
                }
            }.awaitAll()
            assert(target.current > 0)
        }
    }

    @Test
    fun `async restoreFrom separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { target ->
                    Calculator(i).use { source ->
                        source.label = "s$i"
                        val snap = source.snapshot()
                        val r = target.restoreFrom(snap)
                        assertEquals(i, r)
                        assertEquals("s$i", target.label)
                        snap.calc.close()
                    }
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async firstPointOrDefault same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map { i ->
                async(Dispatchers.Default) {
                    calc.firstPointOrDefault(listOf(Point(i, i * 2)))
                }
            }.awaitAll().forEachIndexed { idx, p ->
                assertEquals(idx + 1, p.x)
            }
        }
    }

    @Test
    fun `async firstPointOrDefault empty separate instances`() = runBlocking {
        (1..10).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.firstPointOrDefault(emptyList())
                }
            }
        }.awaitAll().forEach {
            assertEquals(Point(0, 0), it)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ENUM
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `async applyOp ADD same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map {
                async(Dispatchers.Default) { calc.applyOp(Operation.ADD, 1) }
            }.awaitAll()
            assertEquals(15, results.size)
            assertTrue(calc.current > 0)
        }
    }

    @Test
    fun `async applyOp ADD separate instances`() = runBlocking {
        (1..15).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> calc.applyOp(Operation.ADD, i) }
            }
        }.awaitAll().forEachIndexed { idx, r ->
            assertEquals(idx + 1, r)
        }
    }

    @Test
    fun `async applyOp SUBTRACT same instance`() = runBlocking {
        Calculator(100).use { calc ->
            val results = (1..10).map {
                async(Dispatchers.Default) { calc.applyOp(Operation.SUBTRACT, 1) }
            }.awaitAll()
            assertEquals(10, results.size)
            assertTrue(calc.current < 100)
        }
    }

    @Test
    fun `async applyOp SUBTRACT separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(100).use { calc -> calc.applyOp(Operation.SUBTRACT, i) }
            }
        }.awaitAll().forEachIndexed { idx, r ->
            assertEquals(100 - (idx + 1), r)
        }
    }

    @Test
    fun `async applyOp MULTIPLY same instance`() = runBlocking {
        Calculator(1).use { calc ->
            (1..10).map {
                async(Dispatchers.Default) { calc.applyOp(Operation.MULTIPLY, 2) }
            }.awaitAll()
            // multiply is not atomic — concurrent *= 2 on same accumulator is a race.
            // We verify the bridge doesn't crash and the result is a positive power of 2
            // (some multiplications may be lost due to the race, so result <= 1024).
            val result = calc.current
            assertTrue(result > 0, "Result should be positive")
            assertTrue(result and (result - 1) == 0, "Result should be a power of 2, got $result")
        }
    }

    @Test
    fun `async applyOp MULTIPLY separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.applyOp(Operation.MULTIPLY, 3) }
            }
        }.awaitAll().forEachIndexed { idx, r ->
            assertEquals((idx + 1) * 3, r)
        }
    }

    @Test
    fun `async getLastOp same instance`() = runBlocking {
        Calculator(5).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 1)
            (1..12).map {
                async(Dispatchers.Default) { calc.getLastOp() }
            }.awaitAll().forEach {
                assertEquals(Operation.MULTIPLY, it)
            }
        }
    }

    @Test
    fun `async getLastOp separate instances`() = runBlocking {
        (1..12).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val op = Operation.entries[i % Operation.entries.size]
                    calc.applyOp(op, 1)
                    calc.getLastOp()
                }
            }
        }.awaitAll().forEachIndexed { idx, op ->
            val i = idx + 1
            assertEquals(Operation.entries[i % Operation.entries.size], op)
        }
    }

    @Test
    fun `async lastOperation property same instance`() = runBlocking {
        Calculator(0).use { calc ->
            calc.applyOp(Operation.SUBTRACT, 1)
            (1..10).map {
                async(Dispatchers.Default) { calc.lastOperation }
            }.awaitAll().forEach {
                assertEquals(Operation.SUBTRACT, it)
            }
        }
    }

    @Test
    fun `async lastOperation property separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    val op = Operation.entries[i % Operation.entries.size]
                    calc.applyOp(op, 1)
                    calc.lastOperation
                }
            }
        }.awaitAll().forEachIndexed { idx, op ->
            val i = idx + 1
            assertEquals(Operation.entries[i % Operation.entries.size], op)
        }
    }

    @Test
    fun `async findOp same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map {
                async(Dispatchers.Default) { calc.findOp("MULTIPLY") }
            }.awaitAll().forEach {
                assertEquals(Operation.MULTIPLY, it)
            }
        }
    }

    @Test
    fun `async findOp null and non-null separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    if (i % 2 == 0) calc.findOp("ADD") else calc.findOp(null)
                }
            }
        }.awaitAll().forEachIndexed { idx, op ->
            val i = idx + 1
            if (i % 2 == 0) assertEquals(Operation.ADD, op) else assertNull(op)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NESTED CLASSES — CalculatorSnapshot
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `async snapshot same instance`() = runBlocking {
        Calculator(42).use { calc ->
            calc.label = "snap-test"
            (1..10).map {
                async(Dispatchers.Default) {
                    val snap = calc.snapshot()
                    assertEquals(42, snap.calc.current)
                    assertEquals("snap-test", snap.label)
                    snap.calc.close()
                }
            }.awaitAll()
        }
    }

    @Test
    fun `async snapshot separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(i * 5).use { calc ->
                    calc.label = "s$i"
                    val snap = calc.snapshot()
                    assertEquals(i * 5, snap.calc.current)
                    assertEquals("s$i", snap.label)
                    snap.calc.close()
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async snapshot and restoreFrom separate pairs`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { source ->
                    Calculator(0).use { target ->
                        source.label = "src$i"
                        val snap = source.snapshot()
                        target.restoreFrom(snap)
                        assertEquals(i, target.current)
                        assertEquals("src$i", target.label)
                        snap.calc.close()
                    }
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async CalculatorSnapshot constructed manually separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(i * 3).use { calc ->
                    Calculator(0).use { target ->
                        val snap = CalculatorSnapshot(calc, "manual$i")
                        val r = target.restoreFrom(snap)
                        assertEquals(i * 3, r)
                        assertEquals("manual$i", target.label)
                    }
                }
            }
        }.awaitAll()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DATA CLASS WITH COLLECTION FIELDS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `async getTaggedList same instance`() = runBlocking {
        Calculator(4).use { calc ->
            (1..15).map {
                async(Dispatchers.Default) { calc.getTaggedList() }
            }.awaitAll().forEach {
                assertEquals("default", it.label)
                assertEquals(listOf(4, 8, 12), it.scores)
            }
        }
    }

    @Test
    fun `async getTaggedList separate instances`() = runBlocking {
        (1..15).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.getTaggedList() }
            }
        }.awaitAll().forEachIndexed { idx, tl ->
            val i = idx + 1
            assertEquals(listOf(i, i * 2, i * 3), tl.scores)
        }
    }

    @Test
    fun `async applyTaggedList same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map { i ->
                async(Dispatchers.Default) {
                    calc.applyTaggedList(TaggedList("t$i", listOf(i, i * 2)))
                }
            }.awaitAll()
            assert(calc.current > 0)
        }
    }

    @Test
    fun `async applyTaggedList separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    val r = calc.applyTaggedList(TaggedList("tag$i", listOf(i, i * 2, i * 3)))
                    assertEquals(i * 6, r) // i + 2i + 3i
                    assertEquals("tag$i", calc.label)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async getMetadataHolder same instance`() = runBlocking {
        Calculator(20).use { calc ->
            (1..12).map {
                async(Dispatchers.Default) { calc.getMetadataHolder() }
            }.awaitAll().forEach {
                assertEquals("calc", it.name)
                assertEquals(20, it.metadata["current"])
            }
        }
    }

    @Test
    fun `async getMetadataHolder separate instances`() = runBlocking {
        (1..12).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.getMetadataHolder() }
            }
        }.awaitAll().forEachIndexed { idx, mh ->
            assertEquals(idx + 1, mh.metadata["current"])
        }
    }

    @Test
    fun `async applyMetadataHolder same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map { i ->
                async(Dispatchers.Default) {
                    calc.applyMetadataHolder(MetadataHolder("mh$i", mapOf("a" to i)))
                }
            }.awaitAll()
            assert(calc.label.startsWith("mh"))
        }
    }

    @Test
    fun `async applyMetadataHolder separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.applyMetadataHolder(MetadataHolder("meta$i", mapOf("x" to i, "y" to i * 2)))
                }
            }
        }.awaitAll().forEachIndexed { idx, r ->
            val i = idx + 1
            assertEquals("meta$i:${i * 3}", r)
        }
    }

    @Test
    fun `async getMultiCollDC same instance`() = runBlocking {
        Calculator(6).use { calc ->
            (1..10).map {
                async(Dispatchers.Default) { calc.getMultiCollDC() }
            }.awaitAll().forEach {
                assertEquals(listOf("default", "item_6"), it.tags)
                assertEquals(listOf(true, true), it.flags)
                assertEquals(listOf(6, 7, 8), it.counts)
            }
        }
    }

    @Test
    fun `async getMultiCollDC separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.getMultiCollDC() }
            }
        }.awaitAll().forEachIndexed { idx, mc ->
            val i = idx + 1
            assertEquals(listOf(i, i + 1, i + 2), mc.counts)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BYTEARRAY DC FIELD
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `async getBinaryPayload same instance`() = runBlocking {
        Calculator(4).use { calc ->
            (1..15).map {
                async(Dispatchers.Default) { calc.getBinaryPayload() }
            }.awaitAll().forEach {
                assertEquals("payload_4", it.name)
                assertEquals(4, it.data.size)
            }
        }
    }

    @Test
    fun `async getBinaryPayload separate instances`() = runBlocking {
        (1..15).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.getBinaryPayload() }
            }
        }.awaitAll().forEachIndexed { idx, bp ->
            val i = idx + 1
            assertEquals("payload_$i", bp.name)
            assertEquals(i, bp.data.size)
        }
    }

    @Test
    fun `async applyBinaryPayload same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map { i ->
                async(Dispatchers.Default) {
                    calc.applyBinaryPayload(BinaryPayload("bp$i", ByteArray(i)))
                }
            }.awaitAll()
            assert(calc.current in 1..10)
        }
    }

    @Test
    fun `async applyBinaryPayload separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    val r = calc.applyBinaryPayload(BinaryPayload("bin$i", ByteArray(i * 2)))
                    assertEquals(i * 2, r)
                    assertEquals("bin$i", calc.label)
                }
            }
        }.awaitAll()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LIST<DC> PARAM
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `async sumPoints same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..15).map { i ->
                async(Dispatchers.Default) {
                    calc.sumPoints(listOf(Point(i, i)))
                }
            }.awaitAll()
            assertEquals(15, results.size)
            results.forEach { assert(it > 0) }
        }
    }

    @Test
    fun `async sumPoints separate instances`() = runBlocking {
        (1..15).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.sumPoints(listOf(Point(i, i * 2)))
                }
            }
        }.awaitAll().forEachIndexed { idx, r ->
            val i = idx + 1
            assertEquals(i * 3, r)
        }
    }

    @Test
    fun `async countTaggedPoints same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..12).map { i ->
                async(Dispatchers.Default) {
                    val items = List(i) { TaggedPoint(Point(it, it), Operation.ADD) }
                    calc.countTaggedPoints(items)
                }
            }.awaitAll().forEachIndexed { idx, c ->
                assertEquals(idx + 1, c)
            }
        }
    }

    @Test
    fun `async countTaggedPoints separate instances`() = runBlocking {
        (1..12).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.countTaggedPoints(List(i) { TaggedPoint(Point(0, 0), Operation.MULTIPLY) })
                }
            }
        }.awaitAll().forEachIndexed { idx, c ->
            assertEquals(idx + 1, c)
        }
    }

    @Test
    fun `async describeNamedValues same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map { i ->
                async(Dispatchers.Default) {
                    calc.describeNamedValues(listOf(NamedValue("k$i", i)))
                }
            }.awaitAll().forEachIndexed { idx, s ->
                val i = idx + 1
                assertEquals("k$i=$i", s)
            }
        }
    }

    @Test
    fun `async describeNamedValues separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.describeNamedValues(listOf(NamedValue("a", i), NamedValue("b", i * 2)))
                }
            }
        }.awaitAll().forEachIndexed { idx, s ->
            val i = idx + 1
            assertEquals("a=$i, b=${i * 2}", s)
        }
    }

    @Test
    fun `async sumRects same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map { i ->
                async(Dispatchers.Default) {
                    calc.sumRects(listOf(Rect(Point(0, 0), Point(i, i))))
                }
            }.awaitAll().forEachIndexed { idx, r ->
                assertEquals((idx + 1) * 2, r)
            }
        }
    }

    @Test
    fun `async sumRects separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.sumRects(listOf(Rect(Point(i, i), Point(i, i))))
                }
            }
        }.awaitAll().forEachIndexed { idx, r ->
            assertEquals((idx + 1) * 4, r)
        }
    }

    @Test
    fun `async describePersons same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map { i ->
                async(Dispatchers.Default) {
                    calc.describePersons(listOf(Person("P$i", i * 10, Address("St$i", "City$i"))))
                }
            }.awaitAll().forEachIndexed { idx, s ->
                val i = idx + 1
                assertEquals("P$i(${i * 10}) @ St$i, City$i", s)
            }
        }
    }

    @Test
    fun `async describePersons separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.describePersons(listOf(
                        Person("A$i", i, Address("Road$i", "Town")),
                        Person("B$i", i * 2, Address("Ave$i", "Village")),
                    ))
                }
            }
        }.awaitAll().forEachIndexed { idx, s ->
            val i = idx + 1
            assert(s.contains("A$i"))
            assert(s.contains("B$i"))
        }
    }

    @Test
    fun `async oldestPersonAge same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map { i ->
                async(Dispatchers.Default) {
                    calc.oldestPersonAge(listOf(
                        Person("Young", 10, Address("S", "C")),
                        Person("Old", i * 10, Address("S", "C")),
                    ))
                }
            }.awaitAll().forEachIndexed { idx, age ->
                val i = idx + 1
                assertEquals(maxOf(10, i * 10), age)
            }
        }
    }

    @Test
    fun `async oldestPersonAge separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.oldestPersonAge(listOf(Person("X", i * 5, Address("S", "C"))))
                }
            }
        }.awaitAll().forEachIndexed { idx, age ->
            assertEquals((idx + 1) * 5, age)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CROSS-FEATURE: data class + enum + nested combined under concurrency
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `async roundtrip getPoint then addPoint separate instances`() = runBlocking {
        (1..15).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val p = calc.getPoint()
                    Calculator(0).use { other ->
                        val r = other.addPoint(p)
                        assertEquals(i + i * 2, r)
                    }
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async roundtrip getTaggedList then applyTaggedList separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val tl = calc.getTaggedList()
                    Calculator(0).use { other ->
                        val r = other.applyTaggedList(tl)
                        assertEquals(i + i * 2 + i * 3, r)
                    }
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async roundtrip getBinaryPayload then applyBinaryPayload separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val bp = calc.getBinaryPayload()
                    Calculator(0).use { other ->
                        val r = other.applyBinaryPayload(bp)
                        assertEquals(i, r)
                        assertEquals("payload_$i", other.label)
                    }
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async getResult then applyResult roundtrip separate instances`() = runBlocking {
        (1..12).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { src ->
                    val r = src.getResult()
                    Calculator(0).use { dst ->
                        dst.applyResult(r)
                        assertEquals(i, dst.current)
                        assertEquals("Result: $i", dst.label)
                    }
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async mixed enum ops then data class reads same instance`() = runBlocking {
        Calculator(1).use { calc ->
            val ops = Operation.entries
            (1..15).map { i ->
                async(Dispatchers.Default) {
                    val op = ops[i % ops.size]
                    calc.applyOp(op, 1)
                    calc.getTaggedPoint()
                }
            }.awaitAll().forEach { tp ->
                assertNotNull(tp)
                assert(tp.tag in Operation.entries)
            }
        }
    }

    @Test
    fun `async mixed enum ops then data class reads separate instances`() = runBlocking {
        (1..15).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val op = Operation.entries[i % Operation.entries.size]
                    calc.applyOp(op, 1)
                    val tp = calc.getTaggedPoint()
                    assertEquals(op, tp.tag)
                    tp
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async snapshot then getResult then enum same instance`() = runBlocking {
        Calculator(50).use { calc ->
            calc.label = "multi"
            (1..10).map {
                async(Dispatchers.Default) {
                    val snap = calc.snapshot()
                    val result = calc.getResult()
                    val op = calc.getLastOp()
                    snap.calc.close()
                    Triple(snap.label, result.value, op)
                }
            }.awaitAll().forEach { (label, _, op) ->
                assertEquals("multi", label)
                assertNotNull(op)
            }
        }
    }

    @Test
    fun `async snapshot then getResult then enum separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(i * 7).use { calc ->
                    calc.label = "calc$i"
                    calc.applyOp(Operation.SUBTRACT, 0)
                    val snap = calc.snapshot()
                    val result = calc.getResult()
                    val op = calc.getLastOp()
                    assertEquals(i * 7, snap.calc.current)
                    assertEquals("calc$i", snap.label)
                    assertEquals(i * 7, result.value)
                    assertEquals(Operation.SUBTRACT, op)
                    snap.calc.close()
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async getMetadataHolder then applyMetadataHolder roundtrip separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { src ->
                    val mh = src.getMetadataHolder()
                    Calculator(0).use { dst ->
                        val r = dst.applyMetadataHolder(mh)
                        assertEquals("calc:${i + 1}", r) // current=i, scale=1 => sum=i+1
                    }
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async findOp all names separate instances`() = runBlocking {
        val names = listOf("ADD", "SUBTRACT", "MULTIPLY", null, "INVALID")
        (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    val name = names[i % names.size]
                    calc.findOp(name)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async addPointOrNull mixed null and non-null same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..20).map { i ->
                async(Dispatchers.Default) {
                    if (i % 2 == 0) calc.addPointOrNull(Point(1, 0))
                    else calc.addPointOrNull(null)
                }
            }.awaitAll()
            // 10 non-null adds of 1 each
            assertEquals(10, calc.current)
        }
    }

    @Test
    fun `async getPointOrNull transitions separate instances`() = runBlocking {
        (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(i - 5).use { calc ->
                    val p = calc.getPointOrNull()
                    if (i - 5 != 0) assertNotNull(p)
                    else assertNull(p)
                    p
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async getResultOrNull transitions separate instances`() = runBlocking {
        (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(i - 10).use { calc ->
                    val r = calc.getResultOrNull()
                    if (i - 10 != 0) {
                        assertNotNull(r)
                        assertEquals(i - 10, r.value)
                    } else {
                        assertNull(r)
                    }
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async sumPoints large lists separate instances`() = runBlocking {
        (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    val pts = List(50) { Point(i, 1) }
                    val r = calc.sumPoints(pts)
                    assertEquals(50 * (i + 1), r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `async oldestPersonAge empty list same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..10).map {
                async(Dispatchers.Default) {
                    calc.oldestPersonAge(emptyList())
                }
            }.awaitAll().forEach { assertEquals(-1, it) }
        }
    }
}
