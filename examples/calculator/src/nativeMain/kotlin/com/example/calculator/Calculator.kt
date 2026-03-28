package com.example.calculator

class Calculator(initial: Int = 0) {

    private var accumulator: Int = initial

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

    fun describe(): String = "Calculator(current=$accumulator)"
}
