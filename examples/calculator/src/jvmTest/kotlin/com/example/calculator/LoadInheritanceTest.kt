package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoadInheritanceTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // High-volume method calls
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Circle - 100K area calls on same instance`() {
        Circle(5.0).use { circle ->
            repeat(100_000) {
                val area = circle.area()
                assertEquals(Math.PI * 25.0, area, 0.0001)
            }
        }
    }

    @Test
    fun `Rectangle - 100K area calls on same instance`() {
        Rectangle(4.0, 5.0).use { rect ->
            repeat(100_000) {
                val area = rect.area()
                assertEquals(20.0, area, 0.0001)
            }
        }
    }

    @Test
    fun `Cube - 100K volume calls on same instance`() {
        Cube(3.0).use { cube ->
            repeat(100_000) {
                val volume = cube.volume()
                assertEquals(27.0, volume, 0.0001)
            }
        }
    }

    @Test
    fun `Shape - 100K describe calls on same instance string return`() {
        Shape("test").use { shape ->
            repeat(100_000) {
                val desc = shape.describe()
                assertEquals("Shape: test", desc)
            }
        }
    }

    @Test
    fun `Shape - 50K summary calls on same instance string return`() {
        Shape("summary").use { shape ->
            repeat(50_000) {
                val summary = shape.summary()
                assertTrue(summary.contains("summary"))
            }
        }
    }

    @Test
    fun `Circle - 10K circumference calls on same instance`() {
        Circle(5.0).use { circle ->
            repeat(10_000) {
                val circum = circle.circumference()
                assertEquals(2 * Math.PI * 5.0, circum, 0.0001)
            }
        }
    }

    @Test
    fun `Rectangle - 10K perimeter calls on same instance`() {
        Rectangle(4.0, 5.0).use { rect ->
            repeat(10_000) {
                val peri = rect.perimeter()
                assertEquals(18.0, peri, 0.0001)
            }
        }
    }

    @Test
    fun `Cylinder - 50K volume calls on same instance`() {
        Cylinder(2.0, 10.0).use { cyl ->
            val expected = Math.PI * 4.0 * 10.0
            repeat(50_000) {
                val volume = cyl.volume()
                assertEquals(expected, volume, 0.0001)
            }
        }
    }

    @Test
    fun `Shape - 50K describe calls mixed hierarchy circle rectangle cube`() {
        Circle(2.0).use { circle ->
            Rectangle(3.0, 4.0).use { rect ->
                Cube(2.0).use { cube ->
                    repeat(50_000) {
                        when (it % 3) {
                            0 -> assertEquals("Shape: circle", circle.describe())
                            1 -> assertEquals("Shape: rectangle", rect.describe())
                            else -> assertEquals("Shape: cube", cube.describe())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Cube - 100K area calls on same instance face area`() {
        Cube(3.0).use { cube ->
            repeat(100_000) {
                val area = cube.area()
                assertEquals(9.0, area, 0.0001)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // High-volume create-close cycles
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Circle - 10K create-use-close cycles`() {
        repeat(10_000) { i ->
            Circle((i % 10 + 1).toDouble()).use { circle ->
                val area = circle.area()
                assertTrue(area > 0)
            }
        }
    }

    @Test
    fun `Rectangle - 10K create-use-close cycles`() {
        repeat(10_000) { i ->
            Rectangle((i % 10 + 1).toDouble(), (i % 8 + 1).toDouble()).use { rect ->
                val area = rect.area()
                assertTrue(area > 0)
            }
        }
    }

    @Test
    fun `Cube - 10K create-use-close cycles`() {
        repeat(10_000) { i ->
            Cube((i % 10 + 1).toDouble()).use { cube ->
                val volume = cube.volume()
                assertTrue(volume > 0)
            }
        }
    }

    @Test
    fun `Cylinder - 5K create-use-close cycles`() {
        repeat(5_000) { i ->
            Cylinder((i % 10 + 1).toDouble(), (i % 8 + 1).toDouble()).use { cyl ->
                val volume = cyl.volume()
                assertTrue(volume > 0)
            }
        }
    }

    @Test
    fun `SmartRuler - 5K create-use-close cycles`() {
        repeat(5_000) { i ->
            SmartRuler((i % 20 + 1).toDouble()).use { ruler ->
                val measure = ruler.measure()
                assertTrue(measure > 0)
            }
        }
    }

    @Test
    fun `mixed shapes - 5K create-use-close cycles circle rectangle cube`() {
        repeat(5_000) { i ->
            when (i % 3) {
                0 -> Circle(5.0).use { c -> assertTrue(c.area() > 0) }
                1 -> Rectangle(3.0, 4.0).use { r -> assertTrue(r.area() > 0) }
                else -> Cube(2.0).use { c -> assertTrue(c.volume() > 0) }
            }
        }
    }

    @Test
    fun `MeasurementFactory - 1K create-ruler-close cycles`() {
        repeat(1_000) { i ->
            MeasurementFactory().use { factory ->
                val ruler = factory.createRuler((i % 20 + 1).toDouble())
                val measure = ruler.measure()
                assertTrue(measure > 0)
                ruler.close()
            }
        }
    }

    @Test
    fun `Shape - 10K create-use-close cycles with property mutation`() {
        repeat(10_000) { i ->
            Shape("shape-$i").use { shape ->
                shape.color = "color-${i % 10}"
                val desc = shape.describe()
                assertTrue(desc.contains("shape"))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // High-volume property operations
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Shape - 50K color reads on same instance`() {
        Shape("test").use { shape ->
            repeat(50_000) {
                val color = shape.color
                assertEquals("red", color)
            }
        }
    }

    @Test
    fun `Shape - 50K color writes on same instance`() {
        Shape("test").use { shape ->
            repeat(50_000) { i ->
                shape.color = "color-${i % 100}"
                assertEquals("color-${i % 100}", shape.color)
            }
        }
    }

    @Test
    fun `Shape - 10K color reads then describe calls on same instance`() {
        Shape("immutable").use { shape ->
            repeat(10_000) {
                val color = shape.color
                assertEquals("red", color)
                shape.describe()
            }
        }
    }

    @Test
    fun `Ruler - 10K measure calls on same instance`() {
        Ruler(25.0).use { ruler ->
            repeat(10_000) {
                val measure = ruler.measure()
                assertEquals(25.0, measure, 0.0001)
            }
        }
    }

    @Test
    fun `Scale - 10K measure calls on same instance`() {
        Scale(100.0).use { scale ->
            repeat(10_000) {
                val measure = scale.measure()
                assertEquals(100.0, measure, 0.0001)
            }
        }
    }

    @Test
    fun `SmartRuler - 10K measure and reset cycles`() {
        SmartRuler(30.0).use { ruler ->
            repeat(10_000) {
                ruler.reset()
                val measure = ruler.measure()
                assertEquals(30.0, measure, 0.0001)
            }
        }
    }

    @Test
    fun `Circle - 10K displayName extension function calls`() {
        Circle(5.0).use { circle ->
            repeat(10_000) {
                val displayName = circle.displayName()
                assertTrue(displayName.contains("circle"))
            }
        }
    }

    @Test
    fun `Circle - 10K coloredArea extension function calls`() {
        Circle(3.0).use { circle ->
            repeat(10_000) {
                val coloredArea = circle.coloredArea()
                assertTrue(coloredArea.contains("red"))
            }
        }
    }

    @Test
    fun `Shape - 10K coloredArea extension calls mixed types`() {
        Circle(2.0).use { circle ->
            Rectangle(3.0, 4.0).use { rect ->
                Cube(2.0).use { cube ->
                    repeat(10_000) { i ->
                        when (i % 3) {
                            0 -> circle.coloredArea()
                            1 -> rect.coloredArea()
                            else -> cube.coloredArea()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Shape - 50K color read-write alternation on same instance`() {
        Shape("test").use { shape ->
            repeat(50_000) { i ->
                if (i % 2 == 0) {
                    shape.color = "color-${i % 100}"
                } else {
                    shape.color // Just read it
                }
            }
        }
    }

    @Test
    fun `Rectangle - 50K perimeter calls for load`() {
        Rectangle(10.0, 15.0).use { rect ->
            repeat(50_000) {
                val peri = rect.perimeter()
                assertEquals(50.0, peri, 0.0001)
            }
        }
    }

    @Test
    fun `Cube - 50K summary calls for load`() {
        Cube(4.0).use { cube ->
            repeat(50_000) {
                val summary = cube.summary()
                assertTrue(summary.contains("Cube"))
            }
        }
    }

    @Test
    fun `Circle - 50K summary calls for load`() {
        Circle(7.0).use { circle ->
            repeat(50_000) {
                val summary = circle.summary()
                assertTrue(summary.contains("Circle"))
            }
        }
    }

    @Test
    fun `Shape hierarchy - 100K mixed method calls on diverse instances`() {
        Circle(3.0).use { circle ->
            Rectangle(4.0, 5.0).use { rect ->
                Cube(2.0).use { cube ->
                    Cylinder(2.0, 8.0).use { cyl ->
                        repeat(100_000) { i ->
                            when (i % 10) {
                                0 -> circle.area()
                                1 -> rect.area()
                                2 -> cube.volume()
                                3 -> cyl.volume()
                                4 -> circle.describe()
                                5 -> rect.perimeter()
                                6 -> cube.summary()
                                7 -> circle.circumference()
                                8 -> cyl.describe()
                                else -> cube.area()
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Ruler and Scale - 10K alternating measure calls`() {
        Ruler(20.0).use { ruler ->
            Scale(80.0).use { scale ->
                repeat(10_000) { i ->
                    if (i % 2 == 0) {
                        ruler.measure()
                    } else {
                        scale.measure()
                    }
                }
            }
        }
    }

    @Test
    fun `SmartRuler - 20K combined measure reset measure cycles`() {
        SmartRuler(15.0).use { ruler ->
            repeat(20_000) { i ->
                if (i % 3 == 0) {
                    ruler.reset()
                }
                ruler.measure()
            }
        }
    }
}
