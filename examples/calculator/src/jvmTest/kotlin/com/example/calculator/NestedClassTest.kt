package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NestedClassTest {

    // ══════════════════════════════════════════════════════════════════════════
    // NESTED CLASSES
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `nested - Adder basic`() {
        MathSuite_Adder().use { adder ->
            assertEquals(0, adder.current)
            assertEquals(5, adder.add(5))
            assertEquals(15, adder.add(10))
        }
    }

    @Test fun `nested - Adder reset`() {
        MathSuite_Adder().use { adder ->
            adder.add(100)
            adder.reset()
            assertEquals(0, adder.current)
        }
    }

    @Test fun `nested - Multiplier basic`() {
        MathSuite_Multiplier().use { mul ->
            assertEquals(1, mul.current)
            assertEquals(5, mul.multiply(5))
            assertEquals(15, mul.multiply(3))
        }
    }

    @Test fun `nested - Multiplier reset`() {
        MathSuite_Multiplier().use { mul ->
            mul.multiply(10)
            mul.reset()
            assertEquals(1, mul.current)
        }
    }

    @Test fun `nested - Adder and Multiplier independent`() {
        MathSuite_Adder().use { adder ->
            MathSuite_Multiplier().use { mul ->
                adder.add(10)
                mul.multiply(5)
                assertEquals(10, adder.current)
                assertEquals(5, mul.current)
            }
        }
    }

    @Test fun `nested - 10 Adder instances`() {
        val adders = (1..10).map { MathSuite_Adder() }
        adders.forEachIndexed { i, adder -> adder.add(i + 1) }
        val sum = adders.sumOf { it.current }
        assertEquals(55, sum) // 1+2+...+10
        adders.forEach { it.close() }
    }

    @Test fun `nested - Adder 10K adds`() {
        MathSuite_Adder().use { adder ->
            repeat(10_000) { adder.add(1) }
            assertEquals(10_000, adder.current)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NESTED-NESTED CLASSES (MathSuite.Advanced.PowerCalc / ModCalc)
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `nested2 - PowerCalc basic`() {
        MathSuite_Advanced_PowerCalc().use { pc ->
            assertEquals(8L, pc.power(2, 3))
            assertEquals(8L, pc.current)
        }
    }
    @Test fun `nested2 - PowerCalc large`() {
        MathSuite_Advanced_PowerCalc().use { pc ->
            assertEquals(1024L, pc.power(2, 10))
        }
    }
    @Test fun `nested2 - PowerCalc zero exp`() {
        MathSuite_Advanced_PowerCalc().use { pc ->
            assertEquals(1L, pc.power(999, 0))
        }
    }
    @Test fun `nested2 - PowerCalc one exp`() {
        MathSuite_Advanced_PowerCalc().use { pc ->
            assertEquals(42L, pc.power(42, 1))
        }
    }
    @Test fun `nested2 - PowerCalc reset`() {
        MathSuite_Advanced_PowerCalc().use { pc ->
            pc.power(3, 5)
            assertEquals(243L, pc.current)
            pc.reset()
            assertEquals(1L, pc.current)
        }
    }
    @Test fun `nested2 - PowerCalc sequential`() {
        MathSuite_Advanced_PowerCalc().use { pc ->
            pc.power(2, 4)
            assertEquals(16L, pc.current)
            pc.power(3, 3)
            assertEquals(27L, pc.current)
        }
    }
    @Test fun `nested2 - ModCalc basic`() {
        MathSuite_Advanced_ModCalc().use { mc ->
            assertEquals(1, mc.mod(7, 3))
            assertEquals(0, mc.mod(10, 5))
            assertEquals(2, mc.mod(17, 5))
        }
    }
    @Test fun `nested2 - ModCalc negative`() {
        MathSuite_Advanced_ModCalc().use { mc ->
            assertEquals(-1, mc.mod(-7, 3))
        }
    }
    @Test fun `nested2 - gcd basic`() {
        MathSuite_Advanced_ModCalc().use { mc ->
            assertEquals(6, mc.gcd(12, 18))
            assertEquals(1, mc.gcd(7, 13))
            assertEquals(5, mc.gcd(15, 25))
        }
    }
    @Test fun `nested2 - gcd with zero`() {
        MathSuite_Advanced_ModCalc().use { mc ->
            assertEquals(7, mc.gcd(7, 0))
            assertEquals(13, mc.gcd(0, 13))
        }
    }
    @Test fun `nested2 - gcd negative`() {
        MathSuite_Advanced_ModCalc().use { mc ->
            assertEquals(4, mc.gcd(-12, 8))
        }
    }
    @Test fun `nested2 - gcd same`() {
        MathSuite_Advanced_ModCalc().use { mc ->
            assertEquals(42, mc.gcd(42, 42))
        }
    }
    @Test fun `nested2 - PowerCalc and ModCalc independent`() {
        MathSuite_Advanced_PowerCalc().use { pc ->
            MathSuite_Advanced_ModCalc().use { mc ->
                pc.power(2, 8)
                assertEquals(256L, pc.current)
                assertEquals(6, mc.gcd(12, 18))
            }
        }
    }
    @Test fun `nested2 - all nested levels together`() {
        MathSuite_Adder().use { adder ->
            MathSuite_Multiplier().use { mul ->
                MathSuite_Advanced_PowerCalc().use { pc ->
                    MathSuite_Advanced_ModCalc().use { mc ->
                        adder.add(10)
                        mul.multiply(5)
                        pc.power(2, 4)
                        val r = mc.gcd(adder.current, mul.current) // gcd(10, 5)
                        assertEquals(5, r)
                        assertEquals(16L, pc.current)
                    }
                }
            }
        }
    }
    @Test fun `nested2 - 10 PowerCalc instances`() {
        val calcs = (1..10).map { MathSuite_Advanced_PowerCalc() }
        calcs.forEachIndexed { i, pc -> pc.power(2, i) }
        val powers = calcs.map { it.current }
        assertEquals(listOf(1L, 2L, 4L, 8L, 16L, 32L, 64L, 128L, 256L, 512L), powers)
        calcs.forEach { it.close() }
    }
    @Test fun `nested2 - PowerCalc 1K calls`() {
        MathSuite_Advanced_PowerCalc().use { pc ->
            repeat(1_000) { pc.power(2, 3) }
            assertEquals(8L, pc.current) // last call
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OBJECT CALLBACK EDGE CASES (ObjectCallbackTest)
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `obj cb test - forEachCalc`() {
        ObjectCallbackTest().use { test ->
            val c1 = Calculator(10); val c2 = Calculator(20); val c3 = Calculator(30)
            test.addCalc(c1); test.addCalc(c2); test.addCalc(c3)
            assertEquals(3, test.count())
            val values = mutableListOf<Int>()
            test.forEachCalc { values.add(it.current) }
            assertEquals(listOf(10, 20, 30), values)
        }
    }
    @Test fun `obj cb test - forEachCalc empty`() {
        ObjectCallbackTest().use { test ->
            val values = mutableListOf<Int>()
            test.forEachCalc { values.add(it.current) }
            assertEquals(emptyList<Int>(), values)
        }
    }
    @Test fun `obj cb test - findCalc found`() {
        ObjectCallbackTest().use { test ->
            test.addCalc(Calculator(1)); test.addCalc(Calculator(42)); test.addCalc(Calculator(3))
            val found = test.findCalc { it.current == 42 }
            assertTrue(found != null)
            assertEquals(42, found!!.current)
            found.close()
        }
    }
    @Test fun `obj cb test - findCalc not found`() {
        ObjectCallbackTest().use { test ->
            test.addCalc(Calculator(1)); test.addCalc(Calculator(2))
            val found = test.findCalc { it.current == 99 }
            assertNull(found)
        }
    }
    @Test fun `obj cb test - mapCurrents`() {
        ObjectCallbackTest().use { test ->
            test.addCalc(Calculator(5)); test.addCalc(Calculator(10)); test.addCalc(Calculator(15))
            val mapped = test.mapCurrents { it.current * 2 }
            assertEquals(listOf(10, 20, 30), mapped)
        }
    }
    @Test fun `obj cb test - mapCurrents empty`() {
        ObjectCallbackTest().use { test ->
            assertEquals(emptyList<Int>(), test.mapCurrents { it.current })
        }
    }
    @Test fun `obj cb test - reduceWith sum`() {
        ObjectCallbackTest().use { test ->
            test.addCalc(Calculator(10)); test.addCalc(Calculator(20)); test.addCalc(Calculator(30))
            val sum = test.reduceWith(0) { acc, calc -> acc + calc.current }
            assertEquals(60, sum)
        }
    }
    @Test fun `obj cb test - reduceWith product`() {
        ObjectCallbackTest().use { test ->
            test.addCalc(Calculator(2)); test.addCalc(Calculator(3)); test.addCalc(Calculator(4))
            val product = test.reduceWith(1) { acc, calc -> acc * calc.current }
            assertEquals(24, product)
        }
    }
    @Test fun `obj cb test - reduceWith empty`() {
        ObjectCallbackTest().use { test ->
            assertEquals(42, test.reduceWith(42) { acc, _ -> acc })
        }
    }
    @Test fun `obj cb test - onEachWithIndex`() {
        ObjectCallbackTest().use { test ->
            test.addCalc(Calculator(10)); test.addCalc(Calculator(20))
            val pairs = mutableListOf<Pair<Int, Int>>()
            test.onEachWithIndex { idx, calc -> pairs.add(idx to calc.current) }
            assertEquals(listOf(0 to 10, 1 to 20), pairs)
        }
    }
    @Test fun `obj cb test - mutate through callback`() {
        ObjectCallbackTest().use { test ->
            val c1 = Calculator(0); val c2 = Calculator(0)
            test.addCalc(c1); test.addCalc(c2)
            test.forEachCalc { it.add(100) }
            // mutations reflect on original handles
            val values = mutableListOf<Int>()
            test.forEachCalc { values.add(it.current) }
            assertEquals(listOf(100, 100), values)
        }
    }
    @Test fun `obj cb test - 20 calcs forEach`() {
        ObjectCallbackTest().use { test ->
            val calcs = (1..20).map { Calculator(it) }
            calcs.forEach { test.addCalc(it) }
            assertEquals(20, test.count())
            val sum = test.reduceWith(0) { acc, calc -> acc + calc.current }
            assertEquals(210, sum) // 1+2+...+20
        }
    }

    // ── Object callback + nested class cross-feature ────────────────────────

    @Test fun `cross - callback receives self then use nested`() {
        Calculator(5).use { calc ->
            calc.onSelfReady { self ->
                MathSuite_Adder().use { adder ->
                    adder.add(self.current)
                    assertEquals(5, adder.current)
                }
            }
        }
    }
    @Test fun `cross - createVia then use on nested`() {
        Calculator(10).use { calc ->
            val created = calc.createVia { v ->
                Calculator(v * 3)
            }
            assertEquals(30, created.current)
            MathSuite_Advanced_PowerCalc().use { pc ->
                pc.power(created.current, 2)
                assertEquals(900L, pc.current)
            }
            created.close()
        }
    }
    @Test fun `cross - transformWith using nested adder`() {
        Calculator(7).use { a ->
            Calculator(3).use { b ->
                MathSuite_Adder().use { adder ->
                    val result = a.transformWith(b) { x, y ->
                        adder.add(x.current)
                        adder.add(y.current)
                        adder.current
                    }
                    assertEquals(10, result)
                }
            }
        }
    }
    @Test fun `cross - concurrent nested + obj callback`() {
        val threads = (1..5).map { idx ->
            Thread {
                MathSuite_Advanced_PowerCalc().use { pc ->
                    repeat(100) { pc.power(2, idx) }
                    assertEquals(1L shl idx, pc.current)
                }
                Calculator(idx).use { calc ->
                    var received = 0
                    calc.onSelfReady { received = it.current }
                    assertEquals(idx, received)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
