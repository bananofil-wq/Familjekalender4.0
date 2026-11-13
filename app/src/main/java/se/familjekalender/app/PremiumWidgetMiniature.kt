package se.familjekalender.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/** Miniature of the same date/shortcuts/activities/reminders used on every widget. */
@Composable
internal fun PremiumWidgetMiniature(
    style: WidgetThemeColors.PremiumLook,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val sc = minOf(size.width / 160f, size.height / 120f)
        val fg = Color(style.text)
        val dim = Color(style.muted)
        val highlight = Color(style.accent)
        val rowFill = Color(style.rowTone)
        val buttonFill = Color(style.controlTone)
        fun panel(x: Float, y: Float, w: Float, h: Float, color: Color, radius: Float = 6f) {
            drawRoundRect(color, Offset(x * sc, y * sc),
                Size(w * sc, h * sc), CornerRadius(radius * sc))
        }
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(style.backgroundTop), Color(style.backgroundBottom))),
            cornerRadius = CornerRadius(11f * sc),
        )
        drawRoundRect(
            color = highlight.copy(alpha = .35f),
            cornerRadius = CornerRadius(11f * sc),
            style = Stroke(width = .7f * sc),
        )
        // Embossed date badge and header.
        panel(7f, 7f, 30f, 30f, Color(style.numberFill), 5f)
        panel(7f, 7f, 30f, 9f, Color(style.dayFill), 4f)
        panel(44f, 10f, 91f, 4f, fg.copy(alpha = .92f), 2f)
        panel(44f, 19f, 68f, 2.5f, dim.copy(alpha = .76f), 2f)
        panel(44f, 26f, 48f, 2f, dim.copy(alpha = .55f), 2f)
        // Tactile shortcuts: same proportions and spacing as the real widget.
        panel(8f, 43f, 69f, 15f, buttonFill, 8f)
        panel(83f, 43f, 69f, 15f, buttonFill, 8f)
        panel(16f, 49f, 29f, 3f, fg.copy(alpha = .92f), 1f)
        panel(91f, 49f, 29f, 3f, fg.copy(alpha = .92f), 1f)
        panel(8f, 62f, 80f, 3f, fg.copy(alpha = .90f), 2f)
        // Activity rows.
        repeat(2) { i ->
            val y = 69f + i * 13f
            panel(8f, y, 144f, 11f, rowFill, 6f)
            panel(12f, y + 3f, 3f, 6f,
                if (i == 0) Color(0xFF70B5F7) else Color(0xFFE77EB5), 2f)
            panel(20f, y + 4f, 24f, 2.4f, fg.copy(alpha = .90f), 1f)
            panel(52f, y + 4f, 51f, 2.4f, fg.copy(alpha = .70f), 1f)
            panel(121f, y + 4f, 24f, 2.4f, dim.copy(alpha = .70f), 1f)
        }
        // Pinned weekly reminders, without shine or excessive borders.
        panel(7f, 96f, 146f, 22f, rowFill.copy(alpha = .96f), 7f)
        panel(12f, 101f, 70f, 3f, fg.copy(alpha = .92f), 2f)
        panel(132f, 100f, 16f, 5f, highlight.copy(alpha = .42f), 3f)
        repeat(2) { i ->
            val y = 108f + i * 4.4f
            panel(12f, y, 136f, 3.3f, Color(style.backgroundBottom).copy(alpha = .85f), 2f)
            panel(14f, y, 2.2f, 3.3f, highlight, 1f)
        }
    }
}
