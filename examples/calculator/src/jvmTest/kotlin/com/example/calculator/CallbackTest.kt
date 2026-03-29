package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallbackTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Data classes in callbacks
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `callback (Point) to Unit - receives point`() {
        Calculator(5).use { calc ->
            var received: Point? = null
            calc.onPointComputed { p -> received = p }
            assertEquals(Point(5, 10), received)
        }
    }

    @Test
    fun `callback (Point) to Unit - zero values`() {
        Calculator(0).use { calc ->
            var received: Point? = null
            calc.onPointComputed { p -> received = p }
            assertEquals(Point(0, 0), received)
        }
    }

    @Test
    fun `callback (Point) to Unit - negative values`() {
        Calculator(-3).use { calc ->
            var received: Point? = null
            calc.onPointComputed { p -> received = p }
            assertEquals(Point(-3, -6), received)
        }
    }

    @Test
    fun `callback (CalcResult) to Unit - common data class`() {
        Calculator(42).use { calc ->
            var received: CalcResult? = null
            calc.onResultReady { r -> received = r }
            assertEquals(CalcResult(42, "Result: 42"), received)
        }
    }

    @Test
    fun `callback (CalcResult) to Unit - String field preserved`() {
        Calculator(7).use { calc ->
            var receivedDesc = ""
            calc.onResultReady { r -> receivedDesc = r.description }
            assertEquals("Result: 7", receivedDesc)
        }
    }

    @Test
    fun `callback (Int) to Point - create point from value`() {
        Calculator(5).use { calc ->
            val p = calc.createPoint { v -> Point(v, v * 3) }
            assertEquals(Point(5, 15), p)
        }
    }

    @Test
    fun `callback (Int) to Point - zero`() {
        Calculator(0).use { calc ->
            val p = calc.createPoint { v -> Point(v, v) }
            assertEquals(Point(0, 0), p)
        }
    }

    @Test
    fun `callback (Int) to Point - negative`() {
        Calculator(-4).use { calc ->
            val p = calc.createPoint { v -> Point(v, -v) }
            assertEquals(Point(-4, 4), p)
        }
    }

    @Test
    fun `callback (Point) to Int - transform point`() {
        Calculator(5).use { calc ->
            val result = calc.transformPoint { p -> p.x + p.y }
            assertEquals(15, result) // 5 + 10
        }
    }

    @Test
    fun `callback (Point) to Int - use only x`() {
        Calculator(7).use { calc ->
            val result = calc.transformPoint { p -> p.x * 3 }
            assertEquals(21, result)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ByteArray
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ByteArray return`() {
        Calculator(42).use { calc ->
            val bytes = calc.toBytes()
            assertEquals("42", String(bytes))
        }
    }

    @Test
    fun `ByteArray return - negative`() {
        Calculator(-7).use { calc ->
            val bytes = calc.toBytes()
            assertEquals("-7", String(bytes))
        }
    }

    @Test
    fun `ByteArray param - sum`() {
        Calculator(0).use { calc ->
            val result = calc.sumBytes(byteArrayOf(1, 2, 3, 4))
            assertEquals(10, result)
        }
    }

    @Test
    fun `ByteArray param - empty`() {
        Calculator(0).use { calc ->
            val result = calc.sumBytes(byteArrayOf())
            assertEquals(0, result)
        }
    }

    @Test
    fun `ByteArray roundtrip - reverse`() {
        Calculator(0).use { calc ->
            val input = byteArrayOf(1, 2, 3, 4, 5)
            val reversed = calc.reverseBytes(input)
            assertEquals(listOf<Byte>(5, 4, 3, 2, 1), reversed.toList())
        }
    }

    @Test
    fun `ByteArray roundtrip - large`() {
        Calculator(0).use { calc ->
            val input = ByteArray(1000) { (it % 256).toByte() }
            val reversed = calc.reverseBytes(input)
            assertEquals(1000, reversed.size)
            assertEquals(input.last(), reversed.first())
            assertEquals(input.first(), reversed.last())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Enums in callbacks
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `callback (Operation) to Unit - receives enum`() {
        Calculator(0).use { calc ->
            calc.applyOp(Operation.MULTIPLY, 3)
            var received: Operation? = null
            calc.onOperation { op -> received = op }
            assertEquals(Operation.MULTIPLY, received)
        }
    }

    @Test
    fun `callback (Operation) to Unit - all values`() {
        Calculator(1).use { calc ->
            for (expected in Operation.entries) {
                calc.lastOperation = expected
                var received: Operation? = null
                calc.onOperation { op -> received = op }
                assertEquals(expected, received)
            }
        }
    }

    @Test
    fun `callback (Int) to Operation - choose based on value`() {
        Calculator(5).use { calc ->
            val result = calc.chooseOp { v -> if (v > 0) Operation.ADD else Operation.SUBTRACT }
            assertEquals(Operation.ADD, result)
            assertEquals(Operation.ADD, calc.lastOperation)
        }
    }

    @Test
    fun `callback (Int) to Operation - negative triggers subtract`() {
        Calculator(-3).use { calc ->
            val result = calc.chooseOp { v -> if (v > 0) Operation.ADD else Operation.SUBTRACT }
            assertEquals(Operation.SUBTRACT, result)
        }
    }

    @Test
    fun `callback (Int) to Operation - return MULTIPLY`() {
        Calculator(0).use { calc ->
            val result = calc.chooseOp { Operation.MULTIPLY }
            assertEquals(Operation.MULTIPLY, result)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Callbacks: all primitive types (battle-test each type individually)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `callback Long param and return`() {
        Calculator(10).use { calc ->
            val result = calc.withLong { it * 3L }
            assertEquals(30L, result)
        }
    }

    @Test
    fun `callback Long large value`() {
        Calculator(1).use { calc ->
            val result = calc.withLong { it + 1_000_000_000L }
            assertEquals(1_000_000_001L, result)
        }
    }

    @Test
    fun `callback Double param and return`() {
        Calculator(7).use { calc ->
            val result = calc.withDouble { it * 1.5 }
            assertEquals(10.5, result, 0.001)
        }
    }

    @Test
    fun `callback Double precision`() {
        Calculator(0).use { calc ->
            val result = calc.withDouble { 3.141592653589793 }
            assertEquals(3.141592653589793, result, 1e-10)
        }
    }

    @Test
    fun `callback Float param and return`() {
        Calculator(5).use { calc ->
            val result = calc.withFloat { it + 0.5f }
            assertEquals(5.5f, result, 0.01f)
        }
    }

    @Test
    fun `callback Short param and return`() {
        Calculator(100).use { calc ->
            val result = calc.withShort { (it * 2).toShort() }
            assertEquals(200.toShort(), result)
        }
    }

    @Test
    fun `callback Byte param and return`() {
        Calculator(10).use { calc ->
            val result = calc.withByte { (it + 5).toByte() }
            assertEquals(15.toByte(), result)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases: primitives at boundaries
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Int MAX_VALUE`() {
        Calculator(Int.MAX_VALUE).use { calc ->
            assertEquals(Int.MAX_VALUE, calc.current)
        }
    }

    @Test
    fun `Int MIN_VALUE`() {
        Calculator(Int.MIN_VALUE).use { calc ->
            assertEquals(Int.MIN_VALUE, calc.current)
        }
    }

    @Test
    fun `Long large value`() {
        Calculator(0).use { calc ->
            assertEquals(1_000_000_000L, calc.addLong(1_000_000_000L))
        }
    }

    @Test
    fun `Double precision edge`() {
        Calculator(0).use { calc ->
            assertEquals(Double.MAX_VALUE, calc.addDouble(Double.MAX_VALUE), 1e300)
        }
    }

    @Test
    fun `Float NaN`() {
        Calculator(0).use { calc ->
            assertTrue(calc.addFloat(Float.NaN).isNaN())
        }
    }

    @Test
    fun `Boolean roundtrip both values`() {
        Calculator(5).use { calc ->
            assertTrue(calc.checkFlag(true))
            assertFalse(calc.checkFlag(false))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases: String
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `String with special chars`() {
        Calculator(0).use { calc ->
            val special = "tab\there\nnewline\\backslash\"quote"
            assertEquals(special, calc.echo(special))
        }
    }

    @Test
    fun `String with long content`() {
        Calculator(0).use { calc ->
            val long = "x".repeat(4000)
            assertEquals(long, calc.echo(long))
        }
    }

    @Test
    fun `String property roundtrip unicode`() {
        Calculator(0).use { calc ->
            val emoji = "🎉🚀💻🔥"
            calc.label = emoji
            assertEquals(emoji, calc.label)
        }
    }

    @Test
    fun `String concat unicode`() {
        Calculator(0).use { calc ->
            assertEquals("日本語テスト", calc.concat("日本語", "テスト"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases: Enum exhaustive
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `all enum operations produce correct results`() {
        Calculator(10).use { calc ->
            assertEquals(15, calc.applyOp(Operation.ADD, 5))
            assertEquals(10, calc.applyOp(Operation.SUBTRACT, 5))
            assertEquals(50, calc.applyOp(Operation.MULTIPLY, 5))
        }
    }

    @Test
    fun `enum property set and roundtrip all values`() {
        Calculator(0).use { calc ->
            for (op in Operation.entries) {
                calc.lastOperation = op
                assertEquals(op, calc.lastOperation)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases: Nullable boundary
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `nullable String property set null then non-null then null`() {
        Calculator(0).use { calc ->
            assertNull(calc.nickname)
            calc.nickname = "first"
            assertEquals("first", calc.nickname)
            calc.nickname = null
            assertNull(calc.nickname)
            calc.nickname = "second"
            assertEquals("second", calc.nickname)
        }
    }

    @Test
    fun `nullable Int at zero boundary`() {
        Calculator(10).use { calc ->
            assertEquals(5, calc.divideOrNull(2))
            assertNull(calc.divideOrNull(0))
            // Accumulator unchanged after null
            assertEquals(10, calc.current)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cross-feature: exception + other features
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `exception then callback still works`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            val result = calc.transform { it + 1 }
            assertEquals(11, result)
        }
    }

    @Test
    fun `exception then data class still works`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            val p = calc.getPoint()
            assertEquals(10, p.x)
        }
    }

    @Test
    fun `exception then nullable still works`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            assertEquals(5, calc.divideOrNull(2))
        }
    }

    @Test
    fun `exception then enum still works`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            assertEquals(15, calc.applyOp(Operation.ADD, 5))
        }
    }

    @Test
    fun `exception then companion still works`() {
        assertFailsWith<KotlinNativeException> {
            Calculator(0).use { it.divide(0) }
        }
        assertEquals("2.0", Calculator.version())
    }

    @Test
    fun `exception then string still works`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            assertEquals("Calculator(current=10)", calc.describe())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cross-feature: data class + other features
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `data class Point then modify accumulator then get again`() {
        Calculator(5).use { calc ->
            val p1 = calc.getPoint()
            calc.add(10)
            val p2 = calc.getPoint()
            assertEquals(5, p1.x)
            assertEquals(15, p2.x)
        }
    }

    @Test
    fun `data class NamedValue with empty string`() {
        Calculator(0).use { calc ->
            calc.setFromNamed(NamedValue("", 42))
            assertEquals("", calc.label)
            assertEquals(42, calc.current)
        }
    }

    @Test
    fun `data class NamedValue with unicode`() {
        Calculator(0).use { calc ->
            calc.setFromNamed(NamedValue("привет мир 🌍", 1))
            assertEquals("привет мир 🌍", calc.label)
        }
    }

    @Test
    fun `data class after callback`() {
        Calculator(10).use { calc ->
            calc.transform { it * 3 }
            val p = calc.getPoint()
            assertEquals(30, p.x)
            assertEquals(60, p.y)
        }
    }

    @Test
    fun `common data class CalcResult negative value`() {
        Calculator(-5).use { calc ->
            val r = calc.getResult()
            assertEquals(-5, r.value)
            assertEquals("Result: -5", r.description)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cross-feature: callbacks + other features
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `callback String with unicode`() {
        Calculator(42).use { calc ->
            var received = ""
            calc.withDescription { received = it }
            assertTrue(received.contains("42"))
        }
    }

    @Test
    fun `callback (Int) to String with empty return`() {
        Calculator(0).use { calc ->
            val result = calc.formatWith { "" }
            assertEquals("", result)
        }
    }

    @Test
    fun `callback (String) to String identity`() {
        Calculator(0).use { calc ->
            calc.label = "identity"
            val result = calc.transformLabel { it }
            assertEquals("identity", result)
        }
    }

    @Test
    fun `callback after data class operations`() {
        Calculator(0).use { calc ->
            calc.addPoint(Point(5, 10))
            var received = -1
            calc.onValueChanged { received = it }
            assertEquals(15, received)
        }
    }

    @Test
    fun `callback predicate on negative value`() {
        Calculator(-10).use { calc ->
            assertTrue(calc.checkWith { it < 0 })
            assertFalse(calc.checkWith { it > 0 })
            assertTrue(calc.checkWith { it == -10 })
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cross-feature: object types + other features
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `manager with multiple calculators then nullable`() {
        CalculatorManager().use { mgr ->
            mgr.create("a", 10)
            val found = mgr.getOrNull("a")
            val missing = mgr.getOrNull("z")
            assertTrue(found != null)
            assertEquals(10, found!!.current)
            assertNull(missing)
            found.close()
        }
    }

    @Test
    fun `manager count after multiple creates`() {
        CalculatorManager().use { mgr ->
            assertEquals(0, mgr.count())
            mgr.create("a", 1)
            assertEquals(1, mgr.count())
            mgr.create("b", 2)
            assertEquals(2, mgr.count())
            mgr.create("c", 3)
            assertEquals(3, mgr.count())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Sequential stress: many operations in sequence
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `100 sequential adds`() {
        Calculator(0).use { calc ->
            for (i in 1..100) calc.add(1)
            assertEquals(100, calc.current)
        }
    }

    @Test
    fun `50 callback invocations`() {
        Calculator(0).use { calc ->
            val values = mutableListOf<Int>()
            for (i in 1..50) {
                calc.add(1)
                calc.onValueChanged { values.add(it) }
            }
            assertEquals(50, values.size)
            assertEquals(50, values.last())
        }
    }

    @Test
    fun `20 data class roundtrips`() {
        Calculator(0).use { calc ->
            for (i in 1..20) {
                calc.setFromNamed(NamedValue("iter$i", i))
                val nv = calc.getNamedValue()
                assertEquals("iter$i", nv.name)
                assertEquals(i, nv.value)
            }
        }
    }

    @Test
    fun `10 exception recoveries`() {
        Calculator(10).use { calc ->
            for (i in 1..10) {
                assertFailsWith<KotlinNativeException> { calc.divide(0) }
                assertEquals(10, calc.current)
            }
            // Still works after all exceptions
            assertEquals(5, calc.divide(2))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase 4: Callbacks / Lambdas (FFM upcall stubs)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `callback (Int) to Unit - receives current value`() {
        Calculator(42).use { calc ->
            var received = -1
            calc.onValueChanged { value -> received = value }
            assertEquals(42, received)
        }
    }

    @Test
    fun `callback (Int) to Unit - zero value`() {
        Calculator(0).use { calc ->
            var received = -1
            calc.onValueChanged { value -> received = value }
            assertEquals(0, received)
        }
    }

    @Test
    fun `callback (Int) to Unit - negative value`() {
        Calculator(-5).use { calc ->
            var received = 999
            calc.onValueChanged { value -> received = value }
            assertEquals(-5, received)
        }
    }

    @Test
    fun `transform with (Int) to Int lambda - double`() {
        Calculator(10).use { calc ->
            val result = calc.transform { it * 2 }
            assertEquals(20, result)
            assertEquals(20, calc.current)
        }
    }

    @Test
    fun `transform with (Int) to Int lambda - negate`() {
        Calculator(7).use { calc ->
            val result = calc.transform { -it }
            assertEquals(-7, result)
        }
    }

    @Test
    fun `transform with (Int) to Int lambda - add constant`() {
        Calculator(10).use { calc ->
            val result = calc.transform { it + 100 }
            assertEquals(110, result)
        }
    }

    @Test
    fun `compute with (Int, Int) to Int - addition`() {
        Calculator(0).use { calc ->
            val result = calc.compute(3, 4) { a, b -> a + b }
            assertEquals(7, result)
            assertEquals(7, calc.current)
        }
    }

    @Test
    fun `compute with (Int, Int) to Int - multiplication`() {
        Calculator(0).use { calc ->
            val result = calc.compute(6, 7) { a, b -> a * b }
            assertEquals(42, result)
        }
    }

    @Test
    fun `compute with (Int, Int) to Int - subtraction`() {
        Calculator(0).use { calc ->
            val result = calc.compute(10, 3) { a, b -> a - b }
            assertEquals(7, result)
        }
    }

    @Test
    fun `checkWith (Int) to Boolean - true`() {
        Calculator(10).use { calc ->
            assertTrue(calc.checkWith { it > 0 })
        }
    }

    @Test
    fun `checkWith (Int) to Boolean - false`() {
        Calculator(-5).use { calc ->
            assertFalse(calc.checkWith { it > 0 })
        }
    }

    @Test
    fun `checkWith (Int) to Boolean - equality check`() {
        Calculator(42).use { calc ->
            assertTrue(calc.checkWith { it == 42 })
            assertFalse(calc.checkWith { it == 0 })
        }
    }

    @Test
    fun `callbacks work after exception recovery`() {
        Calculator(10).use { calc ->
            assertFailsWith<KotlinNativeException> { calc.divide(0) }
            // Callback should still work after exception
            var received = -1
            calc.onValueChanged { value -> received = value }
            assertEquals(10, received)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase 4b: String in callbacks
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `callback (String) to Unit - receives description`() {
        Calculator(42).use { calc ->
            var received = ""
            calc.withDescription { desc -> received = desc }
            assertEquals("Calculator(current=42)", received)
        }
    }

    @Test
    fun `callback (String) to Unit - empty accumulator`() {
        Calculator(0).use { calc ->
            var received = ""
            calc.withDescription { desc -> received = desc }
            assertEquals("Calculator(current=0)", received)
        }
    }

    @Test
    fun `callback (String) to Unit - negative accumulator`() {
        Calculator(-7).use { calc ->
            var received = ""
            calc.withDescription { desc -> received = desc }
            assertEquals("Calculator(current=-7)", received)
        }
    }

    @Test
    fun `callback (Int) to String - format value`() {
        Calculator(42).use { calc ->
            val result = calc.formatWith { v -> "Value is $v" }
            assertEquals("Value is 42", result)
        }
    }

    @Test
    fun `callback (Int) to String - negative value`() {
        Calculator(-5).use { calc ->
            val result = calc.formatWith { v -> "[$v]" }
            assertEquals("[-5]", result)
        }
    }

    @Test
    fun `callback (Int) to String - empty result`() {
        Calculator(0).use { calc ->
            val result = calc.formatWith { "" }
            assertEquals("", result)
        }
    }

    @Test
    fun `callback (String) to String - transform label`() {
        Calculator(0).use { calc ->
            calc.label = "hello"
            val result = calc.transformLabel { it.uppercase() }
            assertEquals("HELLO", result)
            assertEquals("HELLO", calc.label)
        }
    }

    @Test
    fun `callback (String) to String - prepend prefix`() {
        Calculator(0).use { calc ->
            calc.label = "world"
            val result = calc.transformLabel { "Hello $it" }
            assertEquals("Hello world", result)
        }
    }

    @Test
    fun `callback (String, Int) to Unit - matching keyword`() {
        Calculator(0).use { calc ->
            calc.label = "hello world"
            var receivedLabel = ""
            var receivedFound = -1
            calc.findAndReport("hello") { label, found ->
                receivedLabel = label
                receivedFound = found
            }
            assertEquals("hello world", receivedLabel)
            assertEquals(1, receivedFound)
        }
    }

    @Test
    fun `callback (String, Int) to Unit - no match`() {
        Calculator(0).use { calc ->
            calc.label = "hello"
            var receivedFound = -1
            calc.findAndReport("xyz") { _, found ->
                receivedFound = found
            }
            assertEquals(0, receivedFound)
        }
    }

    @Test
    fun `callback (String, Int) to Unit - empty label`() {
        Calculator(0).use { calc ->
            var receivedLabel = "initial"
            calc.findAndReport("test") { label, _ ->
                receivedLabel = label
            }
            assertEquals("", receivedLabel)
        }
    }

    @Test
    fun `multiple callbacks in sequence`() {
        Calculator(5).use { calc ->
            val values = mutableListOf<Int>()
            calc.onValueChanged { values.add(it) }
            calc.add(3)
            calc.onValueChanged { values.add(it) }
            assertEquals(listOf(5, 8), values)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OBJECT IN CALLBACKS
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `obj cb - onSelfReady receives self`() {
        Calculator(42).use { calc ->
            var received: Calculator? = null
            calc.onSelfReady { received = it }
            assertEquals(42, received?.current)
            received?.close()
        }
    }

    @Test fun `obj cb - onSelfReady modify through callback`() {
        Calculator(10).use { calc ->
            calc.onSelfReady { it.add(5) }
            // callback received a handle to the same native object
            assertEquals(15, calc.current)
        }
    }

    @Test fun `obj cb - transformWith two instances`() {
        Calculator(10).use { a ->
            Calculator(20).use { b ->
                val result = a.transformWith(b) { x, y -> x.current + y.current }
                assertEquals(30, result)
                assertEquals(30, a.current)
            }
        }
    }

    @Test fun `obj cb - transformWith multiply currents`() {
        Calculator(3).use { a ->
            Calculator(7).use { b ->
                assertEquals(21, a.transformWith(b) { x, y -> x.current * y.current })
            }
        }
    }

    @Test fun `obj cb - createVia factory`() {
        Calculator(42).use { calc ->
            val created = calc.createVia { value -> Calculator(value * 2) }
            assertEquals(84, created.current)
            created.close()
        }
    }

    @Test fun `obj cb - createVia factory zero`() {
        Calculator(0).use { calc ->
            val created = calc.createVia { Calculator(it + 1) }
            assertEquals(1, created.current)
            created.close()
        }
    }

    @Test fun `obj cb - onSelfReady multiple times`() {
        Calculator(1).use { calc ->
            val values = mutableListOf<Int>()
            repeat(5) {
                calc.onSelfReady { values.add(it.current) }
                calc.add(1)
            }
            assertEquals(listOf(1, 2, 3, 4, 5), values)
        }
    }

}
