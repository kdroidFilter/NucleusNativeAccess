package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SealedClassTest {

    // ── ResultProcessor.processAndDescribe() tests (15 tests) ───────────────

    @Test fun `ResultProcessor processAndDescribe - positive input 5`() {
        ResultProcessor().use { rp ->
            assertEquals("Success: 10", rp.processAndDescribe(5))
        }
    }

    @Test fun `ResultProcessor processAndDescribe - zero input`() {
        ResultProcessor().use { rp ->
            assertEquals("Loading...", rp.processAndDescribe(0))
        }
    }

    @Test fun `ResultProcessor processAndDescribe - negative input starts with Error`() {
        ResultProcessor().use { rp ->
            val result = rp.processAndDescribe(-3)
            assertTrue(result.startsWith("Error:"), "Should start with 'Error:'")
            assertTrue(result.contains("-3"), "Should contain the negative number")
        }
    }

    @Test fun `ResultProcessor processAndDescribe - boundary positive 1`() {
        ResultProcessor().use { rp ->
            assertEquals("Success: 2", rp.processAndDescribe(1))
        }
    }

    @Test fun `ResultProcessor processAndDescribe - boundary negative -1`() {
        ResultProcessor().use { rp ->
            val result = rp.processAndDescribe(-1)
            assertTrue(result.startsWith("Error:"), "Should be error for negative")
            assertTrue(result.contains("-1"), "Should contain -1")
        }
    }

    @Test fun `ResultProcessor processAndDescribe - Int MAX_VALUE div 2`() {
        ResultProcessor().use { rp ->
            val max = Int.MAX_VALUE / 2
            val expected = "Success: ${max * 2}"
            assertEquals(expected, rp.processAndDescribe(max))
        }
    }

    @Test fun `ResultProcessor processAndDescribe - negative -1`() {
        ResultProcessor().use { rp ->
            val result = rp.processAndDescribe(-1)
            assertTrue(result.startsWith("Error:"), "Negative should error")
            assertTrue(result.contains("-1"))
        }
    }

    @Test fun `ResultProcessor processAndDescribe - negative -100`() {
        ResultProcessor().use { rp ->
            val result = rp.processAndDescribe(-100)
            assertTrue(result.startsWith("Error:"), "Negative should error")
            assertTrue(result.contains("-100"))
        }
    }

    @Test fun `ResultProcessor processAndDescribe - negative -999999`() {
        ResultProcessor().use { rp ->
            val result = rp.processAndDescribe(-999999)
            assertTrue(result.startsWith("Error:"), "Negative should error")
            assertTrue(result.contains("-999999"))
        }
    }

    @Test fun `ResultProcessor processAndDescribe - positive 1`() {
        ResultProcessor().use { rp ->
            assertEquals("Success: 2", rp.processAndDescribe(1))
        }
    }

    @Test fun `ResultProcessor processAndDescribe - positive 10`() {
        ResultProcessor().use { rp ->
            assertEquals("Success: 20", rp.processAndDescribe(10))
        }
    }

    @Test fun `ResultProcessor processAndDescribe - positive 100`() {
        ResultProcessor().use { rp ->
            assertEquals("Success: 200", rp.processAndDescribe(100))
        }
    }

    @Test fun `ResultProcessor processAndDescribe - positive 999`() {
        ResultProcessor().use { rp ->
            assertEquals("Success: 1998", rp.processAndDescribe(999))
        }
    }

    @Test fun `ResultProcessor processAndDescribe - positive 50`() {
        ResultProcessor().use { rp ->
            assertEquals("Success: 100", rp.processAndDescribe(50))
        }
    }

    @Test fun `ResultProcessor processAndDescribe - positive large value`() {
        ResultProcessor().use { rp ->
            assertEquals("Success: 2000", rp.processAndDescribe(1000))
        }
    }

    // ── ResultProcessor.processValue() tests (10 tests) ─────────────────────

    @Test fun `ResultProcessor processValue - positive 5 doubles to 10`() {
        ResultProcessor().use { rp ->
            assertEquals(10, rp.processValue(5))
        }
    }

    @Test fun `ResultProcessor processValue - positive 1 doubles to 2`() {
        ResultProcessor().use { rp ->
            assertEquals(2, rp.processValue(1))
        }
    }

    @Test fun `ResultProcessor processValue - positive 100 doubles to 200`() {
        ResultProcessor().use { rp ->
            assertEquals(200, rp.processValue(100))
        }
    }

    @Test fun `ResultProcessor processValue - zero returns -1`() {
        ResultProcessor().use { rp ->
            assertEquals(-1, rp.processValue(0))
        }
    }

    @Test fun `ResultProcessor processValue - negative -1 returns -1`() {
        ResultProcessor().use { rp ->
            assertEquals(-1, rp.processValue(-1))
        }
    }

    @Test fun `ResultProcessor processValue - negative -10 returns -1`() {
        ResultProcessor().use { rp ->
            assertEquals(-1, rp.processValue(-10))
        }
    }

    @Test fun `ResultProcessor processValue - negative -999 returns -1`() {
        ResultProcessor().use { rp ->
            assertEquals(-1, rp.processValue(-999))
        }
    }

    @Test fun `ResultProcessor processValue - large positive 1000000`() {
        ResultProcessor().use { rp ->
            assertEquals(2000000, rp.processValue(1000000))
        }
    }

    @Test fun `ResultProcessor processValue - positive boundary Int MAX_VALUE div 2`() {
        ResultProcessor().use { rp ->
            val half = Int.MAX_VALUE / 2
            assertEquals(half * 2, rp.processValue(half))
        }
    }

    @Test fun `ResultProcessor processValue - positive 42`() {
        ResultProcessor().use { rp ->
            assertEquals(84, rp.processValue(42))
        }
    }

    // ── Multiple processors (5 tests) ────────────────────────────────────────

    @Test fun `Two processors produce same results for processAndDescribe`() {
        ResultProcessor().use { rp1 ->
            ResultProcessor().use { rp2 ->
                assertEquals(rp1.processAndDescribe(7), rp2.processAndDescribe(7))
                assertEquals(rp1.processAndDescribe(0), rp2.processAndDescribe(0))
                assertEquals(rp1.processAndDescribe(-5), rp2.processAndDescribe(-5))
            }
        }
    }

    @Test fun `Two processors produce same results for processValue`() {
        ResultProcessor().use { rp1 ->
            ResultProcessor().use { rp2 ->
                assertEquals(rp1.processValue(12), rp2.processValue(12))
                assertEquals(rp1.processValue(-7), rp2.processValue(-7))
                assertEquals(rp1.processValue(0), rp2.processValue(0))
            }
        }
    }

    @Test fun `Processor after multiple sequential calls`() {
        ResultProcessor().use { rp ->
            rp.processAndDescribe(3)
            rp.processAndDescribe(0)
            rp.processAndDescribe(-2)
            val result = rp.processAndDescribe(8)
            assertEquals("Success: 16", result)
        }
    }

    @Test fun `Multiple processors in loop - 20 iterations`() {
        repeat(20) {
            ResultProcessor().use { rp ->
                val describe = rp.processAndDescribe(it)
                val value = rp.processValue(it)
                if (it > 0) {
                    assertTrue(describe.startsWith("Success:"))
                    assertEquals(it * 2, value)
                } else if (it == 0) {
                    assertEquals("Loading...", describe)
                    assertEquals(-1, value)
                }
            }
        }
    }

    @Test fun `Processor consistency across multiple operations`() {
        ResultProcessor().use { rp ->
            val describe5 = rp.processAndDescribe(5)
            val value5 = rp.processValue(5)
            assertEquals("Success: 10", describe5)
            assertEquals(10, value5)

            val describe0 = rp.processAndDescribe(0)
            val value0 = rp.processValue(0)
            assertEquals("Loading...", describe0)
            assertEquals(-1, value0)

            val describeNeg = rp.processAndDescribe(-3)
            val valueNeg = rp.processValue(-3)
            assertTrue(describeNeg.startsWith("Error:"))
            assertEquals(-1, valueNeg)
        }
    }
}
