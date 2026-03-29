package com.example.benchmark

// ── Fibonacci ───────────────────────────────────────────────────────────────

class FibCalculator {
    fun fibRecursive(n: Int): Long {
        if (n <= 1) return n.toLong()
        return fibRecursive(n - 1) + fibRecursive(n - 2)
    }

    fun fibIterative(n: Int): Long {
        if (n <= 1) return n.toLong()
        var a = 0L
        var b = 1L
        repeat(n - 1) { val tmp = a + b; a = b; b = tmp }
        return b
    }
}

// ── Pi calculation (Leibniz series) ─────────────────────────────────────────

class PiCalculator {
    fun leibniz(iterations: Int): Double {
        var sum = 0.0
        for (i in 0 until iterations) {
            sum += (if (i % 2 == 0) 1.0 else -1.0) / (2 * i + 1)
        }
        return sum * 4
    }

    fun nilakantha(iterations: Int): Double {
        var pi = 3.0
        var sign = 1.0
        for (i in 1..iterations) {
            val n = (2.0 * i) * (2.0 * i + 1) * (2.0 * i + 2)
            pi += sign * 4.0 / n
            sign = -sign
        }
        return pi
    }

    fun monteCarloPi(samples: Int, seed: Long = 12345L): Double {
        var inside = 0
        var state = seed
        for (i in 0 until samples) {
            state = state * 6364136223846793005L + 1442695040888963407L
            val x = (state.ushr(33).toDouble()) / Int.MAX_VALUE
            state = state * 6364136223846793005L + 1442695040888963407L
            val y = (state.ushr(33).toDouble()) / Int.MAX_VALUE
            if (x * x + y * y <= 1.0) inside++
        }
        return 4.0 * inside / samples
    }
}

// ── Array/collection processing ─────────────────────────────────────────────

class ArrayProcessor {
    fun sumArray(size: Int): Long {
        var sum = 0L
        for (i in 0 until size) sum += i
        return sum
    }

    fun bubbleSortSize(size: Int): Int {
        val arr = IntArray(size) { size - it }
        for (i in 0 until size) {
            for (j in 0 until size - i - 1) {
                if (arr[j] > arr[j + 1]) {
                    val tmp = arr[j]; arr[j] = arr[j + 1]; arr[j + 1] = tmp
                }
            }
        }
        return arr[0] // should be 1
    }

    fun sumList(values: List<Int>): Long = values.fold(0L) { acc, v -> acc + v }

    fun filterAndSum(values: List<Int>): Long = values.filter { it % 2 == 0 }.fold(0L) { acc, v -> acc + v }
}

// ── String processing ───────────────────────────────────────────────────────

class StringProcessor {
    fun concatLoop(iterations: Int): Int {
        var s = ""
        for (i in 0 until iterations) s += i.toString()
        return s.length
    }

    fun reverseString(s: String): String = s.reversed()

    fun countChars(s: String, c: String): Int = s.count { it.toString() == c }
}

// ── Object allocation stress ────────────────────────────────────────────────

data class Vec2(val x: Double, val y: Double)

class AllocationBench {
    fun allocatePoints(count: Int): Double {
        var sumX = 0.0
        for (i in 0 until count) {
            val p = Vec2(i.toDouble(), i * 2.0)
            sumX += p.x
        }
        return sumX
    }

    fun getVec(): Vec2 = Vec2(1.0, 2.0)
    fun sumVec(v: Vec2): Double = v.x + v.y
}
