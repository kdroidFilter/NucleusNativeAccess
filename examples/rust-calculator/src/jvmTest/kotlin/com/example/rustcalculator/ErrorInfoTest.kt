package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorInfoTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // get_error_info — returns different variants based on accumulator
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `get_error_info zero returns None`() {
        Calculator(0).use { calc ->
            calc.get_error_info().use { info ->
                assertTrue(info is ErrorInfo.None)
                assertEquals(ErrorInfo.Tag.None, info.tag)
            }
        }
    }

    @Test fun `get_error_info negative returns DeviceError with two strings`() {
        Calculator(-5).use { calc ->
            calc.get_error_info().use { info ->
                assertTrue(info is ErrorInfo.DeviceError)
                val de = info as ErrorInfo.DeviceError
                assertEquals("calculator", de.value0)
                assertEquals("negative value: -5", de.value1)
            }
        }
    }

    @Test fun `get_error_info large returns PropertyError with three fields`() {
        Calculator(1500).use { calc ->
            calc.get_error_info().use { info ->
                assertTrue(info is ErrorInfo.PropertyError)
                val pe = info as ErrorInfo.PropertyError
                assertEquals("accumulator", pe.value0)
                assertEquals(1500, pe.value1)
                assertEquals("value too large", pe.value2)
            }
        }
    }

    @Test fun `get_error_info medium returns CodedMessage with int and string`() {
        Calculator(200).use { calc ->
            calc.get_error_info().use { info ->
                assertTrue(info is ErrorInfo.CodedMessage)
                val cm = info as ErrorInfo.CodedMessage
                assertEquals(200, cm.value0)
                assertEquals("code_200", cm.value1)
            }
        }
    }

    @Test fun `get_error_info small positive returns Simple`() {
        Calculator(42).use { calc ->
            calc.get_error_info().use { info ->
                assertTrue(info is ErrorInfo.Simple)
                assertEquals("ok: 42", (info as ErrorInfo.Simple).value)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Factory methods — construct from Kotlin side
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `factory DeviceError two strings`() {
        ErrorInfo.deviceError("device1", "failed to open").use { info ->
            assertTrue(info is ErrorInfo.DeviceError)
            assertEquals("device1", info.value0)
            assertEquals("failed to open", info.value1)
            assertEquals(ErrorInfo.Tag.DeviceError, info.tag)
        }
    }

    @Test fun `factory PropertyError three fields`() {
        ErrorInfo.propertyError("brightness", 75, "out of range").use { info ->
            assertTrue(info is ErrorInfo.PropertyError)
            assertEquals("brightness", info.value0)
            assertEquals(75, info.value1)
            assertEquals("out of range", info.value2)
            assertEquals(ErrorInfo.Tag.PropertyError, info.tag)
        }
    }

    @Test fun `factory CodedMessage int and string`() {
        ErrorInfo.codedMessage(404, "not found").use { info ->
            assertTrue(info is ErrorInfo.CodedMessage)
            assertEquals(404, info.value0)
            assertEquals("not found", info.value1)
            assertEquals(ErrorInfo.Tag.CodedMessage, info.tag)
        }
    }

    @Test fun `factory Simple single string`() {
        ErrorInfo.simple("hello").use { info ->
            assertTrue(info is ErrorInfo.Simple)
            assertEquals("hello", info.value)
        }
    }

    @Test fun `factory None`() {
        ErrorInfo.none().use { info ->
            assertTrue(info is ErrorInfo.None)
            assertEquals(ErrorInfo.Tag.None, info.tag)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge - DeviceError with empty strings`() {
        ErrorInfo.deviceError("", "").use { info ->
            assertTrue(info is ErrorInfo.DeviceError)
            assertEquals("", info.value0)
            assertEquals("", info.value1)
        }
    }

    @Test fun `edge - DeviceError with unicode`() {
        ErrorInfo.deviceError("カメラ", "エラー 🔥").use { info ->
            assertTrue(info is ErrorInfo.DeviceError)
            assertEquals("カメラ", info.value0)
            assertEquals("エラー 🔥", info.value1)
        }
    }

    @Test fun `edge - PropertyError with zero int`() {
        ErrorInfo.propertyError("prop", 0, "msg").use { info ->
            assertTrue(info is ErrorInfo.PropertyError)
            assertEquals(0, info.value1)
        }
    }

    @Test fun `edge - PropertyError with MAX_VALUE`() {
        ErrorInfo.propertyError("prop", Int.MAX_VALUE, "overflow").use { info ->
            assertTrue(info is ErrorInfo.PropertyError)
            assertEquals(Int.MAX_VALUE, info.value1)
        }
    }

    @Test fun `edge - PropertyError with MIN_VALUE`() {
        ErrorInfo.propertyError("prop", Int.MIN_VALUE, "underflow").use { info ->
            assertTrue(info is ErrorInfo.PropertyError)
            assertEquals(Int.MIN_VALUE, info.value1)
        }
    }

    @Test fun `edge - CodedMessage with negative code`() {
        ErrorInfo.codedMessage(-1, "negative").use { info ->
            assertTrue(info is ErrorInfo.CodedMessage)
            assertEquals(-1, info.value0)
            assertEquals("negative", info.value1)
        }
    }

    @Test fun `edge - PropertyError with empty strings and zero`() {
        ErrorInfo.propertyError("", 0, "").use { info ->
            assertTrue(info is ErrorInfo.PropertyError)
            assertEquals("", info.value0)
            assertEquals(0, info.value1)
            assertEquals("", info.value2)
        }
    }

    @Test fun `edge - DeviceError with long strings`() {
        val longStr = "x".repeat(10_000)
        ErrorInfo.deviceError(longStr, longStr).use { info ->
            assertTrue(info is ErrorInfo.DeviceError)
            assertEquals(longStr, info.value0)
            assertEquals(longStr, info.value1)
        }
    }

    @Test fun `edge - all tags cycle`() {
        ErrorInfo.deviceError("a", "b").use { assertEquals(ErrorInfo.Tag.DeviceError, it.tag) }
        ErrorInfo.propertyError("a", 1, "b").use { assertEquals(ErrorInfo.Tag.PropertyError, it.tag) }
        ErrorInfo.codedMessage(1, "a").use { assertEquals(ErrorInfo.Tag.CodedMessage, it.tag) }
        ErrorInfo.simple("a").use { assertEquals(ErrorInfo.Tag.Simple, it.tag) }
        ErrorInfo.none().use { assertEquals(ErrorInfo.Tag.None, it.tag) }
    }

    @Test fun `edge - lifecycle create and close many`() {
        repeat(50) { i ->
            ErrorInfo.deviceError("dev$i", "err$i").use { info ->
                assertTrue(info is ErrorInfo.DeviceError)
                assertEquals("dev$i", info.value0)
                assertEquals("err$i", info.value1)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K factory DeviceError calls`() {
        repeat(100_000) {
            ErrorInfo.deviceError("dev", "err").use { info ->
                assertTrue(info is ErrorInfo.DeviceError)
            }
        }
    }

    @Test fun `load - 100K factory PropertyError calls`() {
        repeat(100_000) {
            ErrorInfo.propertyError("prop", 42, "msg").use { info ->
                assertTrue(info is ErrorInfo.PropertyError)
            }
        }
    }

    @Test fun `load - 100K get_error_info calls`() {
        Calculator(-1).use { calc ->
            repeat(100_000) {
                calc.get_error_info().use { info ->
                    assertTrue(info is ErrorInfo.DeviceError)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrency tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 10K factory DeviceError`() {
        val threads = (1..10).map { tid ->
            Thread {
                repeat(10_000) {
                    ErrorInfo.deviceError("dev$tid", "err$tid").use { info ->
                        assertTrue(info is ErrorInfo.DeviceError)
                        assertEquals("dev$tid", info.value0)
                        assertEquals("err$tid", info.value1)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K factory PropertyError`() {
        val threads = (1..10).map { tid ->
            Thread {
                repeat(10_000) {
                    ErrorInfo.propertyError("p$tid", tid, "m$tid").use { info ->
                        assertTrue(info is ErrorInfo.PropertyError)
                        assertEquals("p$tid", info.value0)
                        assertEquals(tid, info.value1)
                        assertEquals("m$tid", info.value2)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K get_error_info on separate instances`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid * -10).use { calc ->
                    repeat(10_000) {
                        calc.get_error_info().use { info ->
                            assertTrue(info is ErrorInfo.DeviceError)
                        }
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
