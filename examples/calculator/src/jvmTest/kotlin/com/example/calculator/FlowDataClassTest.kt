package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.first

class FlowDataClassTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Flow<DataClass> tests
    // ═══════════════════════════════════════════════════════════════════════════

    // ── Flow<Point> ─────────────────────────────────────────────────────────

    @Test fun `flow dc - pointFlow basic`() = runBlocking {
        Calculator(0).use { calc ->
            val points = calc.pointFlow(3).toList()
            assertEquals(3, points.size)
            assertEquals(Point(0, 0), points[0])
            assertEquals(Point(1, 2), points[1])
            assertEquals(Point(2, 4), points[2])
        }
    }

    @Test fun `flow dc - pointFlow single`() = runBlocking {
        Calculator(0).use { calc ->
            val points = calc.pointFlow(1).toList()
            assertEquals(listOf(Point(0, 0)), points)
        }
    }

    @Test fun `flow dc - pointFlow empty`() = runBlocking {
        Calculator(0).use { calc ->
            assertEquals(emptyList<Point>(), calc.pointFlow(0).toList())
        }
    }

    @Test fun `flow dc - emptyPointFlow`() = runBlocking {
        Calculator(0).use { calc ->
            assertEquals(emptyList<Point>(), calc.emptyPointFlow().toList())
        }
    }

    @Test fun `flow dc - singlePointFlow`() = runBlocking {
        Calculator(5).use { calc ->
            val points = calc.singlePointFlow().toList()
            assertEquals(listOf(Point(5, 10)), points)
        }
    }

    @Test fun `flow dc - singlePointFlow zero`() = runBlocking {
        Calculator(0).use { calc ->
            assertEquals(listOf(Point(0, 0)), calc.singlePointFlow().toList())
        }
    }

    @Test fun `flow dc - pointFlow large`() = runBlocking {
        Calculator(0).use { calc ->
            val points = calc.pointFlow(100).toList()
            assertEquals(100, points.size)
            assertEquals(Point(99, 198), points.last())
        }
    }

    @Test fun `flow dc - pointFlow take first`() = runBlocking {
        Calculator(0).use { calc ->
            val first = calc.pointFlow(100).first()
            assertEquals(Point(0, 0), first)
        }
    }

    @Test fun `flow dc - pointFlow take 3`() = runBlocking {
        Calculator(0).use { calc ->
            val items = calc.pointFlow(100).take(3).toList()
            assertEquals(3, items.size)
            assertEquals(Point(2, 4), items[2])
        }
    }

    // ── Flow<NamedValue> (String field) ─────────────────────────────────────

    @Test fun `flow dc - namedValueFlow basic`() = runBlocking {
        Calculator(10).use { calc ->
            calc.label = "hello"
            val items = calc.namedValueFlow().toList()
            assertEquals(2, items.size)
            assertEquals(NamedValue("hello", 10), items[0])
            assertEquals(NamedValue("second", 20), items[1])
        }
    }

    @Test fun `flow dc - namedValueFlow default label`() = runBlocking {
        Calculator(7).use { calc ->
            val items = calc.namedValueFlow().toList()
            assertEquals("default", items[0].name)
            assertEquals(7, items[0].value)
        }
    }

    @Test fun `flow dc - namedValueFlow unicode`() = runBlocking {
        Calculator(1).use { calc ->
            calc.label = "Kotlin"
            val items = calc.namedValueFlow().toList()
            assertEquals("Kotlin", items[0].name)
        }
    }

    // ── Flow<TaggedPoint> (nested DC + enum) ────────────────────────────────

    @Test fun `flow dc - taggedPointFlow`() = runBlocking {
        Calculator(5).use { calc ->
            val items = calc.taggedPointFlow().toList()
            assertEquals(Operation.entries.size, items.size)
            items.forEachIndexed { i, tp ->
                assertEquals(Point(5, 10), tp.point)
                assertEquals(Operation.entries[i], tp.tag)
            }
        }
    }

    @Test fun `flow dc - taggedPointFlow zero accumulator`() = runBlocking {
        Calculator(0).use { calc ->
            val items = calc.taggedPointFlow().toList()
            assertEquals(Operation.entries.size, items.size)
            items.forEach { assertEquals(Point(0, 0), it.point) }
        }
    }

    @Test fun `flow dc - taggedPointFlow negative`() = runBlocking {
        Calculator(-3).use { calc ->
            val items = calc.taggedPointFlow().toList()
            assertEquals(Point(-3, -6), items[0].point)
        }
    }

    // ── Flow<CalcResult> (common DC with String) ────────────────────────────

    @Test fun `flow dc - calcResultFlow basic`() = runBlocking {
        Calculator(10).use { calc ->
            val items = calc.calcResultFlow(3).toList()
            assertEquals(3, items.size)
            assertEquals(CalcResult(10, "step_0"), items[0])
            assertEquals(CalcResult(11, "step_1"), items[1])
            assertEquals(CalcResult(12, "step_2"), items[2])
        }
    }

    @Test fun `flow dc - calcResultFlow empty`() = runBlocking {
        Calculator(0).use { calc ->
            assertEquals(emptyList<CalcResult>(), calc.calcResultFlow(0).toList())
        }
    }

    @Test fun `flow dc - calcResultFlow single`() = runBlocking {
        Calculator(42).use { calc ->
            val items = calc.calcResultFlow(1).toList()
            assertEquals(listOf(CalcResult(42, "step_0")), items)
        }
    }

    @Test fun `flow dc - calcResultFlow take`() = runBlocking {
        Calculator(0).use { calc ->
            val first = calc.calcResultFlow(50).first()
            assertEquals(CalcResult(0, "step_0"), first)
        }
    }

    // ── Flow<Rect> (deeply nested DC) ───────────────────────────────────────

    @Test fun `flow dc - rectFlow basic`() = runBlocking {
        Calculator(10).use { calc ->
            val items = calc.rectFlow().toList()
            assertEquals(2, items.size)
            assertEquals(Rect(Point(0, 0), Point(10, 10)), items[0])
            assertEquals(Rect(Point(1, 1), Point(11, 11)), items[1])
        }
    }

    @Test fun `flow dc - rectFlow zero`() = runBlocking {
        Calculator(0).use { calc ->
            val items = calc.rectFlow().toList()
            assertEquals(Rect(Point(0, 0), Point(0, 0)), items[0])
        }
    }

    @Test fun `flow dc - rectFlow negative`() = runBlocking {
        Calculator(-5).use { calc ->
            val items = calc.rectFlow().toList()
            assertEquals(Rect(Point(0, 0), Point(-5, -5)), items[0])
            assertEquals(Rect(Point(1, 1), Point(-4, -4)), items[1])
        }
    }

    // ── Flow<DC> error handling ─────────────────────────────────────────────

    @Test fun `flow dc - failingPointFlow throws after first`() = runBlocking {
        Calculator(0).use { calc ->
            val items = mutableListOf<Point>()
            assertFailsWith<KotlinNativeException> {
                calc.failingPointFlow().collect { items.add(it) }
            }
            assertEquals(listOf(Point(1, 2)), items)
        }
    }

    // ── Flow<DC> concurrency ────────────────────────────────────────────────

    @Test fun `flow dc - 5 concurrent pointFlow`() = runBlocking {
        Calculator(1).use { calc ->
            val results = (1..5).map { async(Dispatchers.Default) {
                calc.pointFlow(5).toList()
            } }.awaitAll()
            assertEquals(5, results.size)
            results.forEach { points ->
                assertEquals(5, points.size)
                assertEquals(Point(0, 0), points[0])
            }
        }
    }

    @Test fun `flow dc - concurrent on separate instances`() = runBlocking {
        val results = (1..10).map { i ->
            async(Dispatchers.Default) {
                Calculator(i).use { calc -> calc.singlePointFlow().toList() }
            }
        }.awaitAll()
        results.forEachIndexed { i, points ->
            assertEquals(1, points.size)
            assertEquals(Point(i + 1, (i + 1) * 2), points[0])
        }
    }

    // ── Flow<DC> sequential stress ──────────────────────────────────────────

    @Test fun `flow dc - 50 sequential pointFlows`() = runBlocking {
        Calculator(0).use { calc ->
            repeat(50) {
                val points = calc.pointFlow(3).toList()
                assertEquals(3, points.size)
            }
        }
    }

    @Test fun `flow dc - pointFlow then sync methods`() = runBlocking {
        Calculator(5).use { calc ->
            val points = calc.pointFlow(3).toList()
            assertEquals(3, points.size)
            assertEquals(5, calc.current)
            calc.add(10)
            assertEquals(15, calc.current)
        }
    }

    @Test fun `flow dc - pointFlow then suspend`() = runBlocking {
        Calculator(0).use { calc ->
            calc.pointFlow(2).toList()
            val r = calc.delayedAdd(3, 4)
            assertEquals(7, r)
        }
    }

    @Test fun `flow dc - pointFlow then other flow types`() = runBlocking {
        Calculator(5).use { calc ->
            val points = calc.pointFlow(2).toList()
            assertEquals(2, points.size)
            val ints = calc.countUp(3).toList()
            assertEquals(listOf(1, 2, 3), ints)
            val results = calc.calcResultFlow(2).toList()
            assertEquals(2, results.size)
        }
    }

    @Test fun `flow dc - mix all dc flow types`() = runBlocking {
        Calculator(3).use { calc ->
            calc.label = "test"
            val points = calc.pointFlow(2).toList()
            val named = calc.namedValueFlow().toList()
            val tagged = calc.taggedPointFlow().toList()
            val results = calc.calcResultFlow(2).toList()
            val rects = calc.rectFlow().toList()

            assertEquals(2, points.size)
            assertEquals(2, named.size)
            assertEquals(Operation.entries.size, tagged.size)
            assertEquals(2, results.size)
            assertEquals(2, rects.size)

            assertEquals("test", named[0].name)
            assertEquals(Point(3, 6), tagged[0].point)
            assertEquals(CalcResult(3, "step_0"), results[0])
        }
    }
}
