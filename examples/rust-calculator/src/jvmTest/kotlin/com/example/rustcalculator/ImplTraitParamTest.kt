package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E tests for impl Trait as function PARAMETERS (not return types).
 * Verifies that the bridge resolves well-known traits (ToString, Into, AsRef)
 * to concrete types and passes them correctly.
 */
class ImplTraitParamTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. impl ToString parameter
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `greet_impl with simple string`() {
        assertEquals("Hello, World!", Rustcalc.greet_impl("World"))
    }

    @Test fun `greet_impl with empty string`() {
        assertEquals("Hello, !", Rustcalc.greet_impl(""))
    }

    @Test fun `greet_impl with unicode`() {
        assertEquals("Hello, 日本語!", Rustcalc.greet_impl("日本語"))
    }

    @Test fun `greet_impl with emoji`() {
        assertEquals("Hello, 🎉!", Rustcalc.greet_impl("🎉"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. impl Into<String> parameter
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `into_upper with lowercase`() {
        assertEquals("HELLO", Rustcalc.into_upper("hello"))
    }

    @Test fun `into_upper with empty`() {
        assertEquals("", Rustcalc.into_upper(""))
    }

    @Test fun `into_upper with mixed case`() {
        assertEquals("HELLO WORLD", Rustcalc.into_upper("Hello World"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. impl AsRef<str> parameter
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `count_chars with ascii`() {
        assertEquals(5, Rustcalc.count_chars("hello"))
    }

    @Test fun `count_chars with empty`() {
        assertEquals(0, Rustcalc.count_chars(""))
    }

    @Test fun `count_chars with unicode`() {
        // UTF-8 byte count, not char count
        val text = "日本"
        assertEquals(text.toByteArray(Charsets.UTF_8).size, Rustcalc.count_chars(text))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 4. Box<dyn Trait> parameter (ownership transfer)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `consume_describable takes ownership`() {
        val desc = Rustcalc.create_describable(42) as DynDescribable
        val result = Rustcalc.consume_describable(desc)
        assertEquals("consumed: Calculator(current=42, label=)", result)
    }

    @Test fun `consume_describable with zero`() {
        val desc = Rustcalc.create_describable(0) as DynDescribable
        assertEquals("consumed: Calculator(current=0, label=)", Rustcalc.consume_describable(desc))
    }

    @Test fun `consume_describable with negative`() {
        val desc = Rustcalc.create_describable(-99) as DynDescribable
        assertEquals("consumed: Calculator(current=-99, label=)", Rustcalc.consume_describable(desc))
    }
}
