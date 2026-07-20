package com.ahmadkharfan.androidstudiolite.designsystem.component.ide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

enum class AslMemoryChartTone { Normal, Warning, Error }

@Composable
fun AslMemoryChartMini(
    label: String,
    value: Int,
    max: Int,
    series: List<Int>,
    modifier: Modifier = Modifier,
    tone: AslMemoryChartTone = AslMemoryChartTone.Normal,
) {
    val colors = AslTheme.colors
    val lineColor = when (tone) {
        AslMemoryChartTone.Normal -> colors.accentPrimary
        AslMemoryChartTone.Warning -> colors.warning
        AslMemoryChartTone.Error -> colors.error
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            Text(text = "$value / $max MB", style = AslCode.codeTiny, color = lineColor)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            if (series.size < 2) return@Canvas
            val seriesMax = (series.maxOrNull() ?: max).coerceAtLeast(1)
            val stepX = size.width / (series.size - 1)
            val points = series.mapIndexed { index, point ->
                Offset(
                    x = index * stepX,
                    y = size.height - (point.toFloat() / seriesMax) * size.height,
                )
            }
            for (i in 0 until points.lastIndex) {
                drawLine(
                    color = lineColor,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(color = lineColor, radius = 3.dp.toPx(), center = points.last(), style = Stroke(width = 2.dp.toPx()))
        }
    }
}
