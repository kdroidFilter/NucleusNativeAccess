package com.example.rustcalculator

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Rust Calculator - Powered by NNA",
        state = rememberWindowState(width = 380.dp, height = 600.dp),
    ) {
        MaterialTheme(
            colors = darkColors(
                primary = Color(0xFFFF6E40),
                surface = Color(0xFF1E1E2E),
                background = Color(0xFF11111B),
                onSurface = Color(0xFFCDD6F4),
                onBackground = Color(0xFFCDD6F4),
            )
        ) {
            CalculatorApp()
        }
    }
}

@Preview
@Composable
fun CalculatorApp() {
    val calc = remember { Calculator(0) }
    var display by remember { mutableStateOf("0") }
    var inputBuffer by remember { mutableStateOf("") }
    var lastOp by remember { mutableStateOf<Operation?>(null) }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun updateDisplay() {
        display = calc.current.toString()
        description = calc.describe()
        error = null
    }

    fun applyOp(op: Operation) {
        val value = inputBuffer.toIntOrNull() ?: 0
        try {
            calc.apply_op(op, value)
            updateDisplay()
        } catch (e: KotlinNativeException) {
            error = e.message
        }
        inputBuffer = ""
        lastOp = op
    }

    DisposableEffect(Unit) {
        onDispose { calc.close() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header
            Text(
                text = "Rust Calculator",
                fontSize = 14.sp,
                color = Color(0xFFFF6E40),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Powered by Rust + FFM (Foreign Function & Memory API)",
                fontSize = 10.sp,
                color = Color(0xFF6C7086),
                fontFamily = FontFamily.Monospace,
            )

            Spacer(Modifier.height(8.dp))

            // Display
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF1E1E2E),
                shape = RoundedCornerShape(12.dp),
                elevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = description.ifEmpty { "Ready" },
                        fontSize = 12.sp,
                        color = Color(0xFF6C7086),
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = display,
                        fontSize = 48.sp,
                        color = Color(0xFFCDD6F4),
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (inputBuffer.isNotEmpty()) {
                        Text(
                            text = "input: $inputBuffer",
                            fontSize = 14.sp,
                            color = Color(0xFFA6ADC8),
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (error != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = error!!,
                            fontSize = 12.sp,
                            color = Color(0xFFF38BA8),
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Number pad
            val buttons = listOf(
                listOf("7", "8", "9", "/"),
                listOf("4", "5", "6", "*"),
                listOf("1", "2", "3", "-"),
                listOf("C", "0", "=", "+"),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in buttons) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (label in row) {
                            val isOp = label in listOf("+", "-", "*", "/")
                            val isAction = label in listOf("C", "=")

                            Button(
                                onClick = {
                                    when (label) {
                                        "C" -> {
                                            calc.reset()
                                            inputBuffer = ""
                                            updateDisplay()
                                        }
                                        "=" -> {
                                            val value = inputBuffer.toIntOrNull() ?: 0
                                            val op = lastOp ?: Operation.Add
                                            try {
                                                calc.apply_op(op, value)
                                                updateDisplay()
                                            } catch (e: KotlinNativeException) {
                                                error = e.message
                                            }
                                            inputBuffer = ""
                                        }
                                        "+" -> applyOp(Operation.Add)
                                        "-" -> applyOp(Operation.Subtract)
                                        "*" -> applyOp(Operation.Multiply)
                                        "/" -> {
                                            val value = inputBuffer.toIntOrNull() ?: 0
                                            try {
                                                calc.divide(value)
                                                updateDisplay()
                                            } catch (e: KotlinNativeException) {
                                                error = e.message
                                            }
                                            inputBuffer = ""
                                        }
                                        else -> {
                                            inputBuffer += label
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(64.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = when {
                                        isOp -> Color(0xFFFF6E40)
                                        isAction -> Color(0xFF45475A)
                                        else -> Color(0xFF313244)
                                    },
                                    contentColor = Color(0xFFCDD6F4),
                                ),
                                elevation = ButtonDefaults.elevation(2.dp),
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Info section
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF1E1E2E),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "is_positive: ${calc.is_positive()}",
                        fontSize = 12.sp,
                        color = Color(0xFF94E2D5),
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "echo: ${calc.echo("Rust + Kotlin = NNA")}",
                        fontSize = 12.sp,
                        color = Color(0xFF94E2D5),
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "recent_scores: ${calc.get_recent_scores()}",
                        fontSize = 12.sp,
                        color = Color(0xFF94E2D5),
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
