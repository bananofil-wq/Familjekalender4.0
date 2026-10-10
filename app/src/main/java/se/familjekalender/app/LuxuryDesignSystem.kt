package se.familjekalender.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
 * Varumärkesaccenten är stabil genom hela appen. Säsongsteman får fortfarande ge kalenderbilder och
 * sekundära detaljer personlighet, men knappar, navigation, val och primära kontroller använder
 * samma identitet överallt.
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
    letterSpacing: Float = 0f,
) =
    TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight,
        letterSpacing = letterSpacing.sp,
    )

internal val LuxuryTypography =
    Typography(
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
        labelSmall = luxuryStyle(11, 16, FontWeight.Medium, 0.16f),
    )

internal val LuxuryShapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(26.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

private fun cleanMaterialColorScheme(theme: CleanVisualTheme) =
    cleanThemeSpec(theme).let { spec ->
        val lightTheme =
            when (theme) {
                CleanVisualTheme.NORDIC_DAY_PLANNER,
                CleanVisualTheme.JAPANDI,
                CleanVisualTheme.MEMPHIS,
                CleanVisualTheme.SWISS,
                CleanVisualTheme.RETRO_70S,
                CleanVisualTheme.CLAY,
                CleanVisualTheme.PURE_CALENDAR,
                CleanVisualTheme.PASTEL_FLOW,
                CleanVisualTheme.EDITORIAL_PLANNER,
                CleanVisualTheme.EARTH_SAGE,
                CleanVisualTheme.FAMILY_SPECTRUM -> true
                else -> false
            }

        val buildScheme:
            (
                primary: Color,
                onPrimary: Color,
                primaryContainer: Color,
                onPrimaryContainer: Color,
                secondary: Color,
                onSecondary: Color,
                secondaryContainer: Color,
                onSecondaryContainer: Color,
                tertiary: Color,
                onTertiary: Color,
                tertiaryContainer: Color,
                onTertiaryContainer: Color,
                background: Color,
                onBackground: Color,
                surface: Color,
                onSurface: Color,
                surfaceVariant: Color,
                onSurfaceVariant: Color,
                outline: Color,
                outlineVariant: Color,
                error: Color,
                onError: Color,
            ) -> androidx.compose.material3.ColorScheme =
            if (lightTheme) {
                { primary,
                    onPrimary,
                    primaryContainer,
                    onPrimaryContainer,
                    secondary,
                    onSecondary,
                    secondaryContainer,
                    onSecondaryContainer,
                    tertiary,
                    onTertiary,
                    tertiaryContainer,
                    onTertiaryContainer,
                    background,
                    onBackground,
                    surface,
                    onSurface,
                    surfaceVariant,
                    onSurfaceVariant,
                    outline,
                    outlineVariant,
                    error,
                    onError ->
                    lightColorScheme(
                        primary = primary,
                        onPrimary = onPrimary,
                        primaryContainer = primaryContainer,
                        onPrimaryContainer = onPrimaryContainer,
                        secondary = secondary,
                        onSecondary = onSecondary,
                        secondaryContainer = secondaryContainer,
                        onSecondaryContainer = onSecondaryContainer,
                        tertiary = tertiary,
                        onTertiary = onTertiary,
                        tertiaryContainer = tertiaryContainer,
                        onTertiaryContainer = onTertiaryContainer,
                        background = background,
                        onBackground = onBackground,
                        surface = surface,
                        onSurface = onSurface,
                        surfaceVariant = surfaceVariant,
                        onSurfaceVariant = onSurfaceVariant,
                        surfaceTint = Color.Transparent,
                        inverseSurface = spec.text,
                        inverseOnSurface = spec.panelTop,
                        outline = outline,
                        outlineVariant = outlineVariant,
                        error = error,
                        onError = onError,
                    )
                }
            } else {
                { primary,
                    onPrimary,
                    primaryContainer,
                    onPrimaryContainer,
                    secondary,
                    onSecondary,
                    secondaryContainer,
                    onSecondaryContainer,
                    tertiary,
                    onTertiary,
                    tertiaryContainer,
                    onTertiaryContainer,
                    background,
                    onBackground,
                    surface,
                    onSurface,
                    surfaceVariant,
                    onSurfaceVariant,
                    outline,
                    outlineVariant,
                    error,
                    onError ->
                    darkColorScheme(
                        primary = primary,
                        onPrimary = onPrimary,
                        primaryContainer = primaryContainer,
                        onPrimaryContainer = onPrimaryContainer,
                        secondary = secondary,
                        onSecondary = onSecondary,
                        secondaryContainer = secondaryContainer,
                        onSecondaryContainer = onSecondaryContainer,
                        tertiary = tertiary,
                        onTertiary = onTertiary,
                        tertiaryContainer = tertiaryContainer,
                        onTertiaryContainer = onTertiaryContainer,
                        background = background,
                        onBackground = onBackground,
                        surface = surface,
                        onSurface = onSurface,
                        surfaceVariant = surfaceVariant,
                        onSurfaceVariant = onSurfaceVariant,
                        surfaceTint = Color.Transparent,
                        inverseSurface = spec.text,
                        inverseOnSurface = spec.panelTop,
                        outline = outline,
                        outlineVariant = outlineVariant,
                        error = error,
                        onError = onError,
                    )
                }
            }

        buildScheme(
            spec.accentStrong,
            spec.selectedText,
            spec.dayTop,
            spec.text,
            spec.secondary,
            spec.selectedText,
            spec.panelMid,
            spec.text,
            spec.warning,
            spec.selectedText,
            spec.panelBottom,
            spec.text,
            spec.backgroundTop,
            spec.text,
            spec.panelTop,
            spec.text,
            spec.panelMid,
            spec.muted,
            spec.border,
            spec.navBorder,
            if (lightTheme) Color(0xFFB94752) else LuxuryError,
            if (lightTheme) Color.White else Color(0xFF24070C),
        )
    }

private fun cleanMaterialShapes(theme: CleanVisualTheme): Shapes {
    val spec = cleanThemeSpec(theme)
    return Shapes(
        extraSmall = RoundedCornerShape((spec.buttonRadius.value * .55f).dp),
        small = RoundedCornerShape((spec.buttonRadius.value * .75f).dp),
        medium = RoundedCornerShape(spec.buttonRadius),
        large = RoundedCornerShape(spec.cardRadius),
        extraLarge = RoundedCornerShape(spec.calendarRadius),
    )
}

@Composable
internal fun FamiljekalenderLuxuryTheme(
    palette: SeasonPalette,
    lightMode: Boolean = false,
    nordicDarkMode: Boolean = false,
    cleanVisualTheme: CleanVisualTheme? = null,
    content: @Composable () -> Unit,
) {
    val motionEnabled = appMotionEnabled()
    val accent by
    animateColorAsState(
        targetValue = LuxuryAccent,
        animationSpec =
            tween(
                durationMillis = motionDuration(LuxuryMotion.Slow, motionEnabled),
                easing = FastOutSlowInEasing,
            ),
        label = "luxury-theme-accent",
    )

    val scheme =
        when {
            cleanVisualTheme != null -> cleanMaterialColorScheme(cleanVisualTheme)
            lightMode ->
                lightColorScheme(
                    primary = Color(0xFF0875A8),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFE9F5FA),
                    onPrimaryContainer = Color(0xFF17334A),
                    secondary = Color(0xFF46B5D8),
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xFFDFF2F8),
                    onSecondaryContainer = Color(0xFF17334A),
                    tertiary = Color(0xFFD99B2B),
                    onTertiary = Color.White,
                    tertiaryContainer = Color(0xFFFFF2DD),
                    onTertiaryContainer = Color(0xFF4A3A13),
                    background = Color(0xFFFFFEFA),
                    onBackground = Color(0xFF17334A),
                    surface = Color(0xFFFFFFFF),
                    onSurface = Color(0xFF17334A),
                    surfaceVariant = Color(0xFFF8F5ED),
                    onSurfaceVariant = Color(0xFF71808B),
                    surfaceTint = Color.Transparent,
                    inverseSurface = Color(0xFF17334A),
                    inverseOnSurface = Color(0xFFFFFEFA),
                    outline = Color(0xFFB8C4CC),
                    outlineVariant = Color(0xFFDDE4E8),
                    error = Color(0xFFB94752),
                    onError = Color.White,
                )

            nordicDarkMode ->
                darkColorScheme(
                    primary = Color(0xFFFF8A00),
                    onPrimary = Color(0xFF120B02),
                    primaryContainer = Color(0xFF3A2105),
                    onPrimaryContainer = Color(0xFFFFD9AC),
                    secondary = Color(0xFFFFA23A),
                    onSecondary = Color(0xFF160D03),
                    secondaryContainer = Color(0xFF35220E),
                    onSecondaryContainer = Color(0xFFFFDDB6),
                    tertiary = Color(0xFFFFB347),
                    onTertiary = Color(0xFF160D03),
                    tertiaryContainer = Color(0xFF3A2910),
                    onTertiaryContainer = Color(0xFFFFE1B9),
                    background = Color(0xFF0B0B0B),
                    onBackground = Color(0xFFF4F4F4),
                    surface = Color(0xFF151515),
                    onSurface = Color(0xFFF4F4F4),
                    surfaceVariant = Color(0xFF1D1D1D),
                    onSurfaceVariant = Color(0xFFB0B0B0),
                    surfaceTint = Color.Transparent,
                    inverseSurface = Color(0xFFF4F4F4),
                    inverseOnSurface = Color(0xFF111111),
                    outline = Color(0xFF555555),
                    outlineVariant = Color(0xFF303030),
                    error = Color(0xFFFF8FA0),
                    onError = Color(0xFF24070C),
                )

            else ->
                darkColorScheme(
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
                    onError = Color(0xFF24070C),
                )
        }

    MaterialTheme(
        colorScheme = scheme,
        typography = LuxuryTypography,
        shapes =
            cleanVisualTheme?.let(::cleanMaterialShapes)
                ?: LuxuryShapes,
        content = content,
    )
}
