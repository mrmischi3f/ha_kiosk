package com.hakiosk.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Simple line chart. [baseline] (e.g. previous close) is drawn as a dashed line and included
 * in the vertical range.
 */
@Composable
fun LineChart(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    baseline: Double? = null,
    baselineColor: Color = Color.Gray,
    fill: Boolean = true,
    strokeWidthDp: Float = 2f,
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        var min = values.min()
        var max = values.max()
        if (baseline != null) {
            min = minOf(min, baseline)
            max = maxOf(max, baseline)
        }
        if (max - min < 1e-9) {
            max += 1.0
            min -= 1.0
        }
        val pad = (max - min) * 0.08
        min -= pad
        max += pad

        val w = size.width
        val h = size.height
        fun x(i: Int) = i.toFloat() / (values.size - 1) * w
        fun y(v: Double) = (h - ((v - min) / (max - min)) * h).toFloat()

        val line = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) lineTo(x(i), y(values[i]))
        }

        if (fill) {
            val area = Path().apply {
                addPath(line)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                area,
                brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent)),
            )
        }

        if (baseline != null) {
            val by = y(baseline)
            drawLine(
                color = baselineColor,
                start = Offset(0f, by),
                end = Offset(w, by),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }

        drawPath(
            line,
            color = color,
            style = Stroke(width = strokeWidthDp.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier, baseline: Double? = null) {
    LineChart(
        values = values,
        color = color,
        modifier = modifier,
        baseline = baseline,
        baselineColor = Color.Gray.copy(alpha = 0.5f),
        fill = true,
        strokeWidthDp = 1.5f,
    )
}
