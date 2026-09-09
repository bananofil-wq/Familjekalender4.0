package se.familjekalender.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    when (text) {
        "★" -> {
            val iconSize = if (fontSize != TextUnit.Unspecified && fontSize.value >= 18f) 18.dp else 14.dp
            StarMarker(modifier = modifier.offset(y = (-8).dp), size = iconSize, tint = if (color == Color.Unspecified) Color(0xFFFFD75E) else color)
            return
        }
        "🌈" -> {
            val iconSize = if (fontSize != TextUnit.Unspecified && fontSize.value >= 17f) 20.dp else 16.dp
            RainbowMarker(modifier = modifier.offset(y = (-8).dp), size = iconSize)
            return
        }
    }

    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

@Composable
private fun StarMarker(modifier: Modifier, size: androidx.compose.ui.unit.Dp, tint: Color) {
    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val outer = this.size.minDimension * 0.48f
        val inner = outer * 0.46f
        val path = Path()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outer else inner
            val angle = -PI / 2 + i * PI / 5
            val x = cx + cos(angle).toFloat() * radius
            val y = cy + sin(angle).toFloat() * radius
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, tint)
    }
}

@Composable
private fun RainbowMarker(modifier: Modifier, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = modifier.size(size)) {
        val colors = listOf(
            Color(0xFFFF1744),
            Color(0xFFFF7A00),
            Color(0xFFFFD600),
            Color(0xFF32D74B),
            Color(0xFF00A8FF),
            Color(0xFF6C5CE7),
            Color(0xFFC44DFF)
        )
        val band = this.size.minDimension * 0.085f
        val baseWidth = this.size.width - band
        val baseHeight = this.size.height * 1.75f
        colors.forEachIndexed { index, arcColor ->
            val inset = index * band * 0.95f
            drawArc(
                color = arcColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(band / 2f + inset, band / 2f + inset),
                size = Size(
                    width = (baseWidth - inset * 2f).coerceAtLeast(band),
                    height = (baseHeight - inset * 1.65f).coerceAtLeast(band)
                ),
                style = Stroke(width = band)
            )
        }
    }
}
