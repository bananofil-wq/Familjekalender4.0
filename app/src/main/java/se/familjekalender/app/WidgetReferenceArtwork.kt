package se.familjekalender.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.math.sin

/**
 * Original material artwork for the five reference-based home-screen widget skins.
 *
 * The art contains NO event text, dates, counts or controls: those remain live
 * RemoteViews with the existing click targets and family-scoped data.
 * The Settings preview draws this exact same bitmap, not a different mock-up.
 */
internal object WidgetReferenceArtwork {
    private const val W = 360f
    private const val H = 510f
    private val bitmaps = mutableMapOf<String, Bitmap>()

    @Synchronized
    fun bitmap(choice: String): Bitmap? {
        val look = WidgetThemeColors.premiumLook(choice) ?: return null
        return bitmaps.getOrPut(choice) {
            Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888).also {
                draw(Canvas(it), look.skin)
            }
        }
    }

    private fun draw(c: Canvas, skin: WidgetThemeColors.Skin) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val bounds = RectF(3f, 3f, W - 3f, H - 3f)
        val clip = Path().apply { addRoundRect(bounds, 27f, 27f, Path.Direction.CW) }

        p.color = Color.argb(125, 0, 0, 0)
        c.drawRoundRect(RectF(2f, 5f, W - 2f, H - 1f), 28f, 28f, p)
        c.save()
        c.clipPath(clip)

        val tones = when (skin) {
            WidgetThemeColors.Skin.FOREST_WOOD -> 0xFF3A2519.toInt() to 0xFF20120E.toInt()
            WidgetThemeColors.Skin.OBSIDIAN -> 0xFF202327.toInt() to 0xFF0E1216.toInt()
            WidgetThemeColors.Skin.CARBON_RED -> 0xFF282A2D.toInt() to 0xFF0F1012.toInt()
            WidgetThemeColors.Skin.MIST_FOREST -> 0xFF69838E.toInt() to 0xFF243E49.toInt()
            else -> 0xFF41586B.toInt() to 0xFF142536.toInt()
        }
        p.shader = LinearGradient(0f, 0f, 0f, H, tones.first, tones.second,
            Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p)
        p.shader = null

        when (skin) {
            WidgetThemeColors.Skin.FOREST_WOOD -> wood(c, p)
            WidgetThemeColors.Skin.OBSIDIAN -> marble(c, p)
            WidgetThemeColors.Skin.CARBON_RED -> carbon(c, p)
            WidgetThemeColors.Skin.MIST_FOREST -> mist(c, p)
            WidgetThemeColors.Skin.NORDIC_DUSK -> dusk(c, p)
            else -> Unit
        }

        // Dark translucent central reading surface, like the supplied reference.
        p.shader = LinearGradient(0f, 0f, 0f, H,
            intArrayOf(0x06000000, 0x14000000, 0x18000000, 0x22000000),
            floatArrayOf(0f, .27f, .56f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(7f, 6f, 353f, 504f, p)
        p.shader = null

        // Real restrained inset border, including a thin top rim.
        p.style = Paint.Style.STROKE
        p.strokeWidth = if (skin == WidgetThemeColors.Skin.CARBON_RED) 1.2f else 1.6f
        p.color = when (skin) {
            WidgetThemeColors.Skin.FOREST_WOOD -> 0xFFB17D50.toInt()
            WidgetThemeColors.Skin.OBSIDIAN -> 0xFFC49B65.toInt()
            WidgetThemeColors.Skin.CARBON_RED -> 0xFF8A4545.toInt()
            WidgetThemeColors.Skin.MIST_FOREST -> 0xFFADCBD1.toInt()
            else -> 0xFF9BA3AA.toInt()
        }
        c.drawRoundRect(RectF(5f, 5f, 355f, 505f), 25f, 25f, p)
        p.strokeWidth = .7f
        p.color = 0x55FFFFFF
        c.drawRoundRect(RectF(7f, 7f, 353f, 503f), 24f, 24f, p)
        p.style = Paint.Style.FILL
        c.restore()
    }

    private fun wood(c: Canvas, p: Paint) {
        val random = Random(2307L)
        // Fine irregular open-pore walnut grain, not repeating flat stripes.
        repeat(235) {
            val y = random.nextFloat() * 510f
            val x = random.nextFloat() * 55f - 10f
            val end = W + random.nextFloat() * 30f
            val path = Path()
            path.moveTo(x, y)
            path.cubicTo(98f, y + random.nextFloat() * 6f - 3f,
                222f, y + random.nextFloat() * 7f - 3.5f, end,
                y + random.nextFloat() * 8f - 4f)
            p.style = Paint.Style.STROKE
            p.strokeWidth = if (it % 8 == 0) 1.8f else .55f
            p.color = if (it % 3 == 0) 0x265D311A else 0x27B28458
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
        // Subtle, elongated wood knots.
        repeat(5) {
            val x = 36f + random.nextFloat() * 300f
            val y = 56f + random.nextFloat() * 435f
            p.style = Paint.Style.STROKE
            p.color = 0x226D4027
            p.strokeWidth = 1f
            c.drawOval(RectF(x-42f, y-7f, x+42f, y+7f), p)
        }
        p.style = Paint.Style.FILL
        ivy(c, p, 329f, -4f, false)
        ivy(c, p, 12f, 511f, true)
    }

    private fun ivy(c: Canvas, p: Paint, x: Float, y: Float, reverse: Boolean) {
        val direction = if (reverse) -1f else 1f
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.4f
        p.color = 0xFF425626.toInt()
        val stem = Path().apply {
            moveTo(x, y)
            cubicTo(x+24f, y+15f*direction, x-8f, y+53f*direction,
                x+14f, y+112f*direction)
        }
        c.drawPath(stem, p)
        val rnd = Random(if (reverse) 1000L else 1001L)
        repeat(21) { i ->
            val yy = y + (i*6.1f+5f) * direction
            val xx = x + sin(i*.72f).toFloat()*15f + rnd.nextFloat()*14f - 5f
            val sx = if (i%2==0) 1f else -1f
            val radius = 5f + rnd.nextFloat()*5.5f
            val leaf = Path().apply {
                moveTo(xx, yy)
                quadTo(xx+sx*radius*2f, yy-7f*direction,
                    xx+sx*radius*1.4f, yy-radius*2f*direction)
                quadTo(xx-sx*radius*.2f, yy-radius*2f*direction, xx, yy)
                close()
            }
            p.style = Paint.Style.FILL
            p.color = if (i % 4 == 0) 0xFF66843B.toInt()
                else if (i % 3 == 0) 0xFF314F2B.toInt() else 0xFF47682C.toInt()
            c.drawPath(leaf, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = .6f
            p.color = 0x886F9B50.toInt()
            c.drawLine(xx, yy, xx+sx*radius, yy-radius*direction, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun marble(c: Canvas, p: Paint) {
        val rnd = Random(9403L)
        repeat(900) {
            p.color = if (it % 5 == 0) 0x0D9EABAD else 0x0B000000
            c.drawCircle(rnd.nextFloat()*W, rnd.nextFloat()*H,
                .35f+rnd.nextFloat()*.8f, p)
        }
        // Sparse diagonal gold fissures across matte black stone.
        repeat(7) { i ->
            val startX = 220f + i*20f
            val startY = -24f + i*15f
            val path = Path().apply {
                moveTo(startX, startY)
                cubicTo(startX-73f, 90f+i*7f, startX+86f, 170f+i*7f,
                    startX+16f, 275f+i*7f)
                cubicTo(startX-66f, 340f+i*8f, startX+55f, 405f+i*4f,
                    startX-25f, 536f)
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = if (i%3==0) 1.3f else .5f
            p.color = if (i%2==0) 0x6AB78A50 else 0x488E633C
            c.drawPath(path,p)
        }
        p.style = Paint.Style.FILL
    }

    private fun carbon(c: Canvas, p: Paint) {
        // Woven diagonal carbon weave and subdued red corner inlays.
        p.style = Paint.Style.STROKE
        p.strokeWidth = .9f
        for (n in -520..520 step 6) {
            p.color = if ((n/6)%2==0) 0x286E7578 else 0x42090A0C
            c.drawLine(n.toFloat(),0f,n+510f,510f,p)
            p.color = 0x16000000
            c.drawLine(n+510f,0f,n.toFloat(),510f,p)
        }
        p.strokeWidth = 3f
        p.color = 0xBBAD343C.toInt()
        c.drawLine(258f, 9f, 346f, 9f, p)
        c.drawLine(10f, 496f, 104f, 496f, p)
        repeat(7) {
            val start = 265f+it*11f
            p.strokeWidth = 3.8f
            p.color = if (it%2==0) 0xFF971D28.toInt() else 0xFFCB3F44.toInt()
            c.drawLine(start, 8f, start+11f, 8f, p)
            c.drawLine(9f+it*10f, 497f, 21f+it*10f, 497f, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun pine(c: Canvas, p: Paint, x: Float, bottom: Float,
                     h: Float, wide: Float, color: Int) {
        p.color = color
        c.drawRect(x-1f, bottom-h*.2f, x+1f, bottom, p)
        val tree = Path().apply {
            moveTo(x, bottom-h)
            lineTo(x-wide*.32f, bottom-h*.64f)
            lineTo(x-wide*.16f, bottom-h*.64f)
            lineTo(x-wide*.49f, bottom-h*.36f)
            lineTo(x-wide*.26f, bottom-h*.36f)
            lineTo(x-wide*.6f, bottom-h*.06f)
            lineTo(x+wide*.6f, bottom-h*.06f)
            lineTo(x+wide*.26f, bottom-h*.36f)
            lineTo(x+wide*.49f, bottom-h*.36f)
            lineTo(x+wide*.16f, bottom-h*.64f)
            lineTo(x+wide*.32f, bottom-h*.64f)
            close()
        }
        c.drawPath(tree, p)
    }

    private fun mist(c: Canvas, p: Paint) {
        val rnd = Random(299L)
        repeat(27) {
            val x = -5f + it*15.3f
            pine(c,p,x,260f+rnd.nextFloat()*30f,
                70f+rnd.nextFloat()*92f, 14f+rnd.nextFloat()*18f,
                if(it%3==0) 0x38607981 else 0x42506670)
        }
        p.shader = LinearGradient(0f, 145f, 0f, 360f,
            intArrayOf(0x007EA6AE,0x93758E99.toInt(),0x24536A78),
            null,Shader.TileMode.CLAMP)
        c.drawRect(0f,145f,W,360f,p)
        p.shader = null
        repeat(14) {
            val x = rnd.nextFloat()*385f - 8f
            pine(c,p,x,345f+rnd.nextFloat()*125f,60f+rnd.nextFloat()*125f,
                14f+rnd.nextFloat()*18f,0x38223944)
        }
        p.shader = LinearGradient(0f, 260f,0f,510f,
            0x118FAFB2,0x72304D5B,Shader.TileMode.CLAMP)
        c.drawRect(0f,260f,W,510f,p)
        p.shader=null
    }

    private fun dusk(c: Canvas, p: Paint) {
        p.shader = LinearGradient(0f, 90f, 0f, 295f,
            intArrayOf(0x10384D61,0xFFA77167.toInt(),0x446C7B83),
            floatArrayOf(0f,.63f,1f),Shader.TileMode.CLAMP)
        c.drawRect(0f,95f,W,302f,p)
        p.shader=null
        p.color=0xFFF3AF73.toInt()
        c.drawCircle(291f, 229f, 19f,p)
        fun mountains(base: Float, color: Int, points: FloatArray) {
            p.color=color
            val path=Path().apply {
                moveTo(0f,base)
                var i=0
                while(i<points.size) { lineTo(points[i], points[i+1]);i+=2 }
                lineTo(W,base)
                close()
            }
            c.drawPath(path,p)
        }
        mountains(375f,0xFF50667A.toInt(),floatArrayOf(
            0f,270f,42f,236f,78f,263f,112f,219f,156f,264f,
            201f,226f,245f,261f,293f,215f,334f,259f,360f,240f))
        mountains(419f,0xFF314B61.toInt(),floatArrayOf(
            0f,310f,61f,279f,99f,292f,146f,249f,196f,297f,
            251f,263f,303f,300f,360f,272f))
        mountains(510f,0xFF173347.toInt(),floatArrayOf(
            0f,368f,43f,335f,87f,352f,134f,316f,192f,364f,
            243f,321f,293f,355f,331f,312f,360f,330f))
        val rnd = Random(183L)
        repeat(18) {
            val x = -10f + it*22f
            pine(c,p,x,335f+rnd.nextFloat()*75f,33f+rnd.nextFloat()*55f,
                7f+rnd.nextFloat()*12f,0xFF132A3D.toInt())
        }
    }
}
