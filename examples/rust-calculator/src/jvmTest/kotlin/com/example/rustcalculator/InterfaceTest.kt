package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InterfaceTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Describable trait
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `describe_self returns formatted string`() {
        Calculator(42).use { calc ->
            val desc = calc.describe_self()
            assertTrue(desc.contains("42"), "Should contain accumulator value")
        }
    }

    @Test fun `describe_self with label`() {
        Calculator(0).use { calc ->
            calc.label = "test"
            val desc = calc.describe_self()
            assertTrue(desc.contains("test"), "Should contain label")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Resettable trait
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `reset_to_default clears state`() {
        Calculator(0).use { calc ->
            calc.add(100)
            calc.label = "test"
            calc.scale = 2.5
            calc.enabled = false
            calc.reset_to_default()
            assertEquals(0, calc.current)
            assertEquals("", calc.label)
            assertEquals(1.0, calc.scale, 0.001)
            assertTrue(calc.enabled)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Measurable trait
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `measure returns scaled value`() {
        Calculator(10).use { calc ->
            assertEquals(10.0, calc.measure(), 0.001)
            calc.scale = 2.5
            assertEquals(25.0, calc.measure(), 0.001)
        }
    }

    @Test fun `unit returns constant string`() {
        Calculator(0).use { calc ->
            assertEquals("units", calc.unit())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Polymorphism via interfaces
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `Calculator is Describable`() {
        Calculator(5).use { calc ->
            val describable: Describable = calc
            assertTrue(describable.describe_self().contains("5"))
        }
    }

    @Test fun `Calculator is Measurable`() {
        Calculator(7).use { calc ->
            val measurable: Measurable = calc
            assertEquals(7.0, measurable.measure(), 0.001)
        }
    }

    @Test fun `Calculator is Resettable`() {
        Calculator(99).use { calc ->
            val resettable: Resettable = calc
            resettable.reset_to_default()
            assertEquals(0, calc.current)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // &dyn Trait param functions (registry-based handle passing)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `dyn - describe_trait_object returns description`() {
        val obj = Rustcalc.create_describable(42) as DynDescribable
        val desc = Rustcalc.describe_trait_object(obj)
        assertTrue(desc.contains("42"), "Should contain accumulator value, got: $desc")
    }

    @Test fun `dyn - measure_trait_object returns measurement with unit`() {
        val obj = Rustcalc.create_measurable(10) as DynMeasurable
        val result = Rustcalc.measure_trait_object(obj)
        assertTrue(result.contains("10"), "Should contain measurement, got: $result")
        assertTrue(result.contains("units"), "Should contain unit, got: $result")
    }

    @Test fun `dyn - reset_trait_object via &mut dyn Resettable`() {
        val obj = Rustcalc.create_resettable(99) as DynResettable
        Rustcalc.reset_trait_object(obj)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // &dyn Trait edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge dyn - describe with zero value`() {
        val obj = Rustcalc.create_describable(0) as DynDescribable
        val desc = Rustcalc.describe_trait_object(obj)
        assertTrue(desc.contains("0"), "Should contain 0, got: $desc")
    }

    @Test fun `edge dyn - describe with negative value`() {
        val obj = Rustcalc.create_describable(-999) as DynDescribable
        val desc = Rustcalc.describe_trait_object(obj)
        assertTrue(desc.contains("-999"), "Should contain -999, got: $desc")
    }

    @Test fun `edge dyn - describe with Int MAX_VALUE`() {
        val obj = Rustcalc.create_describable(Int.MAX_VALUE) as DynDescribable
        val desc = Rustcalc.describe_trait_object(obj)
        assertTrue(desc.contains("${Int.MAX_VALUE}"), "Should contain MAX_VALUE, got: $desc")
    }

    @Test fun `edge dyn - describe with Int MIN_VALUE`() {
        val obj = Rustcalc.create_describable(Int.MIN_VALUE) as DynDescribable
        val desc = Rustcalc.describe_trait_object(obj)
        assertTrue(desc.contains("${Int.MIN_VALUE}"), "Should contain MIN_VALUE, got: $desc")
    }

    @Test fun `edge dyn - measure with zero`() {
        val obj = Rustcalc.create_measurable(0) as DynMeasurable
        val result = Rustcalc.measure_trait_object(obj)
        assertTrue(result.contains("0"), "Should contain 0, got: $result")
    }

    @Test fun `edge dyn - multiple describe calls on same object`() {
        val obj = Rustcalc.create_describable(7) as DynDescribable
        repeat(10) {
            val desc = Rustcalc.describe_trait_object(obj)
            assertTrue(desc.contains("7"), "Call $it failed, got: $desc")
        }
    }

    @Test fun `edge dyn - multiple different trait objects`() {
        val objs = (1..5).map { Rustcalc.create_describable(it * 10) as DynDescribable }
        objs.forEachIndexed { idx, obj ->
            val desc = Rustcalc.describe_trait_object(obj)
            assertTrue(desc.contains("${(idx + 1) * 10}"), "Object $idx failed, got: $desc")
        }
    }

    @Test fun `edge dyn - reset then describe on different handles`() {
        val resettable = Rustcalc.create_resettable(50) as DynResettable
        val describable = Rustcalc.create_describable(100) as DynDescribable
        Rustcalc.reset_trait_object(resettable)
        val desc = Rustcalc.describe_trait_object(describable)
        assertTrue(desc.contains("100"), "Describable should be unaffected, got: $desc")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // &dyn Trait load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K describe_trait_object calls`() {
        val obj = Rustcalc.create_describable(42) as DynDescribable
        repeat(100_000) {
            val desc = Rustcalc.describe_trait_object(obj)
            assertTrue(desc.contains("42"))
        }
    }

    @Test fun `load - 100K measure_trait_object calls`() {
        val obj = Rustcalc.create_measurable(7) as DynMeasurable
        repeat(100_000) {
            val result = Rustcalc.measure_trait_object(obj)
            assertTrue(result.contains("7"))
        }
    }

    @Test fun `load - 100K reset_trait_object calls`() {
        val obj = Rustcalc.create_resettable(99) as DynResettable
        repeat(100_000) {
            Rustcalc.reset_trait_object(obj)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // &dyn Trait concurrency tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 10K describe_trait_object`() {
        val threads = (1..10).map { tid ->
            Thread {
                val obj = Rustcalc.create_describable(tid * 100) as DynDescribable
                repeat(10_000) {
                    val desc = Rustcalc.describe_trait_object(obj)
                    assertTrue(desc.contains("${tid * 100}"), "Thread $tid failed, got: $desc")
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K measure_trait_object`() {
        val threads = (1..10).map { tid ->
            Thread {
                val obj = Rustcalc.create_measurable(tid) as DynMeasurable
                repeat(10_000) {
                    val result = Rustcalc.measure_trait_object(obj)
                    assertTrue(result.contains("$tid"))
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TraitConsumer: dyn Trait as constructor and method params
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `dyn param - constructor with &dyn Describable`() {
        val source = Rustcalc.create_describable(42) as DynDescribable
        TraitConsumer(source).use { consumer ->
            val desc = consumer.description
            assertTrue(desc.contains("42"), "Should contain source value, got: $desc")
        }
    }

    @Test fun `dyn param - update_from method with &dyn Describable`() {
        val source1 = Rustcalc.create_describable(10) as DynDescribable
        val source2 = Rustcalc.create_describable(99) as DynDescribable
        TraitConsumer(source1).use { consumer ->
            assertTrue(consumer.description.contains("10"))
            consumer.update_from(source2)
            assertTrue(consumer.description.contains("99"), "Should be updated to 99")
        }
    }

    @Test fun `dyn param - measure_from method with &dyn Measurable`() {
        val describable = Rustcalc.create_describable(5) as DynDescribable
        val measurable = Rustcalc.create_measurable(7) as DynMeasurable
        TraitConsumer(describable).use { consumer ->
            val result = consumer.measure_from(measurable)
            assertTrue(result.contains("7"), "Should contain measurement, got: $result")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TraitConsumer edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge dyn param - constructor with zero value source`() {
        val source = Rustcalc.create_describable(0) as DynDescribable
        TraitConsumer(source).use { consumer ->
            assertTrue(consumer.description.contains("0"))
        }
    }

    @Test fun `edge dyn param - constructor with negative value source`() {
        val source = Rustcalc.create_describable(-500) as DynDescribable
        TraitConsumer(source).use { consumer ->
            assertTrue(consumer.description.contains("-500"))
        }
    }

    @Test fun `edge dyn param - constructor with MAX_VALUE source`() {
        val source = Rustcalc.create_describable(Int.MAX_VALUE) as DynDescribable
        TraitConsumer(source).use { consumer ->
            assertTrue(consumer.description.contains("${Int.MAX_VALUE}"))
        }
    }

    @Test fun `edge dyn param - constructor with MIN_VALUE source`() {
        val source = Rustcalc.create_describable(Int.MIN_VALUE) as DynDescribable
        TraitConsumer(source).use { consumer ->
            assertTrue(consumer.description.contains("${Int.MIN_VALUE}"))
        }
    }

    @Test fun `edge dyn param - multiple update_from calls`() {
        val source = Rustcalc.create_describable(1) as DynDescribable
        TraitConsumer(source).use { consumer ->
            repeat(10) { i ->
                val newSource = Rustcalc.create_describable((i + 1) * 100) as DynDescribable
                consumer.update_from(newSource)
                assertTrue(consumer.description.contains("${(i + 1) * 100}"))
            }
        }
    }

    @Test fun `edge dyn param - different trait types on same consumer`() {
        val desc = Rustcalc.create_describable(42) as DynDescribable
        val meas = Rustcalc.create_measurable(7) as DynMeasurable
        TraitConsumer(desc).use { consumer ->
            val result = consumer.measure_from(meas)
            assertTrue(result.contains("42"), "Should contain description, got: $result")
            assertTrue(result.contains("7"), "Should contain measurement, got: $result")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TraitConsumer load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K TraitConsumer constructor calls`() {
        val source = Rustcalc.create_describable(42) as DynDescribable
        repeat(100_000) {
            TraitConsumer(source).use { consumer ->
                assertTrue(consumer.description.contains("42"))
            }
        }
    }

    @Test fun `load - 100K update_from calls on single instance`() {
        val source = Rustcalc.create_describable(7) as DynDescribable
        TraitConsumer(source).use { consumer ->
            repeat(100_000) {
                consumer.update_from(source)
                assertTrue(consumer.description.contains("7"))
            }
        }
    }

    @Test fun `load - 100K measure_from calls on single instance`() {
        val source = Rustcalc.create_describable(1) as DynDescribable
        val meas = Rustcalc.create_measurable(2) as DynMeasurable
        TraitConsumer(source).use { consumer ->
            repeat(100_000) {
                val result = consumer.measure_from(meas)
                assertTrue(result.contains("2"))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TraitConsumer concurrency tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 10K TraitConsumer constructions`() {
        val threads = (1..10).map { tid ->
            Thread {
                val source = Rustcalc.create_describable(tid * 100) as DynDescribable
                repeat(10_000) {
                    TraitConsumer(source).use { consumer ->
                        assertTrue(consumer.description.contains("${tid * 100}"))
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K measure_from calls`() {
        val threads = (1..10).map { tid ->
            Thread {
                val desc = Rustcalc.create_describable(tid) as DynDescribable
                val meas = Rustcalc.create_measurable(tid * 10) as DynMeasurable
                TraitConsumer(desc).use { consumer ->
                    repeat(10_000) {
                        val result = consumer.measure_from(meas)
                        assertTrue(result.contains("${tid * 10}"))
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
