package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals

class GenericMonomorphTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Instance method monomorphisation — apply_transformer<T: ValueTransformer>
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `apply_transformer_doubler doubles accumulator`() {
        Calculator(5).use { calc ->
            Doubler().use { d ->
                assertEquals(10, calc.apply_transformer_doubler(d))
            }
        }
    }

    @Test fun `apply_transformer_tripler triples accumulator`() {
        Calculator(5).use { calc ->
            Tripler().use { t ->
                assertEquals(15, calc.apply_transformer_tripler(t))
            }
        }
    }

    @Test fun `apply_transformer with zero accumulator`() {
        Calculator(0).use { calc ->
            Doubler().use { d -> assertEquals(0, calc.apply_transformer_doubler(d)) }
            Tripler().use { t -> assertEquals(0, calc.apply_transformer_tripler(t)) }
        }
    }

    @Test fun `apply_transformer with negative accumulator`() {
        Calculator(-3).use { calc ->
            Doubler().use { d -> assertEquals(-6, calc.apply_transformer_doubler(d)) }
            Tripler().use { t -> assertEquals(-9, calc.apply_transformer_tripler(t)) }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Instance method monomorphisation — get_transformer_name<T: ValueTransformer>
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `get_transformer_name_doubler returns Doubler`() {
        Calculator(0).use { calc ->
            Doubler().use { d ->
                assertEquals("Doubler", calc.get_transformer_name_doubler(d))
            }
        }
    }

    @Test fun `get_transformer_name_tripler returns Tripler`() {
        Calculator(0).use { calc ->
            Tripler().use { t ->
                assertEquals("Tripler", calc.get_transformer_name_tripler(t))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Top-level function monomorphisation — transform_value<T: ValueTransformer>
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `top-level transform_value_doubler`() {
        Doubler().use { d ->
            assertEquals(20, Rustcalc.transform_value_doubler(10, d))
        }
    }

    @Test fun `top-level transform_value_tripler`() {
        Tripler().use { t ->
            assertEquals(30, Rustcalc.transform_value_tripler(10, t))
        }
    }

    @Test fun `top-level transform_value with zero`() {
        Doubler().use { d -> assertEquals(0, Rustcalc.transform_value_doubler(0, d)) }
        Tripler().use { t -> assertEquals(0, Rustcalc.transform_value_tripler(0, t)) }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge - Int MAX_VALUE with doubler overflows`() {
        Calculator(Int.MAX_VALUE).use { calc ->
            Doubler().use { d ->
                assertEquals(Int.MAX_VALUE * 2, calc.apply_transformer_doubler(d))
            }
        }
    }

    @Test fun `edge - multiple transformers on same calculator`() {
        Calculator(7).use { calc ->
            Doubler().use { d ->
                Tripler().use { t ->
                    assertEquals(14, calc.apply_transformer_doubler(d))
                    assertEquals(21, calc.apply_transformer_tripler(t))
                }
            }
        }
    }

    @Test fun `edge - transformer reuse across calculators`() {
        Doubler().use { d ->
            Calculator(3).use { calc1 -> assertEquals(6, calc1.apply_transformer_doubler(d)) }
            Calculator(10).use { calc2 -> assertEquals(20, calc2.apply_transformer_doubler(d)) }
        }
    }
}
