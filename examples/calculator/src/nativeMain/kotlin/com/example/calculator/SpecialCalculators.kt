package com.example.calculator

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

class PureDefaultCalc(
    val bounds: Rect = Rect(Point(-1, -1), Point(1, 1)),
    val tagged: TaggedPoint = TaggedPoint(Point(0, 0), Operation.ADD),
) {
    fun getBounds(): Rect = bounds
    fun getTagged(): TaggedPoint = tagged
    fun sum(): Int = bounds.topLeft.x + bounds.topLeft.y + bounds.bottomRight.x + bounds.bottomRight.y
}
