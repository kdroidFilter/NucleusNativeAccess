package com.example.rustbenchmark

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Benchmarks comparing Rust FFM native calls vs pure JVM implementations.
 *
 * Mirrors the Kotlin/Native benchmark for direct comparison:
 *   Rust (via FFM) vs Kotlin/Native (via FFM) vs pure JVM
 *
 * Methodology:
 * - 3 warmup iterations discarded, then 5 measured iterations averaged
 * - Both native (Rust via FFM) and pure JVM (equivalent Kotlin code) are measured
 * - Ratio = native_time / jvm_time (>1 means native is slower due to FFM overhead)
 */
class BenchmarkTest {

    companion object {
        private const val WARMUP = 3
        private const val ITERATIONS = 5
    }

    private inline fun bench(warmup: Int = WARMUP, iters: Int = ITERATIONS, block: () -> Unit): Double {
        repeat(warmup) { block() }
        val times = (1..iters).map {
            val start = System.nanoTime()
            block()
            (System.nanoTime() - start) / 1_000_000.0
        }
        return times.average()
    }

    private fun memUsedKB(block: () -> Unit): Long {
        val rt = Runtime.getRuntime()
        rt.gc(); Thread.sleep(50)
        val before = rt.totalMemory() - rt.freeMemory()
        block()
        val after = rt.totalMemory() - rt.freeMemory()
        return (after - before) / 1024
    }

    private fun report(name: String, nativeMs: Double, jvmMs: Double) {
        val ratio = nativeMs / jvmMs
        val label = if (ratio > 1) "Rust slower" else "Rust faster"
        println("  %-40s  rust=%7.2f ms  jvm=%7.2f ms  ratio=%.2fx (%s)".format(name, nativeMs, jvmMs, ratio, label))
    }

    // ── Pure JVM equivalents ────────────────────────────────────────────────

    private fun jvmFibRecursive(n: Int): Long {
        if (n <= 1) return n.toLong()
        return jvmFibRecursive(n - 1) + jvmFibRecursive(n - 2)
    }

    private fun jvmFibIterative(n: Int): Long {
        if (n <= 1) return n.toLong()
        var a = 0L; var b = 1L
        repeat(n - 1) { val tmp = a + b; a = b; b = tmp }
        return b
    }

    private fun jvmLeibniz(iterations: Int): Double {
        var sum = 0.0
        for (i in 0 until iterations) sum += (if (i % 2 == 0) 1.0 else -1.0) / (2 * i + 1)
        return sum * 4
    }

    private fun jvmSumArray(size: Int): Long {
        var sum = 0L; for (i in 0 until size) sum += i; return sum
    }

    private fun jvmBubbleSort(size: Int): Int {
        val arr = IntArray(size) { size - it }
        for (i in 0 until size) for (j in 0 until size - i - 1) {
            if (arr[j] > arr[j + 1]) { val tmp = arr[j]; arr[j] = arr[j + 1]; arr[j + 1] = tmp }
        }
        return arr[0]
    }

    private fun jvmConcatLoop(iterations: Int): Int {
        var s = ""; for (i in 0 until iterations) s += i.toString(); return s.length
    }

    // ── Benchmark tests ─────────────────────────────────────────────────────

    @Test fun `benchmark - fibonacci recursive (n=35)`() {
        println("\n=== RUST BENCHMARKS ===")
        val n = 35
        val nativeMs = bench { FibCalculator().use { it.fib_recursive(n) } }
        val jvmMs = bench { jvmFibRecursive(n) }
        report("fib_recursive(35)", nativeMs, jvmMs)
        assertTrue(true)
    }

    @Test fun `benchmark - fibonacci iterative (n=1M)`() {
        val n = 1_000_000
        val nativeMs = bench { FibCalculator().use { it.fib_iterative(n) } }
        val jvmMs = bench { jvmFibIterative(n) }
        report("fib_iterative(1M)", nativeMs, jvmMs)
        assertTrue(true)
    }

    @Test fun `benchmark - pi leibniz (10M iterations)`() {
        val n = 10_000_000
        val nativeMs = bench { PiCalculator().use { it.leibniz(n) } }
        val jvmMs = bench { jvmLeibniz(n) }
        report("pi_leibniz(10M)", nativeMs, jvmMs)
        assertTrue(true)
    }

    @Test fun `benchmark - sum array (10M)`() {
        val n = 10_000_000
        val nativeMs = bench { ArrayProcessor().use { it.sum_array(n) } }
        val jvmMs = bench { jvmSumArray(n) }
        report("sum_array(10M)", nativeMs, jvmMs)
        assertTrue(true)
    }

    @Test fun `benchmark - bubble sort (5K)`() {
        val n = 5_000
        val nativeMs = bench { ArrayProcessor().use { it.bubble_sort_size(n) } }
        val jvmMs = bench { jvmBubbleSort(n) }
        report("bubble_sort(5K)", nativeMs, jvmMs)
        assertTrue(true)
    }

    @Test fun `benchmark - string concat (10K)`() {
        val n = 10_000
        val nativeMs = bench { StringProcessor().use { it.concat_loop(n) } }
        val jvmMs = bench { jvmConcatLoop(n) }
        report("string_concat(10K)", nativeMs, jvmMs)
        assertTrue(true)
    }

    @Test fun `benchmark - FFM call overhead (100K simple calls)`() {
        val nativeMs = bench {
            FibCalculator().use { calc ->
                repeat(100_000) { calc.fib_iterative(1) }
            }
        }
        val jvmMs = bench {
            repeat(100_000) { jvmFibIterative(1) }
        }
        report("ffm_overhead(100K calls)", nativeMs, jvmMs)
        assertTrue(true)
    }

    @Test fun `benchmark - object create-close (10K cycles)`() {
        val nativeMs = bench {
            repeat(10_000) { FibCalculator().use { it.fib_iterative(10) } }
        }
        val jvmMs = bench {
            repeat(10_000) { jvmFibIterative(10) }
        }
        report("create_close(10K)", nativeMs, jvmMs)
        assertTrue(true)
    }

    @Test fun `benchmark - string return (10K calls)`() {
        val nativeMs = bench {
            StringProcessor().use { proc ->
                repeat(10_000) { proc.reverse_string("hello world benchmark test") }
            }
        }
        val jvmMs = bench {
            repeat(10_000) { "hello world benchmark test".reversed() }
        }
        report("string_return(10K)", nativeMs, jvmMs)
        assertTrue(true)
    }

    @Test fun `benchmark - data class return (10K calls)`() {
        val nativeMs = bench {
            AllocationBench().use { ab ->
                repeat(10_000) { ab.get_vec() }
            }
        }
        val jvmMs = bench {
            repeat(10_000) { Vec2(1.0, 2.0) }
        }
        report("dc_return(10K)", nativeMs, jvmMs)
        assertTrue(true)
    }

    @Test fun `benchmark - data class param (10K calls)`() {
        val nativeMs = bench {
            AllocationBench().use { ab ->
                val v = Vec2(3.0, 4.0)
                repeat(10_000) { ab.sum_vec(v) }
            }
        }
        val jvmMs = bench {
            repeat(10_000) { 3.0 + 4.0 }
        }
        report("dc_param(10K)", nativeMs, jvmMs)
        assertTrue(true)
    }

    @Test fun `benchmark - memory allocation (point creation 100K)`() {
        val nativeMem = memUsedKB {
            AllocationBench().use { it.allocate_points(100_000) }
        }
        val jvmMem = memUsedKB {
            var sum = 0.0
            repeat(100_000) { i -> sum += i.toDouble() }
        }
        println("  %-40s  rust=%5d KB    jvm=%5d KB".format("mem_alloc(100K points)", nativeMem, jvmMem))
        assertTrue(true)
    }

    // ── Concurrent benchmarks ──────────────────────────────────────────────

    @Test fun `benchmark - concurrent fib (10 threads x 1K)`() {
        val nativeMs = bench {
            val threads = (1..10).map { Thread { FibCalculator().use { c -> repeat(1_000) { c.fib_iterative(100) } } } }
            threads.forEach { it.start() }; threads.forEach { it.join() }
        }
        val jvmMs = bench {
            val threads = (1..10).map { Thread { repeat(1_000) { jvmFibIterative(100) } } }
            threads.forEach { it.start() }; threads.forEach { it.join() }
        }
        report("concurrent_fib(10t x 1K)", nativeMs, jvmMs)
        assertTrue(true)
    }

    @Test fun `benchmark - concurrent string (10 threads x 1K)`() {
        val nativeMs = bench {
            val threads = (1..10).map { Thread { StringProcessor().use { p -> repeat(1_000) { p.reverse_string("benchmark") } } } }
            threads.forEach { it.start() }; threads.forEach { it.join() }
        }
        val jvmMs = bench {
            val threads = (1..10).map { Thread { repeat(1_000) { "benchmark".reversed() } } }
            threads.forEach { it.start() }; threads.forEach { it.join() }
        }
        report("concurrent_string(10t x 1K)", nativeMs, jvmMs)
        assertTrue(true)
    }

    @Test fun `benchmark - concurrent create-close (10 threads x 1K)`() {
        val nativeMs = bench {
            val threads = (1..10).map { Thread { repeat(1_000) { FibCalculator().use { it.fib_iterative(10) } } } }
            threads.forEach { it.start() }; threads.forEach { it.join() }
        }
        val jvmMs = bench {
            val threads = (1..10).map { Thread { repeat(1_000) { jvmFibIterative(10) } } }
            threads.forEach { it.start() }; threads.forEach { it.join() }
        }
        report("concurrent_create(10t x 1K)", nativeMs, jvmMs)
        assertTrue(true)
    }

    @Test fun `benchmark - summary`() {
        println("\n  NOTE: Ratios >1 mean Rust (via FFM) is slower than pure JVM.")
        println("  Heavy compute (fib, pi, sort) runs entirely in Rust — expect ratio ~1 or Rust faster.")
        println("  Frequent small calls have FFM overhead — ratio >1.")
        println("  String/DC marshaling has buffer copy overhead.")
        println("  Compare these results with the Kotlin/Native benchmark for KN vs Rust comparison.")
        println("=== END RUST BENCHMARKS ===\n")
        assertTrue(true)
    }
}
