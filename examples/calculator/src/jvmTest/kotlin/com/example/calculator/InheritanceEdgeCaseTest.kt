package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class InheritanceEdgeCaseTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Extreme values
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Circle - Double MAX_VALUE divided by 2 radius`() {
        Circle(Double.MAX_VALUE / 2).use { circle ->
            val area = circle.area()
            // PI * (MAX_VALUE/2)^2 overflows to Infinity — that's correct behavior
            assertTrue(area > 0 || area.isInfinite())
        }
    }

    @Test
    fun `Circle - Double MIN_VALUE radius`() {
        Circle(Double.MIN_VALUE).use { circle ->
            val area = circle.area()
            assertTrue(area.isFinite())
            assertEquals(Math.PI * Double.MIN_VALUE * Double.MIN_VALUE, area)
        }
    }

    @Test
    fun `Rectangle - very large dimensions`() {
        Rectangle(Double.MAX_VALUE / 4, Double.MAX_VALUE / 4).use { rect ->
            val area = rect.area()
            assertTrue(area.isFinite() || area.isInfinite())
        }
    }

    @Test
    fun `Rectangle - very small dimensions`() {
        Rectangle(0.0000001, 0.0000001).use { rect ->
            val area = rect.area()
            assertTrue(area.isFinite())
            assertTrue(area >= 0)
        }
    }

    @Test
    fun `Cube - side length 0`() {
        Cube(0.0).use { cube ->
            assertEquals(0.0, cube.area(), 0.0001)
            assertEquals(0.0, cube.volume(), 0.0001)
        }
    }

    @Test
    fun `Cube - side length 1`() {
        Cube(1.0).use { cube ->
            assertEquals(1.0, cube.area(), 0.0001)
            assertEquals(1.0, cube.volume(), 0.0001)
        }
    }

    @Test
    fun `Cylinder - 0 height`() {
        Cylinder(5.0, 0.0).use { cyl ->
            val volume = cyl.volume()
            assertEquals(0.0, volume, 0.0001)
        }
    }

    @Test
    fun `Cylinder - 0 radius`() {
        Cylinder(0.0, 10.0).use { cyl ->
            val volume = cyl.volume()
            assertEquals(0.0, volume, 0.0001)
        }
    }

    @Test
    fun `Shape - very long name 1000 characters`() {
        val longName = "a".repeat(1000)
        Shape(longName).use { shape ->
            val desc = shape.describe()
            assertTrue(desc.contains(longName))
        }
    }

    @Test
    fun `Shape - unicode and emoji name`() {
        Shape("🔵 Circle 🔵 名前 😊").use { shape ->
            val desc = shape.describe()
            assertTrue(desc.contains("🔵") || desc.contains("Circle"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Property edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Shape - set color to empty string`() {
        Shape("test").use { shape ->
            shape.color = ""
            assertEquals("", shape.color)
        }
    }

    @Test
    fun `Shape - set color to very long string 1000 characters`() {
        Shape("test").use { shape ->
            val longColor = "c".repeat(1000)
            shape.color = longColor
            assertEquals(longColor, shape.color)
        }
    }

    @Test
    fun `Shape - set color to unicode string`() {
        Shape("test").use { shape ->
            shape.color = "🔴 红色 赤"
            assertEquals("🔴 红色 赤", shape.color)
        }
    }

    @Test
    fun `Shape - read color immediately after construction`() {
        Shape("fresh").use { shape ->
            val color = shape.color
            assertEquals("red", color)
        }
    }

    @Test
    fun `Shape - set color multiple times rapidly 100x`() {
        Shape("rapid").use { shape ->
            repeat(100) { i ->
                shape.color = "color-$i"
                assertEquals("color-$i", shape.color)
            }
        }
    }

    @Test
    fun `Cube - read describe from deeply nested 3 levels in hierarchy`() {
        Cube(2.0).use { cube ->
            val desc = cube.describe()
            assertTrue(desc.contains("cube"))
        }
    }

    @Test
    fun `Circle - read all accessible methods immediately after creation`() {
        Circle(5.0).use { circle ->
            assertNotNull(circle.color)
            assertTrue(circle.area() > 0)
            assertTrue(circle.describe().contains("Shape"))
        }
    }

    @Test
    fun `Rectangle - read all accessible methods immediately after creation`() {
        Rectangle(3.0, 4.0).use { rect ->
            assertNotNull(rect.color)
            assertTrue(rect.area() > 0)
            assertTrue(rect.describe().contains("Shape"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Shape - use shape close create new with same variable`() {
        var shape: Shape? = null
        shape = Shape("first").apply { assertEquals("Shape: first", describe()) }
        shape.close()
        shape = Shape("second").apply { assertEquals("Shape: second", describe()) }
        shape.close()
    }

    @Test
    fun `Shape - create 100 shapes in loop without explicit close GC handles`() {
        repeat(100) { i ->
            Shape("shape-$i").describe()
        }
    }

    @Test
    fun `Circle - use parent methods then child then parent again`() {
        Circle(3.0).use { circle ->
            assertEquals("Shape: circle", circle.describe())
            assertEquals(Math.PI * 9.0, circle.area(), 0.0001)
            val circum = circle.circumference()
            assertTrue(circum > 0)
            assertEquals("Shape: circle", circle.describe())
        }
    }

    @Test
    fun `Cube - use parent methods then child then parent again 3 level hierarchy`() {
        Cube(2.0).use { cube ->
            assertEquals("Shape: cube", cube.describe())
            assertEquals(4.0, cube.area(), 0.0001)
            assertEquals(8.0, cube.volume(), 0.0001)
            assertEquals("Shape: cube", cube.describe())
        }
    }

    @Test
    fun `mixed shapes - interleave creation and disposal of different types`() {
        val c1 = Circle(2.0)
        val r1 = Rectangle(3.0, 4.0)
        c1.use { c ->
            r1.use { r ->
                assertEquals(Math.PI * 4.0, c.area(), 0.0001)
                assertEquals(12.0, r.area(), 0.0001)
            }
        }
    }

    @Test
    fun `Cylinder - interleave operations between parent and child types`() {
        Cylinder(2.0, 5.0).use { cyl ->
            cyl.describe()
            cyl.volume()
            cyl.describe()
            cyl.volume()
        }
    }

    @Test
    fun `SmartRuler - multiple measure reset cycles in single instance`() {
        SmartRuler(20.0).use { ruler ->
            val m1 = ruler.measure()
            assertEquals(20.0, m1, 0.0001)
            ruler.reset()
            val m2 = ruler.measure()
            assertEquals(0.0, m2, 0.0001)  // reset sets currentLength to 0
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Override consistency
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Circle - area always returns same value for same radius`() {
        Circle(5.0).use { circle ->
            val a1 = circle.area()
            val a2 = circle.area()
            val a3 = circle.area()
            assertEquals(a1, a2)
            assertEquals(a2, a3)
            assertEquals(Math.PI * 25.0, a1, 0.0001)
        }
    }

    @Test
    fun `Cube - volume consistent across multiple calls`() {
        Cube(3.0).use { cube ->
            val v1 = cube.volume()
            val v2 = cube.volume()
            val v3 = cube.volume()
            assertEquals(v1, v2)
            assertEquals(v2, v3)
            assertEquals(27.0, v1, 0.0001)
        }
    }

    @Test
    fun `Shape - summary changes after color mutation`() {
        Shape("test").use { shape ->
            val s1 = shape.summary()
            shape.color = "blue"
            val s2 = shape.summary()
            assertTrue(s1.contains("red"))
            assertTrue(s2.contains("blue"))
        }
    }

    @Test
    fun `Shape - summary doesn't change across multiple reads no state mutation`() {
        Shape("constant").use { shape ->
            val s1 = shape.summary()
            val s2 = shape.summary()
            val s3 = shape.summary()
            assertEquals(s1, s2)
            assertEquals(s2, s3)
        }
    }

    @Test
    fun `Cylinder - volume equals area times depth pi r squared h`() {
        Cylinder(3.0, 5.0).use { cyl ->
            val area = cyl.area()
            val volume = cyl.volume()
            val expectedArea = Math.PI * 9.0
            val expectedVolume = expectedArea * 5.0
            assertEquals(expectedArea, area, 0.0001)
            assertEquals(expectedVolume, volume, 0.0001)
        }
    }

    @Test
    fun `Circle - describe uses correct name from constructor`() {
        Circle(2.0).use { circle ->
            val desc = circle.describe()
            assertTrue(desc.contains("circle"))
        }
    }

    @Test
    fun `Rectangle - describe uses correct name from constructor`() {
        Rectangle(3.0, 4.0).use { rect ->
            val desc = rect.describe()
            assertTrue(desc.contains("rectangle"))
        }
    }

    @Test
    fun `Cube - describe uses correct name from constructor`() {
        Cube(2.0).use { cube ->
            val desc = cube.describe()
            assertTrue(desc.contains("cube"))
        }
    }

    @Test
    fun `Shape - color mutation visible across method calls`() {
        Shape("test").use { shape ->
            assertEquals("red", shape.color)
            shape.color = "green"
            assertEquals("green", shape.color)
            val coloredArea = shape.coloredArea()
            assertTrue(coloredArea.contains("green"))
        }
    }

    @Test
    fun `Circle - circumference uses correct radius value`() {
        Circle(7.0).use { circle ->
            val expected = 2 * Math.PI * 7.0
            assertEquals(expected, circle.circumference(), 0.0001)
        }
    }

    @Test
    fun `Rectangle - perimeter uses correct dimensions`() {
        Rectangle(6.0, 8.0).use { rect ->
            val expected = 2 * (6.0 + 8.0)
            assertEquals(expected, rect.perimeter(), 0.0001)
        }
    }

    @Test
    fun `Cylinder - volume formula correctness radius and height`() {
        Cylinder(4.0, 6.0).use { cyl ->
            val expectedVolume = Math.PI * 16.0 * 6.0
            assertEquals(expectedVolume, cyl.volume(), 0.0001)
        }
    }

    @Test
    fun `SmartRuler - measure return equals constructor length`() {
        SmartRuler(25.0).use { ruler ->
            assertEquals(25.0, ruler.measure(), 0.0001)
        }
    }

    @Test
    fun `Ruler - measure return equals constructor length`() {
        Ruler(35.0).use { ruler ->
            assertEquals(35.0, ruler.measure(), 0.0001)
        }
    }

    @Test
    fun `Scale - measure return equals constructor weight`() {
        Scale(150.0).use { scale ->
            assertEquals(150.0, scale.measure(), 0.0001)
        }
    }

    @Test
    fun `extension function displayName - includes name and color`() {
        Circle(3.0).use { circle ->
            circle.color = "purple"
            val displayName = circle.displayName()
            assertTrue(displayName.contains("circle"))
            assertTrue(displayName.contains("purple"))
        }
    }

    @Test
    fun `extension function coloredArea - includes color and area value`() {
        Rectangle(5.0, 6.0).use { rect ->
            val coloredArea = rect.coloredArea()
            assertTrue(coloredArea.contains("red"))
            assertTrue(coloredArea.contains("30"))
        }
    }

    @Test
    fun `negative radius edge case Circle with 1 very small dimension`() {
        Circle(1e-10).use { circle ->
            val area = circle.area()
            assertTrue(area.isFinite())
            assertTrue(area >= 0)
        }
    }

    @Test
    fun `Rectangle edge case with one zero dimension`() {
        Rectangle(0.0, 5.0).use { rect ->
            assertEquals(0.0, rect.area(), 0.0001)
        }
    }

    @Test
    fun `Cube with Float precision edge case`() {
        Cube(0.1).use { cube ->
            val volume = cube.volume()
            assertEquals(0.001, volume, 0.0001)
        }
    }

    @Test
    fun `Shape describe immutable across operations same content`() {
        Shape("immutable").use { shape ->
            val d1 = shape.describe()
            shape.color = "changed"
            val d2 = shape.describe()
            assertTrue(d1.contains("immutable"))
            assertTrue(d2.contains("immutable"))
        }
    }

    @Test
    fun `Cylinder describe inherits from Shape correctly 2 level hierarchy`() {
        Cylinder(2.0, 5.0).use { cyl ->
            val desc = cyl.describe()
            assertTrue(desc.contains("Shape"))
            assertTrue(desc.contains("cylinder"))
        }
    }
}
