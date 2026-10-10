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
            listOf(
                Color(0xFFD8B081),
                Color(0xFFC6935D),
                Color(0xFFAE7847),
                Color(0xFF916039),
                Color(0xFF754322),
            )
        )
    )

    // Organic, horizontal oak grain. No hard board seams or straight tile-grid
    // lines: these were the main reason the previous wood theme looked artificial.
    for (line in 0..430) {
        val y = h * (line + .24f) / 431f
        val bend = sin(line * .37f) * 8.0f + sin(line * .096f) * 7.4f
        val drift = sin(line * .23f) * 3.4f
        val path = Path().apply {
            moveTo(-w * .05f, y + bend)
            cubicTo(
                w * .22f, y + bend + sin(line * .81f) * 3.5f,
                w * .46f, y + drift - sin(line * .42f) * 7.2f,
                w * .74f, y + bend * .58f,
            )
            cubicTo(
                w * .91f, y + bend * .24f + sin(line * .25f) * 3.8f,
                w * 1.02f, y + sin(line * .62f) * 5.1f,
                w * 1.05f, y + drift,
            )
        }
        val opacity = when {
            line % 31 == 0 -> .16f
            line % 13 == 0 -> .12f
            line % 5 == 0 -> .072f
            else -> .030f
        }
        drawPath(
            path = path,
            color = if (line % 4 == 0)
                Color(0xFFFFE8C4).copy(alpha = opacity * .72f)
                else Color(0xFF613A23).copy(alpha = opacity),
            style = Stroke(width = if (line % 23 == 0) 1.7f else .85f),
        )
    }

    // In-grain flow around tiny knots; faint enough not to dominate the calendar.
    listOf(Triple(w * .72f, h * .29f, w * .07f), Triple(w * .11f, h * .77f, w * .055f))
        .forEach { (cx, cy, radius) ->
            for (ring in 0..7) {
                val r = radius * (.38f + ring * .31f)
                drawOval(
                    color = Color(0xFF60391E).copy(alpha = (.13f - ring * .012f).coerceAtLeast(.016f)),
                    topLeft = Offset(cx - r, cy - r * .29f),
                    size = Size(r * 2f, r * .58f),
                    style = Stroke(width = 1.3f),
                )
            }
        }

    drawOakSprig(w * .94f, h * .37f, w * .095f, up = true)
    drawOakSprig(w * .96f, h * .72f, w * .10f, up = false)

    // Light catches the natural satin finish; depth increases towards the footer.
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0x19FFF3D9), Color.Transparent, Color.Transparent, Color(0x30250F07))
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
