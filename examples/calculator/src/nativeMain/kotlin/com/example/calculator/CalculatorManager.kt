package com.example.calculator

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
