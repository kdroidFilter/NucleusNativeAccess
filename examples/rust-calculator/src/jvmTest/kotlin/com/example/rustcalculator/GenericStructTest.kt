package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals

class GenericStructTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Processor_Doubler — generic struct monomorphised with Doubler
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `Processor_Doubler constructs and processes`() {
        Processor_Doubler(Doubler(), 10).use { p ->
            // Doubler doubles the input, then adds offset
            assertEquals(30, p.process(10)) // 10*2 + 10
        }
    }

    @Test fun `Processor_Doubler name includes transformer and offset`() {
        Processor_Doubler(Doubler(), 5).use { p ->
            assertEquals("Processor(Doubler+5)", p.name())
        }
    }

    @Test fun `Processor_Doubler offset property`() {
        Processor_Doubler(Doubler(), 0).use { p ->
            assertEquals(0, p.offset)
            p.offset = 42
            assertEquals(42, p.offset)
        }
    }

    @Test fun `Processor_Doubler process with updated offset`() {
        Processor_Doubler(Doubler(), 0).use { p ->
            assertEquals(20, p.process(10)) // 10*2 + 0
            p.offset = 100
            assertEquals(120, p.process(10)) // 10*2 + 100
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Processor_Tripler — generic struct monomorphised with Tripler
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `Processor_Tripler constructs and processes`() {
        Processor_Tripler(Tripler(), 5).use { p ->
            // Tripler triples the input, then adds offset
            assertEquals(35, p.process(10)) // 10*3 + 5
        }
    }

    @Test fun `Processor_Tripler name includes transformer and offset`() {
        Processor_Tripler(Tripler(), 7).use { p ->
            assertEquals("Processor(Tripler+7)", p.name())
        }
    }

    @Test fun `Processor_Tripler offset property`() {
        Processor_Tripler(Tripler(), 99).use { p ->
            assertEquals(99, p.offset)
            p.offset = 0
            assertEquals(0, p.offset)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge - zero input and zero offset`() {
        Processor_Doubler(Doubler(), 0).use { p ->
            assertEquals(0, p.process(0))
        }
    }

    @Test fun `edge - negative values`() {
        Processor_Tripler(Tripler(), -10).use { p ->
            assertEquals(-16, p.process(-2)) // -2*3 + (-10) = -16
        }
    }

    @Test fun `edge - multiple processors coexist`() {
        Processor_Doubler(Doubler(), 1).use { pd ->
            Processor_Tripler(Tripler(), 2).use { pt ->
                assertEquals(21, pd.process(10)) // 10*2 + 1
                assertEquals(32, pt.process(10)) // 10*3 + 2
            }
        }
    }
}
