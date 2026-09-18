package se.familjekalender.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun BirthdayRainbowIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val minStroke = 1.dp.toPx()
        val maxStroke = 2.dp.toPx()
        val stroke = (size.width / 20f).coerceIn(minStroke, maxStroke)
        val colors =
            listOf(
                Color(0xFFFF4D4D),
                Color(0xFFFF9A3D),
                Color(0xFFFFD84D),
                Color(0xFF58C76F),
                Color(0xFF4D9DFF),
                Color(0xFF9A6CFF),
            )

        val centerX = size.width / 2f
        val baseline = size.height - stroke * 0.8f
        val outerRadius =
            minOf(
                size.width / 2f - stroke * 0.55f,
                baseline - stroke * 0.45f,
            )
        val bandStep = stroke * 1.05f

        colors.forEachIndexed { index, color ->
            val radius = outerRadius - index * bandStep
            if (radius > stroke * 0.7f) {
                val rect =
                    Rect(
                        left = centerX - radius,
                        top = baseline - radius,
                        right = centerX + radius,
                        bottom = baseline + radius,
                    )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}
