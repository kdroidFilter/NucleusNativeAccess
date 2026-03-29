package com.example.calculator

class MathSuite {
    class Adder {
        private var sum = 0
        fun add(value: Int): Int { sum += value; return sum }
        val current: Int get() = sum
        fun reset() { sum = 0 }
    }

    class Multiplier {
        private var product = 1
        fun multiply(value: Int): Int { product *= value; return product }
        val current: Int get() = product
        fun reset() { product = 1 }
    }

    // Nested-nested: MathSuite.Advanced.PowerCalc
    class Advanced {
        class PowerCalc {
            private var value = 1L
            fun power(base: Int, exp: Int): Long {
                var result = 1L
                repeat(exp) { result *= base }
                value = result
                return result
            }
            val current: Long get() = value
            fun reset() { value = 1L }
        }

        class ModCalc {
            fun mod(a: Int, b: Int): Int = a % b
            fun gcd(a: Int, b: Int): Int {
                var x = if (a < 0) -a else a
                var y = if (b < 0) -b else b
                while (y != 0) { val tmp = y; y = x % y; x = tmp }
                return x
            }
        }
    }
}
