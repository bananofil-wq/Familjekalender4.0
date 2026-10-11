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
        NORDIC, PURE, FAMILY, CRYSTAL, NEON,
    }

    data class Palette(
        val skin: Skin,
        val root: Int?,
        val row: Int?,
        val text: Int,
        val muted: Int,
        val accent: Int,
        val onAccent: Int,
        val badgeDay: Int? = null,
        val badgeNumber: Int? = null,
        val dayInk: Int = 0xFFFFFFFF.toInt(),
        val numberInk: Int = 0xFF19191D.toInt(),
        val controlInk: Int = 0xFFFFFFFF.toInt(),
        val badgeDayPreview: Int = 0xFFFF646F.toInt(),
        val badgeNumberPreview: Int = 0xFFFFFFFF.toInt(),
        val rowPreview: Int = 0xFF41414D.toInt(),
        val controlPreview: Int = 0xFF26684E.toInt(),
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
        CleanVisualTheme.CARBON_FIBER -> Skin.CARBON
        CleanVisualTheme.NEON_PULSE -> Skin.NEON
        CleanVisualTheme.HIPPIE -> Skin.HIPPIE
        CleanVisualTheme.NORDIC_DAY_PLANNER -> Skin.NORDIC
        CleanVisualTheme.PURE_CALENDAR -> Skin.PURE
        CleanVisualTheme.FAMILY_SPECTRUM -> Skin.FAMILY
        CleanVisualTheme.MIDNIGHT_GOLD, CleanVisualTheme.LUXURY_GOLD -> Skin.GOLD
        CleanVisualTheme.PASTEL_FLOW, CleanVisualTheme.CLAY -> Skin.PASTEL
        CleanVisualTheme.CRYSTAL_GLASS, CleanVisualTheme.ICE_GLASS -> Skin.CRYSTAL
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
            Skin.WOOD -> R.drawable.widget_wood_approved_background
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
        Skin.NORDIC -> R.drawable.widget_skin_nordic_background
        Skin.PURE -> R.drawable.widget_skin_pure_background
        Skin.FAMILY -> R.drawable.widget_skin_family_background
        Skin.CRYSTAL -> R.drawable.widget_skin_crystal_background
        Skin.NEON -> R.drawable.widget_skin_neon_background
    }

    private fun rowResource(skin: Skin): Int? = when (skin) {
        Skin.CLASSIC -> null
            Skin.WOOD -> R.drawable.widget_wood_approved_row
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
        Skin.NORDIC -> R.drawable.widget_skin_nordic_row
        Skin.PURE -> R.drawable.widget_skin_pure_row
        Skin.FAMILY -> R.drawable.widget_skin_family_row
        Skin.CRYSTAL -> R.drawable.widget_skin_crystal_row
        Skin.NEON -> R.drawable.widget_skin_neon_row
    }

    private fun controlResource(skin: Skin): Int = when (skin) {
        Skin.CLASSIC -> R.drawable.widget_refresh_bg
        Skin.WOOD -> R.drawable.widget_wood_approved_control
        Skin.COPPER -> R.drawable.widget_skin_copper_control
        Skin.TITANIUM -> R.drawable.widget_skin_titanium_control
        Skin.CARBON -> R.drawable.widget_skin_carbon_control
        Skin.HIPPIE -> R.drawable.widget_skin_hippie_control
        Skin.LIGHT -> R.drawable.widget_skin_light_control
        Skin.DARK -> R.drawable.widget_skin_dark_control
        Skin.GLASS -> R.drawable.widget_skin_glass_control
        Skin.FOREST -> R.drawable.widget_skin_forest_control
        Skin.GOLD -> R.drawable.widget_skin_gold_control
        Skin.PASTEL -> R.drawable.widget_skin_pastel_control
        Skin.GOTHAM -> R.drawable.widget_skin_gotham_control
        Skin.RETRO -> R.drawable.widget_skin_retro_control
        Skin.SAGE -> R.drawable.widget_skin_sage_control
        Skin.PAPER -> R.drawable.widget_skin_paper_control
        Skin.NORDIC -> R.drawable.widget_skin_nordic_control
        Skin.PURE -> R.drawable.widget_skin_pure_control
        Skin.FAMILY -> R.drawable.widget_skin_family_control
        Skin.CRYSTAL -> R.drawable.widget_skin_crystal_control
        Skin.NEON -> R.drawable.widget_skin_neon_control
    }

    private data class BadgeTone(
        val dayColor: Int, val numberColor: Int, val dayInk: Int,
        val numberInk: Int, val rowColor: Int, val controlColor: Int,
        val controlInk: Int,
    )

    private fun tone(skin: Skin): BadgeTone? = when (skin) {
        Skin.COPPER -> BadgeTone(0xFFBC7651.toInt(), 0xFF3E2721.toInt(), 0xFFFFF9F2.toInt(), 0xFFFFF2E4.toInt(), 0xFF382523.toInt(), 0xFF885037.toInt(), 0xFFFFF2E4.toInt())
        Skin.TITANIUM -> BadgeTone(0xFF576D7C.toInt(), 0xFFEFF2F4.toInt(), 0xFFFFFFFF.toInt(), 0xFF253544.toInt(), 0xFFE3E9EC.toInt(), 0xFFCBD4DC.toInt(), 0xFF253544.toInt())
        Skin.CARBON -> BadgeTone(0xFF1977A3.toInt(), 0xFF101B2A.toInt(), 0xFFFFFFFF.toInt(), 0xFFF9FAFC.toInt(), 0xFF1E2A39.toInt(), 0xFF214563.toInt(), 0xFFF9FAFC.toInt())
        Skin.HIPPIE -> BadgeTone(0xFFD45E7C.toInt(), 0xFFFFF0BA.toInt(), 0xFFFFFFFF.toInt(), 0xFF403327.toInt(), 0xFFFFF6DE.toInt(), 0xFFEFBD70.toInt(), 0xFF403327.toInt())
        Skin.LIGHT -> BadgeTone(0xFF3376BB.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF23394F.toInt(), 0xFFFCFDFF.toInt(), 0xFFE8F0F8.toInt(), 0xFF23394F.toInt())
        Skin.DARK -> BadgeTone(0xFFE17938.toInt(), 0xFF202632.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF303742.toInt(), 0xFF48515D.toInt(), 0xFFFFFFFF.toInt())
        Skin.FOREST -> BadgeTone(0xFF558567.toInt(), 0xFF1A3027.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF273D30.toInt(), 0xFF3A5E44.toInt(), 0xFFFFFFFF.toInt())
        Skin.GOLD -> BadgeTone(0xFFBA9A59.toInt(), 0xFF191813.toInt(), 0xFF18140C.toInt(), 0xFFFFF0CC.toInt(), 0xFF28251C.toInt(), 0xFFB89B5C.toInt(), 0xFF221D12.toInt())
        Skin.PASTEL -> BadgeTone(0xFF9B81C5.toInt(), 0xFFFFF6FA.toInt(), 0xFFFFFFFF.toInt(), 0xFF443D54.toInt(), 0xFFF9F1FB.toInt(), 0xFFE2D4EE.toInt(), 0xFF443D54.toInt())
        Skin.GOTHAM -> BadgeTone(0xFF497FA5.toInt(), 0xFF0D2032.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF142B42.toInt(), 0xFF2A526C.toInt(), 0xFFFFFFFF.toInt())
        Skin.RETRO -> BadgeTone(0xFFDF8A4C.toInt(), 0xFF172D41.toInt(), 0xFF261B14.toInt(), 0xFFFFF9EC.toInt(), 0xFF233E4E.toInt(), 0xFF567A7B.toInt(), 0xFFFFF9EC.toInt())
        Skin.SAGE -> BadgeTone(0xFF718A69.toInt(), 0xFFF6F5E7.toInt(), 0xFFFFFFFF.toInt(), 0xFF344538.toInt(), 0xFFEFF1E7.toInt(), 0xFFD8E0D3.toInt(), 0xFF344538.toInt())
        Skin.PAPER -> BadgeTone(0xFF817267.toInt(), 0xFFFFF8ED.toInt(), 0xFFFFFFFF.toInt(), 0xFF3C3833.toInt(), 0xFFFBF3E7.toInt(), 0xFFE8E1D6.toInt(), 0xFF3C3833.toInt())
        Skin.NORDIC -> BadgeTone(0xFF467FBC.toInt(), 0xFFF5F9FD.toInt(), 0xFFFFFFFF.toInt(), 0xFF193C59.toInt(), 0xFFFFFFFF.toInt(), 0xFFD9E9F7.toInt(), 0xFF193C59.toInt())
        Skin.PURE -> BadgeTone(0xFF2059A0.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF182A3A.toInt(), 0xFFFFFFFF.toInt(), 0xFFD8E9FC.toInt(), 0xFF182A3A.toInt())
        Skin.FAMILY -> BadgeTone(0xFFE56F58.toInt(), 0xFFFFF6E9.toInt(), 0xFFFFFFFF.toInt(), 0xFF453B35.toInt(), 0xFFFFFDF6.toInt(), 0xFFE6F4F2.toInt(), 0xFF453B35.toInt())
        Skin.CRYSTAL -> BadgeTone(0xFF6399D2.toInt(), 0xFFF2F9FE.toInt(), 0xFFFFFFFF.toInt(), 0xFF254769.toInt(), 0xFFEAF4FC.toInt(), 0xFFC8E0F6.toInt(), 0xFF254769.toInt())
        Skin.NEON -> BadgeTone(0xFF1AA9C4.toInt(), 0xFF10121F.toInt(), 0xFF102E3B.toInt(), 0xFFF9FCFF.toInt(), 0xFF172133.toInt(), 0xFF263456.toInt(), 0xFFF9FCFF.toInt())
        Skin.CLASSIC, Skin.WOOD, Skin.GLASS -> null
    }

    private fun badgeDayResource(skin: Skin): Int? = when (skin) {
        Skin.COPPER -> R.drawable.widget_skin_copper_badge_day
        Skin.TITANIUM -> R.drawable.widget_skin_titanium_badge_day
        Skin.CARBON -> R.drawable.widget_skin_carbon_badge_day
        Skin.HIPPIE -> R.drawable.widget_skin_hippie_badge_day
        Skin.LIGHT -> R.drawable.widget_skin_light_badge_day
        Skin.DARK -> R.drawable.widget_skin_dark_badge_day
        Skin.FOREST -> R.drawable.widget_skin_forest_badge_day
        Skin.GOLD -> R.drawable.widget_skin_gold_badge_day
        Skin.PASTEL -> R.drawable.widget_skin_pastel_badge_day
        Skin.GOTHAM -> R.drawable.widget_skin_gotham_badge_day
        Skin.RETRO -> R.drawable.widget_skin_retro_badge_day
        Skin.SAGE -> R.drawable.widget_skin_sage_badge_day
        Skin.PAPER -> R.drawable.widget_skin_paper_badge_day
        Skin.NORDIC -> R.drawable.widget_skin_nordic_badge_day
        Skin.PURE -> R.drawable.widget_skin_pure_badge_day
        Skin.FAMILY -> R.drawable.widget_skin_family_badge_day
        Skin.CRYSTAL -> R.drawable.widget_skin_crystal_badge_day
        Skin.NEON -> R.drawable.widget_skin_neon_badge_day
        Skin.CLASSIC, Skin.WOOD, Skin.GLASS -> null
    }

    private fun badgeNumberResource(skin: Skin): Int? = when (skin) {
        Skin.COPPER -> R.drawable.widget_skin_copper_badge_number
        Skin.TITANIUM -> R.drawable.widget_skin_titanium_badge_number
        Skin.CARBON -> R.drawable.widget_skin_carbon_badge_number
        Skin.HIPPIE -> R.drawable.widget_skin_hippie_badge_number
        Skin.LIGHT -> R.drawable.widget_skin_light_badge_number
        Skin.DARK -> R.drawable.widget_skin_dark_badge_number
        Skin.FOREST -> R.drawable.widget_skin_forest_badge_number
        Skin.GOLD -> R.drawable.widget_skin_gold_badge_number
        Skin.PASTEL -> R.drawable.widget_skin_pastel_badge_number
        Skin.GOTHAM -> R.drawable.widget_skin_gotham_badge_number
        Skin.RETRO -> R.drawable.widget_skin_retro_badge_number
        Skin.SAGE -> R.drawable.widget_skin_sage_badge_number
        Skin.PAPER -> R.drawable.widget_skin_paper_badge_number
        Skin.NORDIC -> R.drawable.widget_skin_nordic_badge_number
        Skin.PURE -> R.drawable.widget_skin_pure_badge_number
        Skin.FAMILY -> R.drawable.widget_skin_family_badge_number
        Skin.CRYSTAL -> R.drawable.widget_skin_crystal_badge_number
        Skin.NEON -> R.drawable.widget_skin_neon_badge_number
        Skin.CLASSIC, Skin.WOOD, Skin.GLASS -> null
    }

    /** Used both by RemoteViews and its settings miniature; never changes the original/wood. */
    internal fun forPreview(theme: CleanVisualTheme?): Palette = palette(theme)

    fun colors(context: Context): Palette = palette(selectedTheme(context))

    private fun palette(theme: CleanVisualTheme?): Palette {
        if (theme == null) {
            return Palette(Skin.CLASSIC,
                R.drawable.widget_background, R.drawable.widget_event_row_bg,
                0xFFFFFFFF.toInt(), 0xD8FFFFFF.toInt(),
                0xFFD9C0FF.toInt(), 0xFFFFFFFF.toInt())
        }
        val selectedSkin = skin(theme)
        val isLight = selectedSkin in setOf(Skin.LIGHT, Skin.TITANIUM, Skin.HIPPIE,
            Skin.PASTEL, Skin.SAGE, Skin.PAPER, Skin.NORDIC, Skin.PURE,
            Skin.FAMILY, Skin.CRYSTAL)
        val text = if (isLight) 0xFF25313A.toInt() else 0xFFFFF1DE.toInt()
        // Existing Nuvarande and Trä retain their exact previous status-text color.
        val muted = if (selectedSkin == Skin.WOOD || selectedSkin == Skin.GLASS) {
            0xFFDACAB7.toInt()
        } else if (isLight) 0xFF536575.toInt() else 0xFFE1CEB7.toInt()
        val accent = cleanThemeSpec(theme).accentStrong.toArgb()
        val tone = tone(selectedSkin)
        return Palette(
            selectedSkin, backgroundResource(selectedSkin), rowResource(selectedSkin),
            text, muted, accent, if (isLight) 0xFF1E2B35.toInt() else 0xFFFFFFFF.toInt(),
            badgeDayResource(selectedSkin), badgeNumberResource(selectedSkin),
            tone?.dayInk ?: 0xFFFFFFFF.toInt(),
            tone?.numberInk ?: 0xFF19191D.toInt(),
            tone?.controlInk ?: text,
            tone?.dayColor ?: 0xFFFF646F.toInt(),
            tone?.numberColor ?: 0xFFFFFFFF.toInt(),
            tone?.rowColor ?: 0xFF41414D.toInt(),
            tone?.controlColor ?: 0xFF26684E.toInt(),
        )
    }

    /** Only add style actions; never replace the widget's date, event data, or click actions. */
    fun apply(context: Context, views: RemoteViews) {
        val style = colors(context)
        // Apply the classic resources as well, so switching BACK from a selected
        // skin fully restores the original widget without re-adding it to the launcher.
        style.root?.let { views.setInt(R.id.widget_root, "setBackgroundResource", it) }
        style.badgeDay?.let { views.setInt(R.id.widget_badge_day, "setBackgroundResource", it) }
        style.badgeNumber?.let { views.setInt(R.id.widget_badge_number, "setBackgroundResource", it) }
        if (style.badgeDay != null) {
            views.setTextColor(R.id.widget_badge_day, style.dayInk)
            views.setTextColor(R.id.widget_badge_number, style.numberInk)
        }
        views.setTextColor(R.id.widget_date, style.text)
        views.setTextColor(R.id.widget_status, style.muted)
        views.setTextColor(R.id.widget_refresh, style.text)
        if (style.skin == Skin.CLASSIC) {
            // Restore the original date/shortcut/control surfaces after a theme change.
            views.setInt(R.id.widget_todo, "setBackgroundResource", R.drawable.widget_action_green)
            views.setInt(R.id.widget_shopping, "setBackgroundResource", R.drawable.widget_action_blue)
            views.setInt(R.id.widget_refresh, "setBackgroundResource", R.drawable.widget_refresh_bg)
            views.setTextColor(R.id.widget_todo, 0xFFFFFFFF.toInt())
            views.setTextColor(R.id.widget_shopping, 0xFFFFFFFF.toInt())
        } else {
            // All three controls get the same bespoke material as the selected theme.
            val control = controlResource(style.skin)
            views.setInt(R.id.widget_todo, "setBackgroundResource", control)
            views.setInt(R.id.widget_shopping, "setBackgroundResource", control)
            views.setInt(R.id.widget_refresh, "setBackgroundResource", control)
            views.setTextColor(R.id.widget_todo, style.controlInk)
            views.setTextColor(R.id.widget_shopping, style.controlInk)
            if (style.badgeDay != null) {
                views.setTextColor(R.id.widget_refresh, style.controlInk)
            }
        }
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
