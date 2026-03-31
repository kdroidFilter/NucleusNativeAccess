package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class AsyncCoreTest {

    private val N = 15

    // ═══════════════════════════════════════════════════════════════════════════
    // add
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `add - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..N).map { async(Dispatchers.Default) { calc.add(1) } }.awaitAll()
            assertEquals(N, results.size)
            assertTrue(calc.current > 0)
        }
    }

    @Test
    fun `add - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> calc.add(i); assertEquals(i, calc.current) }
            }
        }.awaitAll()
    }

    @Test
    fun `add - stress 100 coroutines`() = runBlocking {
        Calculator(0).use { calc ->
            (1..100).map { async(Dispatchers.Default) { calc.add(1) } }.awaitAll()
            // accumulator += 1 is not atomic — concurrent mutations may lose updates
            // Verify the bridge doesn't crash and result is in valid range
            assertTrue(calc.current in 1..100, "Expected 1..100, got ${calc.current}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // subtract
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `subtract - concurrent same instance`() = runBlocking {
        Calculator(N).use { calc ->
            (1..N).map { async(Dispatchers.Default) { calc.subtract(1) } }.awaitAll()
            // Not atomic — verify doesn't crash and result is reasonable
            assertTrue(calc.current in (-N)..N, "Expected range (-$N..$N), got ${calc.current}")
        }
    }

    @Test
    fun `subtract - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(100).use { calc -> assertEquals(100 - i, calc.subtract(i)) }
            }
        }.awaitAll()
    }

    @Test
    fun `subtract - stress 100 coroutines`() = runBlocking {
        Calculator(100).use { calc ->
            (1..100).map { async(Dispatchers.Default) { calc.subtract(1) } }.awaitAll()
            // Not atomic — verify doesn't crash and result is reasonable
            assertTrue(calc.current in (-100)..100, "Expected range, got ${calc.current}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // multiply
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `multiply - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> assertEquals(i * 3, calc.multiply(3)) }
            }
        }.awaitAll()
    }

    @Test
    fun `multiply - concurrent same instance by 1`() = runBlocking {
        Calculator(42).use { calc ->
            (1..N).map { async(Dispatchers.Default) { calc.multiply(1) } }.awaitAll()
            assertEquals(42, calc.current)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // reset
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `reset - concurrent same instance`() = runBlocking {
        Calculator(999).use { calc ->
            (1..N).map { async(Dispatchers.Default) { calc.reset() } }.awaitAll()
            assertEquals(0, calc.current)
        }
    }

    @Test
    fun `reset - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i * 10).use { calc -> calc.reset(); assertEquals(0, calc.current) }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // divide
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `divide - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i * 10).use { calc -> assertEquals(i * 5, calc.divide(2)) }
            }
        }.awaitAll()
    }

    @Test
    fun `divide - stress separate instances`() = runBlocking {
        (1..50).map { i ->
            async(Dispatchers.Default) {
                Calculator(100).use { calc -> assertEquals(50, calc.divide(2)) }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // current property
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `current - concurrent reads same instance`() = runBlocking {
        Calculator(77).use { calc ->
            (1..N).map { async(Dispatchers.Default) { assertEquals(77, calc.current) } }.awaitAll()
        }
    }

    @Test
    fun `current - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> assertEquals(i, calc.current) }
            }
        }.awaitAll()
    }

    @Test
    fun `current - stress 100 reads`() = runBlocking {
        Calculator(42).use { calc ->
            (1..100).map { async(Dispatchers.Default) { assertEquals(42, calc.current) } }.awaitAll()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // addLong
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `addLong - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..N).map { async(Dispatchers.Default) { calc.addLong(1L) } }.awaitAll()
            assertEquals(N, results.size)
        }
    }

    @Test
    fun `addLong - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals(i.toLong(), calc.addLong(i.toLong())) }
            }
        }.awaitAll()
    }

    @Test
    fun `addLong - stress large values`() = runBlocking {
        (1..50).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals(1_000_000L, calc.addLong(1_000_000L)) }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // addDouble
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `addDouble - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals(i.toDouble(), calc.addDouble(i.toDouble()), 0.001) }
            }
        }.awaitAll()
    }

    @Test
    fun `addDouble - stress 50 coroutines`() = runBlocking {
        (1..50).map { i ->
            async(Dispatchers.Default) {
                Calculator(10).use { calc -> assertEquals(13.5, calc.addDouble(3.5), 0.001) }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // addFloat
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `addFloat - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(10).use { calc -> assertEquals(12.5f, calc.addFloat(2.5f), 0.01f) }
            }
        }.awaitAll()
    }

    @Test
    fun `addFloat - stress 50 coroutines`() = runBlocking {
        (1..50).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals(1.0f, calc.addFloat(1.0f), 0.001f) }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // addShort
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `addShort - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(10).use { calc -> assertEquals((10 + i).toShort(), calc.addShort(i.toShort())) }
            }
        }.awaitAll()
    }

    @Test
    fun `addShort - stress 50 coroutines`() = runBlocking {
        (1..50).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals(5.toShort(), calc.addShort(5.toShort())) }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // addByte
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `addByte - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(10).use { calc -> assertEquals((10 + i).toByte(), calc.addByte(i.toByte())) }
            }
        }.awaitAll()
    }

    @Test
    fun `addByte - stress 50 coroutines`() = runBlocking {
        (1..50).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals(3.toByte(), calc.addByte(3.toByte())) }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // isPositive
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `isPositive - concurrent same instance`() = runBlocking {
        Calculator(5).use { calc ->
            (1..N).map { async(Dispatchers.Default) { assertTrue(calc.isPositive()) } }.awaitAll()
        }
    }

    @Test
    fun `isPositive - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> assertTrue(calc.isPositive()) }
            }
        }.awaitAll()
    }

    @Test
    fun `isPositive - stress mixed true and false`() = runBlocking {
        (1..50).map { i ->
            async(Dispatchers.Default) {
                Calculator(if (i % 2 == 0) i else 0).use { calc ->
                    assertEquals(i % 2 == 0, calc.isPositive())
                }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // checkFlag
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `checkFlag - concurrent same instance`() = runBlocking {
        Calculator(5).use { calc ->
            (1..N).map { async(Dispatchers.Default) { assertTrue(calc.checkFlag(true)) } }.awaitAll()
        }
    }

    @Test
    fun `checkFlag - concurrent separate instances`() = runBlocking {
        (1..N).map {
            async(Dispatchers.Default) {
                Calculator(5).use { calc -> assertFalse(calc.checkFlag(false)) }
            }
        }.awaitAll()
    }

    @Test
    fun `checkFlag - stress false on zero`() = runBlocking {
        (1..50).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertFalse(calc.checkFlag(true)) }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // describe
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `describe - concurrent same instance`() = runBlocking {
        Calculator(7).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) { assertEquals("Calculator(current=7)", calc.describe()) }
            }.awaitAll()
        }
    }

    @Test
    fun `describe - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> assertEquals("Calculator(current=$i)", calc.describe()) }
            }
        }.awaitAll()
    }

    @Test
    fun `describe - stress 100 coroutines`() = runBlocking {
        Calculator(42).use { calc ->
            (1..100).map {
                async(Dispatchers.Default) { assertEquals("Calculator(current=42)", calc.describe()) }
            }.awaitAll()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // echo
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `echo - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..N).map { i ->
                async(Dispatchers.Default) { assertEquals("msg$i", calc.echo("msg$i")) }
            }.awaitAll()
        }
    }

    @Test
    fun `echo - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals("hello$i", calc.echo("hello$i")) }
            }
        }.awaitAll()
    }

    @Test
    fun `echo - stress unicode`() = runBlocking {
        (1..50).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals("café ☕", calc.echo("café ☕")) }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // concat
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `concat - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) { assertEquals("helloworld", calc.concat("hello", "world")) }
            }.awaitAll()
        }
    }

    @Test
    fun `concat - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals("a${i}b${i}", calc.concat("a$i", "b$i")) }
            }
        }.awaitAll()
    }

    @Test
    fun `concat - stress 50 coroutines`() = runBlocking {
        (1..50).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals("foobar", calc.concat("foo", "bar")) }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // label (get + set)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `label - concurrent reads same instance`() = runBlocking {
        Calculator(0).use { calc ->
            calc.label = "fixed"
            (1..N).map { async(Dispatchers.Default) { assertEquals("fixed", calc.label) } }.awaitAll()
        }
    }

    @Test
    fun `label - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.label = "label$i"
                    assertEquals("label$i", calc.label)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `label - stress set and get`() = runBlocking {
        (1..50).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.label = "stress$i"
                    assertEquals("stress$i", calc.label)
                }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // scale (get + set)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `scale - concurrent reads same instance`() = runBlocking {
        Calculator(0).use { calc ->
            calc.scale = 3.14
            (1..N).map { async(Dispatchers.Default) { assertEquals(3.14, calc.scale, 0.001) } }.awaitAll()
        }
    }

    @Test
    fun `scale - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.scale = i.toDouble()
                    assertEquals(i.toDouble(), calc.scale, 0.001)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `scale - stress set and get`() = runBlocking {
        (1..50).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.scale = i * 0.1
                    assertEquals(i * 0.1, calc.scale, 0.001)
                }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // enabled (get + set)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `enabled - concurrent reads same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..N).map { async(Dispatchers.Default) { assertTrue(calc.enabled) } }.awaitAll()
        }
    }

    @Test
    fun `enabled - concurrent separate instances toggle`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.enabled = i % 2 == 0
                    assertEquals(i % 2 == 0, calc.enabled)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `enabled - stress toggle`() = runBlocking {
        (1..50).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.enabled = false
                    assertFalse(calc.enabled)
                    calc.enabled = true
                    assertTrue(calc.enabled)
                }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // lastOperation (get + set)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `lastOperation - concurrent reads same instance`() = runBlocking {
        Calculator(0).use { calc ->
            calc.lastOperation = Operation.MULTIPLY
            (1..N).map {
                async(Dispatchers.Default) { assertEquals(Operation.MULTIPLY, calc.lastOperation) }
            }.awaitAll()
        }
    }

    @Test
    fun `lastOperation - concurrent separate instances`() = runBlocking {
        val ops = Operation.entries
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    val op = ops[i % ops.size]
                    calc.lastOperation = op
                    assertEquals(op, calc.lastOperation)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `lastOperation - stress all enum values`() = runBlocking {
        val ops = Operation.entries
        (1..50).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    val op = ops[i % ops.size]
                    calc.lastOperation = op
                    assertEquals(op, calc.lastOperation)
                }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // applyOp
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `applyOp - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(10).use { calc -> assertEquals(10 + i, calc.applyOp(Operation.ADD, i)) }
            }
        }.awaitAll()
    }

    @Test
    fun `applyOp - stress all operations`() = runBlocking {
        val ops = Operation.entries
        (1..50).map { i ->
            async(Dispatchers.Default) {
                Calculator(10).use { calc ->
                    val op = ops[i % ops.size]
                    calc.applyOp(op, 2)
                    assertTrue(true) // no crash
                }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getLastOp
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getLastOp - concurrent separate instances`() = runBlocking {
        (1..N).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.applyOp(Operation.SUBTRACT, 1)
                    assertEquals(Operation.SUBTRACT, calc.getLastOp())
                }
            }
        }.awaitAll()
    }

    @Test
    fun `getLastOp - stress after applyOp`() = runBlocking {
        (1..50).map {
            async(Dispatchers.Default) {
                Calculator(10).use { calc ->
                    calc.applyOp(Operation.MULTIPLY, 2)
                    assertEquals(Operation.MULTIPLY, calc.getLastOp())
                }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // nickname (nullable String property)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `nickname - concurrent separate instances set and get`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.nickname = "nick$i"
                    assertEquals("nick$i", calc.nickname)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `nickname - concurrent separate instances null`() = runBlocking {
        (1..N).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.nickname = "temp"
                    calc.nickname = null
                    assertEquals(null, calc.nickname)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `nickname - stress set-get-clear`() = runBlocking {
        (1..50).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.nickname = "s$i"
                    assertEquals("s$i", calc.nickname)
                    calc.nickname = null
                    assertEquals(null, calc.nickname)
                }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // divideOrNull
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `divideOrNull - concurrent separate instances non-null`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i * 10).use { calc -> assertEquals(i * 5, calc.divideOrNull(2)) }
            }
        }.awaitAll()
    }

    @Test
    fun `divideOrNull - concurrent separate instances null`() = runBlocking {
        (1..N).map {
            async(Dispatchers.Default) {
                Calculator(10).use { calc -> assertEquals(null, calc.divideOrNull(0)) }
            }
        }.awaitAll()
    }

    @Test
    fun `divideOrNull - stress mixed`() = runBlocking {
        (1..50).map { i ->
            async(Dispatchers.Default) {
                Calculator(100).use { calc ->
                    val divisor = if (i % 5 == 0) 0 else i
                    val result = calc.divideOrNull(divisor)
                    if (divisor == 0) assertEquals(null, result) else assertEquals(100 / divisor, result)
                }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // describeOrNull
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `describeOrNull - concurrent separate instances non-null`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> assertEquals("Positive($i)", calc.describeOrNull()) }
            }
        }.awaitAll()
    }

    @Test
    fun `describeOrNull - concurrent separate instances null`() = runBlocking {
        (1..N).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals(null, calc.describeOrNull()) }
            }
        }.awaitAll()
    }

    @Test
    fun `describeOrNull - stress mixed`() = runBlocking {
        (1..50).map { i ->
            async(Dispatchers.Default) {
                Calculator(if (i % 2 == 0) i else 0).use { calc ->
                    val result = calc.describeOrNull()
                    if (i % 2 == 0) assertEquals("Positive($i)", result) else assertEquals(null, result)
                }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // isPositiveOrNull
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `isPositiveOrNull - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> assertEquals(true, calc.isPositiveOrNull()) }
            }
        }.awaitAll()
    }

    @Test
    fun `isPositiveOrNull - concurrent null case`() = runBlocking {
        (1..N).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals(null, calc.isPositiveOrNull()) }
            }
        }.awaitAll()
    }

    @Test
    fun `isPositiveOrNull - stress all branches`() = runBlocking {
        (-20..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc ->
                    val result = calc.isPositiveOrNull()
                    when {
                        i > 0 -> assertEquals(true, result)
                        i < 0 -> assertEquals(false, result)
                        else -> assertEquals(null, result)
                    }
                }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // toLongOrNull
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `toLongOrNull - concurrent separate instances non-null`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> assertEquals(i.toLong(), calc.toLongOrNull()) }
            }
        }.awaitAll()
    }

    @Test
    fun `toLongOrNull - concurrent null case`() = runBlocking {
        (1..N).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals(null, calc.toLongOrNull()) }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // toDoubleOrNull
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `toDoubleOrNull - concurrent separate instances non-null`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> assertEquals(i.toDouble(), calc.toDoubleOrNull()) }
            }
        }.awaitAll()
    }

    @Test
    fun `toDoubleOrNull - concurrent null case`() = runBlocking {
        (1..N).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals(null, calc.toDoubleOrNull()) }
            }
        }.awaitAll()
    }

    @Test
    fun `toDoubleOrNull - stress 50 coroutines`() = runBlocking {
        (1..50).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> assertEquals(i.toDouble(), calc.toDoubleOrNull()) }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // toBytes
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `toBytes - concurrent same instance`() = runBlocking {
        Calculator(42).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) { assertEquals("42", String(calc.toBytes())) }
            }.awaitAll()
        }
    }

    @Test
    fun `toBytes - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> assertEquals("$i", String(calc.toBytes())) }
            }
        }.awaitAll()
    }

    @Test
    fun `toBytes - stress 50 coroutines`() = runBlocking {
        (1..50).map { i ->
            async(Dispatchers.Default) {
                Calculator(i * 10).use { calc -> assertEquals("${i * 10}", String(calc.toBytes())) }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // sumBytes
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `sumBytes - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) { assertEquals(6, calc.sumBytes(byteArrayOf(1, 2, 3))) }
            }.awaitAll()
        }
    }

    @Test
    fun `sumBytes - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals(i, calc.sumBytes(byteArrayOf(i.toByte()))) }
            }
        }.awaitAll()
    }

    @Test
    fun `sumBytes - stress empty arrays`() = runBlocking {
        (1..50).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc -> assertEquals(0, calc.sumBytes(byteArrayOf())) }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // reverseBytes
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `reverseBytes - concurrent same instance`() = runBlocking {
        Calculator(0).use { calc ->
            (1..N).map {
                async(Dispatchers.Default) {
                    assertEquals(listOf<Byte>(3, 2, 1), calc.reverseBytes(byteArrayOf(1, 2, 3)).toList())
                }
            }.awaitAll()
        }
    }

    @Test
    fun `reverseBytes - concurrent separate instances`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    val input = byteArrayOf(i.toByte(), (i + 1).toByte())
                    val reversed = calc.reverseBytes(input)
                    assertEquals(listOf((i + 1).toByte(), i.toByte()), reversed.toList())
                }
            }
        }.awaitAll()
    }

    @Test
    fun `reverseBytes - stress 50 coroutines`() = runBlocking {
        (1..50).map {
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    assertEquals(0, calc.reverseBytes(byteArrayOf()).size)
                }
            }
        }.awaitAll()
    }
}
