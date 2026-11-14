package se.familjekalender.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A faithful Android-drawable preview of the widget's seven visual skins.
 *
 * It intentionally reuses the same drawable resources as RemoteViews rather
 * than showing an unrelated illustrative mockup. The schedule shown below is
 * sample content; events and reminders on the real widget stay untouched.
 */
@Composable
internal fun WidgetMaterialPreview(choice: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context -> WidgetSampleView(context).apply { themeChoice = choice } },
        update = { view -> view.themeChoice = choice },
    )
}

private class WidgetSampleView(context: Context) : View(context) {
    var themeChoice: String = WidgetThemeColors.ORIGINAL
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val graphicCache = mutableMapOf<Int, Drawable>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val palette = WidgetThemeColors.previewPalette(themeChoice)
        val artwork = WidgetThemeColors.previewMaterials(themeChoice)
        val scale = minOf(width / 360f, height / 510f)
        val dx = (width - 360f * scale) / 2f
        val dy = (height - 510f * scale) / 2f
        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)

        fun bg(res: Int, x: Int, y: Int, right: Int, bottom: Int) {
            if (res == 0) return
            val drawable = graphicCache.getOrPut(res) { context.getDrawable(res)?.mutate()
                ?: return }
            drawable.setBounds(x, y, right, bottom)
            drawable.draw(canvas)
        }
        fun label(value: String, x: Float, y: Float, sp: Float, color: Int, bold: Boolean = false) {
            textPaint.reset()
            textPaint.isAntiAlias = true
            textPaint.isSubpixelText = true
            textPaint.color = color
            textPaint.textSize = sp
            textPaint.typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD)
                else Typeface.create("sans-serif", Typeface.NORMAL)
            canvas.drawText(value, x, y, textPaint)
        }
        fun block(x: Float, y: Float, w: Float, h: Float, color: Int, rad: Float) {
            fill.color = color
            canvas.drawRoundRect(x, y, x+w, y+h, rad, rad, fill)
        }

        // Same background, badge, action, row and reminder resources as real widgets.
        // Preview the same clean material and component drawables as the
        // installed widget. Never preview a flattened image with baked text.
        val material = WidgetReferenceImages.image(palette.skin, "material")
        bg(material ?: artwork.background, 0, 0, 360, 510)
        bg(artwork.badgeDay, 15, 18, 72, 49)
        bg(artwork.badgeNumber, 15, 49, 72, 90)
        label("FRE", 28f, 40f, 15f, palette.dayInk, true)
        label("13", 27f, 80f, 30f, palette.numberInk, true)
        label("Fredag 13 november", 84f, 47f, 22f, palette.text, true)
        label("Vecka 46 · 2 aktiviteter idag", 84f, 69f, 13f, palette.muted)

        bg(artwork.refresh, 304, 20, 350, 66)
        label("+", 316f, 53f, 34f, palette.controlInk, true)
        bg(artwork.todo, 13, 105, 173, 151)
        bg(artwork.shopping, 186, 105, 347, 151)
        label("✓  1 To-Do   ›", 31f, 134f, 18f, palette.controlInk, true)
        label("4 inköp  ›", 230f, 134f, 18f, palette.controlInk, true)

        label("▣  Idag 13/11  ·  2 aktiviteter", 17f, 182f, 15f, palette.text, true)
        val rows = listOf(191 to "Hugo", 239 to "Bella")
        rows.forEachIndexed { index, row ->
            val y = row.first
            bg(artwork.row, 12, y, 349, y+42)
            block(20f, y+10f, 5f, 23f,
                if (index == 0) 0xFF4AABEF.toInt() else 0xFFE15C9D.toInt(), 3f)
            label(row.second, 37f, y+27f, 16f, palette.text, true)
            block(101f, y+10f, 1f, 23f, 0x5FFFFFFF, 0f)
            if (index == 0) {
                label("Skola", 117f, y+27f, 16f, palette.text)
                label("08:00–15:00", 256f, y+27f, 12f, palette.muted)
            } else {
                label("Häng hos Stina", 117f, y+27f, 16f, palette.text)
            }
        }

        bg(artwork.reminder, 10, 303, 350, 497)
        label("♧", 26f, 337f, 23f, palette.accent, true)
        label("Veckans påminnelser", 59f, 336f, 18f, palette.text, true)
        bg(artwork.weekBadge, 294, 315, 339, 343)
        label("V46", 303f, 334f, 15f, palette.accent, true)
        val reminders = listOf(
            "Mån · Kim: Kompressor", "Tis · Hugo: Simning",
            "Tis 08:00 · Viggo: BVC"
        )
        reminders.forEachIndexed { i, value ->
            val top = 353 + i*45
            bg(artwork.reminderRow, 19, top, 341, top+39)
            label(value, 39f, top+26f, 15f, palette.text)
        }
        canvas.restore()
    }
}
