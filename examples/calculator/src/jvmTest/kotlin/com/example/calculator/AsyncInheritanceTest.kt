package com.example.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class AsyncInheritanceTest {

    private val N = 15

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrent same instance: methods
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Circle - 15 coroutines calling area on same instance`() = runBlocking {
        Circle(5.0).use { circle ->
            val results = (1..N).map { async(Dispatchers.Default) { circle.area() } }.awaitAll()
            assertEquals(N, results.size)
            val expected = Math.PI * 25.0
            results.forEach { assertEquals(expected, it, 0.0001) }
        }
    }

    @Test
    fun `Circle - 15 coroutines calling circumference on same instance`() = runBlocking {
        Circle(5.0).use { circle ->
            val results = (1..N).map { async(Dispatchers.Default) { circle.circumference() } }.awaitAll()
            assertEquals(N, results.size)
            val expected = 2 * Math.PI * 5.0
            results.forEach { assertEquals(expected, it, 0.0001) }
        }
    }

    @Test
    fun `Rectangle - 15 coroutines calling area on same instance`() = runBlocking {
        Rectangle(4.0, 5.0).use { rect ->
            val results = (1..N).map { async(Dispatchers.Default) { rect.area() } }.awaitAll()
            assertEquals(N, results.size)
            results.forEach { assertEquals(20.0, it, 0.0001) }
        }
    }

    @Test
    fun `Cube - 15 coroutines calling volume on same instance`() = runBlocking {
        Cube(3.0).use { cube ->
            val results = (1..N).map { async(Dispatchers.Default) { cube.volume() } }.awaitAll()
            assertEquals(N, results.size)
            results.forEach { assertEquals(27.0, it, 0.0001) }
        }
    }

    @Test
    fun `Shape - 15 coroutines calling describe on same instance`() = runBlocking {
        Shape("test-shape").use { shape ->
            val results = (1..N).map { async(Dispatchers.Default) { shape.describe() } }.awaitAll()
            assertEquals(N, results.size)
            results.forEach { assertEquals("Shape: test-shape", it) }
        }
    }

    @Test
    fun `Shape - 15 coroutines reading color property on same instance`() = runBlocking {
        Shape("test").use { shape ->
            val results = (1..N).map { async(Dispatchers.Default) { shape.color } }.awaitAll()
            assertEquals(N, results.size)
            results.forEach { assertEquals("red", it) }
        }
    }

    @Test
    fun `Cylinder - 15 coroutines calling volume on same instance`() = runBlocking {
        Cylinder(2.0, 10.0).use { cyl ->
            val results = (1..N).map { async(Dispatchers.Default) { cyl.volume() } }.awaitAll()
            assertEquals(N, results.size)
            val expected = Math.PI * 4.0 * 10.0
            results.forEach { assertEquals(expected, it, 0.0001) }
        }
    }

    @Test
    fun `Shape - 15 coroutines calling summary on same instance`() = runBlocking {
        Shape("summary-test").use { shape ->
            val results = (1..N).map { async(Dispatchers.Default) { shape.summary() } }.awaitAll()
            assertEquals(N, results.size)
            results.forEach { assertTrue(it.contains("summary-test")) }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrent separate instances
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Circle - 15 coroutines each creating own Circle`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Circle((i * 2).toDouble()).use { circle ->
                    val expected = Math.PI * (i * 2).toDouble() * (i * 2).toDouble()
                    assertEquals(expected, circle.area(), 0.0001)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `Rectangle - 15 coroutines each creating own Rectangle`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Rectangle((i * 1).toDouble(), (i * 2).toDouble()).use { rect ->
                    val expected = (i * 1).toDouble() * (i * 2).toDouble()
                    assertEquals(expected, rect.area(), 0.0001)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `Cube - 15 coroutines each creating own Cube`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Cube((i * 1).toDouble()).use { cube ->
                    val expected = (i * 1).toDouble() * (i * 1).toDouble() * (i * 1).toDouble()
                    assertEquals(expected, cube.volume(), 0.0001)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `mixed shapes - 50 coroutines creating Circle Rectangle Cube mix`() = runBlocking {
        (1..50).map { i ->
            async(Dispatchers.Default) {
                when (i % 3) {
                    0 -> Circle(2.0).use { assertEquals(Math.PI * 4.0, it.area(), 0.0001) }
                    1 -> Rectangle(3.0, 4.0).use { assertEquals(12.0, it.area(), 0.0001) }
                    else -> Cube(2.0).use { assertEquals(8.0, it.volume(), 0.0001) }
                }
            }
        }.awaitAll()
    }

    @Test
    fun `Shape - 100 coroutines each creating own Shape`() = runBlocking {
        (1..100).map { i ->
            async(Dispatchers.Default) {
                Shape("shape-$i").use { shape ->
                    assertEquals("Shape: shape-$i", shape.describe())
                    assertEquals(0.0, shape.area())
                }
            }
        }.awaitAll()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrent mixed hierarchy
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `mixed hierarchy - 15 coroutines Circle Rectangle Cube all calling area`() = runBlocking {
        Circle(2.0).use { circle ->
            Rectangle(3.0, 4.0).use { rect ->
                Cube(2.0).use { cube ->
                    val results = (1..N).map { i ->
                        async(Dispatchers.Default) {
                            when (i % 3) {
                                0 -> circle.area()
                                1 -> rect.area()
                                else -> cube.area()
                            }
                        }
                    }.awaitAll()
                    assertEquals(N, results.size)
                    assertEquals(Math.PI * 4.0, results[0], 0.0001)
                    assertEquals(12.0, results[1], 0.0001)
                    assertEquals(4.0, results[2], 0.0001)
                }
            }
        }
    }

    @Test
    fun `mixed hierarchy - 15 coroutines calling describe on different shape types`() = runBlocking {
        Circle(2.0).use { circle ->
            Rectangle(3.0, 4.0).use { rect ->
                Cube(2.0).use { cube ->
                    val results = (1..N).map { i ->
                        async(Dispatchers.Default) {
                            when (i % 3) {
                                0 -> circle.describe()
                                1 -> rect.describe()
                                else -> cube.describe()
                            }
                        }
                    }.awaitAll()
                    assertEquals(N, results.size)
                    assertTrue(results[0].contains("circle"))
                    assertTrue(results[1].contains("rectangle"))
                    assertTrue(results[2].contains("cube"))
                }
            }
        }
    }

    @Test
    fun `mixed hierarchy - 15 coroutines calling summary on different types`() = runBlocking {
        Circle(2.0).use { circle ->
            Rectangle(3.0, 4.0).use { rect ->
                Cube(2.0).use { cube ->
                    val results = (1..N).map { i ->
                        async(Dispatchers.Default) {
                            when (i % 3) {
                                0 -> circle.summary()
                                1 -> rect.summary()
                                else -> cube.summary()
                            }
                        }
                    }.awaitAll()
                    assertEquals(N, results.size)
                    assertTrue(results[0].contains("Circle"))
                    assertTrue(results[1].contains("Shape"))
                    assertTrue(results[2].contains("Cube"))
                }
            }
        }
    }

    @Test
    fun `stress - 50 mixed shapes calling area + describe + summary`() = runBlocking {
        Circle(2.0).use { circle ->
            Rectangle(3.0, 4.0).use { rect ->
                Cube(2.0).use { cube ->
                    (1..50).map { i ->
                        async(Dispatchers.Default) {
                            when (i % 3) {
                                0 -> {
                                    circle.area()
                                    circle.describe()
                                    circle.summary()
                                }
                                1 -> {
                                    rect.area()
                                    rect.describe()
                                    rect.summary()
                                }
                                else -> {
                                    cube.volume()
                                    cube.describe()
                                    cube.summary()
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrent property mutation
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Shape - 15 coroutines all setting color on same shape race condition`() = runBlocking {
        Shape("color-test").use { shape ->
            (1..N).map { i ->
                async(Dispatchers.Default) {
                    shape.color = "color-$i"
                }
            }.awaitAll()
            assertTrue(shape.color.startsWith("color"))
        }
    }

    @Test
    fun `Shape - 15 coroutines each with own shape setting color`() = runBlocking {
        (1..N).map { i ->
            async(Dispatchers.Default) {
                Shape("shape-$i").use { shape ->
                    shape.color = "blue-$i"
                    assertEquals("blue-$i", shape.color)
                }
            }
        }.awaitAll()
    }

    @Test
    fun `Shape - 15 coroutines reading describe while others set color`() = runBlocking {
        Shape("immutable-name").use { shape ->
            val readResults = (1..N).map { _ ->
                async(Dispatchers.Default) { shape.describe() }
            }
            val writeResults = (1..N).map { i ->
                async(Dispatchers.Default) {
                    shape.color = "color-$i"
                }
            }
            val allResults = (readResults + writeResults).awaitAll()
            assertEquals(N * 2, allResults.size)
            readResults.awaitAll().forEach { assertTrue(it.contains("immutable-name")) }
        }
    }

    @Test
    fun `Circle - 15 concurrent area calls return consistent value`() = runBlocking {
        Circle(5.0).use { circle ->
            (1..3).forEach { _ ->
                val results = (1..N).map { async(Dispatchers.Default) { circle.area() } }.awaitAll()
                results.forEach { assertEquals(Math.PI * 25.0, it, 0.0001) }
            }
        }
    }

    @Test
    fun `Cube - 15 concurrent summary calls are consistent`() = runBlocking {
        Cube(3.0).use { cube ->
            val summaries = (1..N).map { async(Dispatchers.Default) { cube.summary() } }.awaitAll()
            summaries.forEach {
                assertTrue(it.contains("Cube"))
                assertTrue(it.contains("vol=27.0"))
            }
        }
    }

    @Test
    fun `Cylinder - 15 concurrent volume calls on same instance`() = runBlocking {
        Cylinder(3.0, 4.0).use { cyl ->
            val results = (1..N).map { async(Dispatchers.Default) { cyl.volume() } }.awaitAll()
            val expected = Math.PI * 9.0 * 4.0
            results.forEach { assertEquals(expected, it, 0.0001) }
        }
    }

    @Test
    fun `Rectangle - 15 concurrent perimeter calls on same instance`() = runBlocking {
        Rectangle(5.0, 7.0).use { rect ->
            val results = (1..N).map { async(Dispatchers.Default) { rect.perimeter() } }.awaitAll()
            results.forEach { assertEquals(24.0, it, 0.0001) }
        }
    }

    @Test
    fun `Shape - 15 concurrent describe calls with color mutation`() = runBlocking {
        Shape("mutation-test").use { shape ->
            val describeResults = (1..N).map {
                async(Dispatchers.Default) { shape.describe() }
            }
            val colorMutateResults = (1..N).map { i ->
                async(Dispatchers.Default) {
                    shape.color = "mutation-color-$i"
                }
            }
            (describeResults + colorMutateResults).awaitAll()
            assertTrue(shape.color.startsWith("mutation-color"))
        }
    }

    @Test
    fun `Cube - stress 100 concurrent volume calls on same instance`() = runBlocking {
        Cube(2.0).use { cube ->
            val results = (1..100).map { async(Dispatchers.Default) { cube.volume() } }.awaitAll()
            results.forEach { assertEquals(8.0, it, 0.0001) }
        }
    }

    @Test
    fun `Rectangle - stress 100 concurrent area calls on same instance`() = runBlocking {
        Rectangle(6.0, 7.0).use { rect ->
            val results = (1..100).map { async(Dispatchers.Default) { rect.area() } }.awaitAll()
            results.forEach { assertEquals(42.0, it, 0.0001) }
        }
    }

    @Test
    fun `Circle - stress 100 concurrent circumference calls on same instance`() = runBlocking {
        Circle(3.0).use { circle ->
            val results = (1..100).map { async(Dispatchers.Default) { circle.circumference() } }.awaitAll()
            val expected = 2 * Math.PI * 3.0
            results.forEach { assertEquals(expected, it, 0.0001) }
        }
    }

    @Test
    fun `Shape hierarchy - 50 coroutines mixed instances and properties`() = runBlocking {
        Circle(2.0).use { circle ->
            Rectangle(3.0, 4.0).use { rect ->
                Cube(2.0).use { cube ->
                    Shape("test").use { shape ->
                        (1..50).map { i ->
                            async(Dispatchers.Default) {
                                when (i % 5) {
                                    0 -> circle.area()
                                    1 -> rect.perimeter()
                                    2 -> cube.volume()
                                    3 -> shape.color = "color-$i"
                                    else -> circle.summary()
                                }
                            }
                        }.awaitAll()
                    }
                }
            }
        }
    }

    @Test
    fun `extension function - 15 coroutines calling displayName on same shape`() = runBlocking {
        Circle(2.0).use { circle ->
            val results = (1..N).map { async(Dispatchers.Default) { circle.displayName() } }.awaitAll()
            assertEquals(N, results.size)
            results.forEach { assertTrue(it.contains("circle")) }
        }
    }

    @Test
    fun `extension function - 15 coroutines calling coloredArea on same circle`() = runBlocking {
        Circle(5.0).use { circle ->
            val results = (1..N).map { async(Dispatchers.Default) { circle.coloredArea() } }.awaitAll()
            assertEquals(N, results.size)
            results.forEach { assertTrue(it.contains("red")) }
        }
    }

    @Test
    fun `Shape - inheritance chain stress test 100 coroutines deep hierarchy`() = runBlocking {
        Cube(3.0).use { cube ->
            (1..100).map { i ->
                async(Dispatchers.Default) {
                    when (i % 4) {
                        0 -> cube.describe()
                        1 -> cube.area()
                        2 -> cube.volume()
                        else -> cube.summary()
                    }
                }
            }.awaitAll()
        }
    }

    @Test
    fun `SmartRuler - 15 concurrent measure calls on same instance`() = runBlocking {
        SmartRuler(10.0).use { ruler ->
            val results = (1..N).map { async(Dispatchers.Default) { ruler.measure() } }.awaitAll()
            assertEquals(N, results.size)
            results.forEach { assertEquals(10.0, it, 0.0001) }
        }
    }

    @Test
    fun `SmartRuler - 15 concurrent reset and measure cycles`() = runBlocking {
        SmartRuler(5.0).use { ruler ->
            (1..N).map { _ ->
                async(Dispatchers.Default) {
                    ruler.reset()
                    ruler.measure()
                }
            }.awaitAll()
        }
    }

    @Test
    fun `Ruler - 15 concurrent measure calls on same instance`() = runBlocking {
        Ruler(15.0).use { ruler ->
            val results = (1..N).map { async(Dispatchers.Default) { ruler.measure() } }.awaitAll()
            assertEquals(N, results.size)
            results.forEach { assertEquals(15.0, it, 0.0001) }
        }
    }

    @Test
    fun `Scale - 15 concurrent measure calls on same instance`() = runBlocking {
        Scale(50.0).use { scale ->
            val results = (1..N).map { async(Dispatchers.Default) { scale.measure() } }.awaitAll()
            assertEquals(N, results.size)
            results.forEach { assertEquals(50.0, it, 0.0001) }
        }
    }
}
