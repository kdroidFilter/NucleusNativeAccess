package com.example.calculator

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

fun main() {
    io.github.kdroidfilter.nucleus.graalvm.GraalVmInitializer.initialize()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Kotlin/Native Calculator (via FFM)",
            state = rememberWindowState(width = 360.dp, height = 660.dp),
            resizable = false,
        ) {
            MaterialTheme(colors = darkColors()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CalculatorScreen()
                }
            }
        }
    }
}

@Composable
@Preview
fun CalculatorScreen() {
    // Every call on this object goes through FFM into Kotlin/Native
    var calc by remember { mutableStateOf(Calculator(0)) }
    var display by remember { mutableStateOf("0") }
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var inputBuffer by remember { mutableStateOf("") }

    fun refresh() { display = calc.current.toString() }
    fun onDigit(d: String) { inputBuffer += d; display = inputBuffer }
    fun applyOp() {
        if (inputBuffer.isEmpty()) return
        val value = inputBuffer.toIntOrNull() ?: return
        inputBuffer = ""
        when (pendingOp) {
            "+" -> calc.add(value)
            "-" -> calc.subtract(value)
            "x" -> calc.multiply(value)
            null -> calc.add(value)
        }
        refresh()
    }
    fun onOperator(op: String) { applyOp(); pendingOp = op }
    fun onEquals() { applyOp(); pendingOp = null }
    fun onClear() {
        calc.close(); calc = Calculator(0)
        display = "0"; inputBuffer = ""; pendingOp = null
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text(
            "Powered by Kotlin/Native via FFM",
            fontSize = 11.sp, color = Color(0xFF81C784),
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))

        Surface(color = Color(0xFF1E1E1E), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Text(
                display, fontSize = 48.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light, color = Color.White, textAlign = TextAlign.End,
                maxLines = 1, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        val buttons = listOf(
            listOf("C", "/"),
            listOf("7", "8", "9", "x"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("0", "="),
        )
        buttons.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    val isOp = label in listOf("+", "-", "x", "/")
                    val isEquals = label == "="
                    Button(
                        onClick = {
                            when {
                                label == "C" -> onClear()
                                label == "=" -> onEquals()
                                isOp -> onOperator(label)
                                else -> onDigit(label)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = when {
                                isEquals -> Color(0xFFFF9800)
                                isOp -> Color(0xFF424242)
                                label == "C" -> Color(0xFF616161)
                                else -> Color(0xFF303030)
                            },
                        ),
                        modifier = Modifier.weight(if (label == "0") 2f else 1f).height(64.dp),
                    ) {
                        Text(label, fontSize = 22.sp, color = Color.White,
                            fontWeight = if (isOp || isEquals) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(calc.describe(), fontSize = 12.sp, color = Color.Gray,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}
