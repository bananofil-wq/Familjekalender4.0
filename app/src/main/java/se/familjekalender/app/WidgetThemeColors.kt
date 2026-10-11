package se.familjekalender.app

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb

/**
 * Widget appearance is independent of the in-app Clean theme.
 * The original frosted dark widget remains the default after migration.
 * All skin changes are performed on the same RemoteViews IDs and layout, so existing
 * widgets keep scrolling, expanding groups, shortcuts and PendingIntents.
 */
internal object WidgetThemeColors {
    const val PREF_KEY = "widget_visual_theme"
    const val ORIGINAL = "ORIGINAL_WIDGET"
    const val FOLLOW_CALENDAR = "FOLLOW_CALENDAR"
    private const val PREFS = "family_calendar"

    enum class Skin {
        CLASSIC, WOOD, COPPER, TITANIUM, CARBON, HIPPIE, LIGHT, DARK,
        GLASS, FOREST, GOLD, PASTEL, GOTHAM, RETRO, SAGE, PAPER,
    }

    data class Palette(
        val skin: Skin,
        val root: Int?,
        val row: Int?,
        val text: Int,
        val muted: Int,
        val accent: Int,
        val onAccent: Int,
    )

    /** Legacy widget installations keep their current dark glass appearance. */
    fun savedChoice(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_KEY, ORIGINAL) ?: ORIGINAL

    fun selectedTheme(context: Context): CleanVisualTheme? {
        val saved = savedChoice(context)
        if (saved == ORIGINAL) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = if (saved == FOLLOW_CALENDAR) {
            prefs.getString("clean_visual_theme", CleanVisualTheme.CURRENT.name)
        } else saved
        return resolveCleanVisualTheme(name)
    }

    fun saveChoice(context: Context, choice: String) {
        val valid = choice == ORIGINAL || choice == FOLLOW_CALENDAR ||
            selectableCleanVisualThemes.any { it.name == choice }
        if (!valid) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(PREF_KEY, choice).apply()
    }

    private fun skin(theme: CleanVisualTheme): Skin = when (theme) {
        CleanVisualTheme.OAK_WOOD -> Skin.WOOD
        CleanVisualTheme.COPPER -> Skin.COPPER
        CleanVisualTheme.TITANIUM -> Skin.TITANIUM
        CleanVisualTheme.CARBON_FIBER, CleanVisualTheme.NEON_PULSE -> Skin.CARBON
        CleanVisualTheme.HIPPIE -> Skin.HIPPIE
        CleanVisualTheme.NORDIC_DAY_PLANNER, CleanVisualTheme.PURE_CALENDAR,
        CleanVisualTheme.FAMILY_SPECTRUM -> Skin.LIGHT
        CleanVisualTheme.MIDNIGHT_GOLD, CleanVisualTheme.LUXURY_GOLD -> Skin.GOLD
        CleanVisualTheme.PASTEL_FLOW, CleanVisualTheme.CLAY -> Skin.PASTEL
        CleanVisualTheme.CRYSTAL_GLASS, CleanVisualTheme.ICE_GLASS -> Skin.GLASS
        CleanVisualTheme.EDITORIAL_PLANNER, CleanVisualTheme.JAPANDI -> Skin.PAPER
        CleanVisualTheme.EARTH_SAGE -> Skin.SAGE
        CleanVisualTheme.FOREST_PRO, CleanVisualTheme.BIOPHILIC -> Skin.FOREST
        CleanVisualTheme.RETRO_DIGITAL, CleanVisualTheme.RETRO_70S -> Skin.RETRO
        CleanVisualTheme.GOTHAM_NIGHT -> Skin.GOTHAM
        CleanVisualTheme.CURRENT -> Skin.GLASS
        else -> Skin.DARK
    }

    private fun backgroundResource(skin: Skin): Int? = when (skin) {
        Skin.CLASSIC -> null
            Skin.WOOD -> R.drawable.widget_skin_wood_background
            Skin.COPPER -> R.drawable.widget_skin_copper_background
            Skin.TITANIUM -> R.drawable.widget_skin_titanium_background
            Skin.CARBON -> R.drawable.widget_skin_carbon_background
            Skin.HIPPIE -> R.drawable.widget_skin_hippie_background
            Skin.LIGHT -> R.drawable.widget_skin_light_background
            Skin.DARK -> R.drawable.widget_skin_dark_background
            Skin.GLASS -> R.drawable.widget_skin_glass_background
            Skin.FOREST -> R.drawable.widget_skin_forest_background
            Skin.GOLD -> R.drawable.widget_skin_gold_background
            Skin.PASTEL -> R.drawable.widget_skin_pastel_background
            Skin.GOTHAM -> R.drawable.widget_skin_gotham_background
            Skin.RETRO -> R.drawable.widget_skin_retro_background
            Skin.SAGE -> R.drawable.widget_skin_sage_background
            Skin.PAPER -> R.drawable.widget_skin_paper_background
    }

    private fun rowResource(skin: Skin): Int? = when (skin) {
        Skin.CLASSIC -> null
            Skin.WOOD -> R.drawable.widget_skin_wood_row
            Skin.COPPER -> R.drawable.widget_skin_copper_row
            Skin.TITANIUM -> R.drawable.widget_skin_titanium_row
            Skin.CARBON -> R.drawable.widget_skin_carbon_row
            Skin.HIPPIE -> R.drawable.widget_skin_hippie_row
            Skin.LIGHT -> R.drawable.widget_skin_light_row
            Skin.DARK -> R.drawable.widget_skin_dark_row
            Skin.GLASS -> R.drawable.widget_skin_glass_row
            Skin.FOREST -> R.drawable.widget_skin_forest_row
            Skin.GOLD -> R.drawable.widget_skin_gold_row
            Skin.PASTEL -> R.drawable.widget_skin_pastel_row
            Skin.GOTHAM -> R.drawable.widget_skin_gotham_row
            Skin.RETRO -> R.drawable.widget_skin_retro_row
            Skin.SAGE -> R.drawable.widget_skin_sage_row
            Skin.PAPER -> R.drawable.widget_skin_paper_row
    }

    fun colors(context: Context): Palette {
        val theme = selectedTheme(context)
        if (theme == null) {
            return Palette(Skin.CLASSIC,
                R.drawable.widget_background, R.drawable.widget_event_row_bg,
                0xFFFFFFFF.toInt(), 0xD8FFFFFF.toInt(),
                0xFFD9C0FF.toInt(), 0xFFFFFFFF.toInt())
        }
        val skin = skin(theme)
        val light = skin in setOf(Skin.LIGHT, Skin.TITANIUM, Skin.HIPPIE,
            Skin.PASTEL, Skin.SAGE, Skin.PAPER)
        val text = if (light) 0xFF25313A.toInt() else 0xFFFFF1DE.toInt()
        val muted = if (light) 0xFF64717A.toInt() else 0xFFDACAB7.toInt()
        val accent = cleanThemeSpec(theme).accentStrong.toArgb()
        return Palette(skin, backgroundResource(skin), rowResource(skin),
            text, muted, accent, if (light) 0xFF1E2B35.toInt() else 0xFFFFFFFF.toInt())
    }

    /** Only add style actions; never replace the widget's date, event data, or click actions. */
    fun apply(context: Context, views: RemoteViews) {
        val style = colors(context)
        // Apply the classic resources as well, so switching BACK from a selected
        // skin fully restores the original widget without re-adding it to the launcher.
        style.root?.let { views.setInt(R.id.widget_root, "setBackgroundResource", it) }
        views.setTextColor(R.id.widget_date, style.text)
        views.setTextColor(R.id.widget_status, style.muted)
        views.setTextColor(R.id.widget_refresh, style.text)
        // The date badge and two shortcut pills retain their existing gradient
        // drawables; applying setBackgroundColor would destroy their rounded corners.
    }

    fun applyEvent(context: Context, views: RemoteViews) {
        val style = colors(context)
        style.row?.let { views.setInt(R.id.widget_item_root, "setBackgroundResource", it) }
        views.setTextColor(R.id.widget_item_who, style.text)
        views.setTextColor(R.id.widget_item_activity, style.text)
        views.setTextColor(R.id.widget_item_time, style.muted)
    }

    fun applyHeader(context: Context, views: RemoteViews) {
        val style = colors(context)
        views.setTextColor(R.id.widget_list_header, style.muted)
    }
}
