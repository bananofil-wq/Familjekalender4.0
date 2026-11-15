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
    const val SUNSET = "PREMIUM_SUNSET"
    // One-time upgrade: the user asked for Solnedgång, but the old APK
    // persisted ORIGINAL_WIDGET even after the new style was installed.
    private const val SUNSET_ACTIVATED = "widget_sunset_activated_20261114_v2"
    const val FOLLOW_CALENDAR = "FOLLOW_CALENDAR"
    private const val PREFS = "family_calendar"

    enum class Skin {
        CLASSIC, WOOD, COPPER, TITANIUM, CARBON, HIPPIE, LIGHT, DARK,
        GLASS, FOREST, GOLD, PASTEL, GOTHAM, RETRO, SAGE, PAPER,
        NORDIC, PURE, FAMILY, CRYSTAL, NEON,
        FOREST_WOOD,         OBSIDIAN,         CARBON_RED,         MIST_FOREST,         NORDIC_DUSK,
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


    /** The five additional, widget-only matte 3D designs; no calendar theme is affected. */
    data class PremiumLook(
        val id: String,
        val title: String,
        val description: String,
        val skin: Skin,
        val backgroundTop: Int,
        val backgroundBottom: Int,
        val rowTone: Int,
        val controlTone: Int,
        val text: Int,
        val muted: Int,
        val accent: Int,
        val dayInk: Int,
        val numberInk: Int,
        val controlInk: Int,
        val dayFill: Int,
        val numberFill: Int,
    )

    val premiumLooks: List<PremiumLook> = listOf(
        PremiumLook(
            "PREMIUM_FOREST_WOOD", "Skogsträ", "Mörkt valnötsträ, mjuk 3D och diskreta gröna detaljer", Skin.FOREST_WOOD,
            0xFF2F241E.toInt(), 0xFF171615.toInt(), 0xFF493930.toInt(), 0xFF705342.toInt(),
            0xFFFFF7ED.toInt(), 0xFFE5D4BE.toInt(), 0xFFD1AA7D.toInt(),
            0xFF2F2117.toInt(), 0xFFFFF7E8.toInt(), 0xFFFFF7ED.toInt(),
            0xFFD4AE7D.toInt(), 0xFF26201C.toInt()
        ),
        PremiumLook(
            "PREMIUM_OBSIDIAN", "Obsidian", "Matt svart sten, tunna mässingsådror och precisa detaljer", Skin.OBSIDIAN,
            0xFF1C1C1D.toInt(), 0xFF0D0F11.toInt(), 0xFF343334.toInt(), 0xFF4C453C.toInt(),
            0xFFF8F4ED.toInt(), 0xFFD8D2C9.toInt(), 0xFFDCB887.toInt(),
            0xFF281E16.toInt(), 0xFFFFF8EB.toInt(), 0xFFF8F4ED.toInt(),
            0xFFD9BC8F.toInt(), 0xFF1C1B19.toInt()
        ),
        PremiumLook(
            "PREMIUM_CARBON_RED", "Kolfiber Röd", "Tyst kolfibermönster, grafit och dämpade röda accenter", Skin.CARBON_RED,
            0xFF28292B.toInt(), 0xFF121416.toInt(), 0xFF383A3D.toInt(), 0xFF3C4043.toInt(),
            0xFFFAF8F8.toInt(), 0xFFD8D0D0.toInt(), 0xFFE88A84.toInt(),
            0xFF25181A.toInt(), 0xFFFCF6F5.toInt(), 0xFFFAF8F8.toInt(),
            0xFFC36663.toInt(), 0xFF18191C.toInt()
        ),
        PremiumLook(
            "PREMIUM_MIST_FOREST", "Frostdimma", "Frostat skogsglas, mjuka skuggor och lugn blågrön ton", Skin.MIST_FOREST,
            0xFF49616B.toInt(), 0xFF293F49.toInt(), 0xFF5B717A.toInt(), 0xFF718D98.toInt(),
            0xFFF9FEFF.toInt(), 0xFFE2F0F0.toInt(), 0xFFD1E8E9.toInt(),
            0xFF29414C.toInt(), 0xFFFFFFFF.toInt(), 0xFFF9FEFF.toInt(),
            0xFFBBD3D9.toInt(), 0xFF304B57.toInt()
        ),
        PremiumLook(
            "PREMIUM_NORDIC_DUSK", "Nordisk skymning", "Mörk fjällblå kväll, matt glas och en lågmäld solnedgång", Skin.NORDIC_DUSK,
            0xFF34485A.toInt(), 0xFF172838.toInt(), 0xFF455868.toInt(), 0xFF577285.toInt(),
            0xFFFFF9F3.toInt(), 0xFFE3E6E5.toInt(), 0xFFECC39D.toInt(),
            0xFF2A313A.toInt(), 0xFFFFFDF9.toInt(), 0xFFFFF9F3.toInt(),
            0xFFE6B888.toInt(), 0xFF243847.toInt()
        ),
        PremiumLook(
            SUNSET, "Solnedgång", "Originalfotot med bärnstensguld, skog och matta mörka 3D-paneler", Skin.FOREST_WOOD,
            0xFF433123.toInt(), 0xFF1B1612.toInt(), 0xFF292727.toInt(), 0xFF39312A.toInt(),
            0xFFFFFAF3.toInt(), 0xFFD4C4B6.toInt(), 0xFFE4AF6C.toInt(),
            0xFF2A1C13.toInt(), 0xFFFFF8E8.toInt(), 0xFFFFF8F2.toInt(),
            0xFFE7BC84.toInt(), 0xFF24201C.toInt()
        )
    )

    fun premiumLook(choice: String): PremiumLook? =
        premiumLooks.firstOrNull { it.id == choice }

    /**
     * The Solnedgång rollout must migrate a previously installed Nuvarande
     * widget as well, otherwise the launcher keeps the old blue layout forever.
     * Migration runs once, not on every Settings visit; future manual switches
     * between Nuvarande and Solnedgång are retained exactly.
     */
    fun savedChoice(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SUNSET_ACTIVATED, false)) {
            prefs.edit()
                .putString(PREF_KEY, SUNSET)
                .putBoolean(SUNSET_ACTIVATED, true)
                .commit()
            return SUNSET
        }
        val choice = prefs.getString(PREF_KEY, SUNSET)
        // If a retired choice is stored, safely migrate to Solnedgång.
        return if (choice == ORIGINAL || choice == SUNSET) choice else {
            prefs.edit().putString(PREF_KEY, SUNSET).apply()
            SUNSET
        }
    }

    fun selectedTheme(context: Context): CleanVisualTheme? = null

    fun saveChoice(context: Context, choice: String): Boolean {
        val normalized = if (choice == ORIGINAL || choice == SUNSET) choice else SUNSET
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY, normalized)
            .putBoolean(SUNSET_ACTIVATED, true)
            .commit()
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
        Skin.FOREST_WOOD -> R.drawable.widget_premium_forest_wood_background
        Skin.OBSIDIAN -> R.drawable.widget_premium_obsidian_background
        Skin.CARBON_RED -> R.drawable.widget_premium_carbon_red_background
        Skin.MIST_FOREST -> R.drawable.widget_premium_mist_forest_background
        Skin.NORDIC_DUSK -> R.drawable.widget_premium_nordic_dusk_background
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
        Skin.FOREST_WOOD -> R.drawable.widget_photo_forest_wood_row
        Skin.OBSIDIAN -> R.drawable.widget_photo_obsidian_row
        Skin.CARBON_RED -> R.drawable.widget_photo_carbon_red_row
        Skin.MIST_FOREST -> R.drawable.widget_photo_mist_forest_row
        Skin.NORDIC_DUSK -> R.drawable.widget_photo_nordic_dusk_row
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
        Skin.FOREST_WOOD -> R.drawable.widget_photo_forest_wood_control
        Skin.OBSIDIAN -> R.drawable.widget_photo_obsidian_control
        Skin.CARBON_RED -> R.drawable.widget_photo_carbon_red_control
        Skin.MIST_FOREST -> R.drawable.widget_photo_mist_forest_control
        Skin.NORDIC_DUSK -> R.drawable.widget_photo_nordic_dusk_control
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

    /** Dedicated square photographic + button; do not stretch wide shortcut pills. */
    private fun quickAddResource(skin: Skin): Int = when (skin) {
        Skin.FOREST_WOOD -> R.drawable.widget_photo_forest_wood_add
        Skin.OBSIDIAN -> R.drawable.widget_photo_obsidian_add
        Skin.CARBON_RED -> R.drawable.widget_carbon_red_flat_add
        Skin.MIST_FOREST -> R.drawable.widget_photo_mist_forest_add
        Skin.NORDIC_DUSK -> R.drawable.widget_photo_nordic_dusk_add
        else -> controlResource(skin)
    }

    private data class BadgeTone(
        val dayColor: Int, val numberColor: Int, val dayInk: Int,
        val numberInk: Int, val rowColor: Int, val controlColor: Int,
        val controlInk: Int,
    )

    private fun tone(skin: Skin): BadgeTone? = when (skin) {
        Skin.FOREST_WOOD, Skin.OBSIDIAN, Skin.CARBON_RED, Skin.MIST_FOREST, Skin.NORDIC_DUSK -> null
        Skin.WOOD -> BadgeTone(0xFFD2A96F.toInt(), 0xFF29231F.toInt(), 0xFF2D2117.toInt(), 0xFFFFF8EF.toInt(), 0xFF342C29.toInt(), 0xFF70553D.toInt(), 0xFFFFFFFF.toInt())
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
        Skin.CLASSIC, Skin.GLASS -> null
    }

    private fun badgeDayResource(skin: Skin): Int? = when (skin) {
        Skin.FOREST_WOOD -> R.drawable.widget_premium_forest_wood_badge_day
        Skin.OBSIDIAN -> R.drawable.widget_premium_obsidian_badge_day
        Skin.CARBON_RED -> R.drawable.widget_premium_carbon_red_badge_day
        Skin.MIST_FOREST -> R.drawable.widget_premium_mist_forest_badge_day
        Skin.NORDIC_DUSK -> R.drawable.widget_premium_nordic_dusk_badge_day
        Skin.WOOD -> R.drawable.widget_wood_approved_badge_day
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
        Skin.CLASSIC, Skin.GLASS -> null
    }

    private fun badgeNumberResource(skin: Skin): Int? = when (skin) {
        Skin.FOREST_WOOD -> R.drawable.widget_premium_forest_wood_badge_number
        Skin.OBSIDIAN -> R.drawable.widget_premium_obsidian_badge_number
        Skin.CARBON_RED -> R.drawable.widget_premium_carbon_red_badge_number
        Skin.MIST_FOREST -> R.drawable.widget_premium_mist_forest_badge_number
        Skin.NORDIC_DUSK -> R.drawable.widget_premium_nordic_dusk_badge_number
        Skin.WOOD -> R.drawable.widget_wood_approved_badge_number
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
        Skin.CLASSIC, Skin.GLASS -> null
    }

    private fun refinedPreviewSurfaces(skin: Skin): Pair<Int, Int>? = when (skin) {
        Skin.FOREST_WOOD -> 0xFF493930.toInt() to 0xFF705342.toInt()
        Skin.OBSIDIAN -> 0xFF343334.toInt() to 0xFF4C453C.toInt()
        Skin.CARBON_RED -> 0xFF383A3D.toInt() to 0xFF3C4043.toInt()
        Skin.MIST_FOREST -> 0xFF5B717A.toInt() to 0xFF718D98.toInt()
        Skin.NORDIC_DUSK -> 0xFF455868.toInt() to 0xFF577285.toInt()
        Skin.WOOD -> 0xFF342C29.toInt() to 0xFF70553D.toInt()
        Skin.COPPER -> 0xFF393238.toInt() to 0xFF6B463A.toInt()
        Skin.TITANIUM -> 0xFFF1F6FA.toInt() to 0xFFB4D0E3.toInt()
        Skin.CARBON -> 0xFF353E49.toInt() to 0xFF314C65.toInt()
        Skin.HIPPIE -> 0xFFFCF6F8.toInt() to 0xFFF2C6B1.toInt()
        Skin.LIGHT -> 0xFFF8FAFC.toInt() to 0xFFB9D9F8.toInt()
        Skin.DARK -> 0xFF3B4653.toInt() to 0xFF526276.toInt()
        Skin.FOREST -> 0xFF354B40.toInt() to 0xFF436A55.toInt()
        Skin.GOLD -> 0xFF353333.toInt() to 0xFF684E36.toInt()
        Skin.PASTEL -> 0xFFFBF5FC.toInt() to 0xFFDFC8F5.toInt()
        Skin.GOTHAM -> 0xFF2D4558.toInt() to 0xFF456883.toInt()
        Skin.RETRO -> 0xFF374B56.toInt() to 0xFF52777C.toInt()
        Skin.SAGE -> 0xFFF4F8F1.toInt() to 0xFFC7DDC4.toInt()
        Skin.PAPER -> 0xFFFFFDF8.toInt() to 0xFFE1D0B9.toInt()
        Skin.NORDIC -> 0xFFF6FAFD.toInt() to 0xFFC4E0F4.toInt()
        Skin.PURE -> 0xFFFCFEFF.toInt() to 0xFFC7E1F8.toInt()
        Skin.FAMILY -> 0xFFFFF9F4.toInt() to 0xFFCDE8E4.toInt()
        Skin.CRYSTAL -> 0xFFF2F9FF.toInt() to 0xFFB4D9F4.toInt()
        Skin.NEON -> 0xFF293950.toInt() to 0xFF2A5B6B.toInt()
        Skin.CLASSIC, Skin.GLASS -> null
    }

    data class PreviewMaterials(
        val background: Int,
        val badgeDay: Int,
        val badgeNumber: Int,
        val todo: Int,
        val shopping: Int,
        val refresh: Int,
        val row: Int,
        val reminder: Int,
        val reminderRow: Int,
        val weekBadge: Int,
    )

    fun previewPalette(choice: String): Palette =
        premiumLook(choice)?.let { premiumPalette(it) }
            ?: if (choice == ORIGINAL) palette(null)
            else palette(resolveCleanVisualTheme(choice))

    fun previewMaterials(choice: String): PreviewMaterials {
        if (choice == SUNSET) {
            return PreviewMaterials(
                background = R.drawable.widget_sunset_material_v2,
                badgeDay = R.drawable.widget_sunset_badge_top_clean,
                badgeNumber = R.drawable.widget_sunset_badge_bottom_clean,
                todo = R.drawable.widget_sunset_control_clean,
                shopping = R.drawable.widget_sunset_control_clean,
                refresh = R.drawable.widget_sunset_add_clean,
                row = R.drawable.widget_sunset_event_clean,
                reminder = R.drawable.widget_sunset_reminder_panel,
                reminderRow = R.drawable.widget_sunset_reminder_clean,
                weekBadge = R.drawable.widget_sunset_week_badge,
            )
        }
        val style = previewPalette(choice)
        val premium = premiumLook(choice)
        val control = when (style.skin) {
            Skin.CLASSIC -> R.drawable.widget_refresh_bg
            Skin.CARBON_RED -> R.drawable.widget_carbon_red_flat_control
            else -> controlResource(style.skin)
        }
        val rem = when (premium?.skin) {
            Skin.FOREST_WOOD -> R.drawable.widget_photo_forest_wood_reminder
            Skin.OBSIDIAN -> R.drawable.widget_photo_obsidian_reminder
            Skin.CARBON_RED -> R.drawable.widget_carbon_red_flat_reminder
            Skin.MIST_FOREST -> R.drawable.widget_photo_mist_forest_reminder
            Skin.NORDIC_DUSK -> R.drawable.widget_photo_nordic_dusk_reminder
            else -> R.drawable.widget_reminder_bg
        }
        val remRow = when (premium?.skin) {
            Skin.FOREST_WOOD -> R.drawable.widget_clean_forest_wood_reminder_row
            Skin.OBSIDIAN -> R.drawable.widget_clean_obsidian_reminder_row
            Skin.CARBON_RED -> R.drawable.widget_carbon_red_flat_reminder_row
            Skin.MIST_FOREST -> R.drawable.widget_clean_mist_forest_reminder_row
            Skin.NORDIC_DUSK -> R.drawable.widget_clean_nordic_dusk_reminder_row
            else -> R.drawable.widget_reminder_line_bg
        }
        val week = when (premium?.skin) {
            Skin.FOREST_WOOD -> R.drawable.widget_premium_forest_wood_week_badge
            Skin.OBSIDIAN -> R.drawable.widget_premium_obsidian_week_badge
            Skin.CARBON_RED -> R.drawable.widget_premium_carbon_red_week_badge
            Skin.MIST_FOREST -> R.drawable.widget_premium_mist_forest_week_badge
            Skin.NORDIC_DUSK -> R.drawable.widget_premium_nordic_dusk_week_badge
            else -> R.drawable.widget_reminder_week_badge
        }
        return PreviewMaterials(
            background = style.root ?: R.drawable.widget_background,
            badgeDay = style.badgeDay ?: R.drawable.widget_date_badge_top,
            badgeNumber = style.badgeNumber ?: R.drawable.widget_date_badge_bottom,
            todo = if (style.skin == Skin.CLASSIC) R.drawable.widget_action_green else control,
            shopping = if (style.skin == Skin.CLASSIC) R.drawable.widget_action_blue else control,
            refresh = if (premium != null) quickAddResource(style.skin)
                else if (style.skin == Skin.CLASSIC) R.drawable.widget_add_gloss
                else control,
            row = if (style.skin == Skin.CARBON_RED)
                R.drawable.widget_carbon_red_flat_event
                else style.row ?: R.drawable.widget_event_row_bg,
            reminder = rem,
            reminderRow = remRow,
            weekBadge = week,
        )
    }

    /** Widget previews use the same palette as actual RemoteViews. Classic/Nuvarande remain unchanged. */
    internal fun forPreview(theme: CleanVisualTheme?): Palette = palette(theme)

    private fun premiumPalette(look: PremiumLook) = Palette(
        look.skin, backgroundResource(look.skin), rowResource(look.skin),
        look.text, look.muted, look.accent, look.text,
        badgeDayResource(look.skin), badgeNumberResource(look.skin),
        look.dayInk, look.numberInk, look.controlInk,
        look.dayFill, look.numberFill, look.rowTone, look.controlTone,
    )

    fun colors(context: Context): Palette {
        val selection = savedChoice(context)
        return premiumLook(selection)?.let { premiumPalette(it) }
            ?: palette(selectedTheme(context))
    }

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
        val refinedPreview = refinedPreviewSurfaces(selectedSkin)
        return Palette(
            selectedSkin, backgroundResource(selectedSkin), rowResource(selectedSkin),
            text, muted, accent, if (isLight) 0xFF1E2B35.toInt() else 0xFFFFFFFF.toInt(),
            badgeDayResource(selectedSkin), badgeNumberResource(selectedSkin),
            tone?.dayInk ?: 0xFFFFFFFF.toInt(),
            tone?.numberInk ?: 0xFF19191D.toInt(),
            if (selectedSkin == Skin.GLASS) (tone?.controlInk ?: text)
            else if (isLight) 0xFF23313F.toInt() else 0xFFFFFFFF.toInt(),
            tone?.dayColor ?: 0xFFFF646F.toInt(),
            tone?.numberColor ?: 0xFFFFFFFF.toInt(),
            refinedPreview?.first ?: tone?.rowColor ?: 0xFF41414D.toInt(),
            refinedPreview?.second ?: tone?.controlColor ?: 0xFF26684E.toInt(),
        )
    }

    /** Only add style actions; never replace the widget's date, event data, or click actions. */
    fun apply(context: Context, views: RemoteViews) {
        val style = colors(context)
        val premium = premiumLook(savedChoice(context))
        val sunset = savedChoice(context) == SUNSET
        // Apply the classic resources as well, so switching BACK from a selected
        // skin fully restores the original widget without re-adding it to the launcher.
        if (premium != null) {
            // The reference artwork already contains its complete polished frame.
            // Avoid painting a second XML border behind that image.
            views.setInt(R.id.widget_root, "setBackgroundResource",
                android.R.color.transparent)
        } else {
            style.root?.let {
                views.setInt(R.id.widget_root, "setBackgroundResource", it)
            }
        }
        // The reference artwork is the actual widget background, not a mock preview.
        // Existing live event rows, counters, scrolling and PendingIntents sit above it.
        val materialResource = if (sunset) R.drawable.widget_sunset_material_v2
            else WidgetReferenceImages.image(style.skin, "material")
        if (materialResource != null) {
            // RemoteViews sends a resource ID, not a large bitmap over Binder.
            // This is important for launcher stability and older Android devices.
            views.setImageViewResource(R.id.widget_premium_art, materialResource)
            views.setViewVisibility(R.id.widget_premium_art, android.view.View.VISIBLE)
            views.setInt(R.id.widget_quick_add, "setBackgroundResource",
                quickAddResource(style.skin))
            views.setInt(R.id.widget_week_bell, "setColorFilter", premium!!.accent)
            // Material buttons get real vector symbols instead of Android's
            // platform-colored emoji (blue shopping cart on red carbon).
            views.setInt(R.id.widget_todo_icon, "setColorFilter", premium.accent)
            views.setInt(R.id.widget_shopping_icon, "setColorFilter", premium.accent)
            views.setViewVisibility(R.id.widget_refresh, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_section_separator, android.view.View.GONE)
            views.setTextColor(R.id.widget_week_reminders_title, style.text)
        } else {
            // Reverting to either approved legacy theme must be lossless.
            views.setViewVisibility(R.id.widget_premium_art, android.view.View.GONE)
            views.setInt(R.id.widget_quick_add, "setBackgroundResource",
                R.drawable.widget_add_gloss)
            views.setViewVisibility(R.id.widget_refresh, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_section_separator, android.view.View.VISIBLE)
            views.setTextColor(R.id.widget_week_reminders_title, 0xFFF2F5FA.toInt())
        }
        if (style.skin == Skin.CLASSIC || style.skin == Skin.GLASS) {
            // Protect the established Nuvarande/Original date badge after changing themes.
            views.setInt(R.id.widget_badge_day, "setBackgroundResource", R.drawable.widget_date_badge_top)
            views.setInt(R.id.widget_badge_number, "setBackgroundResource", R.drawable.widget_date_badge_bottom)
            views.setTextColor(R.id.widget_badge_day, 0xFFFFFFFF.toInt())
            views.setTextColor(R.id.widget_badge_number, 0xFF19191D.toInt())
        }
        style.badgeDay?.let { views.setInt(R.id.widget_badge_day, "setBackgroundResource", it) }
        style.badgeNumber?.let { views.setInt(R.id.widget_badge_number, "setBackgroundResource", it) }
        if (style.badgeDay != null) {
            views.setTextColor(R.id.widget_badge_day, style.dayInk)
            views.setTextColor(R.id.widget_badge_number, style.numberInk)
        }
        views.setTextColor(R.id.widget_date, style.text)
        views.setTextColor(R.id.widget_status, style.muted)
        views.setTextColor(R.id.widget_warning, style.muted)
        views.setTextColor(R.id.widget_quick_add, premium?.accent ?: style.text)
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
            val control = if (style.skin == Skin.CARBON_RED) R.drawable.widget_carbon_red_flat_control else controlResource(style.skin)
            views.setInt(R.id.widget_todo, "setBackgroundResource", control)
            views.setInt(R.id.widget_shopping, "setBackgroundResource", control)
            views.setInt(R.id.widget_refresh, "setBackgroundResource", control)
            views.setTextColor(R.id.widget_todo, style.controlInk)
            views.setTextColor(R.id.widget_shopping, style.controlInk)
            if (style.badgeDay != null) {
                views.setTextColor(R.id.widget_refresh, style.controlInk)
            }
        }

        // All new skins theme their reminder card too; switching back restores
        // the exact established Nuvarande/Trä reminder resources.
        val reminderBackground = when (premium?.skin) {
            Skin.FOREST_WOOD -> R.drawable.widget_photo_forest_wood_reminder
            Skin.OBSIDIAN -> R.drawable.widget_photo_obsidian_reminder
            Skin.CARBON_RED -> R.drawable.widget_carbon_red_flat_reminder
            Skin.MIST_FOREST -> R.drawable.widget_photo_mist_forest_reminder
            Skin.NORDIC_DUSK -> R.drawable.widget_photo_nordic_dusk_reminder
            else -> R.drawable.widget_reminder_bg
        }
        val reminderRow = when (premium?.skin) {
            Skin.FOREST_WOOD -> R.drawable.widget_clean_forest_wood_reminder_row
            Skin.OBSIDIAN -> R.drawable.widget_clean_obsidian_reminder_row
            Skin.CARBON_RED -> R.drawable.widget_carbon_red_flat_reminder_row
            Skin.MIST_FOREST -> R.drawable.widget_clean_mist_forest_reminder_row
            Skin.NORDIC_DUSK -> R.drawable.widget_clean_nordic_dusk_reminder_row
            else -> R.drawable.widget_reminder_line_bg
        }
        val weekBadge = when (premium?.skin) {
            Skin.FOREST_WOOD -> R.drawable.widget_premium_forest_wood_week_badge
            Skin.OBSIDIAN -> R.drawable.widget_premium_obsidian_week_badge
            Skin.CARBON_RED -> R.drawable.widget_premium_carbon_red_week_badge
            Skin.MIST_FOREST -> R.drawable.widget_premium_mist_forest_week_badge
            Skin.NORDIC_DUSK -> R.drawable.widget_premium_nordic_dusk_week_badge
            else -> R.drawable.widget_reminder_week_badge
        }
        views.setInt(R.id.widget_week_reminders, "setBackgroundResource", reminderBackground)
        // Nuvarande: remove the extra raised capsule behind V46. It was
        // visually fighting with the week number. Show clean gold text only.
        views.setInt(
            R.id.widget_week_reminders_week, "setBackgroundResource",
            if (premium == null) android.R.color.transparent else weekBadge,
        )
        views.setTextColor(R.id.widget_week_reminders_week,
            premium?.text ?: 0xFFF0CEA0.toInt())
        intArrayOf(
            R.id.widget_birthday_1, R.id.widget_birthday_2,
            R.id.widget_private_reminder_1, R.id.widget_private_reminder_2,
            R.id.widget_private_reminder_more,
            R.id.widget_persistent_reminder_1, R.id.widget_persistent_reminder_2,
            R.id.widget_persistent_reminder_more,
            R.id.widget_week_reminder_empty,
            R.id.widget_week_reminder_1, R.id.widget_week_reminder_2,
            R.id.widget_week_reminder_3,
        ).forEach { id ->
            views.setInt(id, "setBackgroundResource", reminderRow)
            // The clean reminder pills have an inset accent marker on the
            // left. Move live text past that marker only for premium skins.
            if (premium != null) {
                val d = context.resources.displayMetrics.density
                views.setViewPadding(id,
                    (24f * d).toInt(), (5f * d).toInt(),
                    (10f * d).toInt(), (5f * d).toInt())
            }
        }
        if (sunset) {
            // The artwork is derived from the user's uploaded photograph.
            // Text and data live in RemoteViews; never bake example events into the widget.
            views.setImageViewResource(R.id.widget_premium_art, R.drawable.widget_sunset_material_v2)
            // The uploaded sample's original button graphics included opaque
            // rectangular pixels. Use clean bevels, preserving live tap targets.
            views.setInt(R.id.widget_quick_add, "setBackgroundResource", R.drawable.widget_sunset_add_clean)
            views.setInt(R.id.widget_badge_day, "setBackgroundResource", R.drawable.widget_sunset_badge_top_clean)
            views.setInt(R.id.widget_badge_number, "setBackgroundResource", R.drawable.widget_sunset_badge_bottom_clean)
            views.setTextColor(R.id.widget_badge_day, 0xFF241B16.toInt())
            views.setTextColor(R.id.widget_badge_number, 0xFFFFFAF0.toInt())
            views.setInt(R.id.widget_todo, "setBackgroundResource", R.drawable.widget_sunset_control_clean)
            views.setInt(R.id.widget_shopping, "setBackgroundResource", R.drawable.widget_sunset_control_clean)
            views.setInt(R.id.widget_week_reminders, "setBackgroundResource", R.drawable.widget_sunset_reminder_panel)
            views.setInt(R.id.widget_week_reminders_week, "setBackgroundResource", R.drawable.widget_sunset_week_badge)
            intArrayOf(
                R.id.widget_birthday_1, R.id.widget_birthday_2,
                R.id.widget_private_reminder_1, R.id.widget_private_reminder_2,
                R.id.widget_private_reminder_more,
                R.id.widget_persistent_reminder_1, R.id.widget_persistent_reminder_2,
                R.id.widget_persistent_reminder_more,
                R.id.widget_week_reminder_empty,
                R.id.widget_week_reminder_1, R.id.widget_week_reminder_2,
                R.id.widget_week_reminder_3,
            ).forEach { id ->
                views.setInt(id, "setBackgroundResource", R.drawable.widget_sunset_reminder_clean)
                // Match the reference artwork: reminder labels sit beside the
                // amber bar, not centered in the middle of each pill.
                views.setInt(id, "setGravity",
                    android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL)
            }
            // The week number must stay inside the oval on both compact and
            // expanded launchers. The XML width is fixed; do not inflate it
            // through the large padding used by the earlier version.
            views.setTextColor(R.id.widget_week_reminders_week, 0xFFFFF5E8.toInt())
        }
    }

    fun applyEvent(context: Context, views: RemoteViews) {
        val style = colors(context)
        if (savedChoice(context) == SUNSET) {
            views.setInt(R.id.widget_item_root, "setBackgroundResource",
                R.drawable.widget_sunset_event_clean)
        } else if (style.skin == Skin.CARBON_RED) {
            // Separate matte event cards sit directly on the carbon background.
            views.setInt(R.id.widget_item_root, "setBackgroundResource",
                R.drawable.widget_carbon_red_flat_event)
        } else {
            style.row?.let { views.setInt(R.id.widget_item_root, "setBackgroundResource", it) }
        }
        views.setTextColor(R.id.widget_item_who, style.text)
        views.setTextColor(R.id.widget_item_activity, style.text)
        views.setTextColor(R.id.widget_item_live_status, style.muted)
        views.setTextColor(R.id.widget_item_time, style.muted)
        // The graphite time capsule is part of the premium default only.
        // Other selectable skins keep their original flat time treatment.
        if (style.skin != Skin.CLASSIC) {
            views.setInt(R.id.widget_item_time, "setBackgroundResource", android.R.color.transparent)
        }
    }

    fun applyHeader(context: Context, views: RemoteViews) {
        val style = colors(context)
        val sunset = savedChoice(context) == SUNSET
        if (sunset) {
            // A subtle left-to-right dark scrim keeps the actual date legible
            // against the bright, changing photo without blocking the forest.
            views.setInt(R.id.widget_list_header_root, "setBackgroundResource",
                R.drawable.widget_sunset_header_scrim)
        }
        views.setTextColor(
            R.id.widget_list_header,
            if (sunset) 0xFFFFF7EF.toInt()
            else if (style.skin == Skin.CLASSIC) 0xFFF2F5FC.toInt()
            else style.muted,
        )
    }
}
