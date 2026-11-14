package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.data.model.SegmentProgress
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.SonicEmerald

@Composable
fun MultiSegmentBar(
    segments: List<SegmentProgress>,
    overallFraction: Float,
    threadCount: Int,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val emerald = SonicEmerald
    val amber = NeonAmber

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            drawRoundRect(
                color = trackColor,
                size = size,
                cornerRadius = radius
            )

            if (segments.isNotEmpty() && segments.size > 1) {
                val gap = 2.dp.toPx()
                val totalGap = gap * (segments.size - 1)
                val segWidth = ((size.width - totalGap) / segments.size).coerceAtLeast(2f)
                segments.forEachIndexed { idx, seg ->
                    val left = idx * (segWidth + gap)
                    drawRoundRect(
                        color = trackColor.copy(alpha = 0.7f),
                        topLeft = Offset(left, 0f),
                        size = Size(segWidth, size.height),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                    val filled = segWidth * seg.fraction
                    if (filled > 0.5f) {
                        val segColor = when {
                            seg.isComplete -> emerald
                            idx % 2 == 0 -> primaryColor
                            else -> amber
                        }
                        drawRoundRect(
                            color = segColor,
                            topLeft = Offset(left, 0f),
                            size = Size(filled, size.height),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }
            } else {
                val filledW = size.width * overallFraction.coerceIn(0f, 1f)
                if (filledW > 0.5f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(primaryColor, emerald)),
                        size = Size(filledW, size.height),
                        cornerRadius = radius
                    )
                }
            }
        }

        if (segments.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val activeSegs = segments.count { !it.isComplete && it.downloadedBytes > 0L }
                val doneSegs = segments.count { it.isComplete }
                Text(
                    text = "$threadCount-Thread Accelerator • $doneSegs/${segments.size} chunks merged",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (activeSegs > 0) {
                    Text(
                        text = "$activeSegs active",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryColor
                    )
                }
            }
        }
    }
}

@Composable
fun SpeedSparkline(
    historyKbps: List<Float>,
    modifier: Modifier = Modifier
) {
    val lineColor = ElectricCyan
    val fillTop = ElectricCyan.copy(alpha = 0.28f)
    val fillBottom = Color.Transparent

    Canvas(modifier = modifier) {
        if (historyKbps.size < 2) return@Canvas
        val maxVal = (historyKbps.maxOrNull() ?: 100f).coerceAtLeast(128f)
        val stepX = size.width / (historyKbps.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path()

        historyKbps.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = (value / maxVal).coerceIn(0f, 1f)
            val y = size.height - (normalized * (size.height * 0.85f)) - (size.height * 0.05f)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(listOf(fillTop, fillBottom))
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
