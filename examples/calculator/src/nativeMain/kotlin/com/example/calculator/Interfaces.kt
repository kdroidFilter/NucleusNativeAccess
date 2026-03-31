package com.example.calculator

// Interface tests

interface Measurable {
    fun measure(): Double
    val unit: String
}

interface Printable {
    fun prettyPrint(): String
}

interface Resettable {
    fun reset()
}

class Ruler(val length: Double) : Measurable, Printable {
    override fun measure(): Double = length
    override val unit: String = "cm"
    override fun prettyPrint(): String = "$length $unit"
}

class Scale(val weight: Double) : Measurable {
    override fun measure(): Double = weight
    override val unit: String = "kg"
}

// Class with both inheritance and interfaces
class SmartRuler(length: Double) : Shape("smart-ruler"), Measurable, Printable, Resettable {
    private var currentLength = length

    override fun area(): Double = currentLength * 0.01
    override fun measure(): Double = currentLength
    override val unit: String = "mm"
    override fun prettyPrint(): String = "$currentLength $unit (${describe()})"
    override fun reset() { currentLength = 0.0 }

    fun currentValue(): Double = currentLength
}

// Factory returning concrete types
class MeasurementFactory {
    fun createRuler(length: Double): Ruler = Ruler(length)
    fun createScale(weight: Double): Scale = Scale(weight)
}
