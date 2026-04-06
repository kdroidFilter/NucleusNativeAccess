package com.example.rustsysinfo

import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Dark palette inspired by modern system monitors
object AppColors {
    val bg = Color(0xFF0F1117)
    val surface = Color(0xFF181A20)
    val surfaceLight = Color(0xFF1E2028)
    val card = Color(0xFF1C1E26)
    val cardHover = Color(0xFF22242E)
    val border = Color(0xFF2A2D38)
    val accent = Color(0xFF6C8EFF)
    val accentDim = Color(0xFF3D5299)
    val green = Color(0xFF4ADE80)
    val greenDim = Color(0xFF166534)
    val orange = Color(0xFFFBBF24)
    val orangeDim = Color(0xFF92400E)
    val red = Color(0xFFEF4444)
    val redDim = Color(0xFF991B1B)
    val cyan = Color(0xFF22D3EE)
    val purple = Color(0xFFA78BFA)
    val textPrimary = Color(0xFFE4E4E7)
    val textSecondary = Color(0xFF9CA3AF)
    val textMuted = Color(0xFF6B7280)
    val sidebarBg = Color(0xFF13141A)
    val sidebarSelected = Color(0xFF1E2235)
    val sidebarHover = Color(0xFF1A1C24)
    val divider = Color(0xFF2A2D38)
}

val DarkColorPalette = Colors(
    primary = AppColors.accent,
    primaryVariant = AppColors.accentDim,
    secondary = AppColors.cyan,
    secondaryVariant = AppColors.purple,
    background = AppColors.bg,
    surface = AppColors.surface,
    error = AppColors.red,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = AppColors.textPrimary,
    onSurface = AppColors.textPrimary,
    onError = Color.White,
    isLight = false,
)

val AppTypography = Typography(
    h5 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppColors.textPrimary),
    h6 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = AppColors.textPrimary),
    subtitle1 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = AppColors.textPrimary),
    subtitle2 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, color = AppColors.textSecondary),
    body1 = TextStyle(fontSize = 13.sp, color = AppColors.textPrimary),
    body2 = TextStyle(fontSize = 12.sp, color = AppColors.textSecondary),
    caption = TextStyle(fontSize = 11.sp, color = AppColors.textMuted),
)

@Composable
fun SysInfoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = DarkColorPalette,
        typography = AppTypography,
        content = content,
    )
}
