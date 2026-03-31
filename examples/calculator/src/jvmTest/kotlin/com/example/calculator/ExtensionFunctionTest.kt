package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtensionFunctionTest {

    // ── Shape.displayName() tests (10 tests) ────────────────────────────────

    @Test fun `Shape displayName - default color`() {
        Shape("shapeName").use { shape ->
            assertEquals("shapeName (red)", shape.displayName())
        }
    }

    @Test fun `Shape displayName - after color change`() {
        Shape("square").use { shape ->
            shape.color = "blue"
            assertEquals("square (blue)", shape.displayName())
        }
    }

    @Test fun `Circle displayName - inherits extension`() {
        Circle(5.0).use { circle ->
            assertEquals("circle (red)", circle.displayName())
        }
    }

    @Test fun `Rectangle displayName - inherits extension`() {
        Rectangle(4.0, 5.0).use { rect ->
            assertEquals("rectangle (red)", rect.displayName())
        }
    }

    @Test fun `Cube displayName - through three levels`() {
        Cube(3.0).use { cube ->
            assertEquals("cube (red)", cube.displayName())
        }
    }

    @Test fun `Cylinder displayName - inherits extension`() {
        Cylinder(2.0, 5.0).use { cyl ->
            assertEquals("cylinder (red)", cyl.displayName())
        }
    }

    @Test fun `Shape displayName - after color change on subclass`() {
        Cylinder(1.0, 2.0).use { cyl ->
            cyl.color = "green"
            assertEquals("cylinder (green)", cyl.displayName())
        }
    }

    @Test fun `Shape displayName - with empty shape name`() {
        Shape("").use { shape ->
            assertEquals(" (red)", shape.displayName())
        }
    }

    @Test fun `SmartRuler displayName - inherits extension`() {
        SmartRuler(100.0).use { ruler ->
            assertEquals("smart-ruler (red)", ruler.displayName())
        }
    }

    @Test fun `Shape displayName - multiple color changes`() {
        Rectangle(2.0, 3.0).use { rect ->
            rect.color = "yellow"
            assertEquals("rectangle (yellow)", rect.displayName())
            rect.color = "purple"
            assertEquals("rectangle (purple)", rect.displayName())
        }
    }

    // ── Shape.coloredArea() tests (10 tests) ────────────────────────────────

    @Test fun `Shape coloredArea - base shape`() {
        Shape("base").use { shape ->
            assertEquals("red: 0.0", shape.coloredArea())
        }
    }

    @Test fun `Circle coloredArea - pi * r²`() {
        Circle(1.0).use { circle ->
            val result = circle.coloredArea()
            assertTrue(result.startsWith("red:"), "Should start with 'red:'")
            assertTrue(result.contains("3.14"), "Should contain pi approximation")
        }
    }

    @Test fun `Rectangle coloredArea - width * height`() {
        Rectangle(4.0, 5.0).use { rect ->
            assertEquals("red: 20.0", rect.coloredArea())
        }
    }

    @Test fun `Shape coloredArea - after color change`() {
        Circle(1.0).use { circle ->
            circle.color = "blue"
            val result = circle.coloredArea()
            assertTrue(result.startsWith("blue:"), "Should start with 'blue:'")
        }
    }

    @Test fun `Cube coloredArea - side²`() {
        Cube(3.0).use { cube ->
            assertEquals("red: 9.0", cube.coloredArea())
        }
    }

    @Test fun `Cylinder coloredArea - pi * r²`() {
        Cylinder(2.0, 5.0).use { cyl ->
            val result = cyl.coloredArea()
            assertTrue(result.startsWith("red:"), "Should start with 'red:'")
        }
    }

    @Test fun `Rectangle coloredArea - 1x1`() {
        Rectangle(1.0, 1.0).use { rect ->
            assertEquals("red: 1.0", rect.coloredArea())
        }
    }

    @Test fun `SmartRuler coloredArea`() {
        SmartRuler(100.0).use { ruler ->
            val result = ruler.coloredArea()
            assertTrue(result.startsWith("red:"), "Should start with 'red:'")
        }
    }

    @Test fun `Shape coloredArea - large circle`() {
        Circle(10.0).use { circle ->
            val result = circle.coloredArea()
            assertTrue(result.startsWith("red:"), "Should start with 'red:'")
            assertTrue(result.contains("314"), "Should approximate 314.16")
        }
    }

    @Test fun `Rectangle coloredArea - precision with fractional values`() {
        Rectangle(2.5, 4.0).use { rect ->
            assertEquals("red: 10.0", rect.coloredArea())
        }
    }

    // ── Calculator.addTwice() tests (12 tests) ──────────────────────────────

    @Test fun `Calculator addTwice - from zero`() {
        Calculator(0).use { calc ->
            val result = calc.addTwice(5)
            assertEquals(10, result)
            assertEquals(10, calc.current)
        }
    }

    @Test fun `Calculator addTwice - zero addition`() {
        Calculator(0).use { calc ->
            val result = calc.addTwice(0)
            assertEquals(0, result)
            assertEquals(0, calc.current)
        }
    }

    @Test fun `Calculator addTwice - negative value`() {
        Calculator(0).use { calc ->
            val result = calc.addTwice(-3)
            assertEquals(-6, result)
            assertEquals(-6, calc.current)
        }
    }

    @Test fun `Calculator addTwice - after other operations`() {
        Calculator(0).use { calc ->
            calc.add(10)
            calc.subtract(2)  // current = 8
            val result = calc.addTwice(3)  // add(3)=11, add(3)=14
            assertEquals(14, result)
            assertEquals(14, calc.current)
        }
    }

    @Test fun `Calculator addTwice - multiple calls`() {
        Calculator(0).use { calc ->
            calc.addTwice(2)
            val result = calc.addTwice(3)
            assertEquals(10, result)
            assertEquals(10, calc.current)
        }
    }

    @Test fun `Calculator addTwice - large value`() {
        Calculator(0).use { calc ->
            val result = calc.addTwice(1000000)
            assertEquals(2000000, result)
            assertEquals(2000000, calc.current)
        }
    }

    @Test fun `Calculator addTwice - verify both additions occur`() {
        Calculator(100).use { calc ->
            val result = calc.addTwice(1)
            assertEquals(102, result)
            assertEquals(102, calc.current)
        }
    }

    @Test fun `Calculator addTwice - repeated 10 times equals 20 single units`() {
        Calculator(0).use { calc ->
            repeat(10) { calc.addTwice(1) }
            assertEquals(20, calc.current)
        }
    }

    @Test fun `Calculator addTwice - returns final value`() {
        Calculator(50).use { calc ->
            val result = calc.addTwice(25)
            assertEquals(result, calc.current)
        }
    }

    @Test fun `Calculator addTwice - with negative starting value`() {
        Calculator(-10).use { calc ->
            val result = calc.addTwice(5)
            assertEquals(0, result)
            assertEquals(0, calc.current)
        }
    }

    @Test fun `Calculator addTwice - small positive value`() {
        Calculator(0).use { calc ->
            val result = calc.addTwice(1)
            assertEquals(2, result)
            assertEquals(2, calc.current)
        }
    }

    @Test fun `Calculator addTwice - after reset`() {
        Calculator(100).use { calc ->
            calc.reset()
            val result = calc.addTwice(7)
            assertEquals(14, result)
            assertEquals(14, calc.current)
        }
    }

    // ── Calculator.describeWithPrefix() tests (8 tests) ──────────────────────

    @Test fun `Calculator describeWithPrefix - basic prefix`() {
        Calculator(42).use { calc ->
            val result = calc.describeWithPrefix("Value")
            assertTrue(result.startsWith("Value:"), "Should start with 'Value:'")
            assertTrue(result.contains("42"), "Should contain the current value")
        }
    }

    @Test fun `Calculator describeWithPrefix - empty prefix`() {
        Calculator(10).use { calc ->
            val result = calc.describeWithPrefix("")
            assertTrue(result.startsWith(":"), "Should start with ':'")
        }
    }

    @Test fun `Calculator describeWithPrefix - prefix with special characters`() {
        Calculator(15).use { calc ->
            val result = calc.describeWithPrefix("@#$%^&*()")
            assertTrue(result.startsWith("@#$%^&*():"), "Should preserve special chars")
        }
    }

    @Test fun `Calculator describeWithPrefix - after operations`() {
        Calculator(0).use { calc ->
            calc.add(10)
            calc.multiply(3)
            val result = calc.describeWithPrefix("Result")
            assertTrue(result.startsWith("Result:"), "Should start with prefix")
            assertTrue(result.contains("30"), "Should contain result")
        }
    }

    @Test fun `Calculator describeWithPrefix - long prefix`() {
        Calculator(5).use { calc ->
            val longPrefix = "x".repeat(100)
            val result = calc.describeWithPrefix(longPrefix)
            assertTrue(result.startsWith(longPrefix + ":"), "Should include full long prefix")
        }
    }

    @Test fun `Calculator describeWithPrefix - with zero value`() {
        Calculator(0).use { calc ->
            val result = calc.describeWithPrefix("Zero")
            assertTrue(result.startsWith("Zero:"), "Should start with 'Zero:'")
            assertTrue(result.contains("0"), "Should contain 0")
        }
    }

    @Test fun `Calculator describeWithPrefix - with negative value`() {
        Calculator(-25).use { calc ->
            val result = calc.describeWithPrefix("Negative")
            assertTrue(result.startsWith("Negative:"), "Should start with 'Negative:'")
            assertTrue(result.contains("-25"), "Should contain -25")
        }
    }

    @Test fun `Calculator describeWithPrefix - multiple calls with different prefixes`() {
        Calculator(100).use { calc ->
            val result1 = calc.describeWithPrefix("First")
            val result2 = calc.describeWithPrefix("Second")
            assertTrue(result1.startsWith("First:"))
            assertTrue(result2.startsWith("Second:"))
        }
    }

    // ── Edge cases (5 tests) ────────────────────────────────────────────────

    @Test fun `Extension - newly created shape immediately used`() {
        val displayName = Shape("fresh").use { it.displayName() }
        assertEquals("fresh (red)", displayName)
    }

    @Test fun `Extension - on object used for other operations`() {
        Circle(3.0).use { circle ->
            val circumference = circle.circumference()
            val display = circle.displayName()
            assertEquals("circle (red)", display)
            assertTrue(circumference > 0)
        }
    }

    @Test fun `Extension - chaining extensions on calculator`() {
        Calculator(0).use { calc ->
            calc.addTwice(5)
            val described = calc.describeWithPrefix("After addTwice")
            assertTrue(described.startsWith("After addTwice:"))
            assertTrue(described.contains("10"))
        }
    }

    @Test fun `Extension - multiple shape extensions in sequence`() {
        Rectangle(3.0, 4.0).use { rect ->
            val name = rect.displayName()
            val area = rect.coloredArea()
            assertEquals("rectangle (red)", name)
            assertEquals("red: 12.0", area)
        }
    }

    @Test fun `Extension - shape extension after property mutation`() {
        Cube(2.0).use { cube ->
            cube.color = "orange"
            val display = cube.displayName()
            val area = cube.coloredArea()
            assertEquals("cube (orange)", display)
            assertTrue(area.startsWith("orange:"))
        }
    }
}
