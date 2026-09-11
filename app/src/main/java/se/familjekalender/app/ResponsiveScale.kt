package se.familjekalender.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private const val REFERENCE_PHONE_WIDTH_DP = 411f

/**
 * Makes the complete app render against the same effective phone width.
 *
 * A 360 dp phone is scaled down and a wider phone is scaled up so the layout
 * keeps the same proportions instead of drifting between devices. The scale is
 * clamped so unusual phones/foldables do not become absurdly large or tiny.
 * Android's user font-scale setting is preserved.
 */
@Composable
internal fun ResponsiveApp(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val systemDensity = LocalDensity.current
    val widthScale = (configuration.screenWidthDp / REFERENCE_PHONE_WIDTH_DP)
        .coerceIn(0.82f, 1.12f)

    val responsiveDensity = remember(
        systemDensity.density,
        systemDensity.fontScale,
        widthScale
    ) {
        Density(
            density = systemDensity.density * widthScale,
            fontScale = systemDensity.fontScale
        )
    }

    CompositionLocalProvider(LocalDensity provides responsiveDensity) {
        content()
    }
}
