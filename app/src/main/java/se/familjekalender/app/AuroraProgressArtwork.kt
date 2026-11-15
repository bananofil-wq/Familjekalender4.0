package se.familjekalender.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient

/**
 * Renders the glass/metal progress ring for the REAL completed share.
 * The supplied reference's 78% cannot be baked into a widget with live data:
 * a zero-progress day must not appear 78% completed.
 */
internal object AuroraProgressArtwork {
    fun bitmap(completedPercent: Int): Bitmap {
        val percent = completedPercent.coerceIn(0, 100)
        val size = 320
        val c = size / 2f
        val canvasBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Feathered dark-gold glass disk conceals the reference's static 78% ring,
        // while blending naturally into its amber/emerald second card.
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            c, c, 160f,
            intArrayOf(
                Color.argb(255, 58, 41, 46),
                Color.argb(255, 57, 43, 43),
                Color.argb(253, 47, 42, 42),
                Color.argb(235, 44, 39, 40),
                Color.argb(0, 44, 39, 40)
            ),
            floatArrayOf(0f, 0.57f, 0.81f, 0.92f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(c, c, 159f, paint)
        paint.shader = null

        val bounds = RectF(47f, 47f, size - 47f, size - 47f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        // Dark inset rail, soft outer edge, and inner metal rim.
        paint.color = Color.argb(190, 124, 98, 69)
        paint.strokeWidth = 25f
        canvas.drawArc(bounds, -124f, 360f, false, paint)
        paint.color = Color.argb(220, 40, 45, 47)
        paint.strokeWidth = 20f
        canvas.drawArc(bounds, -124f, 360f, false, paint)
        paint.color = Color.argb(125, 250, 204, 119)
        paint.strokeWidth = 2.6f
        canvas.drawCircle(c, c, 133f, paint)
        paint.color = Color.argb(80, 240, 245, 239)
        paint.strokeWidth = 1.5f
        canvas.drawCircle(c, c, 88f, paint)

        if (percent > 0) {
            val sweep = percent * 3.6f
            val shader = SweepGradient(
                c, c,
                intArrayOf(
                    Color.rgb(255, 212, 129),
                    Color.rgb(255, 155, 35),
                    Color.rgb(244, 203, 106),
                    Color.rgb(48, 226, 203),
                    Color.rgb(48, 226, 203)
                ),
                floatArrayOf(0f, .30f, .45f, .77f, 1f),
            )
            val rotate = Matrix().apply { setRotate(-124f, c, c) }
            shader.setLocalMatrix(rotate)

            // Soft glow around the filled arc.
            paint.shader = shader
            paint.color = Color.WHITE
            paint.strokeWidth = 31f
            paint.alpha = 85
            canvas.drawArc(bounds, -124f, sweep, false, paint)

            // Illuminated gold-to-turquoise 3D glass.
            paint.alpha = 255
            paint.strokeWidth = 22f
            canvas.drawArc(bounds, -124f, sweep, false, paint)
            paint.shader = null

            // Offset highlights give the ring genuine thickness, not a flat
            // colored stroke. These are restrained when the ring is empty.
            val shineBounds = RectF(51f, 51f, size - 51f, size - 51f)
            paint.color = Color.argb(165, 255, 250, 218)
            paint.strokeWidth = 3.1f
            canvas.drawArc(shineBounds, -123f, sweep, false, paint)
        }

        // The center stays clear and the live % and KLAR text are separate views.
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            c, c, 97f,
            intArrayOf(Color.argb(240, 52, 43, 44), Color.argb(190, 36, 36, 43)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(c, c, 94f, paint)
        paint.shader = null
        paint.color = Color.argb(110, 255, 228, 168)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        canvas.drawCircle(c, c, 94f, paint)

        return canvasBitmap
    }
}
