package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NestedDataClassTest {

    // ══════════════════════════════════════════════════════════════════════════
    // MASSIVE EDGE-CASE BATTERY: Nested DC + Enum + Default params
    // ══════════════════════════════════════════════════════════════════════════

    // ── RichCalculator: 6 default params (DC, Enum, String, Double) ─────────

    @Test fun `rich - no-arg all defaults`() {
        RichCalculator().use { assertEquals(0, it.current); assertEquals("rich", it.getName()); assertEquals(1.0, it.getFactor(), 0.001) }
    }
    @Test fun `rich - initial only`() = RichCalculator(42).use { assertEquals(42, it.current); assertEquals("rich", it.getName()) }
    @Test fun `rich - initial + style`() {
        RichCalculator(1, Style(true, 255)).use { val s = it.getStyle(); assertTrue(s.bold); assertEquals(255, s.color) }
    }
    @Test fun `rich - initial + style + origin`() {
        RichCalculator(1, Style(false, 10), Point(5, 5)).use { assertEquals(5, it.getOrigin().x); assertEquals(5, it.getOrigin().y) }
    }
    @Test fun `rich - initial + style + origin + op`() {
        RichCalculator(1, Style(false, 0), Point(0, 0), Operation.MULTIPLY).use { assertEquals(Operation.MULTIPLY, it.getOp()) }
    }
    @Test fun `rich - initial + style + origin + op + name`() {
        RichCalculator(1, Style(false, 0), Point(0, 0), Operation.ADD, "custom").use { assertEquals("custom", it.getName()) }
    }
    @Test fun `rich - all params explicit`() {
        RichCalculator(10, Style(true, 128), Point(3, 7), Operation.SUBTRACT, "full", 2.5).use { calc ->
            assertEquals(10, calc.current)
            assertTrue(calc.getStyle().bold)
            assertEquals(128, calc.getStyle().color)
            assertEquals(3, calc.getOrigin().x)
            assertEquals(7, calc.getOrigin().y)
            assertEquals(Operation.SUBTRACT, calc.getOp())
            assertEquals("full", calc.getName())
            assertEquals(2.5, calc.getFactor(), 0.001)
        }
    }
    @Test fun `rich - scaled after add`() {
        RichCalculator(0, Style(false, 0), Point(0, 0), Operation.ADD, "rich", 3.0).use { it.add(10); assertEquals(30.0, it.scaled(), 0.001) }
    }
    @Test fun `rich - negative factor`() {
        RichCalculator(5, Style(false, 0), Point(0, 0), Operation.ADD, "rich", -2.0).use { assertEquals(-10.0, it.scaled(), 0.001) }
    }
    @Test fun `rich - zero factor`() {
        RichCalculator(100, Style(false, 0), Point(0, 0), Operation.ADD, "rich", 0.0).use { assertEquals(0.0, it.scaled(), 0.001) }
    }
    @Test fun `rich - add then check all fields stable`() {
        RichCalculator(1, Style(true, 42), Point(10, 20), Operation.MULTIPLY, "test", 1.5).use { calc ->
            calc.add(99)
            assertEquals(100, calc.current)
            assertTrue(calc.getStyle().bold)
            assertEquals(42, calc.getStyle().color)
            assertEquals(10, calc.getOrigin().x)
            assertEquals(Operation.MULTIPLY, calc.getOp())
            assertEquals("test", calc.getName())
            assertEquals(1.5, calc.getFactor(), 0.001)
        }
    }

    // ── PureDefaultCalc: only DC defaults, no primitive defaults ─────────────

    @Test fun `pure - no-arg uses all defaults`() {
        PureDefaultCalc().use { calc ->
            val b = calc.getBounds()
            assertEquals(-1, b.topLeft.x); assertEquals(-1, b.topLeft.y)
            assertEquals(1, b.bottomRight.x); assertEquals(1, b.bottomRight.y)
            val t = calc.getTagged()
            assertEquals(0, t.point.x); assertEquals(0, t.point.y)
            assertEquals(Operation.ADD, t.tag)
        }
    }
    @Test fun `pure - custom bounds`() {
        PureDefaultCalc(Rect(Point(10, 20), Point(30, 40))).use { calc ->
            assertEquals(10, calc.getBounds().topLeft.x)
            assertEquals(40, calc.getBounds().bottomRight.y)
            assertEquals(Operation.ADD, calc.getTagged().tag) // default
        }
    }
    @Test fun `pure - all custom`() {
        PureDefaultCalc(Rect(Point(5, 5), Point(10, 10)), TaggedPoint(Point(7, 8), Operation.MULTIPLY)).use { calc ->
            assertEquals(5, calc.getBounds().topLeft.x)
            assertEquals(10, calc.getBounds().bottomRight.x)
            assertEquals(7, calc.getTagged().point.x)
            assertEquals(8, calc.getTagged().point.y)
            assertEquals(Operation.MULTIPLY, calc.getTagged().tag)
        }
    }
    @Test fun `pure - sum with defaults`() = PureDefaultCalc().use { assertEquals(0, it.sum()) } // -1-1+1+1=0
    @Test fun `pure - sum with custom`() = PureDefaultCalc(Rect(Point(1, 2), Point(3, 4))).use { assertEquals(10, it.sum()) }

    // ── NestedDcProcessor: StyledPoint, TaggedRect, DeepNested ───────────────

    @Test fun `nested - processStyledPoint basic`() {
        NestedDcProcessor().use { assertEquals(13, it.processStyledPoint(StyledPoint(Point(3, 5), Style(true, 5)))) }
    }
    @Test fun `nested - processStyledPoint zero`() {
        NestedDcProcessor().use { assertEquals(0, it.processStyledPoint(StyledPoint(Point(0, 0), Style(false, 0)))) }
    }
    @Test fun `nested - processStyledPoint negative`() {
        NestedDcProcessor().use { assertEquals(-5, it.processStyledPoint(StyledPoint(Point(-3, -5), Style(false, 3)))) }
    }
    @Test fun `nested - set then get StyledPoint`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(10, 20), Style(true, 99)))
            val sp = proc.getStyledPoint()
            assertEquals(10, sp.point.x)
            assertEquals(20, sp.point.y)
            assertTrue(sp.style.bold)
            assertEquals(99, sp.style.color)
        }
    }
    @Test fun `nested - StyledPoint roundtrip`() {
        NestedDcProcessor().use { proc ->
            val original = StyledPoint(Point(7, 8), Style(false, 42))
            proc.setStyledPoint(original)
            val retrieved = proc.getStyledPoint()
            assertEquals(original.point.x, retrieved.point.x)
            assertEquals(original.point.y, retrieved.point.y)
            assertEquals(original.style.bold, retrieved.style.bold)
            assertEquals(original.style.color, retrieved.style.color)
        }
    }
    @Test fun `nested - processTaggedRect`() {
        NestedDcProcessor().use { proc ->
            val tr = TaggedRect(Rect(Point(1, 2), Point(3, 4)), Operation.MULTIPLY, "box")
            assertEquals("box:MULTIPLY(1,2-3,4)", proc.processTaggedRect(tr))
        }
    }
    @Test fun `nested - processTaggedRect with ADD`() {
        NestedDcProcessor().use { proc ->
            val tr = TaggedRect(Rect(Point(0, 0), Point(10, 10)), Operation.ADD, "area")
            assertEquals("area:ADD(0,0-10,10)", proc.processTaggedRect(tr))
        }
    }
    @Test fun `nested - processTaggedRect with SUBTRACT`() {
        NestedDcProcessor().use { proc ->
            val tr = TaggedRect(Rect(Point(-5, -5), Point(5, 5)), Operation.SUBTRACT, "centered")
            assertEquals("centered:SUBTRACT(-5,-5-5,5)", proc.processTaggedRect(tr))
        }
    }
    @Test fun `nested - getTaggedRect after set`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(7, 8), Style(false, 0)))
            val tr = proc.getTaggedRect()
            assertEquals(7, tr.rect.topLeft.x)
            assertEquals(8, tr.rect.topLeft.y)
            assertEquals("default", tr.name)
            assertEquals(Operation.ADD, tr.tag)
        }
    }
    @Test fun `nested - processDeepNested`() {
        NestedDcProcessor().use { proc ->
            val dn = DeepNested(TaggedPoint(Point(3, 4), Operation.ADD), Style(true, 10), 1.5)
            assertEquals(3 + 4 + 10 + 15, proc.processDeepNested(dn)) // 32
        }
    }
    @Test fun `nested - processDeepNested zero`() {
        NestedDcProcessor().use { proc ->
            val dn = DeepNested(TaggedPoint(Point(0, 0), Operation.ADD), Style(false, 0), 0.0)
            assertEquals(0, proc.processDeepNested(dn))
        }
    }
    @Test fun `nested - processDeepNested negative scale`() {
        NestedDcProcessor().use { proc ->
            val dn = DeepNested(TaggedPoint(Point(1, 1), Operation.SUBTRACT), Style(false, 5), -3.0)
            assertEquals(1 + 1 + 5 + (-30), proc.processDeepNested(dn)) // -23
        }
    }
    @Test fun `nested - getDeepNested after set`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(5, 6), Style(true, 77)))
            val dn = proc.getDeepNested()
            assertEquals(5, dn.tagged.point.x)
            assertEquals(6, dn.tagged.point.y)
            assertEquals(Operation.MULTIPLY, dn.tagged.tag)
            assertTrue(dn.style.bold)
            assertEquals(77, dn.style.color)
            assertEquals(2.5, dn.scale, 0.001)
        }
    }
    @Test fun `nested - processConfig`() {
        NestedDcProcessor().use { assertEquals(15, it.processConfig(Config(Point(3, 7), 5))) }
    }
    @Test fun `nested - processConfig zero`() {
        NestedDcProcessor().use { assertEquals(0, it.processConfig(Config(Point(0, 0), 0))) }
    }
    @Test fun `nested - swapPoint`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(10, 20), Style(false, 0)))
            val old = proc.swapPoint(Point(99, 88))
            assertEquals(10, old.x); assertEquals(20, old.y)
            val now = proc.getStyledPoint()
            assertEquals(99, now.point.x); assertEquals(88, now.point.y)
        }
    }
    @Test fun `nested - swapPoint twice`() {
        NestedDcProcessor().use { proc ->
            proc.swapPoint(Point(1, 2))
            val old = proc.swapPoint(Point(3, 4))
            assertEquals(1, old.x); assertEquals(2, old.y)
        }
    }

    // ── Nullable nested DC ──────────────────────────────────────────────────

    @Test fun `nested nullable - getStyleOrNull null`() {
        NestedDcProcessor().use { assertNull(it.getStyleOrNull()) } // color=0 → null
    }
    @Test fun `nested nullable - getStyleOrNull non-null`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(0, 0), Style(true, 42)))
            val s = proc.getStyleOrNull()
            assertTrue(s != null)
            assertTrue(s!!.bold)
            assertEquals(42, s.color)
        }
    }
    @Test fun `nested nullable - getStyledPointOrNull null`() {
        NestedDcProcessor().use { assertNull(it.getStyledPointOrNull()) } // x=0 → null
    }
    @Test fun `nested nullable - getStyledPointOrNull non-null`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(5, 10), Style(false, 0)))
            val sp = proc.getStyledPointOrNull()
            assertTrue(sp != null)
            assertEquals(5, sp!!.point.x)
            assertEquals(10, sp.point.y)
        }
    }
    @Test fun `nested nullable - transition null to non-null`() {
        NestedDcProcessor().use { proc ->
            assertNull(proc.getStyledPointOrNull())
            proc.swapPoint(Point(1, 0))
            val sp = proc.getStyledPointOrNull()
            assertTrue(sp != null)
            assertEquals(1, sp!!.point.x)
        }
    }

    // ── Cross-feature: nested DC in collections ─────────────────────────────

    @Test fun `cross nested - Calculator getTaggedPoint`() {
        Calculator(5).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 2)
            val tp = calc.getTaggedPoint()
            assertEquals(10, tp.point.x)
            assertEquals(20, tp.point.y)
            assertEquals(Operation.MULTIPLY, tp.tag)
        }
    }
    @Test fun `cross nested - Calculator setFromTagged`() {
        Calculator(0).use { calc ->
            calc.setFromTagged(TaggedPoint(Point(7, 3), Operation.SUBTRACT))
            assertEquals(10, calc.current) // 7+3
            assertEquals(Operation.SUBTRACT, calc.lastOperation)
        }
    }
    @Test fun `cross nested - Rect roundtrip`() {
        Calculator(15).use { calc ->
            val r = calc.getRect()
            assertEquals(0, r.topLeft.x)
            assertEquals(0, r.topLeft.y)
            assertEquals(15, r.bottomRight.x)
            assertEquals(15, r.bottomRight.y)
        }
    }
    @Test fun `cross nested - NamedValue set then get`() {
        Calculator(0).use { calc ->
            calc.setFromNamed(NamedValue("myLabel", 42))
            assertEquals(42, calc.current)
            assertEquals("myLabel", calc.label)
            val nv = calc.getNamedValue()
            assertEquals("myLabel", nv.name)
            assertEquals(42, nv.value)
        }
    }
    @Test fun `cross nested - snapshot and restore`() {
        Calculator(100).use { calc ->
            calc.label = "saved"
            val snap = calc.snapshot()
            assertEquals("saved", snap.label)
            // snap.calc is a reference to the same object, so after add(50) it reflects the change
            calc.add(50)
            assertEquals(150, calc.current)
            // restoreFrom reads snap.calc.current which is now 150 (shared reference)
            val restored = calc.restoreFrom(snap)
            assertEquals(150, restored)
            assertEquals("saved", calc.label)
            snap.calc.close()
        }
    }

    // ── Multi-instance nested DC stress ──────────────────────────────────────

    @Test fun `stress nested - 20 StyledPoint sets`() {
        NestedDcProcessor().use { proc ->
            repeat(20) { i ->
                proc.setStyledPoint(StyledPoint(Point(i, i * 2), Style(i % 2 == 0, i * 10)))
                val sp = proc.getStyledPoint()
                assertEquals(i, sp.point.x)
                assertEquals(i * 2, sp.point.y)
                assertEquals(i % 2 == 0, sp.style.bold)
                assertEquals(i * 10, sp.style.color)
            }
        }
    }
    @Test fun `stress nested - 20 DeepNested processes`() {
        NestedDcProcessor().use { proc ->
            repeat(20) { i ->
                val dn = DeepNested(TaggedPoint(Point(i, i), Operation.entries[i % 3]), Style(false, i), i.toDouble())
                val result = proc.processDeepNested(dn)
                assertEquals(i + i + i + (i * 10), result)
            }
        }
    }
    @Test fun `stress nested - 10 RichCalculator create-use-close`() {
        repeat(10) { i ->
            RichCalculator(i, Style(i % 2 == 0, i), Point(i, i), Operation.entries[i % 3], "r$i", i.toDouble()).use { calc ->
                assertEquals(i, calc.current)
                assertEquals("r$i", calc.getName())
                calc.add(1)
                assertEquals(i + 1, calc.current)
            }
        }
    }
    @Test fun `stress nested - 10 PureDefaultCalc create-close`() {
        repeat(10) { i ->
            PureDefaultCalc(
                Rect(Point(i, i), Point(i * 2, i * 2)),
                TaggedPoint(Point(i, 0), Operation.entries[i % 3])
            ).use { calc ->
                assertEquals(i + i + i * 2 + i * 2, calc.sum())
            }
        }
    }

    // ── Edge cases: extreme values in nested DC ─────────────────────────────

    @Test fun `edge nested - Point MAX_VALUE`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(Int.MAX_VALUE, Int.MIN_VALUE), Style(true, Int.MAX_VALUE)))
            val sp = proc.getStyledPoint()
            assertEquals(Int.MAX_VALUE, sp.point.x)
            assertEquals(Int.MIN_VALUE, sp.point.y)
            assertEquals(Int.MAX_VALUE, sp.style.color)
        }
    }
    @Test fun `edge nested - Style false 0`() {
        NestedDcProcessor().use { proc ->
            proc.setStyledPoint(StyledPoint(Point(1, 1), Style(false, 0)))
            val sp = proc.getStyledPoint()
            assertFalse(sp.style.bold)
            assertEquals(0, sp.style.color)
        }
    }
    @Test fun `edge nested - Config negative scale`() {
        NestedDcProcessor().use { assertEquals(-7, it.processConfig(Config(Point(-3, -2), -2))) }
    }
    @Test fun `edge nested - TaggedRect all zeros`() {
        NestedDcProcessor().use { proc ->
            val tr = TaggedRect(Rect(Point(0, 0), Point(0, 0)), Operation.ADD, "zero")
            assertEquals("zero:ADD(0,0-0,0)", proc.processTaggedRect(tr))
        }
    }
    @Test fun `edge nested - DeepNested large scale`() {
        NestedDcProcessor().use { proc ->
            val dn = DeepNested(TaggedPoint(Point(0, 0), Operation.ADD), Style(false, 0), 999.9)
            assertEquals(9999, proc.processDeepNested(dn))
        }
    }
    @Test fun `edge nested - empty name in TaggedRect`() {
        NestedDcProcessor().use { proc ->
            val tr = TaggedRect(Rect(Point(1, 1), Point(2, 2)), Operation.SUBTRACT, "")
            assertEquals(":SUBTRACT(1,1-2,2)", proc.processTaggedRect(tr))
        }
    }
}
