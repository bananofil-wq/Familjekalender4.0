package se.familjekalender.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The Settings screen is the visual source of truth for To-Do, Shopping and
 * Location. These are the same exact colors used by SettingsCategoryCard.
 *
 * Keep all state, data and navigation in each original screen.
 */
internal object SettingsMatchedPalette {
    val surface = Color(0xFF251F30)
    val surfaceTop = Color(0xFF302A3C)
    val surfaceBottom = Color(0xFF211C2C)
    val sectionSurface = Color(0xFF262231)
    val border = Color(0xFF645777)
    val expandedBorder = Color(0xFFAC83ED)
    val accent = Color(0xFF9861E7)
    val accentDeep = Color(0xFF6231B2)
    val text = Color.White
    val subText = Color(0xFFE0DCE9)
    val secondaryBackground = Color(0xFF201B2A)
    val divider = Color(0xFF675A76)

    fun cardGradient(): Brush = Brush.verticalGradient(
        listOf(surfaceTop, surfaceBottom)
    )
    fun iconGradient(): Brush = Brush.verticalGradient(
        listOf(accent, accentDeep)
    )
}

internal fun Modifier.settingsMatchedFill(radius: androidx.compose.ui.unit.Dp = 22.dp): Modifier =
    this.background(SettingsMatchedPalette.cardGradient(), RoundedCornerShape(radius))

/** A copy of the settings-category's 44dp purple gradient icon tile. */
@Composable
internal fun SettingsMatchedIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(SettingsMatchedPalette.iconGradient()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(23.dp),
        )
    }
}
