package se.familjekalender.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One responsive calendar layout; only the native-drawn material changes. */
internal val LocalPremiumMaterialTheme = compositionLocalOf { CleanVisualTheme.OAK_WOOD }

internal fun isPremiumMaterialTheme(theme: CleanVisualTheme) =
    theme == CleanVisualTheme.OAK_WOOD ||
        theme == CleanVisualTheme.COPPER ||
        theme == CleanVisualTheme.CARBON_FIBER ||
        theme == CleanVisualTheme.TITANIUM ||
        theme == CleanVisualTheme.HIPPIE

internal data class PremiumMaterialSkin(
    val theme: CleanVisualTheme,
    val spec: CleanThemeSpec,
    val calendarInk: Color,
    val panelInk: Color,
    val headerInk: Color,
    val mutedInk: Color,
    val tileInk: Color,
    val selectedInk: Color,
    val calendarTop: Color,
    val calendarBottom: Color,
    val tileTop: Color,
    val tileBottom: Color,
    val selectedTop: Color,
    val selectedBottom: Color,
    val border: Color,
    val todayRing: Color,
    val buttonTop: Color,
    val buttonMid: Color,
    val buttonBottom: Color,
    val panelTop: Color,
    val panelBottom: Color,
    val agendaRowTop: Color,
    val agendaRowBottom: Color,
) {
    val oak get() = theme == CleanVisualTheme.OAK_WOOD
    val hippie get() = theme == CleanVisualTheme.HIPPIE
}

internal fun premiumSkin(theme: CleanVisualTheme): PremiumMaterialSkin {
    val s = cleanThemeSpec(theme)
    return when (theme) {
        CleanVisualTheme.COPPER -> PremiumMaterialSkin(
            theme, s, Color(0xFFFFD9A6), Color(0xFFFFE3BC),
            Color(0xFFFFE3BC), Color(0xFFDBAD84),
            Color(0xFFFFE2BF), Color(0xFFFFF4D7),
            Color(0xFF824322), Color(0xFF3E1D12),
            Color(0xFF96512D), Color(0xFF512715),
            Color(0xFFA75A2D), Color(0xFF35180F),
            Color(0xFFDB985E), Color(0xFFFFDF8C),
            Color(0xFFE8AB70), Color(0xFF95502C), Color(0xFF311710),
            Color(0xFF60301B), Color(0xFF29140E),
            Color(0xFF6E3821), Color(0xFF32180F),
        )
        CleanVisualTheme.CARBON_FIBER -> PremiumMaterialSkin(
            theme, s, Color(0xFFEAF4FF), Color(0xFFF1F8FF),
            Color(0xFFF3FAFF), Color(0xFF9FBBD0),
            Color(0xFFF2F8FF), Color(0xFFFFFFFF),
            Color(0xFF182A38), Color(0xFF070E17),
            Color(0xFF1D2D3B), Color(0xFF0A121B),
            Color(0xFF154D82), Color(0xFF071B30),
            Color(0xFF2C94D5), Color(0xFF45BDFF),
            Color(0xFF72CFFF), Color(0xFF174A70), Color(0xFF050D17),
            Color(0xFF182C39), Color(0xFF08111B),
            Color(0xFF162B38), Color(0xFF0A161F),
        )
        CleanVisualTheme.TITANIUM -> PremiumMaterialSkin(
            theme, s, Color(0xFF1F2B34), Color(0xFF1B2934),
            Color(0xFF18252E), Color(0xFF4C5C67),
            Color(0xFF1B2934), Color(0xFFFFFFFF),
            Color(0xFFE0E5E8), Color(0xFF929FA8),
            Color(0xFFF0F2F3), Color(0xFFB3BDC4),
            Color(0xFF677A88), Color(0xFF233542),
            Color(0xFF657580), Color(0xFF146EAB),
            Color(0xFFF5F8FA), Color(0xFF9FAEB7), Color(0xFF4B5964),
            Color(0xFFDAE2E6), Color(0xFF86949D),
            Color(0xFFD8E0E5), Color(0xFFABB7BE),
        )
        CleanVisualTheme.HIPPIE -> PremiumMaterialSkin(
            theme, s, Color(0xFF492517), Color(0xFFFFF0CD),
            Color(0xFFFFEF9C), Color(0xFF88542F),
            Color(0xFF45250F), Color(0xFF35190B),
            Color(0xFFFFE7B0), Color(0xFFF8C987),
            Color(0xFFFFEAC1), Color(0xFFFFCC8A),
            Color(0xFFFFAC3C), Color(0xFFE87129),
            Color(0xFFB76126), Color(0xFFFFE64A),
            Color(0xFFFFBD54), Color(0xFFDD6E2A), Color(0xFF74351B),
            Color(0xFF176D67), Color(0xFF0A454D),
            Color(0xFF20746B), Color(0xFF0B4D52),
        )
        else -> PremiumMaterialSkin(
            theme, s, Color(0xFF34190C), Color(0xFFFCE5BF),
            Color(0xFF2E1609), Color(0xFF775039),
            Color(0xFF34190C), Color(0xFFFCE5BF),
            Color(0xFFF1C793), Color(0xFFDFB17E),
            Color(0xFFFFE9C7), Color(0xFFDDB285),
            Color(0xFF8F5129), Color(0xFF321608),
            Color(0xFFBD814C), Color(0xFFFFC66C),
            Color(0xFFC99459), Color(0xFF73411F), Color(0xFF341A0E),
            Color(0xFF4C2816), Color(0xFF29140B),
            Color(0xFF6B4229), Color(0xFF361A0D),
        )
    }
}

/** Procedural lines are drawn in physical pixels; text and icons are NEVER rasterized. */
@Composable
internal fun PremiumMaterialSurface(
    modifier: Modifier,
    opacity: Float = 1f,
    material: OakPhotoMaterial = OakPhotoMaterial.DARK,
) {
    val theme = LocalPremiumMaterialTheme.current
    if (theme == CleanVisualTheme.OAK_WOOD) {
        OakPhotographicSurface(modifier, opacity, material)
        return
    }
    val skin = premiumSkin(theme)
    val pair = when (material) {
        OakPhotoMaterial.PALE -> skin.calendarTop to skin.calendarBottom
        OakPhotoMaterial.TOP -> skin.spec.backgroundTop to skin.spec.backgroundBottom
        OakPhotoMaterial.AMBER -> skin.panelTop to skin.panelBottom
        OakPhotoMaterial.DARK -> skin.panelTop to skin.panelBottom
    }
    Canvas(modifier) {
        drawRect(brush = Brush.verticalGradient(listOf(
            pair.first.copy(alpha = opacity), pair.second.copy(alpha = opacity),
        )))
        if (opacity < .35f) return@Canvas
        when (theme) {
            CleanVisualTheme.COPPER -> {
                var y = 1f
                while (y < size.height) {
                    val line = if ((y.toInt() / 3) % 3 == 0) Color(0xFFDE9B65) else Color(0xFF1C0804)
                    drawLine(line.copy(alpha = opacity * .17f),
                        Offset(0f, y), Offset(size.width, y + (y % 3f)),
                        strokeWidth = if (y.toInt() % 7 == 0) 1.3f else .55f)
                    y += 3.2f
                }
            }
            CleanVisualTheme.CARBON_FIBER -> {
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(Color(0xFFB1C4D5).copy(alpha = opacity * .12f),
                        Offset(x, 0f), Offset(x + size.height, size.height),
                        strokeWidth = 3f)
                    drawLine(Color.Black.copy(alpha = opacity * .25f),
                        Offset(x + 5f, 0f), Offset(x + size.height + 5f, size.height),
                        strokeWidth = 2.5f)
                    x += 10f
                }
                var x2 = 0f
                while (x2 < size.width + size.height) {
                    drawLine(Color(0xFF6288A1).copy(alpha = opacity * .075f),
                        Offset(x2, 0f), Offset(x2 - size.height, size.height), strokeWidth = 2f)
                    x2 += 12f
                }
            }
            CleanVisualTheme.TITANIUM -> {
                var y = 0f
                while (y < size.height) {
                    drawLine(Color.White.copy(alpha = opacity * .23f),
                        Offset(0f, y), Offset(size.width, y),
                        strokeWidth = .65f)
                    drawLine(Color(0xFF22313B).copy(alpha = opacity * .13f),
                        Offset(0f, y + 1.6f), Offset(size.width, y + 1.6f),
                        strokeWidth = .55f)
                    y += 4.5f
                }
            }
            CleanVisualTheme.HIPPIE -> {
                // Subtle hand-drawn flower motifs; keep central content contrast.
                val centers = listOf(
                    Offset(size.width * .07f, size.height * .18f),
                    Offset(size.width * .93f, size.height * .82f),
                )
                centers.forEachIndexed { i, center ->
                    val radius = (size.width * .026f).coerceIn(5f, 17f)
                    val petals = if (i == 0) Color(0xFFFFC336) else Color(0xFFF68EBB)
                    repeat(6) { n ->
                        val angle = n * Math.PI / 3
                        drawCircle(petals.copy(alpha = opacity * .4f), radius * .7f,
                            center + Offset(
                                (kotlin.math.cos(angle) * radius).toFloat(),
                                (kotlin.math.sin(angle) * radius).toFloat(),
                            ))
                    }
                    drawCircle(Color(0xFF7D3B36).copy(alpha = opacity * .55f),
                        radius * .43f, center)
                }
            }
            else -> Unit
        }
    }
}

@Composable
internal fun PremiumMaterialBackground(modifier: Modifier = Modifier) {
    val theme = LocalPremiumMaterialTheme.current
    if (theme == CleanVisualTheme.OAK_WOOD) {
        OakPhotographicBackground(modifier)
    } else {
        PremiumMaterialSurface(modifier, material = OakPhotoMaterial.TOP)
    }
}

@Composable
internal fun PremiumMaterialDecoration(modifier: Modifier) {
    val theme = LocalPremiumMaterialTheme.current
    when (theme) {
        CleanVisualTheme.OAK_WOOD -> OakLeafDecoration(modifier)
        CleanVisualTheme.HIPPIE ->
            Box(modifier, contentAlignment = Alignment.Center) {
                Text("✿", color = Color(0xFFFFCB42), fontSize = 29.sp,
                    fontWeight = FontWeight.Bold)
            }
        CleanVisualTheme.CARBON_FIBER ->
            Box(modifier, contentAlignment = Alignment.Center) {
                Text("◆", color = Color(0xFF41AEFC), fontSize = 17.sp)
            }
        CleanVisualTheme.TITANIUM ->
            Box(modifier, contentAlignment = Alignment.Center) {
                Text("◇", color = Color(0xFF32434D), fontSize = 22.sp)
            }
        CleanVisualTheme.COPPER ->
            Box(modifier, contentAlignment = Alignment.Center) {
                Text("✦", color = Color(0xFFF7C28B), fontSize = 22.sp)
            }
        else -> Unit
    }
}

@Composable
internal fun PremiumMaterialWeatherScene(modifier: Modifier) {
    val theme = LocalPremiumMaterialTheme.current
    if (theme == CleanVisualTheme.OAK_WOOD) {
        OakSunsetDecoration(modifier)
        return
    }
    val hippie = theme == CleanVisualTheme.HIPPIE
    val sky = when (theme) {
        CleanVisualTheme.COPPER -> listOf(Color(0xFFC77A3F), Color(0xFF432016))
        CleanVisualTheme.CARBON_FIBER -> listOf(Color(0xFF20619A), Color(0xFF07121E))
        CleanVisualTheme.TITANIUM -> listOf(Color(0xFF9AB6C9), Color(0xFF334652))
        else -> listOf(Color(0xFFFDA44A), Color(0xFFE65F58))
    }
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(sky))
        drawCircle(if (hippie) Color(0xFFFFEC7A) else Color(0xFFFFD9A1),
            radius = size.minDimension * .22f,
            center = Offset(size.width * .72f, size.height * .52f))
        val mountain = Path().apply {
            moveTo(0f, size.height * .85f)
            lineTo(size.width * .21f, size.height * .39f)
            lineTo(size.width * .45f, size.height * .82f)
            lineTo(size.width * .66f, size.height * .30f)
            lineTo(size.width, size.height * .82f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(mountain,
            if (hippie) Color(0xFF176963)
            else if (theme == CleanVisualTheme.TITANIUM) Color(0xFF465E6F)
            else if (theme == CleanVisualTheme.CARBON_FIBER) Color(0xFF102C46)
            else Color(0xFF402012))
        drawLine(Color.White.copy(alpha = .17f),
            Offset(0f, size.height * .90f),
            Offset(size.width, size.height * .90f), strokeWidth = 1.2f)
    }
}
