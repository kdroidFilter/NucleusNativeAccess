package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class ListDataClassParamTest {

    // ══════════════════════════════════════════════════════════════════════════
    // LIST<DATACLASS> AS PARAMETER — BATTLE TESTED
    // ══════════════════════════════════════════════════════════════════════════

    // ── Basic List<Point> param (primitive fields) ──────────────────────────

    @Test
    fun `list DC param - sumPoints basic`() {
        Calculator(0).use { calc ->
            val result = calc.sumPoints(listOf(Point(1, 2), Point(3, 4)))
            assertEquals(10, result) // (1+2) + (3+4) = 10
        }
    }

    @Test
    fun `list DC param - sumPoints single element`() {
        Calculator(0).use { calc ->
            val result = calc.sumPoints(listOf(Point(5, 7)))
            assertEquals(12, result)
        }
    }

    @Test
    fun `list DC param - sumPoints empty list`() {
        Calculator(0).use { calc ->
            val result = calc.sumPoints(emptyList())
            assertEquals(0, result)
        }
    }

    @Test
    fun `list DC param - sumPoints negative values`() {
        Calculator(0).use { calc ->
            val result = calc.sumPoints(listOf(Point(-1, -2), Point(3, -4)))
            assertEquals(-4, result)
        }
    }

    @Test
    fun `list DC param - sumPoints large list`() {
        Calculator(0).use { calc ->
            val points = List(100) { Point(it, it * 2) }
            val expected = points.sumOf { it.x + it.y }
            assertEquals(expected, calc.sumPoints(points))
        }
    }

    // ── Nullable List<Point>? param ────────────────────────────────────────

    @Test
    fun `list DC param - sumPointsOrNull non-null`() {
        Calculator(0).use { calc ->
            val result = calc.sumPointsOrNull(listOf(Point(1, 2)))
            assertEquals(3, result)
        }
    }

    @Test
    fun `list DC param - sumPointsOrNull null`() {
        Calculator(0).use { calc ->
            val result = calc.sumPointsOrNull(null)
            assertEquals(-1, result)
        }
    }

    // ── List<TaggedPoint> (nested DC + Enum) ───────────────────────────────

    @Test
    fun `list DC param - countTaggedPoints nested DC`() {
        Calculator(0).use { calc ->
            val items = listOf(
                TaggedPoint(Point(1, 2), Operation.ADD),
                TaggedPoint(Point(3, 4), Operation.SUBTRACT),
                TaggedPoint(Point(5, 6), Operation.MULTIPLY),
            )
            assertEquals(3, calc.countTaggedPoints(items))
        }
    }

    @Test
    fun `list DC param - countTaggedPoints empty`() {
        Calculator(0).use { calc ->
            assertEquals(0, calc.countTaggedPoints(emptyList()))
        }
    }

    // ── List<NamedValue> (DC with String field) ────────────────────────────

    @Test
    fun `list DC param - describeNamedValues with strings`() {
        Calculator(0).use { calc ->
            val items = listOf(
                NamedValue("alpha", 1),
                NamedValue("beta", 2),
            )
            assertEquals("alpha=1, beta=2", calc.describeNamedValues(items))
        }
    }

    @Test
    fun `list DC param - describeNamedValues unicode strings`() {
        Calculator(0).use { calc ->
            val items = listOf(NamedValue("café", 42), NamedValue("日本語", 99))
            assertEquals("café=42, 日本語=99", calc.describeNamedValues(items))
        }
    }

    @Test
    fun `list DC param - describeNamedValues single`() {
        Calculator(0).use { calc ->
            assertEquals("x=5", calc.describeNamedValues(listOf(NamedValue("x", 5))))
        }
    }

    // ── List<Rect> (deeply nested DC) ──────────────────────────────────────

    @Test
    fun `list DC param - sumRects deeply nested`() {
        Calculator(0).use { calc ->
            val rects = listOf(
                Rect(Point(0, 0), Point(10, 20)),
                Rect(Point(1, 2), Point(3, 4)),
            )
            // (0+0+10+20) + (1+2+3+4) = 40
            assertEquals(40, calc.sumRects(rects))
        }
    }

    @Test
    fun `list DC param - sumRects empty`() {
        Calculator(0).use { calc ->
            assertEquals(0, calc.sumRects(emptyList()))
        }
    }

    // ── Return DC from List<DC> param ──────────────────────────────────────

    @Test
    fun `list DC param - firstPointOrDefault returns first`() {
        Calculator(0).use { calc ->
            val result = calc.firstPointOrDefault(listOf(Point(7, 8), Point(9, 10)))
            assertEquals(Point(7, 8), result)
        }
    }

    @Test
    fun `list DC param - firstPointOrDefault empty returns default`() {
        Calculator(0).use { calc ->
            val result = calc.firstPointOrDefault(emptyList())
            assertEquals(Point(0, 0), result)
        }
    }

    // ── Concurrent calls with List<DC> param ───────────────────────────────

    @Test
    fun `list DC param - concurrent calls same instance`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..20).map { i ->
                async(Dispatchers.Default) {
                    calc.sumPoints(listOf(Point(i, i)))
                }
            }.awaitAll()
            assertEquals(20, results.size)
            results.forEach { assertTrue(it > 0) }
        }
    }

    @Test
    fun `list DC param - concurrent calls separate instances`() = runBlocking {
        val results = (1..20).map { i ->
            async(Dispatchers.Default) {
                Calculator(0).use { calc ->
                    calc.sumPoints(listOf(Point(i, i * 2)))
                }
            }
        }.awaitAll()
        results.forEachIndexed { idx, result ->
            val i = idx + 1
            assertEquals(i + i * 2, result)
        }
    }

    // ── Sequential stress / leak detection ─────────────────────────────────

    @Test
    fun `list DC param - 200 sequential calls no leak`() {
        Calculator(0).use { calc ->
            repeat(200) { i ->
                val result = calc.sumPoints(listOf(Point(i, 1)))
                assertEquals(i + 1, result)
            }
        }
        System.gc()
    }

    @Test
    fun `list DC param - 100 instances with large lists`() {
        repeat(100) { i ->
            Calculator(0).use { calc ->
                val points = List(10) { Point(i, it) }
                calc.sumPoints(points)
            }
        }
        System.gc()
    }

    // ── List<Person> (brand new nested DC with String fields) ────────────

    @Test
    fun `list DC param - describePersons new nested DC`() {
        Calculator(0).use { calc ->
            val persons = listOf(
                Person("Alice", 30, Address("123 Main St", "Paris")),
                Person("Bob", 25, Address("456 Oak Ave", "Lyon")),
            )
            assertEquals(
                "Alice(30) @ 123 Main St, Paris; Bob(25) @ 456 Oak Ave, Lyon",
                calc.describePersons(persons),
            )
        }
    }

    @Test
    fun `list DC param - describePersons single person`() {
        Calculator(0).use { calc ->
            val persons = listOf(Person("Charlie", 42, Address("1 Rue de Rivoli", "Paris")))
            assertEquals("Charlie(42) @ 1 Rue de Rivoli, Paris", calc.describePersons(persons))
        }
    }

    @Test
    fun `list DC param - describePersons unicode`() {
        Calculator(0).use { calc ->
            val persons = listOf(
                Person("日本太郎", 28, Address("東京都渋谷区", "東京")),
                Person("Éloïse", 35, Address("Château-d'Eau", "Montréal")),
            )
            val result = calc.describePersons(persons)
            assertTrue(result.contains("日本太郎"))
            assertTrue(result.contains("Éloïse"))
            assertTrue(result.contains("Château-d'Eau"))
        }
    }

    @Test
    fun `list DC param - oldestPersonAge`() {
        Calculator(0).use { calc ->
            val persons = listOf(
                Person("Young", 18, Address("St A", "City A")),
                Person("Old", 99, Address("St B", "City B")),
                Person("Mid", 45, Address("St C", "City C")),
            )
            assertEquals(99, calc.oldestPersonAge(persons))
        }
    }

    @Test
    fun `list DC param - oldestPersonAge empty returns -1`() {
        Calculator(0).use { calc ->
            assertEquals(-1, calc.oldestPersonAge(emptyList()))
        }
    }

    @Test
    fun `list DC param - persons concurrent`() = runBlocking {
        Calculator(0).use { calc ->
            val results = (1..10).map { i ->
                async(Dispatchers.Default) {
                    calc.describePersons(listOf(Person("P$i", i, Address("St $i", "City"))))
                }
            }.awaitAll()
            assertEquals(10, results.size)
            results.forEachIndexed { idx, r ->
                assertTrue(r.contains("P${idx + 1}"))
            }
        }
    }

    @Test
    fun `list DC param - persons large list stress`() {
        Calculator(0).use { calc ->
            val persons = List(100) { i ->
                Person("Person_$i", i, Address("Street_$i", "City_${i % 10}"))
            }
            assertEquals(99, calc.oldestPersonAge(persons))
            val desc = calc.describePersons(persons)
            assertTrue(desc.contains("Person_0"))
            assertTrue(desc.contains("Person_99"))
        }
    }

    // ── Mixed with other operations ────────────────────────────────────────

    @Test
    fun `list DC param - interleaved with sync mutations`() {
        Calculator(0).use { calc ->
            calc.add(5)
            calc.sumPoints(listOf(Point(1, 2), Point(3, 4)))
            assertEquals(10, calc.current) // accumulator set by sumPoints
            calc.add(1)
            assertEquals(11, calc.current)
        }
    }

    @Test
    fun `list DC param - roundtrip get then pass back`() {
        Calculator(5).use { calc ->
            val points = calc.getPoints() // returns List<Point>
            val sum = calc.sumPoints(points)
            // getPoints returns [Point(5,0), Point(0,5), Point(5,5)]
            assertEquals(5 + 0 + 0 + 5 + 5 + 5, sum) // 20
        }
    }
}
