package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class CoreTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor + Int arithmetic
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `constructor with Int parameter`() {
        Calculator(42).use { calc -> assertEquals(42, calc.current) }
    }

    @Test fun `constructor with zero`() {
        Calculator(0).use { calc -> assertEquals(0, calc.current) }
    }

    @Test fun `add returns accumulated value`() {
        Calculator(0).use { calc ->
            assertEquals(5, calc.add(5))
            assertEquals(8, calc.add(3))
            assertEquals(8, calc.current)
        }
    }

    @Test fun `add with negative values`() {
        Calculator(10).use { calc -> assertEquals(7, calc.add(-3)) }
    }

    @Test fun `subtract returns accumulated value`() {
        Calculator(10).use { calc ->
            assertEquals(7, calc.subtract(3))
            assertEquals(7, calc.current)
        }
    }

    @Test fun `multiply returns accumulated value`() {
        Calculator(4).use { calc -> assertEquals(12, calc.multiply(3)) }
    }

    @Test fun `multiply by zero`() {
        Calculator(100).use { calc -> assertEquals(0, calc.multiply(0)) }
    }

    @Test fun `reset clears accumulator`() {
        Calculator(0).use { calc ->
            calc.add(42)
            calc.reset()
            assertEquals(0, calc.current)
        }
    }

    @Test fun `val property current reads correctly`() {
        Calculator(99).use { calc ->
            assertEquals(99, calc.current)
            calc.add(1)
            assertEquals(100, calc.current)
        }
    }

    @Test fun `chain multiple operations`() {
        Calculator(0).use { calc ->
            calc.add(10)
            calc.multiply(3)
            calc.subtract(5)
            assertEquals(25, calc.current)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // String type: param, return, edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `describe returns formatted string`() {
        Calculator(7).use { calc -> assertEquals("Calculator(current=7)", calc.describe()) }
    }

    @Test fun `echo returns same string`() {
        Calculator(0).use { calc -> assertEquals("hello", calc.echo("hello")) }
    }

    @Test fun `echo empty string`() {
        Calculator(0).use { calc -> assertEquals("", calc.echo("")) }
    }

    @Test fun `echo unicode string`() {
        Calculator(0).use { calc -> assertEquals("café ☕ 日本語", calc.echo("café ☕ 日本語")) }
    }

    @Test fun `concat two strings`() {
        Calculator(0).use { calc -> assertEquals("helloworld", calc.concat("hello", "world")) }
    }

    @Test fun `concat with empty strings`() {
        Calculator(0).use { calc ->
            assertEquals("hello", calc.concat("hello", ""))
            assertEquals("world", calc.concat("", "world"))
            assertEquals("", calc.concat("", ""))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // All primitive types: Long, Double, Float, Short, Byte, Boolean
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `Long param and return`() {
        Calculator(10).use { calc -> assertEquals(15L, calc.add_long(5L)) }
    }

    @Test fun `Long with large values`() {
        Calculator(0).use { calc -> assertEquals(1_000_000L, calc.add_long(1_000_000L)) }
    }

    @Test fun `Double param and return`() {
        Calculator(10).use { calc -> assertEquals(13.5, calc.add_double(3.5), 0.001) }
    }

    @Test fun `Float param and return`() {
        Calculator(10).use { calc -> assertEquals(12.5f, calc.add_float(2.5f), 0.01f) }
    }

    @Test fun `Short param and return`() {
        Calculator(10).use { calc -> assertEquals(15.toShort(), calc.add_short(5.toShort())) }
    }

    @Test fun `Byte param and return`() {
        Calculator(10).use { calc -> assertEquals(13.toByte(), calc.add_byte(3.toByte())) }
    }

    @Test fun `Boolean return true`() {
        Calculator(5).use { calc -> assertTrue(calc.is_positive()) }
    }

    @Test fun `Boolean return false`() {
        Calculator(0).use { calc -> assertFalse(calc.is_positive()) }
    }

    @Test fun `Boolean param true`() {
        Calculator(5).use { calc -> assertTrue(calc.check_flag(true)) }
    }

    @Test fun `Boolean param false`() {
        Calculator(5).use { calc -> assertFalse(calc.check_flag(false)) }
    }

    @Test fun `Boolean both false when accumulator zero`() {
        Calculator(0).use { calc -> assertFalse(calc.check_flag(true)) }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Exception propagation
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `divide works normally`() {
        Calculator(10).use { calc ->
            assertEquals(5, calc.divide(2))
            assertEquals(5, calc.current)
        }
    }

    @Test fun `divide by zero throws KotlinNativeException`() {
        Calculator(10).use { calc ->
            val ex = assertFailsWith<KotlinNativeException> { calc.divide(0) }
            assertTrue(ex.message!!.contains("Division by zero"), "Expected 'Division by zero' but got: ${ex.message}")
        }
    }

    @Test fun `calculator works normally after exception`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            assertEquals(15, calc.add(5))
            assertEquals(15, calc.current)
        }
    }

    @Test fun `multiple exceptions in sequence`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            assertEquals(5, calc.divide(2))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Mutable properties (var)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `var String property set and get`() {
        Calculator(0).use { calc ->
            assertEquals("", calc.label)
            calc.label = "test"
            assertEquals("test", calc.label)
        }
    }

    @Test fun `var String property unicode`() {
        Calculator(0).use { calc ->
            calc.label = "日本語テスト"
            assertEquals("日本語テスト", calc.label)
        }
    }

    @Test fun `var Double property set and get`() {
        Calculator(0).use { calc ->
            assertEquals(1.0, calc.scale, 0.001)
            calc.scale = 2.5
            assertEquals(2.5, calc.scale, 0.001)
        }
    }

    @Test fun `var Boolean property set and get`() {
        Calculator(0).use { calc ->
            assertTrue(calc.enabled)
            calc.enabled = false
            assertFalse(calc.enabled)
            calc.enabled = true
            assertTrue(calc.enabled)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge prim - Int MAX_VALUE`() {
        Calculator(Int.MAX_VALUE).use { calc -> assertEquals(Int.MAX_VALUE, calc.current) }
    }

    @Test fun `edge prim - Int MIN_VALUE`() {
        Calculator(Int.MIN_VALUE).use { calc -> assertEquals(Int.MIN_VALUE, calc.current) }
    }

    @Test fun `edge str - very long string`() {
        Calculator(0).use { calc ->
            val long = "A".repeat(10_000)
            assertEquals(long, calc.echo(long))
        }
    }

    @Test fun `edge str - emoji string`() {
        Calculator(0).use { calc ->
            assertEquals("🔥💯🚀🎉", calc.echo("🔥💯🚀🎉"))
        }
    }

    @Test fun `edge str - null char in string`() {
        Calculator(0).use { calc ->
            calc.label = "before"
            assertEquals("before", calc.label)
        }
    }

    @Test fun `edge prim - Long MAX_VALUE`() {
        Calculator(0).use { calc ->
            assertEquals(Long.MAX_VALUE, calc.add_long(Long.MAX_VALUE))
        }
    }

    @Test fun `edge prim - Double NaN`() {
        Calculator(0).use { calc ->
            val result = calc.add_double(Double.NaN)
            assertTrue(result.isNaN())
        }
    }

    @Test fun `edge prim - Double Infinity`() {
        Calculator(0).use { calc ->
            val result = calc.add_double(Double.POSITIVE_INFINITY)
            assertEquals(Double.POSITIVE_INFINITY, result)
        }
    }

    @Test fun `edge exc - fail_always throws`() {
        Calculator(0).use { calc ->
            val ex = assertFailsWith<KotlinNativeException> { calc.fail_always() }
            assertTrue(ex.message!!.contains("Intentional error"))
        }
    }

    @Test fun `edge exc - panic_always throws`() {
        Calculator(0).use { calc ->
            assertFailsWith<RuntimeException> { calc.panic_always() }
        }
    }

    @Test fun `edge obj - lifecycle create use close`() {
        repeat(100) { i ->
            Calculator(i).use { calc ->
                assertEquals(i, calc.current)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K add calls single instance`() {
        Calculator(0).use { calc ->
            repeat(100_000) { calc.add(1) }
            assertEquals(100_000, calc.current)
        }
    }

    @Test fun `load - 100K describe calls`() {
        Calculator(42).use { calc ->
            repeat(100_000) {
                assertEquals("Calculator(current=42)", calc.describe())
            }
        }
    }

    @Test fun `load - 100K echo calls`() {
        Calculator(0).use { calc ->
            repeat(100_000) {
                assertEquals("test", calc.echo("test"))
            }
        }
    }

    @Test fun `load - 100K property reads`() {
        Calculator(42).use { calc ->
            repeat(100_000) {
                assertEquals(42, calc.current)
            }
        }
    }

    @Test fun `load - 100K label set and get`() {
        Calculator(0).use { calc ->
            repeat(100_000) {
                calc.label = "x"
                assertEquals("x", calc.label)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrency tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 10K add on separate instances`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(0).use { calc ->
                    repeat(10_000) { calc.add(1) }
                    assertEquals(10_000, calc.current)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K describe`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid).use { calc ->
                    repeat(10_000) {
                        val desc = calc.describe()
                        assertTrue(desc.contains("$tid"))
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K echo`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(0).use { calc ->
                    repeat(10_000) {
                        assertEquals("t$tid", calc.echo("t$tid"))
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K property set and get`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(0).use { calc ->
                    repeat(10_000) {
                        calc.label = "t$tid"
                        assertEquals("t$tid", calc.label)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
