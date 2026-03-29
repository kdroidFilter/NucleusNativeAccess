package com.example.calculator

// ── Data classes ────────────────────────────────────────────────────────────

data class Point(val x: Int, val y: Int)

data class NamedValue(val name: String, val value: Int)

data class TaggedPoint(val point: Point, val tag: Operation)

data class Rect(val topLeft: Point, val bottomRight: Point)

data class CalculatorSnapshot(val calc: Calculator, val label: String)

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

    // ── ByteArray support ─────────────────────────────────────────────────

    fun toBytes(): ByteArray {
        val str = accumulator.toString()
        return str.encodeToByteArray()
    }

    fun sumBytes(data: ByteArray): Int {
        accumulator = data.sumOf { it.toInt() }
        return accumulator
    }

    fun reverseBytes(data: ByteArray): ByteArray = data.reversedArray()

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

    fun getTaggedPoint(): TaggedPoint = TaggedPoint(Point(accumulator, accumulator * 2), lastOperation)

    fun setFromTagged(tp: TaggedPoint) {
        accumulator = tp.point.x + tp.point.y
        lastOperation = tp.tag
    }

    fun getRect(): Rect = Rect(Point(0, 0), Point(accumulator, accumulator))

    fun snapshot(): CalculatorSnapshot = CalculatorSnapshot(this, label.ifEmpty { "snapshot" })

    fun restoreFrom(snap: CalculatorSnapshot): Int {
        accumulator = snap.calc.current
        label = snap.label
        return accumulator
    }

    fun getPointOrNull(): Point? = if (accumulator != 0) Point(accumulator, accumulator * 2) else null

    fun addPointOrNull(p: Point?): Int {
        if (p != null) accumulator += p.x + p.y
        return accumulator
    }

    fun getResultOrNull(): CalcResult? = if (accumulator != 0) CalcResult(accumulator, "Result: $accumulator") else null

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

    fun onOperation(callback: (Operation) -> Unit) {
        callback(lastOperation)
    }

    fun chooseOp(chooser: (Int) -> Operation): Operation {
        lastOperation = chooser(accumulator)
        return lastOperation
    }

    fun withLong(fn: (Long) -> Long): Long = fn(accumulator.toLong())
    fun withDouble(fn: (Double) -> Double): Double = fn(accumulator.toDouble())
    fun withFloat(fn: (Float) -> Float): Float = fn(accumulator.toFloat())
    fun withShort(fn: (Short) -> Short): Short = fn(accumulator.toShort())
    fun withByte(fn: (Byte) -> Byte): Byte = fn(accumulator.toByte())

    fun onPointComputed(callback: (Point) -> Unit) {
        callback(Point(accumulator, accumulator * 2))
    }

    fun onResultReady(callback: (CalcResult) -> Unit) {
        callback(CalcResult(accumulator, "Result: $accumulator"))
    }

    fun createPoint(factory: (Int) -> Point): Point {
        return factory(accumulator)
    }

    fun transformPoint(fn: (Point) -> Int): Int {
        accumulator = fn(Point(accumulator, accumulator * 2))
        return accumulator
    }

    fun findAndReport(keyword: String, callback: (String, Int) -> Unit) {
        val found = if (label.contains(keyword)) 1 else 0
        callback(label, found)
    }

    // ── Object in callbacks ────────────────────────────────────────────────────

    fun onSelfReady(callback: (Calculator) -> Unit) {
        callback(this)
    }

    fun transformWith(other: Calculator, fn: (Calculator, Calculator) -> Int): Int {
        accumulator = fn(this, other)
        return accumulator
    }

    fun createVia(factory: (Int) -> Calculator): Calculator = factory(accumulator)

    // ── Collection support ────────────────────────────────────────────────────

    // List<Int>
    fun getScores(): List<Int> = listOf(accumulator, accumulator * 2, accumulator * 3)

    fun sumAll(values: List<Int>): Int {
        accumulator = values.sum()
        return accumulator
    }

    // List<String>
    fun getLabels(): List<String> = listOf(label.ifEmpty { "default" }, "item_$accumulator")

    fun joinLabels(labels: List<String>): String = labels.joinToString(", ")

    // List<Double>
    fun getWeights(): List<Double> = listOf(accumulator.toDouble(), accumulator * 1.5)

    // List<Boolean>
    fun getFlags(): List<Boolean> = listOf(accumulator > 0, accumulator % 2 == 0, label.isNotEmpty())

    // List<Enum>
    fun getOperations(): List<Operation> = Operation.entries.toList()

    fun countOps(ops: List<Operation>): Int = ops.size

    // Set<Int>
    fun getUniqueDigits(): Set<Int> {
        val digits = mutableSetOf<Int>()
        var n = if (accumulator < 0) -accumulator else accumulator
        if (n == 0) digits.add(0)
        while (n > 0) { digits.add(n % 10); n /= 10 }
        return digits
    }

    fun sumUnique(values: Set<Int>): Int {
        accumulator = values.sum()
        return accumulator
    }

    // Map<String, Int>
    fun getMetadata(): Map<String, Int> = mapOf("current" to accumulator, "scale" to scale.toInt())

    fun sumMap(data: Map<String, Int>): Int {
        accumulator = data.values.sum()
        return accumulator
    }

    // List<Long>
    fun getLongScores(): List<Long> = listOf(accumulator.toLong(), accumulator.toLong() * 100_000L)

    fun sumLongs(values: List<Long>): Long = values.sum()

    // List<Float>
    fun getFloatWeights(): List<Float> = listOf(accumulator.toFloat(), accumulator * 0.5f)

    // List<Short>
    fun getShortValues(): List<Short> = listOf(accumulator.toShort(), (accumulator * 2).toShort())

    // List<Byte>
    fun getByteValues(): List<Byte> = listOf(accumulator.toByte(), (accumulator + 1).toByte())

    // Set<String>
    fun getUniqueLabels(): Set<String> = setOf(label.ifEmpty { "default" }, "item_$accumulator", label.ifEmpty { "default" })

    fun joinUniqueStrings(values: Set<String>): String = values.sorted().joinToString(";")

    // Set<Enum>
    fun getUsedOps(): Set<Operation> = setOf(lastOperation, Operation.ADD)

    // Map<Int, String>
    fun getIndexedLabels(): Map<Int, String> = mapOf(0 to label.ifEmpty { "default" }, 1 to "item_$accumulator")

    // Map<Int, Int>
    fun getSquares(): Map<Int, Int> = mapOf(1 to 1, 2 to 4, 3 to 9, accumulator to accumulator * accumulator)

    fun sumMapValues(data: Map<Int, Int>): Int {
        accumulator = data.values.sum()
        return accumulator
    }

    // Map<String, String>
    fun getStringMap(): Map<String, String> = mapOf("name" to label.ifEmpty { "unnamed" }, "value" to accumulator.toString())

    fun concatMapEntries(data: Map<String, String>): String = data.entries.joinToString(", ") { "${it.key}=${it.value}" }

    // ── Collections in callbacks ────────────────────────────────────────────

    fun onScoresReady(callback: (List<Int>) -> Unit) {
        callback(listOf(accumulator, accumulator * 2, accumulator * 3))
    }

    fun onLabelsReady(callback: (List<String>) -> Unit) {
        callback(listOf(label.ifEmpty { "default" }, "item_$accumulator"))
    }

    fun onOpsReady(callback: (List<Operation>) -> Unit) {
        callback(Operation.entries.toList())
    }

    fun onFlagsReady(callback: (List<Boolean>) -> Unit) {
        callback(listOf(accumulator > 0, accumulator % 2 == 0))
    }

    // Map in callbacks
    fun onMetadataReady(callback: (Map<String, Int>) -> Unit) {
        callback(mapOf("current" to accumulator, "doubled" to accumulator * 2))
    }

    // Map in callbacks - extra
    fun onMapIntIntReady(callback: (Map<Int, Int>) -> Unit) {
        callback(mapOf(accumulator to accumulator * accumulator))
    }

    // Collection as callback return
    fun getTransformedScores(fn: (Int) -> List<Int>): List<Int> = fn(accumulator)

    fun getComputedLabels(fn: (Int) -> List<String>): List<String> = fn(accumulator)

    fun getComputedMap(fn: (Int) -> Map<String, Int>): Map<String, Int> = fn(accumulator)

    // Collection return callback - extra combinations
    fun getComputedOps(fn: (Int) -> List<Operation>): List<Operation> = fn(accumulator)

    fun getComputedBools(fn: (Int) -> List<Boolean>): List<Boolean> = fn(accumulator)

    fun getComputedLongs(fn: (Int) -> List<Long>): List<Long> = fn(accumulator)

    // Multi-param callback with collection
    fun computeWithScores(base: Int, callback: (List<Int>, String) -> Unit) {
        callback(listOf(base, base * 2, base * 3), "computed_$base")
    }

    // ── Nullable collections ────────────────────────────────────────────────

    fun getScoresOrNull(): List<Int>? = if (accumulator != 0) listOf(accumulator, accumulator * 2) else null

    fun getLabelsOrNull(): List<String>? = if (label.isNotEmpty()) listOf(label, "extra") else null

    fun sumAllOrNull(values: List<Int>?): Int {
        if (values == null) return -1
        accumulator = values.sum()
        return accumulator
    }

    fun getOpsOrNull(): Set<Operation>? = if (accumulator > 0) setOf(lastOperation, Operation.ADD) else null

    fun getMetadataOrNull(): Map<String, Int>? = if (accumulator != 0) mapOf("val" to accumulator) else null

    // ── Extra collection edge-case methods ──────────────────────────────────

    fun getSingletonList(): List<Int> = listOf(accumulator)

    fun getEmptyList(): List<Int> = emptyList()

    fun getLargeList(size: Int): List<Int> = List(size) { it * accumulator }

    fun reverseList(values: List<Int>): List<Int> {
        val reversed = values.reversed()
        accumulator = reversed.firstOrNull() ?: 0
        return reversed
    }

    fun filterPositive(values: List<Int>): List<Int> = values.filter { it > 0 }

    fun getEmptyStringList(): List<String> = emptyList()

    fun repeatLabel(count: Int): List<String> = List(count) { "${label.ifEmpty { "item" }}_$it" }

    fun transformStrings(values: List<String>): List<String> = values.map { it.uppercase() }

    fun getSingletonMap(): Map<String, Int> = mapOf("only" to accumulator)

    fun getEmptyMap(): Map<String, Int> = emptyMap()

    fun mergeMapValues(a: Map<String, Int>, b: Map<String, Int>): Map<String, Int> = a + b

    fun getEmptySet(): Set<Int> = emptySet()

    fun intersectSets(a: Set<Int>, b: Set<Int>): Set<Int> = a.intersect(b)

    fun onLargeListReady(size: Int, callback: (List<Int>) -> Unit) {
        callback(List(size) { it })
    }

    fun onEmptyListReady(callback: (List<Int>) -> Unit) {
        callback(emptyList())
    }

    fun getScoresOrNullByLabel(): List<String>? = if (label.isEmpty()) null else listOf(label)

    fun getNullableSetByAccum(): Set<Int>? = if (accumulator < 0) null else setOf(accumulator, accumulator + 1)

    fun getNullableMapByLabel(): Map<String, String>? = if (label.isEmpty()) null else mapOf("label" to label)

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

    fun getAll(): List<Calculator> = calculators.values.toList()

    fun sumAll(calcs: List<Calculator>): Int = calcs.sumOf { it.current }

    fun getAllOrNull(): List<Calculator>? = if (calculators.isEmpty()) null else calculators.values.toList()
}

// ── Classes with complex default parameters ─────────────────────────────────

data class Config(val origin: Point, val scale: Int)

class StyledCalculator(
    initial: Int = 0,
    val config: Config = Config(Point(0, 0), 1),
    val mode: Operation = Operation.ADD,
    val tag: String = "default",
) {
    private var accumulator: Int = initial
    val current: Int get() = accumulator
    fun getConfig(): Config = config
    fun getMode(): Operation = mode
    fun getTag(): String = tag
    fun add(value: Int): Int { accumulator += value; return accumulator }
    fun describe(): String = "StyledCalculator(current=$accumulator, tag=$tag, mode=$mode)"
}

class FramedCalculator(
    initial: Int = 0,
    val frame: Rect = Rect(Point(0, 0), Point(100, 100)),
    val label: String = "framed",
) {
    private var accumulator: Int = initial
    val current: Int get() = accumulator
    fun getFrame(): Rect = frame
    fun getLabel(): String = label
    fun add(value: Int): Int { accumulator += value; return accumulator }
}

// ── Deeply nested DC + Enum combinations ────────────────────────────────────

data class Style(val bold: Boolean, val color: Int)

data class StyledPoint(val point: Point, val style: Style)

data class TaggedRect(val rect: Rect, val tag: Operation, val name: String)

data class DeepNested(val tagged: TaggedPoint, val style: Style, val scale: Double)

// Class with various DC/Enum/primitive default combos
class RichCalculator(
    initial: Int = 0,
    val style: Style = Style(false, 0),
    val origin: Point = Point(0, 0),
    val op: Operation = Operation.ADD,
    val name: String = "rich",
    val factor: Double = 1.0,
) {
    private var accumulator: Int = initial
    val current: Int get() = accumulator
    fun getStyle(): Style = style
    fun getOrigin(): Point = origin
    fun getOp(): Operation = op
    fun getName(): String = name
    fun getFactor(): Double = factor
    fun add(value: Int): Int { accumulator += value; return accumulator }
    fun scaled(): Double = accumulator * factor
}

// Class with only DC defaults (no primitives)
class PureDefaultCalc(
    val bounds: Rect = Rect(Point(-1, -1), Point(1, 1)),
    val tagged: TaggedPoint = TaggedPoint(Point(0, 0), Operation.ADD),
) {
    fun getBounds(): Rect = bounds
    fun getTagged(): TaggedPoint = tagged
    fun sum(): Int = bounds.topLeft.x + bounds.topLeft.y + bounds.bottomRight.x + bounds.bottomRight.y
}

// Methods that take/return nested DCs
class NestedDcProcessor {
    private var lastStyle = Style(false, 0)
    private var lastPoint = Point(0, 0)

    fun processStyledPoint(sp: StyledPoint): Int = sp.point.x + sp.point.y + sp.style.color
    fun getStyledPoint(): StyledPoint = StyledPoint(lastPoint, lastStyle)
    fun setStyledPoint(sp: StyledPoint) { lastPoint = sp.point; lastStyle = sp.style }

    fun processTaggedRect(tr: TaggedRect): String = "${tr.name}:${tr.tag}(${tr.rect.topLeft.x},${tr.rect.topLeft.y}-${tr.rect.bottomRight.x},${tr.rect.bottomRight.y})"
    fun getTaggedRect(): TaggedRect = TaggedRect(Rect(lastPoint, lastPoint), Operation.ADD, "default")

    fun processDeepNested(dn: DeepNested): Int = dn.tagged.point.x + dn.tagged.point.y + dn.style.color + (dn.scale * 10).toInt()
    fun getDeepNested(): DeepNested = DeepNested(TaggedPoint(lastPoint, Operation.MULTIPLY), lastStyle, 2.5)

    fun processConfig(cfg: Config): Int = cfg.origin.x + cfg.origin.y + cfg.scale
    fun swapPoint(p: Point): Point { val old = lastPoint; lastPoint = p; return old }

    fun getStyleOrNull(): Style? = if (lastStyle.color != 0) lastStyle else null
    fun getStyledPointOrNull(): StyledPoint? = if (lastPoint.x != 0) StyledPoint(lastPoint, lastStyle) else null
}

// ── Nested class test ────────────────────────────────────────────────────────

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
}
