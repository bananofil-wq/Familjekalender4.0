package se.familjekalender.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun BirthdayRainbowIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val colors = listOf(
            Color(0xFFFF4D4D),
            Color(0xFFFF9A3D),
            Color(0xFFFFD84D),
            Color(0xFF58C76F),
            Color(0xFF4D9DFF),
            Color(0xFF9A6CFF)
        )
        val centerX = size.width / 2f
        val baseline = size.height - stroke * 0.6f
        val outerRadius = minOf(size.width / 2f - stroke, size.height - stroke)

        colors.forEachIndexed { index, color ->
            val radius = outerRadius - index * stroke * 0.92f
            if (radius > stroke) {
                val rect = Rect(
                    left = centerX - radius,
                    top = baseline - radius,
                    right = centerX + radius,
                    bottom = baseline + radius
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
            }
        }

        drawLine(
            color = Color.White.copy(alpha = 0.08f),
            start = Offset(stroke, baseline + 0.5f),
            end = Offset(size.width - stroke, baseline + 0.5f),
            strokeWidth = 1f
        )
    }
}
