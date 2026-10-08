package se.familjekalender.app

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb

/** Colors are resolved from the same saved theme as the main calendar. */
internal object WidgetThemeColors {
    data class Palette(val background: Int, val row: Int, val text: Int, val muted: Int, val accent: Int, val onAccent: Int)

    fun colors(context: Context): Palette {
        val prefs = context.getSharedPreferences("family_calendar", Context.MODE_PRIVATE)
        val theme = runCatching {
            CleanVisualTheme.valueOf(prefs.getString("clean_visual_theme", CleanVisualTheme.CURRENT.name)!!)
        }.getOrDefault(CleanVisualTheme.CURRENT)
        val spec = cleanThemeSpec(theme)
        val accent = spec.accent.toArgb()
        val onAccent = if (androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.42) 0xFF171717.toInt() else 0xFFFFFFFF.toInt()
        return Palette(spec.backgroundTop.toArgb(), spec.panelMid.toArgb(), spec.text.toArgb(), spec.muted.toArgb(), accent, onAccent)
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
