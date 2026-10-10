package se.familjekalender.app

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance

/** Colors are resolved from the same saved theme as the main calendar. */
internal object WidgetThemeColors {
    data class Palette(val background: Int, val row: Int, val text: Int, val muted: Int, val accent: Int, val onAccent: Int)

    fun colors(context: Context): Palette {
        val prefs = context.getSharedPreferences("family_calendar", Context.MODE_PRIVATE)
        val theme = resolveCleanVisualTheme(prefs.getString("clean_visual_theme", CleanVisualTheme.CURRENT.name))
        val spec = cleanThemeSpec(theme)
        val accent = spec.accent.toArgb()
        // Avoid white text on the light accents of glass, gold and pastel palettes.
        val onAccent = if (spec.accent.luminance() > 0.45f) 0xFF14202B.toInt() else 0xFFFFFFFF.toInt()
        return Palette(spec.backgroundTop.toArgb(), spec.panelTop.toArgb(), spec.text.toArgb(), spec.muted.toArgb(), accent, onAccent)
    }

    fun apply(context: Context, views: RemoteViews) {
        val c = colors(context)
        views.setInt(R.id.widget_root, "setBackgroundColor", c.background)
        views.setInt(R.id.widget_badge_day, "setBackgroundColor", c.accent)
        views.setInt(R.id.widget_badge_number, "setBackgroundColor", c.row)
        views.setTextColor(R.id.widget_badge_day, c.onAccent)
        views.setTextColor(R.id.widget_badge_number, c.text)
        views.setTextColor(R.id.widget_date, c.text)
        views.setTextColor(R.id.widget_status, c.muted)
        views.setTextColor(R.id.widget_refresh, c.accent)
        views.setTextColor(R.id.widget_todo, c.onAccent)
        views.setTextColor(R.id.widget_shopping, c.onAccent)
        views.setInt(R.id.widget_todo, "setBackgroundColor", c.accent)
        views.setInt(R.id.widget_shopping, "setBackgroundColor", c.accent)
    }
}
