package se.familjekalender.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Settings always use a legible dark color system, irrespective of calendar skin.
 * This is intentionally scoped to the Settings tab, rather than changing other screens
 * or overriding a family's selected calendar or widget theme.
 */
private val SettingsColorScheme = darkColorScheme(
    primary = Color(0xFFB780FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF523078),
    onPrimaryContainer = Color(0xFFF9F3FF),
    secondary = Color(0xFFD7B9FF),
    onSecondary = Color(0xFF291837),
    background = Color(0xFF130F1E),
    onBackground = Color(0xFFF9F7FE),
    surface = Color(0xFF292433),
    onSurface = Color(0xFFF9F7FE),
    surfaceVariant = Color(0xFF342E40),
    onSurfaceVariant = Color(0xFFE1DCEB),
    outline = Color(0xFFBEB2D0),
    outlineVariant = Color(0xFF80728F),
    error = Color(0xFFFFABA8),
    onError = Color(0xFF42100F),
)

@Composable
internal fun SettingsReadabilityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SettingsColorScheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
