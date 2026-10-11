package se.familjekalender.app

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Individually rendered, small VISUAL theme thumbnails, not just a colored swatch.
 * Every preview shares the same calendar/widget structure but uses the actual
 * Clean theme palette and a distinctive material pattern for the theme.
 * It is explicitly a static representative preview, not a screenshot.
 */
@Composable
internal fun ThemeMiniature(
    theme: CleanVisualTheme?,
    widget: Boolean,
    modifier: Modifier = Modifier,
) {
    val selectedTheme = theme ?: CleanVisualTheme.CURRENT
    val spec = cleanThemeSpec(selectedTheme)
    val wood = selectedTheme == CleanVisualTheme.OAK_WOOD
    val hippie = selectedTheme == CleanVisualTheme.HIPPIE
    val carbon = selectedTheme == CleanVisualTheme.CARBON_FIBER
    val metal = selectedTheme in listOf(CleanVisualTheme.TITANIUM, CleanVisualTheme.COPPER)
    val light = theme != null && isLightCleanTheme(selectedTheme)
    val baseTop = if (theme == null) Color(0xE82D2A35) else spec.backgroundTop
    val baseBottom = if (theme == null) Color(0xF5191922) else spec.backgroundBottom
    val ink = if (theme == null) Color.White else spec.text
    val panel = if (theme == null) Color(0xA940414D) else spec.panelMid
    val bright = if (theme == null) Color(0xFFB998F9) else spec.accentStrong

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sc = w / 160f
        fun rect(x: Float, y: Float, dx: Float, dy: Float, color: Color, rad: Float = 5f) {
            drawRoundRect(color, Offset(x*sc, y*sc), Size(dx*sc,dy*sc),
                CornerRadius(rad*sc,rad*sc))
        }
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(baseTop,baseBottom)),
            cornerRadius = CornerRadius(10f*sc),
        )
        // Material signatures: grain, checker-weave, metal brushing, playful circles.
        if (wood) {
            for(i in 0..35) {
                val y = (i * 3.7f) * sc
                val drift = sin(i * .82).toFloat() * sc * 2f
                drawLine(
                    if(i % 4 == 0) Color(0x553B1A0C) else Color(0x2BFFE5BB),
                    Offset(0f,y+drift),Offset(w,y-drift),(.42f + (i % 3)*.2f)*sc,
                )
            }
        }
        if (carbon) {
            for(i in 0..30) for(j in 0..13) {
                if ((i+j)%2==0) rect(i*6f, j*9f, 5.7f,4.3f,Color(0x2CFFFFFF),.7f)
            }
        }
        if (metal) {
            for(i in 0..65) {
                val y=i*h/65f
                drawLine(Color.White.copy(alpha=if(i%4==0) .13f else .04f),
                    Offset(0f,y),Offset(w,y),.55f*sc)
            }
        }
        if (hippie) {
            listOf(Offset(w*.9f,h*.17f),Offset(w*.09f,h*.8f),Offset(w*.8f,h*.86f))
                .forEachIndexed { i,pt ->
                    drawCircle(listOf(Color(0x88EB6B8E),Color(0x88F4B642),Color(0x8868B7A8))[i],
                        18f*sc,pt)
                }
        }
        if (widget) {
            // A miniature of the ACTUAL widget hierarchy:
            // calendar badge, headline, two shortcuts, rounded event rows.
            rect(8f,8f,28f,32f,Color(0xFFE9EEF3),5f)
            rect(8f,8f,28f,9f,Color(0xFFE45666),4f)
            rect(45f,10f,91f,4f,ink.copy(alpha=.90f),2f)
            rect(45f,20f,67f,3f,ink.copy(alpha=.43f),2f)
            rect(45f,28f,52f,2f,ink.copy(alpha=.32f),1f)
            rect(8f,47f,69f,16f,Color(0xD526684E),9f)
            rect(82f,47f,69f,16f,Color(0xD5316190),9f)
            for(i in 0..2) {
                val y=69f+i*17f
                rect(9f,y,142f,14f,if(light)Color(0x99FFFFFF) else panel.copy(alpha=.94f),8f)
                rect(16f,y+4f,26f,2.8f,ink.copy(alpha=.83f),1f)
                rect(50f,y+4f,60f,2.8f,ink.copy(alpha=.60f),1f)
                rect(121f,y+4f,22f,2.8f,ink.copy(alpha=.45f),1f)
            }
            drawCircle(bright,3f*sc,Offset(140f*sc,18f*sc))
        } else {
            // A miniature of the calendar layout: header, family row / stats,
            // month grid, agenda, weather, bottom tab strip.
            rect(9f,9f,83f,5f,ink.copy(alpha=.85f),2f)
            rect(9f,17f,55f,2f,ink.copy(alpha=.40f),1f)
            drawCircle(bright,6f*sc,Offset(139f*sc,14f*sc))
            for(i in 0..3) {
                rect(8f+i*37f,27f,33f,19f,
                    if (wood) Color(0xCD4A2B18) else panel.copy(alpha=.90f),4f)
                rect(13f+i*37f,31f,13f,3f,bright.copy(alpha=.78f),2f)
                rect(13f+i*37f,37f,9f,5f,ink.copy(alpha=.70f),1f)
            }
            rect(7f,51f,146f,69f,if(wood)Color(0xFFF0CEA0)
                else if(light) Color.White.copy(alpha=.83f)
                else panel.copy(alpha=.94f),7f)
            val dateInk=if(wood||light)Color(0xFF4D3A2D) else ink
            rect(13f,56f,51f,4f,dateInk.copy(alpha=.9f),2f)
            for(i in 0..1) drawCircle(bright.copy(alpha=.84f),3.5f*sc,
                Offset((133f+i*11f)*sc,58f*sc))
            for(row in 0..4) for(col in 0..6) {
                val x=13f+col*20f;val y=65f+row*10f
                val selected=row==1&&col==4
                rect(x,y,17.5f,8.6f,
                    if(selected)bright else if(wood)Color(0xFFE7B987)
                    else dateInk.copy(alpha=.075f),2f)
                drawCircle(
                    if(selected) Color.White else if(col%3==0)Color(0xFF50A5E2)
                    else if(col%3==1)Color(0xFFE4AB58) else Color(0xFFCF6C99),
                    1.1f*sc,Offset((x+8.5f)*sc,(y+6.4f)*sc),
                )
            }
            rect(7f,125f,95f,18f,panel.copy(alpha=.92f),5f)
            rect(106f,125f,47f,18f,panel.copy(alpha=.92f),5f)
            for(i in 0..1) rect(12f,129f+i*6.2f,83f,4.7f,
                if(light)Color(0x80FFFFFF) else ink.copy(alpha=.14f),2f)
            rect(9f,147f,142f,6f,if(wood)Color(0xDB4E2D1A)
                else panel.copy(alpha=.88f),3f)
            for(i in 0..4) drawCircle(
                if(i==0)bright else ink.copy(alpha=.55f),
                2f*sc, Offset((23f+i*28f)*sc,150f*sc))
        }
        if (wood) {
            // Tiny brass corner dots: enough to tell it apart from plain beige themes.
            listOf(Offset(6f,6f),Offset(154f,6f),Offset(6f,widget?113f:118f),
                Offset(154f,widget?113f:118f)).forEach {
                drawCircle(Color(0xFFECC080),1.4f*sc,Offset(it.x*sc,it.y*sc))
            }
        }
    }
}
