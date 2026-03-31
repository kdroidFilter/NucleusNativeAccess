package com.example.calculator

// Extension function tests

fun Shape.displayName(): String = "${this.name} (${this.color})"

fun Shape.coloredArea(): String = "${this.color}: ${this.area()}"

fun Calculator.addTwice(value: Int): Int {
    add(value)
    return add(value)
}

fun Calculator.describeWithPrefix(prefix: String): String = "$prefix: ${describe()}"
