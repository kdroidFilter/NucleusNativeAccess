package com.example.calculator

// ── Data classes ────────────────────────────────────────────────────────────

data class Point(val x: Int, val y: Int)

data class NamedValue(val name: String, val value: Int)

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

    fun divide(divisor: Int): Int {
        require(divisor != 0) { "Division by zero" }
        accumulator /= divisor
        return accumulator
    }

    fun failAlways(): String {
        error("Intentional error for testing")
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

    // ── Nullable types ──────────────────────────────────────────────────────

    var nickname: String? = null

    fun divideOrNull(divisor: Int): Int? = if (divisor != 0) accumulator / divisor else null

    fun describeOrNull(): String? = if (accumulator > 0) "Positive($accumulator)" else null

    fun isPositiveOrNull(): Boolean? = if (accumulator == 0) null else accumulator > 0

    fun findOp(name: String?): Operation? = if (name != null) Operation.entries.find { it.name == name } else null

    fun toLongOrNull(): Long? = if (accumulator != 0) accumulator.toLong() else null

    fun toDoubleOrNull(): Double? = if (accumulator != 0) accumulator.toDouble() else null

    // ── Callback support ──────────────────────────────────────────────────

    fun onValueChanged(callback: (Int) -> Unit) {
        callback(accumulator)
    }

    fun transform(fn: (Int) -> Int): Int {
        accumulator = fn(accumulator)
        return accumulator
    }

    fun compute(a: Int, b: Int, op: (Int, Int) -> Int): Int {
        accumulator = op(a, b)
        return accumulator
    }

    fun checkWith(predicate: (Int) -> Boolean): Boolean {
        return predicate(accumulator)
    }

    // ── Data class support (nativeMain-only) ───────────────────────────────

    fun getPoint(): Point = Point(accumulator, accumulator * 2)

    fun addPoint(p: Point): Int {
        accumulator += p.x + p.y
        return accumulator
    }

    fun getNamedValue(): NamedValue = NamedValue(label.ifEmpty { "default" }, accumulator)

    fun setFromNamed(nv: NamedValue) {
        label = nv.name
        accumulator = nv.value
    }

    // ── Data class support (commonMain) ─────────────────────────────────

    fun getResult(): CalcResult = CalcResult(accumulator, "Result: $accumulator")

    fun applyResult(r: CalcResult): Int {
        accumulator = r.value
        label = r.description
        return accumulator
    }

    // ── Callback support ────────────────────────────────────────────────────

    fun withDescription(callback: (String) -> Unit) {
        callback("Calculator(current=$accumulator)")
    }

    fun formatWith(formatter: (Int) -> String): String {
        return formatter(accumulator)
    }

    fun transformLabel(fn: (String) -> String): String {
        label = fn(label)
        return label
    }

    fun findAndReport(keyword: String, callback: (String, Int) -> Unit) {
        val found = if (label.contains(keyword)) 1 else 0
        callback(label, found)
    }

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

    fun getOrNull(name: String): Calculator? = calculators[name]

    fun addWith(calc: Calculator, value: Int): Int = calc.add(value)

    fun count(): Int = calculators.size

    fun describe(calc: Calculator): String = calc.describe()
}
