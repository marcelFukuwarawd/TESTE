package com.btcswing.simulator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Green = Color(0xFF4ADE80)
val Red = Color(0xFFF87171)
val Orange = Color(0xFFF7931A)
val Muted = Color(0xFF94A3B8)

private val us = Locale.US

fun usdt(v: Double): String = String.format(us, "%,.2f", v)
fun btc(v: Double): String = String.format(us, "%.6f", v)
fun pct(v: Double): String = String.format(us, "%+.2f%%", v)
fun pnlColor(v: Double): Color = when {
    v > 0 -> Green
    v < 0 -> Red
    else -> Color.White
}

private val dtFmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
fun fmtTime(epoch: Long): String = dtFmt.format(Date(epoch))

@Composable
fun StatCard(
    label: String,
    value: String,
    valueColor: Color = Color.White,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = Muted, fontSize = MaterialTheme.typography.labelMedium.fontSize)
            Text(
                value, color = valueColor,
                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = MaterialTheme.typography.bodySmall.fontSize)
        }
    }
}

@Composable
fun TwoStats(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) { left() }
        Column(Modifier.weight(1f)) { right() }
    }
}

/** Curva de patrimônio desenhada à mão (sem libs externas). */
@Composable
fun Sparkline(
    values: List<Double>,
    positive: Boolean,
    modifier: Modifier = Modifier
) {
    if (values.size < 2) return
    val color = if (positive) Green else Red
    Canvas(modifier.fillMaxWidth().height(64.dp)) {
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / range).toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        drawCircle(color, radius = 5f, center = Offset(size.width, size.height - ((values.last() - min) / range).toFloat() * size.height))
    }
}
