package com.example.rustcamera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionHeader(title: String, count: Int? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppColors.accent)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.h6,
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Badge("$count")
        }
    }
}

@Composable
fun Badge(text: String, color: Color = AppColors.accent) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.card)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.body2)
        Text(value, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GaugeBar(
    label: String,
    fraction: Float,
    detail: String,
    height: Dp = 6.dp,
    colors: Pair<Color, Color> = barColors(fraction),
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(500),
    )
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.caption)
            Text(detail, style = MaterialTheme.typography.caption, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(AppColors.border)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(RoundedCornerShape(height / 2))
                    .background(Brush.horizontalGradient(listOf(colors.first, colors.second)))
            )
        }
    }
}

fun barColors(fraction: Float): Pair<Color, Color> = when {
    fraction > 0.9f -> AppColors.red to AppColors.red
    fraction > 0.7f -> AppColors.orange to AppColors.red
    fraction > 0.5f -> AppColors.accent to AppColors.orange
    else -> AppColors.accent to AppColors.accent
}

@Composable
fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color = AppColors.accent,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.card)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accentColor)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.caption)
    }
}

@Composable
fun MiniLabel(text: String, color: Color = AppColors.textMuted) {
    Text(
        text,
        style = MaterialTheme.typography.caption,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
