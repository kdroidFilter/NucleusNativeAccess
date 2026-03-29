package com.example.calculator

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

class ObjectCallbackTest {
    private val calculators = mutableListOf<Calculator>()

    fun addCalc(calc: Calculator) { calculators.add(calc) }
    fun count(): Int = calculators.size

    fun forEachCalc(callback: (Calculator) -> Unit) {
        calculators.forEach { callback(it) }
    }

    fun findCalc(predicate: (Calculator) -> Boolean): Calculator? {
        return calculators.firstOrNull { predicate(it) }
    }

    fun mapCurrents(transform: (Calculator) -> Int): List<Int> {
        return calculators.map { transform(it) }
    }

    fun reduceWith(initial: Int, fn: (Int, Calculator) -> Int): Int {
        var acc = initial
        calculators.forEach { acc = fn(acc, it) }
        return acc
    }

    fun onEachWithIndex(callback: (Int, Calculator) -> Unit) {
        calculators.forEachIndexed { i, c -> callback(i, c) }
    }
}
