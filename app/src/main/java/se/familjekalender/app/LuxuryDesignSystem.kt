package se.familjekalender.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Familjeappens gemensamma visuella språk.
 *
 * Varumärkesaccenten är stabil genom hela appen. Säsongsteman får fortfarande
 * ge kalenderbilder och sekundära detaljer personlighet, men knappar, navigation,
 * val och primära kontroller använder samma identitet överallt.
 */
internal val LuxuryBackground = Color(0xFF09080D)
internal val LuxurySurface = Color(0xFF111016)
internal val LuxurySurfaceElevated = Color(0xFF18161F)
internal val LuxurySurfaceHigh = Color(0xFF211E29)
internal val LuxuryText = Color(0xFFF7F3F8)
internal val LuxuryTextMuted = Color(0xFFAFA8B8)
internal val LuxuryOutline = Color(0xFF3B3544)
internal val LuxuryOutlineSoft = Color(0xFF29242F)
internal val LuxuryAccent = Color(0xFFB792F6)
internal val LuxuryAccentSoft = Color(0xFF2A2038)
internal val LuxuryChampagne = Color(0xFFD8C3A5)
internal val LuxurySuccess = Color(0xFF78D6B1)
internal val LuxuryError = Color(0xFFFF8FA0)

internal object LuxuryMotion {
    const val Micro = 130
    const val Fast = 190
    const val Standard = 300
    const val Slow = 430
}

private fun luxuryStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Float = 0f
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.sp
)

internal val LuxuryTypography = Typography(
    displayLarge = luxuryStyle(50, 56, FontWeight.SemiBold, -1.0f),
    displayMedium = luxuryStyle(42, 48, FontWeight.SemiBold, -0.8f),
    displaySmall = luxuryStyle(34, 40, FontWeight.SemiBold, -0.5f),
    headlineLarge = luxuryStyle(31, 37, FontWeight.SemiBold, -0.4f),
    headlineMedium = luxuryStyle(27, 33, FontWeight.SemiBold, -0.3f),
    headlineSmall = luxuryStyle(23, 29, FontWeight.SemiBold, -0.2f),
    titleLarge = luxuryStyle(21, 27, FontWeight.SemiBold, -0.15f),
    titleMedium = luxuryStyle(16, 22, FontWeight.SemiBold, 0f),
    titleSmall = luxuryStyle(14, 20, FontWeight.SemiBold, 0.05f),
    bodyLarge = luxuryStyle(16, 24, FontWeight.Normal, 0.03f),
    bodyMedium = luxuryStyle(14, 21, FontWeight.Normal, 0.06f),
    bodySmall = luxuryStyle(12, 18, FontWeight.Normal, 0.08f),
    labelLarge = luxuryStyle(14, 20, FontWeight.SemiBold, 0.08f),
    labelMedium = luxuryStyle(12, 17, FontWeight.SemiBold, 0.12f),
    labelSmall = luxuryStyle(11, 16, FontWeight.Medium, 0.16f)
)

internal val LuxuryShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
internal fun FamiljekalenderLuxuryTheme(
    palette: SeasonPalette,
    content: @Composable () -> Unit
) {
    val motionEnabled = appMotionEnabled()
    val accent by animateColorAsState(
        targetValue = LuxuryAccent,
        animationSpec = tween(
            durationMillis = motionDuration(LuxuryMotion.Slow, motionEnabled),
            easing = FastOutSlowInEasing
        ),
        label = "luxury-theme-accent"
    )

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF130F18),
            primaryContainer = LuxuryAccentSoft,
            onPrimaryContainer = LuxuryText,
            secondary = accent,
            onSecondary = Color(0xFF130F18),
            secondaryContainer = LuxurySurfaceHigh,
            onSecondaryContainer = LuxuryText,
            tertiary = palette.accent,
            onTertiary = Color(0xFF171019),
            tertiaryContainer = palette.soft,
            onTertiaryContainer = LuxuryText,
            background = LuxuryBackground,
            onBackground = LuxuryText,
            surface = LuxurySurface,
            onSurface = LuxuryText,
            surfaceVariant = LuxurySurfaceElevated,
            onSurfaceVariant = LuxuryTextMuted,
            surfaceTint = Color.Transparent,
            inverseSurface = LuxuryText,
            inverseOnSurface = LuxuryBackground,
            outline = LuxuryOutline,
            outlineVariant = LuxuryOutlineSoft,
            error = LuxuryError,
            onError = Color(0xFF24070C)
        ),
        typography = LuxuryTypography,
        shapes = LuxuryShapes,
        content = content
    )
}
