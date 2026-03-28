package com.example.calculator

// ── Enum ────────────────────────────────────────────────────────────────────

enum class Operation {
    ADD,
    SUBTRACT,
    MULTIPLY,
}

// ── Main class: covers all primitive types, String, enums ───────────────────

class Calculator(initial: Int = 0) {

    private var accumulator: Int = initial

    // ── Int methods ─────────────────────────────────────────────────────────

    fun add(value: Int): Int {
        accumulator += value
        return accumulator
    }

    fun subtract(value: Int): Int {
        accumulator -= value
        return accumulator
    }

    fun multiply(value: Int): Int {
        accumulator *= value
        return accumulator
    }

    fun reset() {
        accumulator = 0
    }

    val current: Int
        get() = accumulator

    // ── All primitive types as params and returns ───────────────────────────

    fun addLong(value: Long): Long = (accumulator + value.toInt()).toLong()
    fun addDouble(value: Double): Double = accumulator.toDouble() + value
    fun addFloat(value: Float): Float = accumulator.toFloat() + value
    fun addShort(value: Short): Short = (accumulator + value).toShort()
    fun addByte(value: Byte): Byte = (accumulator + value).toByte()
    fun isPositive(): Boolean = accumulator > 0
    fun checkFlag(flag: Boolean): Boolean = flag && accumulator > 0

    // ── String methods ──────────────────────────────────────────────────────

    fun describe(): String = "Calculator(current=$accumulator)"
    fun echo(text: String): String = text
    fun concat(a: String, b: String): String = a + b

    // ── Mutable properties ──────────────────────────────────────────────────

    var label: String = ""
    var scale: Double = 1.0
    var enabled: Boolean = true

    // ── Enum support ────────────────────────────────────────────────────────

    var lastOperation: Operation = Operation.ADD

    fun applyOp(op: Operation, value: Int): Int {
        lastOperation = op
        return when (op) {
            Operation.ADD -> add(value)
            Operation.SUBTRACT -> subtract(value)
            Operation.MULTIPLY -> multiply(value)
        }
    }

    fun getLastOp(): Operation = lastOperation

    // ── Companion object ────────────────────────────────────────────────────

    companion object {
        val VERSION: String = "2.0"
        var instanceCount: Int = 0

        fun version(): String = VERSION
        fun create(initial: Int): Calculator {
            instanceCount++
            return Calculator(initial)
        }
    }
}

// ── Object-type tests: class composition ────────────────────────────────────

class CalculatorManager {
    private val calculators = mutableMapOf<String, Calculator>()

    fun create(name: String, initial: Int): Calculator {
        val calc = Calculator(initial)
        calculators[name] = calc
        return calc
    }

    fun get(name: String): Calculator = calculators[name] ?: Calculator(0)

    fun addWith(calc: Calculator, value: Int): Int = calc.add(value)

    fun count(): Int = calculators.size

    fun describe(calc: Calculator): String = calc.describe()
}
