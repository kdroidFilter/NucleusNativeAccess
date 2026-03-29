package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObjectTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase 2: Companion object
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `companion fun returning String`() {
        assertEquals("2.0", Calculator.version())
    }

    @Test
    fun `companion fun returning object`() {
        Calculator.create(42).use { calc ->
            assertEquals(42, calc.current)
            calc.add(8)
            assertEquals(50, calc.current)
        }
    }

    @Test
    fun `companion fun with Int param`() {
        Calculator.create(100).use { calc ->
            assertEquals(100, calc.current)
        }
    }

    @Test
    fun `companion var property read and write`() {
        val before = Calculator.instanceCount
        Calculator.create(0).close()
        assertEquals(before + 1, Calculator.instanceCount)
    }

    @Test
    fun `companion val property`() {
        assertEquals("2.0", Calculator.VERSION)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase 2: Object types (class-as-param, class-as-return)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `object return - create returns usable Calculator`() {
        CalculatorManager().use { mgr ->
            mgr.create("a", 10).use { calc ->
                assertEquals(10, calc.current)
                assertEquals(15, calc.add(5))
            }
        }
    }

    @Test
    fun `object return - get returns Calculator`() {
        CalculatorManager().use { mgr ->
            mgr.create("x", 42)
            mgr.get("x").use { calc ->
                assertEquals(42, calc.current)
            }
        }
    }

    @Test
    fun `object return - get unknown returns default`() {
        CalculatorManager().use { mgr ->
            mgr.get("nonexistent").use { calc ->
                assertEquals(0, calc.current)
            }
        }
    }

    @Test
    fun `object as parameter`() {
        CalculatorManager().use { mgr ->
            Calculator(0).use { calc ->
                assertEquals(7, mgr.addWith(calc, 7))
                assertEquals(7, calc.current)
            }
        }
    }

    @Test
    fun `object as parameter - string method delegation`() {
        CalculatorManager().use { mgr ->
            Calculator(99).use { calc ->
                assertEquals("Calculator(current=99)", mgr.describe(calc))
            }
        }
    }

    @Test
    fun `multiple objects from same manager`() {
        CalculatorManager().use { mgr ->
            mgr.create("a", 1).use { a ->
                mgr.create("b", 2).use { b ->
                    assertEquals(1, a.current)
                    assertEquals(2, b.current)
                    assertEquals(2, mgr.count())
                }
            }
        }
    }

    @Test
    fun `returned object survives source operations`() {
        CalculatorManager().use { mgr ->
            val calc = mgr.create("test", 10)
            mgr.create("other", 99) // create another to ensure no interference
            assertEquals(10, calc.current)
            calc.add(5)
            assertEquals(15, calc.current)
            calc.close()
        }
    }

    @Test
    fun `companion object returns usable object`() {
        Calculator.create(77).use { calc ->
            assertEquals(77, calc.current)
            calc.add(3)
            assertEquals(80, calc.current)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle: AutoCloseable, close(), double-close safety
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `use block auto-closes`() {
        Calculator(0).use { calc ->
            calc.add(1)
            assertEquals(1, calc.current)
        }
        // No crash = success (Cleaner handles cleanup)
    }

    @Test
    fun `explicit close does not crash`() {
        val calc = Calculator(0)
        calc.add(1)
        calc.close()
        // No crash = success
    }

    @Test
    fun `double close does not crash`() {
        val calc = Calculator(0)
        calc.close()
        calc.close() // Second close should be safe (runCatching)
    }

    @Test
    fun `manager double close does not crash`() {
        val mgr = CalculatorManager()
        mgr.close()
        mgr.close()
    }

}
