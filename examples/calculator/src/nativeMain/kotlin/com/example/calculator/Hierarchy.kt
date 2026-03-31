package com.example.calculator

// Inheritance hierarchy for testing open classes, overrides, and multi-level chains

open class Shape(val name: String) {
    open fun area(): Double = 0.0
    fun describe(): String = "Shape: $name"
    var color: String = "red"
    open fun summary(): String = "$name area=${area()} color=$color"
}

class Circle(val radius: Double) : Shape("circle") {
    override fun area(): Double = 3.141592653589793 * radius * radius
    fun circumference(): Double = 2 * 3.141592653589793 * radius
    override fun summary(): String = "Circle r=$radius area=${area()} color=$color"
}

class Rectangle(val width: Double, val height: Double) : Shape("rectangle") {
    override fun area(): Double = width * height
    fun perimeter(): Double = 2 * (width + height)
    override fun summary(): String = "Rect ${width}x$height area=${area()} color=$color"
}

// 3-level hierarchy
open class Shape3D(name: String, val depth: Double) : Shape(name) {
    open fun volume(): Double = area() * depth
    override fun summary(): String = "$name area=${area()} vol=${volume()} color=$color"
}

class Cube(val side: Double) : Shape3D("cube", side) {
    override fun area(): Double = side * side
    override fun volume(): Double = side * side * side
    override fun summary(): String = "Cube side=$side vol=${volume()} color=$color"
}

class Cylinder(val cylRadius: Double, cylHeight: Double) : Shape3D("cylinder", cylHeight) {
    override fun area(): Double = 3.141592653589793 * cylRadius * cylRadius
    override fun volume(): Double = area() * depth
    override fun summary(): String = "Cylinder r=$cylRadius h=$depth vol=${volume()} color=$color"
}
