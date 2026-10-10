package se.familjekalender.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Original scalable illustration artwork inspired by natural oak / moonlit noir architecture.
 * Drawn locally with Compose; no external artwork, network access or franchise image assets.
 */
@Composable
internal fun StorybookThemeBackdrop(theme: CleanVisualTheme, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when (theme) {
            CleanVisualTheme.OAK_WOOD -> drawOakBackground()
            CleanVisualTheme.GOTHAM_NIGHT -> drawGothamBackground()
            else -> Unit
        }
    }
}

private fun DrawScope.drawOakBackground() {
    val w = size.width
    val h = size.height
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFFD6A46A), Color(0xFFC68C53), Color(0xFFA46737), Color(0xFF7B4727))
        )
    )

    // Oak boards, narrow seams, and irregular hand-drawn grain.
    for (board in 1..4) {
        val x = w * board / 5f
        drawLine(Color(0x553E1F0D), Offset(x, 0f), Offset(x, h), 3.8f)
        drawLine(Color(0x55F6D4A6), Offset(x + 3.5f, 0f), Offset(x + 3.5f, h), 1.8f)
    }
    for (line in 0..175) {
        val y = h * (line + .45f) / 175f
        val wave = sin(line * 2.31) * 7.5f
        val path = Path().apply {
            moveTo(-20f, y + wave.toFloat())
            cubicTo(w * .26f, y + wave.toFloat() + sin(line * .78).toFloat() * 9,
                w * .60f, y + sin(line * 1.21).toFloat() * 8,
                w + 20, y + sin(line * .58).toFloat() * 5)
        }
        val opacity = if (line % 7 == 0) .18f else if (line % 3 == 0) .095f else .045f
        drawPath(
            path = path,
            color = if (line % 5 == 0) Color(0xFFFFE0B4).copy(alpha = opacity)
                else Color(0xFF5D341A).copy(alpha = opacity),
            style = Stroke(width = if (line % 9 == 0) 2.1f else 1f),
        )
    }

    // Visible knots in the wood, without relying on a repeated flat texture.
    listOf(Triple(w * .77f, h * .29f, w * .095f), Triple(w * .18f, h * .80f, w * .075f)).forEach { (x,y,r) ->
        for (ring in 0..6) {
            val rx = r * (1f + ring * .29f)
            val ry = rx * .30f
            drawOval(
                color = Color(0xFF633719).copy(alpha = (.18f - ring * .019f).coerceAtLeast(.015f)),
                topLeft = Offset(x - rx, y - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = 1.7f),
            )
        }
        drawOval(
            Color(0x30602C11),
            topLeft = Offset(x - r * .37f, y - r * .13f),
            size = Size(r * .74f, r * .26f)
        )
    }

    // Plant stems and small leaves in the upper and lower right, like the reference mock-up.
    drawOakSprig(w * .93f, h * .27f, w * .12f, up = true)
    drawOakSprig(w * .90f, h * .80f, w * .13f, up = false)

    // Subtle light from the top edge to balance the dark wooden footer.
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0x1FFFF7E8), Color.Transparent, Color.Transparent, Color(0x240F0805))
        )
    )
}

private fun DrawScope.drawOakSprig(x: Float, y: Float, radius: Float, up: Boolean) {
    val direction = if (up) -1f else 1f
    val stem = Path().apply {
        moveTo(x + radius * .78f, y + radius * direction * .65f)
        cubicTo(x + radius * .40f, y, x + radius * .18f, y + radius * direction * .40f,
            x - radius * .35f, y + radius * direction * .90f)
    }
    drawPath(stem, Color(0xFF40532A), style = Stroke(width = 3.0f))
    for (leaf in 0..5) {
        val xx = x + radius * (.55f - leaf * .165f)
        val yy = y + radius * direction * (.10f + leaf * .19f)
        val side = if (leaf % 2 == 0) 1f else -1f
        val leafPath = Path().apply {
            moveTo(xx, yy)
            quadraticBezierTo(
                xx + side * radius * .32f, yy - radius * .35f,
                xx + side * radius * .48f, yy - radius * .42f,
            )
            quadraticBezierTo(
                xx + side * radius * .42f, yy - radius * .07f,
                xx, yy,
            )
            close()
        }
        drawPath(
            leafPath,
            brush = Brush.linearGradient(
                listOf(Color(0xFF7C9953), Color(0xFF314F32)),
                start = Offset(xx, yy),
                end = Offset(xx + side * radius * .45f, yy - radius * .4f),
            ),
        )
    }
}

private fun DrawScope.drawGothamBackground() {
    val w = size.width
    val h = size.height
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF163452), Color(0xFF0A1B30), Color(0xFF071323), Color(0xFF020811))
        )
    )

    // Cold moon glow above the roofline. Layered circles approximate atmospheric haze.
    val moon = Offset(w * .79f, h * .10f)
    for (ring in 12 downTo 1) {
        drawCircle(
            Color(0xFFB6D6F7).copy(alpha = .004f + (12 - ring) * .002f),
            radius = w * (.09f + ring * .018f),
            center = moon,
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFF1F3EF), Color(0xFFC6DAEB), Color(0xFF9AAFC6)),
            center = moon,
            radius = w * .09f,
        ),
        radius = w * .085f,
        center = moon,
    )
    for (i in 0..9) {
        val xx = w * (.09f + (i * .283f % .81f))
        val yy = h * (.020f + (i * .047f % .21f))
        drawCircle(Color(0x77D6E3F1), radius = 1.0f + i % 2, center = Offset(xx, yy))
    }

    // Far-off silhouette: irregular stone towers and narrow spires.
    drawGothicSkyline(baseY = h * .30f, scale = .70f, far = true)
    drawGothicSkyline(baseY = h * .37f, scale = 1f, far = false)

    // Cool atmospheric wash over the skyline, preserving readability of the UI.
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0x100A1525), Color(0x55132238), Color(0xC2030A16), Color(0xDE030813))
        )
    )
    drawLine(
        color = Color(0x224CA3E4),
        start = Offset(0f, h * .38f),
        end = Offset(w, h * .38f),
        strokeWidth = 1.4f,
    )
}

private fun DrawScope.drawGothicSkyline(baseY: Float, scale: Float, far: Boolean) {
    val w = size.width
    val h = size.height
    val color = if (far) Color(0xFF1A3550) else Color(0xFF0B1C31)
    val lit = if (far) Color(0x386F9FC0) else Color(0xAAE6B980)

    for (i in 0..19) {
        val x = w * i / 19f
        val width = w * (if (i % 5 == 0) .078f else .054f) * scale
        val height = h * (if (i % 7 == 0) .185f else .045f + ((i * 13) % 9) * .012f) * scale
        val top = baseY - height
        drawRect(color, topLeft = Offset(x, top), size = Size(width, height + h * .07f))

        // Characteristic stepped/gabled roofs and needle-like cathedral spikes.
        if (i % 3 == 0) {
            val roof = Path().apply {
                moveTo(x - width * .08f, top)
                lineTo(x + width * .50f, top - height * .42f)
                lineTo(x + width * 1.06f, top)
                close()
            }
            drawPath(roof, color)
        }
        if (i % 4 == 0) {
            drawLine(color, Offset(x + width * .50f, top - height * .29f),
                Offset(x + width * .50f, top - height * .65f), 2.5f)
        }

        val windowCols = if (width > w * .06f) 3 else 2
        for (row in 0..4) {
            for (col in 0 until windowCols) {
                if (((i * 11 + row * 7 + col * 3) % 5) < 2) {
                    drawRect(
                        lit.copy(alpha = if (far) .22f else .66f),
                        topLeft = Offset(x + width * (.18f + col * .24f),
                            top + height * (.16f + row * .15f)),
                        size = Size(width * .075f, h * .0036f),
                    )
                }
            }
        }
    }
}
