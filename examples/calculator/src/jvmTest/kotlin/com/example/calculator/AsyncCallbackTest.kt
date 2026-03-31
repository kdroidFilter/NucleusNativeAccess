package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class AsyncCallbackTest {

    private val N = 15

    // ── onValueChanged ──────────────────────────────────────────────────────

    @Test
    fun `onValueChanged - concurrent same instance`() = runBlocking {
        Calculator(7).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    var v = -1
                    calc.onValueChanged { v = it }
                    assertEquals(7, v)
                }
            }.awaitAll()
        }
    }

    @Test
    fun `onValueChanged - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var v = -1
                    calc.onValueChanged { v = it }
                    assertEquals(i, v)
                }
            }
        }.awaitAll()
    }

    // ── transform ───────────────────────────────────────────────────────────

    @Test
    fun `transform - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val r = calc.transform { it * 3 }
                    assertEquals(i * 3, r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `transform - concurrent same instance different lambdas`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(10).use { calc ->
                    val r = calc.transform { it + i }
                    assertTrue(r > 0)
                }
            }
        }.awaitAll()
    }

    // ── compute ─────────────────────────────────────────────────────────────

    @Test
    fun `compute - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    val r = calc.compute(i, i * 2) { a, b -> a + b }
                    assertEquals(i * 3, r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `compute - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..N).map { i ->
                async(Dispatchers.Default) {
                    val r = calc.compute(i, 2) { a, b -> a * b }
                    assertEquals(i * 2, r)
                }
            }.awaitAll()
        }
    }

    // ── checkWith ───────────────────────────────────────────────────────────

    @Test
    fun `checkWith - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    assertTrue(calc.checkWith { it > 0 })
                }
            }
        }.awaitAll()
    }

    @Test
    fun `checkWith - concurrent same instance`() = runBlocking {
        Calculator(42).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    assertTrue(calc.checkWith { it == 42 })
                }
            }.awaitAll()
        }
    }

    // ── withDescription ─────────────────────────────────────────────────────

    @Test
    fun `withDescription - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var desc = ""
                    calc.withDescription { desc = it }
                    assertEquals("Calculator(current=$i)", desc)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `withDescription - concurrent same instance`() = runBlocking {
        Calculator(99).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    var desc = ""
                    calc.withDescription { desc = it }
                    assertEquals("Calculator(current=99)", desc)
                }
            }.awaitAll()
        }
    }

    // ── formatWith ──────────────────────────────────────────────────────────

    @Test
    fun `formatWith - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val s = calc.formatWith { "val=$it" }
                    assertEquals("val=$i", s)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `formatWith - concurrent same instance`() = runBlocking {
        Calculator(5).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    val s = calc.formatWith { "x=$it" }
                    assertEquals("x=5", s)
                }
            }.awaitAll()
        }
    }

    // ── transformLabel ──────────────────────────────────────────────────────

    @Test
    fun `transformLabel - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    calc.label = "base"
                    val r = calc.transformLabel { "$it-$i" }
                    assertEquals("base-$i", r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `transformLabel - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            calc.label = "x"
            (1..N).map {
                async(Dispatchers.Default) {
                    calc.transformLabel { "${it}_done" }
                }
            }.awaitAll()
            // Just verify no crash; label may be any of the transformed values
            assertTrue(calc.label.contains("done"))
        }
    }

    // ── onOperation ─────────────────────────────────────────────────────────

    @Test
    fun `onOperation - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var op: Operation? = null
                    calc.onOperation { op = it }
                    assertEquals(Operation.ADD, op)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `onOperation - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    var op: Operation? = null
                    calc.onOperation { op = it }
                    assertEquals(Operation.ADD, op)
                }
            }.awaitAll()
        }
    }

    // ── chooseOp ────────────────────────────────────────────────────────────

    @Test
    fun `chooseOp - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val op = calc.chooseOp { if (it > 5) Operation.MULTIPLY else Operation.ADD }
                    assertEquals(if (i > 5) Operation.MULTIPLY else Operation.ADD, op)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `chooseOp - concurrent same instance`() = runBlocking {
        Calculator(10).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    val op = calc.chooseOp { Operation.SUBTRACT }
                    assertEquals(Operation.SUBTRACT, op)
                }
            }.awaitAll()
        }
    }

    // ── withLong ────────────────────────────────────────────────────────────

    @Test
    fun `withLong - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val r = calc.withLong { it * 100L }
                    assertEquals(i.toLong() * 100L, r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `withLong - concurrent same instance`() = runBlocking {
        Calculator(3).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    assertEquals(6L, calc.withLong { it + 3L })
                }
            }.awaitAll()
        }
    }

    // ── withDouble ──────────────────────────────────────────────────────────

    @Test
    fun `withDouble - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val r = calc.withDouble { it * 2.5 }
                    assertEquals(i.toDouble() * 2.5, r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `withDouble - concurrent same instance`() = runBlocking {
        Calculator(4).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    assertEquals(8.0, calc.withDouble { it * 2.0 })
                }
            }.awaitAll()
        }
    }

    // ── withFloat ───────────────────────────────────────────────────────────

    @Test
    fun `withFloat - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val r = calc.withFloat { it + 1.0f }
                    assertEquals(i.toFloat() + 1.0f, r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `withFloat - concurrent same instance`() = runBlocking {
        Calculator(2).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    assertEquals(4.0f, calc.withFloat { it * 2.0f })
                }
            }.awaitAll()
        }
    }

    // ── withShort ───────────────────────────────────────────────────────────

    @Test
    fun `withShort - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val r = calc.withShort { (it + 1).toShort() }
                    assertEquals((i + 1).toShort(), r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `withShort - concurrent same instance`() = runBlocking {
        Calculator(5).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    assertEquals(10.toShort(), calc.withShort { (it * 2).toShort() })
                }
            }.awaitAll()
        }
    }

    // ── withByte ────────────────────────────────────────────────────────────

    @Test
    fun `withByte - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val r = calc.withByte { (it + 1).toByte() }
                    assertEquals((i + 1).toByte(), r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `withByte - concurrent same instance`() = runBlocking {
        Calculator(3).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    assertEquals(6.toByte(), calc.withByte { (it * 2).toByte() })
                }
            }.awaitAll()
        }
    }

    // ── onPointComputed ─────────────────────────────────────────────────────

    @Test
    fun `onPointComputed - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var p: Point? = null
                    calc.onPointComputed { p = it }
                    assertEquals(Point(i, i * 2), p)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `onPointComputed - concurrent same instance`() = runBlocking {
        Calculator(8).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    var p: Point? = null
                    calc.onPointComputed { p = it }
                    assertEquals(Point(8, 16), p)
                }
            }.awaitAll()
        }
    }

    // ── onResultReady ───────────────────────────────────────────────────────

    @Test
    fun `onResultReady - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var r: CalcResult? = null
                    calc.onResultReady { r = it }
                    assertEquals(CalcResult(i, "Result: $i"), r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `onResultReady - concurrent same instance`() = runBlocking {
        Calculator(11).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    var r: CalcResult? = null
                    calc.onResultReady { r = it }
                    assertEquals(CalcResult(11, "Result: 11"), r)
                }
            }.awaitAll()
        }
    }

    // ── createPoint ─────────────────────────────────────────────────────────

    @Test
    fun `createPoint - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val p = calc.createPoint { v -> Point(v, v * 3) }
                    assertEquals(Point(i, i * 3), p)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `createPoint - concurrent same instance`() = runBlocking {
        Calculator(4).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    val p = calc.createPoint { v -> Point(v, v + 1) }
                    assertEquals(Point(4, 5), p)
                }
            }.awaitAll()
        }
    }

    // ── transformPoint ──────────────────────────────────────────────────────

    @Test
    fun `transformPoint - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val r = calc.transformPoint { p -> p.x + p.y }
                    assertEquals(i + i * 2, r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `transformPoint - concurrent same instance`() = runBlocking {
        Calculator(5).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    val r = calc.transformPoint { p -> p.x * p.y }
                    assertTrue(r >= 0)
                }
            }.awaitAll()
        }
    }

    // ── findAndReport ───────────────────────────────────────────────────────

    @Test
    fun `findAndReport - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    calc.label = "hello"
                    var reportedLabel = ""
                    var reportedCount = -1
                    calc.findAndReport("hello") { lbl, count ->
                        reportedLabel = lbl
                        reportedCount = count
                    }
                    assertEquals("hello", reportedLabel)
                    assertEquals(1, reportedCount)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `findAndReport - concurrent same instance`() = runBlocking {
        Calculator(1).use { calc ->
            calc.label = "test"
            (1..N).map {
                async(Dispatchers.Default) {
                    var found = -1
                    calc.findAndReport("test") { _, count -> found = count }
                    assertEquals(1, found)
                }
            }.awaitAll()
        }
    }

    @Test
    fun `findAndReport - not found concurrent`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    calc.label = "abc"
                    var found = -1
                    calc.findAndReport("xyz") { _, count -> found = count }
                    assertEquals(0, found)
                }
            }
        }.awaitAll()
    }

    // ── onBytesReady ────────────────────────────────────────────────────────

    @Test
    fun `onBytesReady - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var received: ByteArray? = null
                    calc.onBytesReady { received = it }
                    assertEquals(i, received!!.size)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `onBytesReady - concurrent same instance`() = runBlocking {
        Calculator(3).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    var received: ByteArray? = null
                    calc.onBytesReady { received = it }
                    assertEquals(3, received!!.size)
                }
            }.awaitAll()
        }
    }

    // ── transformBytes ──────────────────────────────────────────────────────

    @Test
    fun `transformBytes - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val input = byteArrayOf(1, 2, 3)
                    val r = calc.transformBytes(input) { it.reversedArray() }
                    assertContentEquals(byteArrayOf(3, 2, 1), r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `transformBytes - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    val input = byteArrayOf(10, 20)
                    val r = calc.transformBytes(input) { arr -> arr.map { (it * 2).toByte() }.toByteArray() }
                    assertContentEquals(byteArrayOf(20, 40), r)
                }
            }.awaitAll()
        }
    }

    // ── onValueChangedOrNull ────────────────────────────────────────────────

    @Test
    fun `onValueChangedOrNull - with callback concurrent`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var v = -1
                    val result = calc.onValueChangedOrNull { v = it }
                    assertTrue(result)
                    assertEquals(i, v)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `onValueChangedOrNull - null callback concurrent`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val result = calc.onValueChangedOrNull(null)
                    assertTrue(!result)
                }
            }
        }.awaitAll()
    }

    // ── transformOrDefault ──────────────────────────────────────────────────

    @Test
    fun `transformOrDefault - with fn concurrent`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val r = calc.transformOrDefault({ it * 2 }, 0)
                    assertEquals(i * 2, r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `transformOrDefault - null fn concurrent`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val r = calc.transformOrDefault(null, 99)
                    assertEquals(99, r)
                }
            }
        }.awaitAll()
    }

    // ── formatOrNull ────────────────────────────────────────────────────────

    @Test
    fun `formatOrNull - with formatter concurrent`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val r = calc.formatOrNull { "n=$it" }
                    assertEquals("n=$i", r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `formatOrNull - null formatter concurrent`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val r = calc.formatOrNull(null)
                    assertEquals("null", r)
                }
            }
        }.awaitAll()
    }

    // ── getAdder ────────────────────────────────────────────────────────────

    @Test
    fun `getAdder - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val adder = calc.getAdder(10)
                    assertEquals(i + 10, adder(i))
                }
            }
        }.awaitAll()
    }

    @Test
    fun `getAdder - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..N).map { i ->
                async(Dispatchers.Default) {
                    val adder = calc.getAdder(i)
                    assertEquals(100 + i, adder(100))
                }
            }.awaitAll()
        }
    }

    // ── getFormatter ────────────────────────────────────────────────────────

    @Test
    fun `getFormatter - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val fmt = calc.getFormatter()
                    val s = fmt(42)
                    assertTrue(s.contains("value=42"))
                }
            }
        }.awaitAll()
    }

    @Test
    fun `getFormatter - concurrent same instance`() = runBlocking {
        Calculator(7).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    val fmt = calc.getFormatter()
                    assertTrue(fmt(1).contains("value=1"))
                }
            }.awaitAll()
        }
    }

    // ── getNotifier ─────────────────────────────────────────────────────────

    @Test
    fun `getNotifier - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    val notifier = calc.getNotifier()
                    notifier(i)
                    assertEquals(i, calc.current)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `getNotifier - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..N).map { i ->
                async(Dispatchers.Default) {
                    val notifier = calc.getNotifier()
                    notifier(i)
                }
            }.awaitAll()
            // Final value is one of the concurrent writes
            assertTrue(calc.current in 1..N)
        }
    }

    // ── onSelfReady ─────────────────────────────────────────────────────────

    @Test
    fun `onSelfReady - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var received = -1
                    calc.onSelfReady { c -> received = c.current }
                    assertEquals(i, received)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `onSelfReady - concurrent same instance`() = runBlocking {
        Calculator(55).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    var received = -1
                    calc.onSelfReady { c -> received = c.current }
                    assertEquals(55, received)
                }
            }.awaitAll()
        }
    }

    // ── transformWith ───────────────────────────────────────────────────────

    @Test
    fun `transformWith - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { a ->
                    Calculator(i * 2).use { b ->
                        val r = a.transformWith(b) { x, y -> x.current + y.current }
                        assertEquals(i + i * 2, r)
                    }
                }
            }
        }.awaitAll()
    }

    @Test
    fun `transformWith - concurrent same instance`() = runBlocking {
        Calculator(10).use { a ->
            Calculator(20).use { b ->
                (1..N).map {
                    async(Dispatchers.Default) {
                        val r = a.transformWith(b) { x, y -> x.current + y.current }
                        assertTrue(r > 0)
                    }
                }.awaitAll()
            }
        }
    }

    // ── createVia ───────────────────────────────────────────────────────────

    @Test
    fun `createVia - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val created = calc.createVia { v -> Calculator(v * 10) }
                    created.use {
                        assertEquals(i * 10, it.current)
                    }
                }
            }
        }.awaitAll()
    }

    @Test
    fun `createVia - concurrent same instance`() = runBlocking {
        Calculator(5).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    val created = calc.createVia { v -> Calculator(v + 1) }
                    created.use {
                        assertEquals(6, it.current)
                    }
                }
            }.awaitAll()
        }
    }

    // ── onScoresReady ───────────────────────────────────────────────────────

    @Test
    fun `onScoresReady - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var scores: List<Int>? = null
                    calc.onScoresReady { scores = it }
                    assertEquals(listOf(i, i * 2, i * 3), scores)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `onScoresReady - concurrent same instance`() = runBlocking {
        Calculator(4).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    var scores: List<Int>? = null
                    calc.onScoresReady { scores = it }
                    assertEquals(listOf(4, 8, 12), scores)
                }
            }.awaitAll()
        }
    }

    // ── onLabelsReady ───────────────────────────────────────────────────────

    @Test
    fun `onLabelsReady - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var labels: List<String>? = null
                    calc.onLabelsReady { labels = it }
                    assertEquals(listOf("default", "item_$i"), labels)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `onLabelsReady - concurrent same instance`() = runBlocking {
        Calculator(7).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    var labels: List<String>? = null
                    calc.onLabelsReady { labels = it }
                    assertEquals(listOf("default", "item_7"), labels)
                }
            }.awaitAll()
        }
    }

    // ── onOpsReady ──────────────────────────────────────────────────────────

    @Test
    fun `onOpsReady - concurrent separate instances`() = runBlocking {
        (1..N).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    var ops: List<Operation>? = null
                    calc.onOpsReady { ops = it }
                    assertEquals(Operation.entries.toList(), ops)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `onOpsReady - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    var ops: List<Operation>? = null
                    calc.onOpsReady { ops = it }
                    assertEquals(3, ops!!.size)
                }
            }.awaitAll()
        }
    }

    // ── onFlagsReady ────────────────────────────────────────────────────────

    @Test
    fun `onFlagsReady - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var flags: List<Boolean>? = null
                    calc.onFlagsReady { flags = it }
                    assertEquals(listOf(i > 0, i % 2 == 0), flags)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `onFlagsReady - concurrent same instance`() = runBlocking {
        Calculator(6).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    var flags: List<Boolean>? = null
                    calc.onFlagsReady { flags = it }
                    assertEquals(listOf(true, true), flags)
                }
            }.awaitAll()
        }
    }

    // ── onMetadataReady ─────────────────────────────────────────────────────

    @Test
    fun `onMetadataReady - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var meta: Map<String, Int>? = null
                    calc.onMetadataReady { meta = it }
                    assertEquals(mapOf("current" to i, "doubled" to i * 2), meta)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `onMetadataReady - concurrent same instance`() = runBlocking {
        Calculator(3).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    var meta: Map<String, Int>? = null
                    calc.onMetadataReady { meta = it }
                    assertEquals(3, meta!!["current"])
                }
            }.awaitAll()
        }
    }

    // ── onMapIntIntReady ────────────────────────────────────────────────────

    @Test
    fun `onMapIntIntReady - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var m: Map<Int, Int>? = null
                    calc.onMapIntIntReady { m = it }
                    assertEquals(mapOf(i to i * i), m)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `onMapIntIntReady - concurrent same instance`() = runBlocking {
        Calculator(5).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    var m: Map<Int, Int>? = null
                    calc.onMapIntIntReady { m = it }
                    assertEquals(mapOf(5 to 25), m)
                }
            }.awaitAll()
        }
    }

    // ── getTransformedScores ────────────────────────────────────────────────

    @Test
    fun `getTransformedScores - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val r = calc.getTransformedScores { v -> listOf(v, v + 1) }
                    assertEquals(listOf(i, i + 1), r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `getTransformedScores - concurrent same instance`() = runBlocking {
        Calculator(9).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    val r = calc.getTransformedScores { v -> listOf(v * 2) }
                    assertEquals(listOf(18), r)
                }
            }.awaitAll()
        }
    }

    // ── getComputedLabels ───────────────────────────────────────────────────

    @Test
    fun `getComputedLabels - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val r = calc.getComputedLabels { v -> listOf("a_$v", "b_$v") }
                    assertEquals(listOf("a_$i", "b_$i"), r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `getComputedLabels - concurrent same instance`() = runBlocking {
        Calculator(2).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    val r = calc.getComputedLabels { v -> listOf("x$v") }
                    assertEquals(listOf("x2"), r)
                }
            }.awaitAll()
        }
    }

    // ── mixed callback types under pressure ────────────────────────────────

    @Test
    fun `onValueChanged and transform interleaved - separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var seen = -1
                    calc.onValueChanged { seen = it }
                    val r = calc.transform { it + 1 }
                    assertEquals(i, seen)
                    assertEquals(i + 1, r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `formatWith and checkWith combined - separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val s = calc.formatWith { "v=$it" }
                    val b = calc.checkWith { it > 0 }
                    assertEquals("v=$i", s)
                    assertTrue(b)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `createPoint and transformPoint roundtrip - separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val p = calc.createPoint { v -> Point(v, v) }
                    assertEquals(Point(i, i), p)
                    val sum = calc.transformPoint { pt -> pt.x + pt.y }
                    assertTrue(sum >= 0)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `onBytesReady and transformBytes combined - separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i.coerceAtLeast(1)).use { calc ->
                    var size = 0
                    calc.onBytesReady { size = it.size }
                    assertTrue(size > 0)
                    val r = calc.transformBytes(byteArrayOf(1)) { byteArrayOf(2) }
                    assertContentEquals(byteArrayOf(2), r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `getAdder and getFormatter combined - same instance`() = runBlocking {
        Calculator(10).use { calc ->
            (1..N).map { i ->
                async(Dispatchers.Default) {
                    val adder = calc.getAdder(i)
                    val fmt = calc.getFormatter()
                    assertEquals(10 + i, adder(10))
                    assertTrue(fmt(i).contains("value=$i"))
                }
            }.awaitAll()
        }
    }

    @Test
    fun `collection callbacks and return callbacks combined - separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    var scores: List<Int>? = null
                    calc.onScoresReady { scores = it }
                    assertEquals(3, scores!!.size)
                    val computed = calc.getTransformedScores { v -> listOf(v) }
                    assertEquals(listOf(i), computed)
                }
            }
        }.awaitAll()
    }

    // ── getComputedMap ──────────────────────────────────────────────────────

    @Test
    fun `getComputedMap - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val r = calc.getComputedMap { v -> mapOf("val" to v, "sq" to v * v) }
                    assertEquals(mapOf("val" to i, "sq" to i * i), r)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `getComputedMap - concurrent same instance`() = runBlocking {
        Calculator(3).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    val r = calc.getComputedMap { v -> mapOf("k" to v) }
                    assertEquals(mapOf("k" to 3), r)
                }
            }.awaitAll()
        }
    }
}
