package com.example.calculator

// Sealed class hierarchy

sealed class AppResult {
    class Success(val value: Int) : AppResult()
    class Error(val message: String) : AppResult()
    class Loading : AppResult()
}

class ResultProcessor {
    fun processAndDescribe(input: Int): String = when {
        input < 0 -> "Error: Negative input: $input"
        input == 0 -> "Loading..."
        else -> "Success: ${input * 2}"
    }

    fun processValue(input: Int): Int = if (input > 0) input * 2 else -1
}
