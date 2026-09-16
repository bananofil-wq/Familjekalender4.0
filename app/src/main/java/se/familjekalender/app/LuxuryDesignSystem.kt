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
 * One visual language for the whole app.
 * Seasonal themes may change the accent, while surfaces, typography, radius and motion stay consistent.
 */
internal val LuxuryBackground = Color(0xFF0B0A0F)
internal val LuxurySurface = Color(0xFF141218)
internal val LuxurySurfaceElevated = Color(0xFF1B1821)
internal val LuxurySurfaceHigh = Color(0xFF24202B)
internal val LuxuryText = Color(0xFFF8F5FA)
internal val LuxuryTextMuted = Color(0xFFBBB4C2)
internal val LuxuryOutline = Color(0xFF4A4352)
internal val LuxuryOutlineSoft = Color(0xFF2F2A36)
internal val LuxuryChampagne = Color(0xFFE3C995)
internal val LuxuryError = Color(0xFFFF8A9B)

internal object LuxuryMotion {
    const val Micro = 150
    const val Fast = 220
    const val Standard = 340
    const val Slow = 520
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
    displayLarge = luxuryStyle(52, 58, FontWeight.SemiBold, -1.0f),
    displayMedium = luxuryStyle(44, 50, FontWeight.SemiBold, -0.8f),
    displaySmall = luxuryStyle(36, 42, FontWeight.SemiBold, -0.5f),
    headlineLarge = luxuryStyle(32, 38, FontWeight.SemiBold, -0.4f),
    headlineMedium = luxuryStyle(28, 34, FontWeight.SemiBold, -0.3f),
    headlineSmall = luxuryStyle(24, 30, FontWeight.SemiBold, -0.2f),
    titleLarge = luxuryStyle(22, 28, FontWeight.SemiBold, -0.15f),
    titleMedium = luxuryStyle(16, 22, FontWeight.SemiBold, 0f),
    titleSmall = luxuryStyle(14, 20, FontWeight.SemiBold, 0.05f),
    bodyLarge = luxuryStyle(16, 24, FontWeight.Normal, 0.05f),
    bodyMedium = luxuryStyle(14, 21, FontWeight.Normal, 0.08f),
    bodySmall = luxuryStyle(12, 18, FontWeight.Normal, 0.1f),
    labelLarge = luxuryStyle(14, 20, FontWeight.SemiBold, 0.1f),
    labelMedium = luxuryStyle(12, 17, FontWeight.SemiBold, 0.15f),
    labelSmall = luxuryStyle(11, 16, FontWeight.Medium, 0.2f)
)

internal val LuxuryShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
internal fun FamiljekalenderLuxuryTheme(
    palette: SeasonPalette,
    content: @Composable () -> Unit
) {
    val motionEnabled = appMotionEnabled()
    val accent by animateColorAsState(
        targetValue = palette.accent,
        animationSpec = tween(
            durationMillis = motionDuration(LuxuryMotion.Slow, motionEnabled),
            easing = FastOutSlowInEasing
        ),
        label = "luxury-theme-accent"
    )

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF140F18),
            primaryContainer = palette.soft,
            onPrimaryContainer = LuxuryText,
            secondary = accent,
            onSecondary = Color(0xFF140F18),
            secondaryContainer = LuxurySurfaceHigh,
            onSecondaryContainer = LuxuryText,
            tertiary = LuxuryChampagne,
            onTertiary = Color(0xFF18130B),
            tertiaryContainer = Color(0xFF302819),
            onTertiaryContainer = Color(0xFFF5E8CD),
            background = LuxuryBackground,
            onBackground = LuxuryText,
            surface = LuxurySurface,
            onSurface = LuxuryText,
            surfaceVariant = LuxurySurfaceElevated,
            onSurfaceVariant = LuxuryTextMuted,
            surfaceTint = accent,
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
