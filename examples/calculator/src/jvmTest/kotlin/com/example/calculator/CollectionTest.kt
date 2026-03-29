package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionTest {

    // ── Collection: List<Int> ───────────────────────────────────────────────

    @Test
    fun `List Int return - getScores`() {
        Calculator(10).use { calc ->
            val scores = calc.getScores()
            assertEquals(listOf(10, 20, 30), scores)
        }
    }

    @Test
    fun `List Int return - zero accumulator`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(0, 0, 0), calc.getScores())
        }
    }

    @Test
    fun `List Int return - negative accumulator`() {
        Calculator(-3).use { calc ->
            assertEquals(listOf(-3, -6, -9), calc.getScores())
        }
    }

    @Test
    fun `List Int param - sumAll`() {
        Calculator(0).use { calc ->
            val result = calc.sumAll(listOf(1, 2, 3, 4, 5))
            assertEquals(15, result)
        }
    }

    @Test
    fun `List Int param - empty list`() {
        Calculator(42).use { calc ->
            assertEquals(0, calc.sumAll(emptyList()))
        }
    }

    @Test
    fun `List Int param - single element`() {
        Calculator(0).use { calc ->
            assertEquals(99, calc.sumAll(listOf(99)))
        }
    }

    @Test
    fun `List Int param - large list`() {
        Calculator(0).use { calc ->
            val largeList = List(1000) { it + 1 }
            assertEquals(500500, calc.sumAll(largeList))
        }
    }

    // ── Collection: List<String> ────────────────────────────────────────────

    @Test
    fun `List String return - getLabels`() {
        Calculator(5).use { calc ->
            calc.label = "test"
            val labels = calc.getLabels()
            assertEquals(listOf("test", "item_5"), labels)
        }
    }

    @Test
    fun `List String return - default label`() {
        Calculator(0).use { calc ->
            assertEquals(listOf("default", "item_0"), calc.getLabels())
        }
    }

    @Test
    fun `List String param - joinLabels`() {
        Calculator(0).use { calc ->
            val result = calc.joinLabels(listOf("a", "b", "c"))
            assertEquals("a, b, c", result)
        }
    }

    @Test
    fun `List String param - empty list`() {
        Calculator(0).use { calc ->
            assertEquals("", calc.joinLabels(emptyList()))
        }
    }

    @Test
    fun `List String param - special characters`() {
        Calculator(0).use { calc ->
            val result = calc.joinLabels(listOf("hello world", "foo/bar", "a=b"))
            assertEquals("hello world, foo/bar, a=b", result)
        }
    }

    // ── Collection: List<Double> ────────────────────────────────────────────

    @Test
    fun `List Double return - getWeights`() {
        Calculator(10).use { calc ->
            val weights = calc.getWeights()
            assertEquals(2, weights.size)
            assertEquals(10.0, weights[0], 0.001)
            assertEquals(15.0, weights[1], 0.001)
        }
    }

    // ── Collection: List<Boolean> ───────────────────────────────────────────

    @Test
    fun `List Boolean return - getFlags positive`() {
        Calculator(5).use { calc ->
            calc.label = "test"
            val flags = calc.getFlags()
            assertEquals(listOf(true, false, true), flags)
        }
    }

    @Test
    fun `List Boolean return - getFlags zero`() {
        Calculator(0).use { calc ->
            val flags = calc.getFlags()
            assertEquals(listOf(false, true, false), flags)
        }
    }

    // ── Collection: List<Enum> ──────────────────────────────────────────────

    @Test
    fun `List Enum return - getOperations`() {
        Calculator(0).use { calc ->
            val ops = calc.getOperations()
            assertEquals(listOf(Operation.ADD, Operation.SUBTRACT, Operation.MULTIPLY), ops)
        }
    }

    @Test
    fun `List Enum param - countOps`() {
        Calculator(0).use { calc ->
            assertEquals(2, calc.countOps(listOf(Operation.ADD, Operation.SUBTRACT)))
        }
    }

    @Test
    fun `List Enum param - empty list`() {
        Calculator(0).use { calc ->
            assertEquals(0, calc.countOps(emptyList()))
        }
    }

    // ── Collection: Set<Int> ────────────────────────────────────────────────

    @Test
    fun `Set Int return - getUniqueDigits`() {
        Calculator(123).use { calc ->
            val digits = calc.getUniqueDigits()
            assertEquals(setOf(1, 2, 3), digits)
        }
    }

    @Test
    fun `Set Int return - repeated digits`() {
        Calculator(111).use { calc ->
            assertEquals(setOf(1), calc.getUniqueDigits())
        }
    }

    @Test
    fun `Set Int return - zero`() {
        Calculator(0).use { calc ->
            assertEquals(setOf(0), calc.getUniqueDigits())
        }
    }

    @Test
    fun `Set Int param - sumUnique`() {
        Calculator(0).use { calc ->
            assertEquals(6, calc.sumUnique(setOf(1, 2, 3)))
        }
    }

    @Test
    fun `Set Int param - empty set`() {
        Calculator(42).use { calc ->
            assertEquals(0, calc.sumUnique(emptySet()))
        }
    }

    // ── Collection: Map<String, Int> ────────────────────────────────────────

    @Test
    fun `Map String Int return - getMetadata`() {
        Calculator(42).use { calc ->
            calc.scale = 3.0
            val meta = calc.getMetadata()
            assertEquals(42, meta["current"])
            assertEquals(3, meta["scale"])
            assertEquals(2, meta.size)
        }
    }

    @Test
    fun `Map String Int param - sumMap`() {
        Calculator(0).use { calc ->
            val result = calc.sumMap(mapOf("a" to 10, "b" to 20, "c" to 30))
            assertEquals(60, result)
        }
    }

    @Test
    fun `Map String Int param - empty map`() {
        Calculator(42).use { calc ->
            assertEquals(0, calc.sumMap(emptyMap()))
        }
    }

    // ── Collection: List<Long> ────────────────────────────────────────────

    @Test
    fun `List Long return - getLongScores`() {
        Calculator(5).use { calc ->
            val scores = calc.getLongScores()
            assertEquals(listOf(5L, 500_000L), scores)
        }
    }

    @Test
    fun `List Long param - sumLongs`() {
        Calculator(0).use { calc ->
            assertEquals(300_000L, calc.sumLongs(listOf(100_000L, 200_000L)))
        }
    }

    @Test
    fun `List Long param - empty`() {
        Calculator(0).use { calc ->
            assertEquals(0L, calc.sumLongs(emptyList()))
        }
    }

    @Test
    fun `List Long param - large values`() {
        Calculator(0).use { calc ->
            assertEquals(2_000_000_000L, calc.sumLongs(listOf(1_000_000_000L, 1_000_000_000L)))
        }
    }

    // ── Collection: List<Float> ─────────────────────────────────────────────

    @Test
    fun `List Float return - getFloatWeights`() {
        Calculator(10).use { calc ->
            val weights = calc.getFloatWeights()
            assertEquals(2, weights.size)
            assertEquals(10.0f, weights[0], 0.001f)
            assertEquals(5.0f, weights[1], 0.001f)
        }
    }

    @Test
    fun `List Float return - zero`() {
        Calculator(0).use { calc ->
            val weights = calc.getFloatWeights()
            assertEquals(0.0f, weights[0], 0.001f)
            assertEquals(0.0f, weights[1], 0.001f)
        }
    }

    // ── Collection: List<Short> ─────────────────────────────────────────────

    @Test
    fun `List Short return - getShortValues`() {
        Calculator(7).use { calc ->
            val values = calc.getShortValues()
            assertEquals(listOf(7.toShort(), 14.toShort()), values)
        }
    }

    // ── Collection: List<Byte> ──────────────────────────────────────────────

    @Test
    fun `List Byte return - getByteValues`() {
        Calculator(3).use { calc ->
            val values = calc.getByteValues()
            assertEquals(listOf(3.toByte(), 4.toByte()), values)
        }
    }

    // ── Collection: List<Double> extended ───────────────────────────────────

    @Test
    fun `List Double return - zero accumulator`() {
        Calculator(0).use { calc ->
            val weights = calc.getWeights()
            assertEquals(0.0, weights[0], 0.001)
            assertEquals(0.0, weights[1], 0.001)
        }
    }

    @Test
    fun `List Double return - negative accumulator`() {
        Calculator(-4).use { calc ->
            val weights = calc.getWeights()
            assertEquals(-4.0, weights[0], 0.001)
            assertEquals(-6.0, weights[1], 0.001)
        }
    }

    // ── Collection: List<Int> extended ──────────────────────────────────────

    @Test
    fun `List Int param - negative values`() {
        Calculator(0).use { calc ->
            assertEquals(-6, calc.sumAll(listOf(-1, -2, -3)))
        }
    }

    @Test
    fun `List Int param and return roundtrip`() {
        Calculator(0).use { calc ->
            calc.sumAll(listOf(10, 20, 30))
            val scores = calc.getScores()
            assertEquals(listOf(60, 120, 180), scores)
        }
    }

    // ── Collection: List<String> extended ───────────────────────────────────

    @Test
    fun `List String param - unicode strings`() {
        Calculator(0).use { calc ->
            val result = calc.joinLabels(listOf("café", "naïve", "über"))
            assertEquals("café, naïve, über", result)
        }
    }

    @Test
    fun `List String param - single element`() {
        Calculator(0).use { calc ->
            assertEquals("only", calc.joinLabels(listOf("only")))
        }
    }

    @Test
    fun `List String param - long strings`() {
        Calculator(0).use { calc ->
            val longStr = "a".repeat(200)
            val result = calc.joinLabels(listOf(longStr, "b"))
            assertEquals("$longStr, b", result)
        }
    }

    // ── Collection: List<Boolean> extended ──────────────────────────────────

    @Test
    fun `List Boolean return - negative accumulator`() {
        Calculator(-5).use { calc ->
            val flags = calc.getFlags()
            assertEquals(false, flags[0]) // not positive
            assertEquals(false, flags[1]) // -5 is odd
            assertEquals(false, flags[2]) // label is empty
        }
    }

    // ── Collection: List<Enum> extended ─────────────────────────────────────

    @Test
    fun `List Enum param - all entries`() {
        Calculator(0).use { calc ->
            assertEquals(3, calc.countOps(listOf(Operation.ADD, Operation.SUBTRACT, Operation.MULTIPLY)))
        }
    }

    @Test
    fun `List Enum param - duplicates`() {
        Calculator(0).use { calc ->
            assertEquals(4, calc.countOps(listOf(Operation.ADD, Operation.ADD, Operation.ADD, Operation.MULTIPLY)))
        }
    }

    @Test
    fun `List Enum return and param roundtrip`() {
        Calculator(0).use { calc ->
            val ops = calc.getOperations()
            assertEquals(3, calc.countOps(ops))
        }
    }

    // ── Collection: Set<Int> extended ───────────────────────────────────────

    @Test
    fun `Set Int return - large number`() {
        Calculator(9876).use { calc ->
            val digits = calc.getUniqueDigits()
            assertEquals(setOf(9, 8, 7, 6), digits)
        }
    }

    @Test
    fun `Set Int param - single element`() {
        Calculator(0).use { calc ->
            assertEquals(42, calc.sumUnique(setOf(42)))
        }
    }

    @Test
    fun `Set Int param and return roundtrip`() {
        Calculator(321).use { calc ->
            val digits = calc.getUniqueDigits()
            calc.sumUnique(digits)
            assertEquals(6, calc.current) // 1+2+3=6
        }
    }

    // ── Collection: Set<String> ─────────────────────────────────────────────

    @Test
    fun `Set String return - getUniqueLabels`() {
        Calculator(5).use { calc ->
            calc.label = "test"
            val labels = calc.getUniqueLabels()
            assertTrue(labels.contains("test"))
            assertTrue(labels.contains("item_5"))
            // "test" appears twice in input → deduped in set
            assertEquals(2, labels.size)
        }
    }

    @Test
    fun `Set String return - default label dedup`() {
        Calculator(0).use { calc ->
            val labels = calc.getUniqueLabels()
            assertTrue(labels.contains("default"))
            assertTrue(labels.contains("item_0"))
        }
    }

    @Test
    fun `Set String param - joinUniqueStrings`() {
        Calculator(0).use { calc ->
            val result = calc.joinUniqueStrings(setOf("c", "a", "b"))
            assertEquals("a;b;c", result)
        }
    }

    @Test
    fun `Set String param - empty set`() {
        Calculator(0).use { calc ->
            assertEquals("", calc.joinUniqueStrings(emptySet()))
        }
    }

    // ── Collection: Set<Enum> ───────────────────────────────────────────────

    @Test
    fun `Set Enum return - getUsedOps`() {
        Calculator(0).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 5)
            val ops = calc.getUsedOps()
            assertTrue(ops.contains(Operation.MULTIPLY))
            assertTrue(ops.contains(Operation.ADD))
        }
    }

    @Test
    fun `Set Enum return - dedup when same`() {
        Calculator(0).use { calc ->
            // lastOperation defaults to ADD, getUsedOps returns {lastOperation, ADD}
            val ops = calc.getUsedOps()
            assertEquals(setOf(Operation.ADD), ops)
        }
    }

    // ── Collection: Map<String, Int> extended ───────────────────────────────

    @Test
    fun `Map String Int return - different scale`() {
        Calculator(100).use { calc ->
            calc.scale = 7.0
            val meta = calc.getMetadata()
            assertEquals(100, meta["current"])
            assertEquals(7, meta["scale"])
        }
    }

    @Test
    fun `Map String Int param - single entry`() {
        Calculator(0).use { calc ->
            assertEquals(42, calc.sumMap(mapOf("x" to 42)))
        }
    }

    @Test
    fun `Map String Int param - negative values`() {
        Calculator(0).use { calc ->
            assertEquals(-10, calc.sumMap(mapOf("a" to -3, "b" to -7)))
        }
    }

    @Test
    fun `Map String Int roundtrip`() {
        Calculator(50).use { calc ->
            calc.scale = 2.0
            val meta = calc.getMetadata()
            calc.sumMap(meta) // sum of current(50) + scale(2) = 52
            assertEquals(52, calc.current)
        }
    }

    // ── Collection: Map<Int, String> ────────────────────────────────────────

    @Test
    fun `Map Int String return - getIndexedLabels`() {
        Calculator(7).use { calc ->
            calc.label = "hello"
            val labels = calc.getIndexedLabels()
            assertEquals("hello", labels[0])
            assertEquals("item_7", labels[1])
            assertEquals(2, labels.size)
        }
    }

    @Test
    fun `Map Int String return - default label`() {
        Calculator(0).use { calc ->
            val labels = calc.getIndexedLabels()
            assertEquals("default", labels[0])
            assertEquals("item_0", labels[1])
        }
    }

    // ── Collection: Map<Int, Int> ───────────────────────────────────────────

    @Test
    fun `Map Int Int return - getSquares`() {
        Calculator(5).use { calc ->
            val squares = calc.getSquares()
            assertEquals(1, squares[1])
            assertEquals(4, squares[2])
            assertEquals(9, squares[3])
            assertEquals(25, squares[5])
        }
    }

    @Test
    fun `Map Int Int param - sumMapValues`() {
        Calculator(0).use { calc ->
            assertEquals(14, calc.sumMapValues(mapOf(1 to 4, 2 to 10)))
        }
    }

    @Test
    fun `Map Int Int param - empty`() {
        Calculator(42).use { calc ->
            assertEquals(0, calc.sumMapValues(emptyMap()))
        }
    }

    @Test
    fun `Map Int Int roundtrip`() {
        Calculator(3).use { calc ->
            val squares = calc.getSquares()
            // getSquares: {1:1, 2:4, 3:9, 3:9} → key 3 = accumulator so deduped → {1:1, 2:4, 3:9}
            calc.sumMapValues(squares)
            assertEquals(14, calc.current) // 1 + 4 + 9
        }
    }

    // ── Collection: Map<String, String> ─────────────────────────────────────

    @Test
    fun `Map String String return - getStringMap`() {
        Calculator(42).use { calc ->
            calc.label = "test"
            val map = calc.getStringMap()
            assertEquals("test", map["name"])
            assertEquals("42", map["value"])
        }
    }

    @Test
    fun `Map String String return - unnamed`() {
        Calculator(0).use { calc ->
            val map = calc.getStringMap()
            assertEquals("unnamed", map["name"])
            assertEquals("0", map["value"])
        }
    }

    @Test
    fun `Map String String param - concatMapEntries`() {
        Calculator(0).use { calc ->
            // Note: map iteration order may vary, so use a sorted map
            val result = calc.concatMapEntries(sortedMapOf("a" to "1", "b" to "2"))
            assertEquals("a=1, b=2", result)
        }
    }

    @Test
    fun `Map String String param - empty`() {
        Calculator(0).use { calc ->
            assertEquals("", calc.concatMapEntries(emptyMap()))
        }
    }

    @Test
    fun `Map String String param - special chars`() {
        Calculator(0).use { calc ->
            val result = calc.concatMapEntries(sortedMapOf("key" to "hello world"))
            assertEquals("key=hello world", result)
        }
    }

    @Test
    fun `Map String String roundtrip`() {
        Calculator(99).use { calc ->
            calc.label = "myCalc"
            val map = calc.getStringMap()
            val result = calc.concatMapEntries(map)
            assertTrue(result.contains("name=myCalc"))
            assertTrue(result.contains("value=99"))
        }
    }

    // ── Nullable Collection: List<Int>? ─────────────────────────────────────

    @Test
    fun `nullable List Int return - non-null`() {
        Calculator(5).use { calc ->
            val scores = calc.getScoresOrNull()
            assertEquals(listOf(5, 10), scores)
        }
    }

    @Test
    fun `nullable List Int return - null`() {
        Calculator(0).use { calc ->
            assertNull(calc.getScoresOrNull())
        }
    }

    @Test
    fun `nullable List Int return - negative`() {
        Calculator(-3).use { calc ->
            val scores = calc.getScoresOrNull()
            assertEquals(listOf(-3, -6), scores)
        }
    }

    // ── Nullable Collection: List<String>? ──────────────────────────────────

    @Test
    fun `nullable List String return - non-null`() {
        Calculator(0).use { calc ->
            calc.label = "hello"
            val labels = calc.getLabelsOrNull()
            assertEquals(listOf("hello", "extra"), labels)
        }
    }

    @Test
    fun `nullable List String return - null`() {
        Calculator(0).use { calc ->
            assertNull(calc.getLabelsOrNull())
        }
    }

    // ── Nullable Collection param: List<Int>? ───────────────────────────────

    @Test
    fun `nullable List Int param - non-null`() {
        Calculator(0).use { calc ->
            assertEquals(15, calc.sumAllOrNull(listOf(5, 10)))
        }
    }

    @Test
    fun `nullable List Int param - null`() {
        Calculator(0).use { calc ->
            assertEquals(-1, calc.sumAllOrNull(null))
        }
    }

    @Test
    fun `nullable List Int param - empty`() {
        Calculator(42).use { calc ->
            assertEquals(0, calc.sumAllOrNull(emptyList()))
        }
    }

    // ── Nullable Collection: Set<Enum>? ─────────────────────────────────────

    @Test
    fun `nullable Set Enum return - non-null`() {
        Calculator(5).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 2)
            val ops = calc.getOpsOrNull()
            assertTrue(ops != null)
            assertTrue(ops!!.contains(Operation.MULTIPLY))
            assertTrue(ops.contains(Operation.ADD))
        }
    }

    @Test
    fun `nullable Set Enum return - null`() {
        Calculator(-1).use { calc ->
            assertNull(calc.getOpsOrNull())
        }
    }

    // ── Nullable Collection: Map<String, Int>? ──────────────────────────────

    @Test
    fun `nullable Map return - non-null`() {
        Calculator(42).use { calc ->
            val meta = calc.getMetadataOrNull()
            assertEquals(mapOf("val" to 42), meta)
        }
    }

    @Test
    fun `nullable Map return - null`() {
        Calculator(0).use { calc ->
            assertNull(calc.getMetadataOrNull())
        }
    }

    // ── Collection: List<Object> ────────────────────────────────────────────

    @Test
    fun `List Object return - getAll`() {
        CalculatorManager().use { mgr ->
            mgr.create("a", 10)
            mgr.create("b", 20)
            val all = mgr.getAll()
            assertEquals(2, all.size)
            // Each element is a Calculator proxy
            val currents = all.map { it.current }.sorted()
            assertEquals(listOf(10, 20), currents)
            all.forEach { it.close() }
        }
    }

    @Test
    fun `List Object return - empty`() {
        CalculatorManager().use { mgr ->
            val all = mgr.getAll()
            assertEquals(0, all.size)
        }
    }

    @Test
    fun `List Object param - sumAll`() {
        CalculatorManager().use { mgr ->
            val c1 = mgr.create("a", 10)
            val c2 = mgr.create("b", 20)
            val sum = mgr.sumAll(listOf(c1, c2))
            assertEquals(30, sum)
        }
    }

    @Test
    fun `List Object param - empty list`() {
        CalculatorManager().use { mgr ->
            assertEquals(0, mgr.sumAll(emptyList()))
        }
    }

    @Test
    fun `nullable List Object return - non-null`() {
        CalculatorManager().use { mgr ->
            mgr.create("x", 5)
            val all = mgr.getAllOrNull()
            assertTrue(all != null)
            assertEquals(1, all!!.size)
            assertEquals(5, all[0].current)
            all.forEach { it.close() }
        }
    }

    @Test
    fun `nullable List Object return - null`() {
        CalculatorManager().use { mgr ->
            assertNull(mgr.getAllOrNull())
        }
    }

    // ── Callback: List<Int> ─────────────────────────────────────────────────

    @Test
    fun `callback List Int - onScoresReady`() {
        Calculator(10).use { calc ->
            var received = emptyList<Int>()
            calc.onScoresReady { scores -> received = scores }
            assertEquals(listOf(10, 20, 30), received)
        }
    }

    @Test
    fun `callback List Int - zero`() {
        Calculator(0).use { calc ->
            var received = emptyList<Int>()
            calc.onScoresReady { received = it }
            assertEquals(listOf(0, 0, 0), received)
        }
    }

    // ── Callback: List<String> ──────────────────────────────────────────────

    @Test
    fun `callback List String - onLabelsReady`() {
        Calculator(5).use { calc ->
            calc.label = "test"
            var received = emptyList<String>()
            calc.onLabelsReady { received = it }
            assertEquals(listOf("test", "item_5"), received)
        }
    }

    @Test
    fun `callback List String - default label`() {
        Calculator(0).use { calc ->
            var received = emptyList<String>()
            calc.onLabelsReady { received = it }
            assertEquals(listOf("default", "item_0"), received)
        }
    }

    // ── Callback: List<Enum> ────────────────────────────────────────────────

    @Test
    fun `callback List Enum - onOpsReady`() {
        Calculator(0).use { calc ->
            var received = emptyList<Operation>()
            calc.onOpsReady { received = it }
            assertEquals(listOf(Operation.ADD, Operation.SUBTRACT, Operation.MULTIPLY), received)
        }
    }

    // ── Callback: List<Boolean> ─────────────────────────────────────────────

    @Test
    fun `callback List Boolean - onFlagsReady positive`() {
        Calculator(4).use { calc ->
            var received = emptyList<Boolean>()
            calc.onFlagsReady { received = it }
            assertEquals(listOf(true, true), received) // 4 > 0, 4 % 2 == 0
        }
    }

    @Test
    fun `callback List Boolean - onFlagsReady zero`() {
        Calculator(0).use { calc ->
            var received = emptyList<Boolean>()
            calc.onFlagsReady { received = it }
            assertEquals(listOf(false, true), received) // 0 > 0 = false, 0 % 2 == 0 = true
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EDGE-CASE BATTERY: Collections
    // ══════════════════════════════════════════════════════════════════════════

    // ── Singleton / Empty List ───────────────────────────────────────────────

    @Test
    fun `edge - singleton list return`() {
        Calculator(42).use { calc ->
            assertEquals(listOf(42), calc.getSingletonList())
        }
    }

    @Test
    fun `edge - empty list return`() {
        Calculator(99).use { calc ->
            assertEquals(emptyList<Int>(), calc.getEmptyList())
        }
    }

    @Test
    fun `edge - large list 500 elements`() {
        Calculator(2).use { calc ->
            val result = calc.getLargeList(500)
            assertEquals(500, result.size)
            assertEquals(0, result[0])
            assertEquals(2, result[1])
            assertEquals(998, result[499])
        }
    }

    @Test
    fun `edge - large list 2000 elements`() {
        Calculator(1).use { calc ->
            val result = calc.getLargeList(2000)
            assertEquals(2000, result.size)
            assertEquals(1999, result[1999])
        }
    }

    @Test
    fun `edge - getLargeList zero accumulator`() {
        Calculator(0).use { calc ->
            val result = calc.getLargeList(100)
            assertTrue(result.all { it == 0 })
        }
    }

    @Test
    fun `edge - getLargeList size 1`() {
        Calculator(7).use { calc ->
            assertEquals(listOf(0), calc.getLargeList(1))
        }
    }

    @Test
    fun `edge - getLargeList size 0`() {
        Calculator(7).use { calc ->
            assertEquals(emptyList<Int>(), calc.getLargeList(0))
        }
    }

    // ── List reversal / transform ───────────────────────────────────────────

    @Test
    fun `edge - reverseList basic`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(3, 2, 1), calc.reverseList(listOf(1, 2, 3)))
            assertEquals(3, calc.current) // first of reversed
        }
    }

    @Test
    fun `edge - reverseList single`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(42), calc.reverseList(listOf(42)))
        }
    }

    @Test
    fun `edge - reverseList empty`() {
        Calculator(99).use { calc ->
            assertEquals(emptyList<Int>(), calc.reverseList(emptyList()))
            assertEquals(0, calc.current) // firstOrNull = null → 0
        }
    }

    @Test
    fun `edge - reverseList preserves all elements`() {
        Calculator(0).use { calc ->
            val input = List(100) { it }
            val result = calc.reverseList(input)
            assertEquals(input.reversed(), result)
        }
    }

    @Test
    fun `edge - filterPositive mixed`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(1, 3, 5), calc.filterPositive(listOf(-2, 1, -4, 3, 0, 5)))
        }
    }

    @Test
    fun `edge - filterPositive all negative`() {
        Calculator(0).use { calc ->
            assertEquals(emptyList<Int>(), calc.filterPositive(listOf(-1, -2, -3)))
        }
    }

    @Test
    fun `edge - filterPositive all positive`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(1, 2, 3), calc.filterPositive(listOf(1, 2, 3)))
        }
    }

    @Test
    fun `edge - filterPositive empty`() {
        Calculator(0).use { calc ->
            assertEquals(emptyList<Int>(), calc.filterPositive(emptyList()))
        }
    }

    // ── String list edge cases ──────────────────────────────────────────────

    @Test
    fun `edge - empty string list return`() {
        Calculator(0).use { calc ->
            assertEquals(emptyList<String>(), calc.getEmptyStringList())
        }
    }

    @Test
    fun `edge - repeatLabel generates correct count`() {
        Calculator(0).use { calc ->
            calc.label = "test"
            val result = calc.repeatLabel(5)
            assertEquals(5, result.size)
            assertEquals("test_0", result[0])
            assertEquals("test_4", result[4])
        }
    }

    @Test
    fun `edge - repeatLabel zero count`() {
        Calculator(0).use { calc ->
            assertEquals(emptyList<String>(), calc.repeatLabel(0))
        }
    }

    @Test
    fun `edge - repeatLabel one count`() {
        Calculator(0).use { calc ->
            calc.label = "x"
            assertEquals(listOf("x_0"), calc.repeatLabel(1))
        }
    }

    @Test
    fun `edge - repeatLabel large count`() {
        Calculator(0).use { calc ->
            calc.label = "item"
            val result = calc.repeatLabel(200)
            assertEquals(200, result.size)
            assertEquals("item_199", result[199])
        }
    }

    @Test
    fun `edge - transformStrings uppercase`() {
        Calculator(0).use { calc ->
            assertEquals(listOf("HELLO", "WORLD"), calc.transformStrings(listOf("hello", "world")))
        }
    }

    @Test
    fun `edge - transformStrings empty`() {
        Calculator(0).use { calc ->
            assertEquals(emptyList<String>(), calc.transformStrings(emptyList()))
        }
    }

    @Test
    fun `edge - transformStrings unicode`() {
        Calculator(0).use { calc ->
            assertEquals(listOf("CAFÉ", "NAÏVE"), calc.transformStrings(listOf("café", "naïve")))
        }
    }

    @Test
    fun `edge - transformStrings single char`() {
        Calculator(0).use { calc ->
            assertEquals(listOf("A"), calc.transformStrings(listOf("a")))
        }
    }

    @Test
    fun `edge - joinLabels many items`() {
        Calculator(0).use { calc ->
            val items = List(50) { "item$it" }
            val result = calc.joinLabels(items)
            assertEquals(items.joinToString(", "), result)
        }
    }

    // ── Map edge cases ──────────────────────────────────────────────────────

    @Test
    fun `edge - singleton map return`() {
        Calculator(42).use { calc ->
            val map = calc.getSingletonMap()
            assertEquals(1, map.size)
            assertEquals(42, map["only"])
        }
    }

    @Test
    fun `edge - empty map return`() {
        Calculator(0).use { calc ->
            assertEquals(emptyMap<String, Int>(), calc.getEmptyMap())
        }
    }

    @Test
    fun `edge - mergeMapValues basic`() {
        Calculator(0).use { calc ->
            val result = calc.mergeMapValues(mapOf("a" to 1), mapOf("b" to 2))
            assertEquals(mapOf("a" to 1, "b" to 2), result)
        }
    }

    @Test
    fun `edge - mergeMapValues overlap`() {
        Calculator(0).use { calc ->
            val result = calc.mergeMapValues(mapOf("a" to 1, "b" to 2), mapOf("b" to 99, "c" to 3))
            assertEquals(3, result.size)
            assertEquals(99, result["b"]) // second map wins
            assertEquals(3, result["c"])
        }
    }

    @Test
    fun `edge - mergeMapValues both empty`() {
        Calculator(0).use { calc ->
            assertEquals(emptyMap<String, Int>(), calc.mergeMapValues(emptyMap(), emptyMap()))
        }
    }

    @Test
    fun `edge - mergeMapValues one empty`() {
        Calculator(0).use { calc ->
            val a = mapOf("x" to 42)
            assertEquals(a, calc.mergeMapValues(a, emptyMap()))
            assertEquals(a, calc.mergeMapValues(emptyMap(), a))
        }
    }

    // ── Set edge cases ──────────────────────────────────────────────────────

    @Test
    fun `edge - empty set return`() {
        Calculator(0).use { calc ->
            assertEquals(emptySet<Int>(), calc.getEmptySet())
        }
    }

    @Test
    fun `edge - intersectSets overlap`() {
        Calculator(0).use { calc ->
            assertEquals(setOf(2, 3), calc.intersectSets(setOf(1, 2, 3), setOf(2, 3, 4)))
        }
    }

    @Test
    fun `edge - intersectSets no overlap`() {
        Calculator(0).use { calc ->
            assertEquals(emptySet<Int>(), calc.intersectSets(setOf(1, 2), setOf(3, 4)))
        }
    }

    @Test
    fun `edge - intersectSets same`() {
        Calculator(0).use { calc ->
            assertEquals(setOf(1, 2, 3), calc.intersectSets(setOf(1, 2, 3), setOf(1, 2, 3)))
        }
    }

    @Test
    fun `edge - intersectSets one empty`() {
        Calculator(0).use { calc ->
            assertEquals(emptySet<Int>(), calc.intersectSets(setOf(1, 2), emptySet()))
        }
    }

    @Test
    fun `edge - intersectSets both empty`() {
        Calculator(0).use { calc ->
            assertEquals(emptySet<Int>(), calc.intersectSets(emptySet(), emptySet()))
        }
    }

    // ── Callback edge cases ─────────────────────────────────────────────────

    @Test
    fun `edge - callback large list 500`() {
        Calculator(0).use { calc ->
            var received = emptyList<Int>()
            calc.onLargeListReady(500) { received = it }
            assertEquals(500, received.size)
            assertEquals(0, received[0])
            assertEquals(499, received[499])
        }
    }

    @Test
    fun `edge - callback large list 1000`() {
        Calculator(0).use { calc ->
            var received = emptyList<Int>()
            calc.onLargeListReady(1000) { received = it }
            assertEquals(1000, received.size)
        }
    }

    @Test
    fun `edge - callback empty list`() {
        Calculator(0).use { calc ->
            var received: List<Int>? = null
            calc.onEmptyListReady { received = it }
            assertEquals(emptyList<Int>(), received)
        }
    }

    @Test
    fun `edge - callback list int negative accumulator`() {
        Calculator(-7).use { calc ->
            var received = emptyList<Int>()
            calc.onScoresReady { received = it }
            assertEquals(listOf(-7, -14, -21), received)
        }
    }

    @Test
    fun `edge - multiple callback invocations`() {
        Calculator(1).use { calc ->
            val all = mutableListOf<List<Int>>()
            calc.onScoresReady { all.add(it) }
            calc.add(9)
            calc.onScoresReady { all.add(it) }
            assertEquals(2, all.size)
            assertEquals(listOf(1, 2, 3), all[0])
            assertEquals(listOf(10, 20, 30), all[1])
        }
    }

    // ── Nullable collection edge cases ──────────────────────────────────────

    @Test
    fun `edge - nullable List String return - non-null then null`() {
        Calculator(0).use { calc ->
            calc.label = "hello"
            val r1 = calc.getScoresOrNullByLabel()
            assertEquals(listOf("hello"), r1)
            calc.label = ""
            assertNull(calc.getScoresOrNullByLabel())
        }
    }

    @Test
    fun `edge - nullable Set Int return - non-null`() {
        Calculator(5).use { calc ->
            val result = calc.getNullableSetByAccum()
            assertEquals(setOf(5, 6), result)
        }
    }

    @Test
    fun `edge - nullable Set Int return - null`() {
        Calculator(-1).use { calc ->
            assertNull(calc.getNullableSetByAccum())
        }
    }

    @Test
    fun `edge - nullable Set Int return - zero is non-null`() {
        Calculator(0).use { calc ->
            val result = calc.getNullableSetByAccum()
            assertEquals(setOf(0, 1), result)
        }
    }

    @Test
    fun `edge - nullable Map String String return - non-null`() {
        Calculator(0).use { calc ->
            calc.label = "test"
            val result = calc.getNullableMapByLabel()
            assertEquals(mapOf("label" to "test"), result)
        }
    }

    @Test
    fun `edge - nullable Map String String return - null`() {
        Calculator(0).use { calc ->
            assertNull(calc.getNullableMapByLabel())
        }
    }

    @Test
    fun `edge - nullable List Int param - large list`() {
        Calculator(0).use { calc ->
            val result = calc.sumAllOrNull(List(500) { 1 })
            assertEquals(500, result)
        }
    }

    @Test
    fun `edge - nullable List Int param - alternating null and non-null`() {
        Calculator(0).use { calc ->
            assertEquals(-1, calc.sumAllOrNull(null))
            assertEquals(10, calc.sumAllOrNull(listOf(10)))
            assertEquals(-1, calc.sumAllOrNull(null))
            assertEquals(5, calc.sumAllOrNull(listOf(2, 3)))
        }
    }

    // ── Cross-feature: collection after state mutation ───────────────────────

    @Test
    fun `cross - collection return reflects state changes`() {
        Calculator(0).use { calc ->
            assertEquals(listOf(0, 0, 0), calc.getScores())
            calc.add(10)
            assertEquals(listOf(10, 20, 30), calc.getScores())
            calc.multiply(2)
            assertEquals(listOf(20, 40, 60), calc.getScores())
        }
    }

    @Test
    fun `cross - collection param mutates state then return`() {
        Calculator(0).use { calc ->
            calc.sumAll(listOf(5, 10, 15))
            assertEquals(30, calc.current)
            val weights = calc.getWeights()
            assertEquals(30.0, weights[0], 0.001)
        }
    }

    @Test
    fun `cross - map return after label and scale change`() {
        Calculator(0).use { calc ->
            calc.add(100)
            calc.scale = 5.0
            val meta = calc.getMetadata()
            assertEquals(100, meta["current"])
            assertEquals(5, meta["scale"])
        }
    }

    @Test
    fun `cross - set return after multiple operations`() {
        Calculator(0).use { calc ->
            calc.add(12345)
            val digits = calc.getUniqueDigits()
            assertEquals(setOf(1, 2, 3, 4, 5), digits)
        }
    }

    @Test
    fun `cross - callback list after state change`() {
        Calculator(0).use { calc ->
            calc.add(7)
            var received = emptyList<Int>()
            calc.onScoresReady { received = it }
            assertEquals(listOf(7, 14, 21), received)
        }
    }

    @Test
    fun `cross - List Object with state mutation`() {
        CalculatorManager().use { mgr ->
            val c1 = mgr.create("a", 10)
            val c2 = mgr.create("b", 20)
            c1.add(5) // c1 now 15
            val all = mgr.getAll()
            val currents = all.map { it.current }.sorted()
            assertEquals(listOf(15, 20), currents)
            all.forEach { it.close() }
        }
    }

    @Test
    fun `cross - nullable collection transition`() {
        Calculator(0).use { calc ->
            // accumulator = 0 → null
            assertNull(calc.getScoresOrNull())
            calc.add(5)
            // accumulator = 5 → non-null
            assertEquals(listOf(5, 10), calc.getScoresOrNull())
            calc.subtract(5)
            // back to 0 → null again
            assertNull(calc.getScoresOrNull())
        }
    }

    // ── Callback: Map param ─────────────────────────────────────────────────

    @Test
    fun `callback Map String Int param - onMetadataReady`() {
        Calculator(10).use { calc ->
            var received = emptyMap<String, Int>()
            calc.onMetadataReady { received = it }
            assertEquals(10, received["current"])
            assertEquals(20, received["doubled"])
        }
    }

    @Test
    fun `callback Map String Int param - zero`() {
        Calculator(0).use { calc ->
            var received = emptyMap<String, Int>()
            calc.onMetadataReady { received = it }
            assertEquals(0, received["current"])
            assertEquals(0, received["doubled"])
        }
    }

    // ── Callback: collection return ─────────────────────────────────────────

    @Test
    fun `callback return List Int - getTransformedScores`() {
        Calculator(5).use { calc ->
            val result = calc.getTransformedScores { v -> listOf(v, v * 10, v * 100) }
            assertEquals(listOf(5, 50, 500), result)
        }
    }

    @Test
    fun `callback return List Int - empty`() {
        Calculator(0).use { calc ->
            val result = calc.getTransformedScores { emptyList() }
            assertEquals(emptyList<Int>(), result)
        }
    }

    @Test
    fun `callback return List Int - single`() {
        Calculator(42).use { calc ->
            val result = calc.getTransformedScores { listOf(it * 2) }
            assertEquals(listOf(84), result)
        }
    }

    @Test
    fun `callback return List String - getComputedLabels`() {
        Calculator(3).use { calc ->
            val result = calc.getComputedLabels { v -> listOf("val=$v", "doubled=${v * 2}") }
            assertEquals(listOf("val=3", "doubled=6"), result)
        }
    }

    @Test
    fun `callback return List String - empty`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedLabels { emptyList() }
            assertEquals(emptyList<String>(), result)
        }
    }

    @Test
    fun `callback return Map String Int - getComputedMap`() {
        Calculator(7).use { calc ->
            val result = calc.getComputedMap { v -> mapOf("input" to v, "squared" to v * v) }
            assertEquals(7, result["input"])
            assertEquals(49, result["squared"])
        }
    }

    @Test
    fun `callback return Map String Int - empty`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedMap { emptyMap() }
            assertEquals(emptyMap<String, Int>(), result)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EDGE-CASE BATTERY: Callbacks with collections
    // ══════════════════════════════════════════════════════════════════════════

    // ── Map callback param edge cases ───────────────────────────────────────

    @Test
    fun `cb map param - negative values`() {
        Calculator(-5).use { calc ->
            var received = emptyMap<String, Int>()
            calc.onMetadataReady { received = it }
            assertEquals(-5, received["current"])
            assertEquals(-10, received["doubled"])
        }
    }

    @Test
    fun `cb map param - large accumulator`() {
        Calculator(100_000).use { calc ->
            var received = emptyMap<String, Int>()
            calc.onMetadataReady { received = it }
            assertEquals(100_000, received["current"])
            assertEquals(200_000, received["doubled"])
        }
    }

    @Test
    fun `cb map Int Int param - basic`() {
        Calculator(5).use { calc ->
            var received = emptyMap<Int, Int>()
            calc.onMapIntIntReady { received = it }
            assertEquals(25, received[5])
        }
    }

    @Test
    fun `cb map Int Int param - zero`() {
        Calculator(0).use { calc ->
            var received = emptyMap<Int, Int>()
            calc.onMapIntIntReady { received = it }
            assertEquals(0, received[0])
        }
    }

    @Test
    fun `cb map param - multiple invocations`() {
        Calculator(1).use { calc ->
            val all = mutableListOf<Map<String, Int>>()
            calc.onMetadataReady { all.add(it) }
            calc.add(9)
            calc.onMetadataReady { all.add(it) }
            assertEquals(2, all.size)
            assertEquals(1, all[0]["current"])
            assertEquals(10, all[1]["current"])
        }
    }

    // ── Collection callback return edge cases ───────────────────────────────

    @Test
    fun `cb return List Int - large list`() {
        Calculator(1).use { calc ->
            val result = calc.getTransformedScores { v -> List(500) { v + it } }
            assertEquals(500, result.size)
            assertEquals(1, result[0])
            assertEquals(500, result[499])
        }
    }

    @Test
    fun `cb return List Int - negative values`() {
        Calculator(-3).use { calc ->
            val result = calc.getTransformedScores { v -> listOf(v, v - 1, v - 2) }
            assertEquals(listOf(-3, -4, -5), result)
        }
    }

    @Test
    fun `cb return List Int - singleton`() {
        Calculator(42).use { calc ->
            val result = calc.getTransformedScores { listOf(it) }
            assertEquals(listOf(42), result)
        }
    }

    @Test
    fun `cb return List String - long strings`() {
        Calculator(0).use { calc ->
            val longStr = "a".repeat(200)
            val result = calc.getComputedLabels { listOf(longStr, "short") }
            assertEquals(longStr, result[0])
            assertEquals("short", result[1])
        }
    }

    @Test
    fun `cb return List String - unicode`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedLabels { listOf("café", "naïve", "über") }
            assertEquals(listOf("café", "naïve", "über"), result)
        }
    }

    @Test
    fun `cb return List String - many items`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedLabels { v -> List(100) { "item_$it" } }
            assertEquals(100, result.size)
            assertEquals("item_0", result[0])
            assertEquals("item_99", result[99])
        }
    }

    @Test
    fun `cb return List Enum - getComputedOps`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedOps { listOf(Operation.ADD, Operation.MULTIPLY) }
            assertEquals(listOf(Operation.ADD, Operation.MULTIPLY), result)
        }
    }

    @Test
    fun `cb return List Enum - empty`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedOps { emptyList() }
            assertEquals(emptyList<Operation>(), result)
        }
    }

    @Test
    fun `cb return List Boolean - getComputedBools`() {
        Calculator(5).use { calc ->
            val result = calc.getComputedBools { v -> listOf(v > 0, v > 10, v == 5) }
            assertEquals(listOf(true, false, true), result)
        }
    }

    @Test
    fun `cb return List Boolean - all true`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedBools { listOf(true, true, true) }
            assertEquals(listOf(true, true, true), result)
        }
    }

    @Test
    fun `cb return List Boolean - all false`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedBools { listOf(false, false) }
            assertEquals(listOf(false, false), result)
        }
    }

    @Test
    fun `cb return List Long - getComputedLongs`() {
        Calculator(5).use { calc ->
            val result = calc.getComputedLongs { v -> listOf(v.toLong(), v.toLong() * 1_000_000L) }
            assertEquals(listOf(5L, 5_000_000L), result)
        }
    }

    @Test
    fun `cb return List Long - empty`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedLongs { emptyList() }
            assertEquals(emptyList<Long>(), result)
        }
    }

    @Test
    fun `cb return Map String Int - large map`() {
        Calculator(0).use { calc ->
            val result = calc.getComputedMap { v -> (0 until 50).associate { "key_$it" to it } }
            assertEquals(50, result.size)
            assertEquals(0, result["key_0"])
            assertEquals(49, result["key_49"])
        }
    }

    @Test
    fun `cb return Map String Int - singleton`() {
        Calculator(42).use { calc ->
            val result = calc.getComputedMap { mapOf("answer" to it) }
            assertEquals(mapOf("answer" to 42), result)
        }
    }

    @Test
    fun `cb return Map String Int - unicode keys`() {
        Calculator(1).use { calc ->
            val result = calc.getComputedMap { mapOf("café" to 1, "naïve" to 2) }
            assertEquals(1, result["café"])
            assertEquals(2, result["naïve"])
        }
    }

    // ── Multi-param callback with collection ────────────────────────────────

    @Test
    fun `cb multi-param with List + String`() {
        Calculator(0).use { calc ->
            var receivedList = emptyList<Int>()
            var receivedStr = ""
            calc.computeWithScores(10) { list, str ->
                receivedList = list
                receivedStr = str
            }
            assertEquals(listOf(10, 20, 30), receivedList)
            assertEquals("computed_10", receivedStr)
        }
    }

    @Test
    fun `cb multi-param with List + String - zero`() {
        Calculator(0).use { calc ->
            var receivedList = emptyList<Int>()
            var receivedStr = ""
            calc.computeWithScores(0) { list, str ->
                receivedList = list
                receivedStr = str
            }
            assertEquals(listOf(0, 0, 0), receivedList)
            assertEquals("computed_0", receivedStr)
        }
    }

    // ── Callback return + state mutation ─────────────────────────────────────

    @Test
    fun `cb return List Int - depends on accumulator`() {
        Calculator(0).use { calc ->
            calc.add(5)
            val r1 = calc.getTransformedScores { listOf(it, it + 1) }
            assertEquals(listOf(5, 6), r1)
            calc.add(10)
            val r2 = calc.getTransformedScores { listOf(it, it + 1) }
            assertEquals(listOf(15, 16), r2)
        }
    }

    @Test
    fun `cb return Map - depends on accumulator`() {
        Calculator(0).use { calc ->
            calc.add(3)
            val r1 = calc.getComputedMap { mapOf("val" to it) }
            assertEquals(mapOf("val" to 3), r1)
            calc.multiply(4)
            val r2 = calc.getComputedMap { mapOf("val" to it) }
            assertEquals(mapOf("val" to 12), r2)
        }
    }

    // ── Callback List param + collection return roundtrip ────────────────────

    @Test
    fun `cb list param then collection return`() {
        Calculator(10).use { calc ->
            // First use callback with List param
            var receivedScores = emptyList<Int>()
            calc.onScoresReady { receivedScores = it }
            assertEquals(listOf(10, 20, 30), receivedScores)

            // Then use callback that returns collection
            val transformed = calc.getTransformedScores { v -> receivedScores.map { it + v } }
            assertEquals(listOf(20, 30, 40), transformed)
        }
    }

    @Test
    fun `cb map param then map return`() {
        Calculator(5).use { calc ->
            var meta = emptyMap<String, Int>()
            calc.onMetadataReady { meta = it }

            // Use the received map to build a new one
            val computed = calc.getComputedMap { v ->
                meta.mapValues { (_, mv) -> mv + v }
            }
            assertEquals(10, computed["current"]) // 5 + 5
            assertEquals(15, computed["doubled"]) // 10 + 5
        }
    }

    // ── Sequential callbacks stress test ─────────────────────────────────────

    @Test
    fun `stress - 20 sequential callback invocations`() {
        Calculator(0).use { calc ->
            repeat(20) { i ->
                calc.add(1)
                var received = emptyList<Int>()
                calc.onScoresReady { received = it }
                assertEquals(i + 1, received[0])
            }
        }
    }

    @Test
    fun `stress - 20 sequential callback returns`() {
        Calculator(0).use { calc ->
            repeat(20) { i ->
                calc.add(1)
                val result = calc.getTransformedScores { listOf(it * 2) }
                assertEquals(listOf((i + 1) * 2), result)
            }
        }
    }

    @Test
    fun `stress - alternating param and return callbacks`() {
        Calculator(1).use { calc ->
            repeat(10) {
                var received = emptyMap<String, Int>()
                calc.onMetadataReady { received = it }
                val computed = calc.getComputedMap { v -> mapOf("x" to v) }
                assertEquals(received["current"], computed["x"])
                calc.add(1)
            }
        }
    }

}
